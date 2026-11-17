package net.mcpe.worldgen.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.stream.IntStream;

/** Resolves competing finite-world veins in PE's chunk load order, regardless of JE worker order. */
public final class PeEarlyOreLayout {
    private final PeWorldGenerator core;
    private final long seed;
    private final LinkedHashMap<Long, PeWorldGenerator.TerrainChunk> terrain = new LinkedHashMap<>(64, .75f, true);
    private final LinkedHashMap<Long, Veins> veins = new LinkedHashMap<>(64, .75f, true);

    private record Veins(int[][] writes, PeRandom after) {}
    private record Source(int x, int z, int loadX, int loadZ, int priority) {}

    public PeEarlyOreLayout(PeWorldGenerator core, long seed) {
        this.core = core;
        this.seed = seed;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | Integer.toUnsignedLong(z);
    }

    private PeWorldGenerator.TerrainChunk base(int x, int z) {
        var result = terrain.computeIfAbsent(key(x,z), k -> core.terrainChunk(x,z));
        if (terrain.size() > 128) terrain.pollFirstEntry();
        return result;
    }

    public synchronized PeWorldGenerator.TerrainChunk chunk(int x, int z) {
        var base = base(x, z);
        byte[] blocks = base.blocks().clone();
        if (!core.isInsideChunk(x, z)) return new PeWorldGenerator.TerrainChunk(blocks, base.data().clone());
        var sources = new ArrayList<Source>(4);
        for (int sx = x-1; sx <= x; sx++) for (int sz = z-1; sz <= z; sz++) {
            if (!core.isInsideChunk(sx, sz)) continue;
            // prepareLevel visits X outer, Z inner. Out-of-bounds neighbors count as already loaded.
            int east = core.isInsideChunk(sx+1, sz) ? 1 : 0;
            int south = core.isInsideChunk(sx, sz+1) ? 1 : 0;
            sources.add(new Source(sx, sz, sx+east, sz+south, east + 2*south));
        }
        sources.sort(Comparator.comparingInt(Source::loadX).thenComparingInt(Source::loadZ)
                .thenComparingInt(Source::priority));
        for (var source : sources) {
            int quadrant = (x-source.x)*2 + z-source.z;
            for (int write : veins(source.x, source.z).writes[quadrant]) {
                int index = write >>> 8, id = write & 255;
                if (blocks[index] == (id == 82 ? 12 : 1)) blocks[index] = (byte)id;
            }
        }
        return new PeWorldGenerator.TerrainChunk(blocks, base.data().clone());
    }

    public synchronized PeRandom afterOres(int cx, int cz) {
        return veins(cx, cz).after.copy();
    }

    private Veins veins(int cx, int cz) {
        long key = key(cx,cz);
        Veins result = veins.get(key);
        if (result != null) return result;
        // Veins and clay stay within the source chunk and its three positive neighbors.
        // Their placement rolls depend on water at the clay origin. Veins cannot replace water.
        var world = new VeinWorld(cx,cz);
        PeRandom random = new PeRandom(PePopulation.seed(seed,cx,cz));
        PeEarlyOres.generate(world,random,cx*16,cz*16);
        int[][] writes = Arrays.stream(world.changed).map(b -> b.build().toArray()).toArray(int[][]::new);
        result = new Veins(writes, random);
        veins.put(key,result);
        if (veins.size() > 128) veins.pollFirstEntry();
        return result;
    }

    private final class VeinWorld implements PePopulationWorld {
        final int cx, cz;
        final byte[][] blocks = new byte[4][];
        final IntStream.Builder[] changed = new IntStream.Builder[4];
        VeinWorld(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            for (int dx=0; dx<2; dx++) for (int dz=0; dz<2; dz++) {
                int i = dx*2 + dz;
                if (core.isInsideChunk(cx+dx, cz+dz)) blocks[i] = base(cx+dx, cz+dz).blocks().clone();
                changed[i] = IntStream.builder();
            }
        }
        private int quadrant(int x, int z) {
            return ((x >> 4) - cx) * 2 + (z >> 4) - cz;
        }
        private static int index(int x, int y, int z) { return (((x & 15)*16 + (z & 15)) << 7) + y; }
        @Override public boolean isInside(int x, int y, int z) {
            return y >= 0 && y < 128 && blocks[quadrant(x,z)] != null;
        }
        @Override public int getBlock(int x,int y,int z) {
            return isInside(x,y,z) ? blocks[quadrant(x,z)][index(x,y,z)] & 255 : 95;
        }
        @Override public void setBlock(int x,int y,int z,int id,int data) {
            int q = quadrant(x,z), i = index(x,y,z);
            blocks[q][i] = (byte)id;
            changed[q].add((i << 8) | id);
        }
        @Override public PeRelease release() { return core.release(); }
        @Override public int surfaceY(int x,int z) { throw new UnsupportedOperationException(); }
        @Override public int biomeAt(int x,int z) { throw new UnsupportedOperationException(); }
    }
}
