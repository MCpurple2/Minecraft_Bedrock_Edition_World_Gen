package net.mcpe.worldgen.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Alpha 0.16.2 OceanMonumentFeature placement and MonumentBuilding exterior. */
final class PeOceanMonuments {
    private record Column(boolean foundation, byte[] runs) {}
    private record Start(int x, int z, int direction, int rotationSeed) {}
    private static final int OFFSET = 8, SIDE = 80;
    private final long seed;
    private final PeBiomes biomes;
    private final Map<Long, Optional<Start>> starts = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Optional<Start>> entry) { return size() > 256; }
    };

    PeOceanMonuments(long seed, PeBiomes biomes) { this.seed = seed; this.biomes = biomes; }

    private static final class Template {
        static final Column[][] COLUMNS = read();
        private static Column[][] read() {
            Column[][] columns = new Column[SIDE][SIDE];
            try (var in = new DataInputStream(PeOceanMonuments.class.getResourceAsStream("/pe_worldgen/monument_exterior.bin"))) {
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    int x = in.readByte(), z = in.readByte();
                    boolean foundation = in.readBoolean();
                    byte[] runs = new byte[in.readUnsignedByte() * 4];
                    in.readFully(runs);
                    columns[x + OFFSET][z + OFFSET] = new Column(foundation, runs);
                }
            } catch (IOException e) { throw new ExceptionInInitializerError(e); }
            return columns;
        }
    }

    private Optional<Start> find(int regionX, int regionZ) {
        var random = new PeRandom((int)seed + regionX * 0x9939f508 + regionZ * 0xf1565bd5 + 0x9e7f71);
        int cx = regionX * 32 + (random.nextInt(27) + random.nextInt(27)) / 2;
        int cz = regionZ * 32 + (random.nextInt(27) + random.nextInt(27)) / 2;
        int x = cx * 16 + 8, z = cz * 16 + 8;
        if (!oceanArea(x,z,16,true) || !oceanArea(x,z,29,false)) return Optional.empty();
        random.setSeed(seed);
        int rotationSeed = (int)seed ^ (int)Math.max(0, random.nextFloat() * cx) ^ (int)Math.max(0, random.nextFloat() * cz);
        random.setSeed(rotationSeed);
        return Optional.of(new Start(x - 29, z - 29, 2 + (random.nextRawInt() & 3), rotationSeed));
    }
    private boolean oceanArea(int x, int z, int radius, boolean deep) {
        int minX = (x-radius)>>2, minZ = (z-radius)>>2;
        int[] area = biomes.rawArea(minX,minZ,((x+radius)>>2)-minX+1,((z+radius)>>2)-minZ+1);
        for (int id : area) if (id != 24 && (deep || id != 0 && id != 7 && id != 10 && id != 11)) return false;
        return true;
    }
    void populate(PePopulationWorld world, PeRandom random, int cx, int cz) {
        for (int rz=(cz-3)>>5; rz<=(cz+3)>>5; rz++) for (int rx=(cx-3)>>5; rx<=(cx+3)>>5; rx++) {
            long key = ((long)rx<<32) | Integer.toUnsignedLong(rz);
            var start = starts.get(key);
            if (start == null) { start=find(rx,rz); starts.put(key,start); }
            if (start.isEmpty()) continue;
            Start s = start.get();
            int left = cx * 16, top = cz * 16;
            if (left+15 < s.x-6 || left > s.x+64 || top+15 < s.z-6 || top > s.z+64) continue;
            for (int x=left; x<left+16; x++) for (int z=top; z<top+16; z++) {
                int dx=x-s.x, dz=z-s.z;
                int u=s.direction<4 ? dx : dz;
                int v=switch (s.direction) { case 2 -> 57-dz; case 3 -> dz; case 4 -> 57-dx; default -> dx; };
                if (u+OFFSET<0 || u+OFFSET>=SIDE || v+OFFSET<0 || v+OFFSET>=SIDE) continue;
                Column col=Template.COLUMNS[u+OFFSET][v+OFFSET];
                if (col==null) continue;
                for (int i=0; i<col.runs.length; i+=4) {
                    int id=Byte.toUnsignedInt(col.runs[i+2]);
                    for(int y=39+col.runs[i]; y<=39+col.runs[i+1]; y++) world.setBlock(x,y,z,id,col.runs[i+3]);
                }
                if (col.foundation) for (int y=38; y>1; y--) {
                    int id=world.getBlock(x,y,z);
                    if (id!=0 && id!=8 && id!=9 && id!=10 && id!=11) break;
                    world.setBlock(x,y,z,168,2);
                }
            }
            world.populateMonumentRooms(s.x,s.z,s.direction,s.rotationSeed,random,cx,cz);
        }
    }
}
