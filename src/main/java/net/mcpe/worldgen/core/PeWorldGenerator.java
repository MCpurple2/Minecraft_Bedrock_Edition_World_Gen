package net.mcpe.worldgen.core;

/**
 * Experimental PE 0.11.1 terrain port, recovered from the Android 740110102 build.
 * Native anchors (Ghidra VA, image base 0x10000): constructor 001d70e0,
 * getHeights 001cbaf4, prepareHeights 001e6598, loadChunk 001e6994,
 * buildSurfaces 001e68d0, Biome::buildSurfaceAtDefault 001ccd00.
 * Produces density, the common surface pass and infinite-world caves.
 * PePopulation supplies the subsequent structures and biome decoration.
 */
public final class PeWorldGenerator {
    public static final int HEIGHT = 128;
    public static final int SEA_LEVEL = 63;
    private final boolean old;
    private final PeRelease release;
    public record Spawn(int x, int y, int z) {}
    private final boolean useSpawnBounds;
    private final boolean nativeBiomeSeeds;
    private final long worldSeed;
    private Spawn initialSpawn;
    private final PeEarlyTerrain earlyTerrain;
    private final PeCaves caves;
    private final PeBiomes biomes;
    private final PeOctaveNoise lower, upper, selector, depth;
    private final PeSimplexOctaves surface;
    private final PeSpecialSurfaces specialSurfaces;
    private static float f(int bits) { return Float.intBitsToFloat(bits); }
    private static final float[] KERNEL = {
        f(0x40d7be73), f(0x41120f31), f(0x40d7be73),
        f(0x41120f31), f(0x41b2e2ac), f(0x41120f31),
        f(0x40d7be73), f(0x41120f31), f(0x40d7be73)
    };

    public PeWorldGenerator(long seed, boolean old) {
        this(seed, old, true);
    }

    public PeWorldGenerator(long seed, boolean old, boolean useSpawnBounds) {
        this(seed, old, useSpawnBounds, true);
    }

    public PeWorldGenerator(long seed, boolean old, boolean useSpawnBounds, boolean nativeBiomeSeeds) {
        this(seed, old, useSpawnBounds, nativeBiomeSeeds, PeRelease.V0_11_1);
    }

    public PeWorldGenerator(long seed, boolean old, boolean useSpawnBounds, boolean nativeBiomeSeeds, PeRelease release) {
        this(seed, old, useSpawnBounds, nativeBiomeSeeds, release, !release.hasWorldTypes());
    }

    public PeWorldGenerator(long seed, boolean old, boolean useSpawnBounds, boolean nativeBiomeSeeds, PeRelease release, boolean nativeEarly) {
        if (!release.supports(old)) throw new IllegalArgumentException(release.id());
        this.release = release;
        earlyTerrain = nativeEarly && !release.hasWorldTypes() ? new PeEarlyTerrain(seed) : null;
        this.old = old;
        this.useSpawnBounds = useSpawnBounds;
        this.nativeBiomeSeeds = nativeBiomeSeeds;
        this.worldSeed = seed;
        boolean reversedAxes = release.ordinal() >= PeRelease.V0_15_10.ordinal();
        specialSurfaces = earlyTerrain != null || release == PeRelease.V0_11_1 ? null : new PeSpecialSurfaces(seed);
        caves = old ? null : new PeCaves(seed, false, reversedAxes);
        biomes = new PeBiomes(seed, old, nativeBiomeSeeds, release);
        if (earlyTerrain != null) {
            lower = upper = selector = depth = null;
            surface = null;
            return;
        }
        PeRandom random = new PeRandom(seed);
        lower = new PeOctaveNoise(random, 16);
        upper = new PeOctaveNoise(random, 16);
        selector = new PeOctaveNoise(random, 8);
        surface = new PeSimplexOctaves(random, 4, reversedAxes);
        // Native constructs both of these even when getHeights does not sample them.
        new PeOctaveNoise(random, 10);
        depth = new PeOctaveNoise(random, 16);
        new PeOctaveNoise(random, 8);
    }

    private PeAlphaClimate alphaClimate;
    public PeAlphaClimate alphaClimate() { return alphaClimate; }
    public void useAlphaClimate(boolean enabled) {
        alphaClimate = enabled ? new PeAlphaClimate(worldSeed) : null;
        biomes.setAlphaClimate(alphaClimate);
    }

    public int structureBiomeAt(int chunkX,int chunkZ) {
        return biomes.rawArea(chunkX*4+2,chunkZ*4+2,1,1)[0];
    }

    PeBiomes biomes() { return biomes; }
    public boolean usesNativeEarlyTerrain() { return earlyTerrain != null; }
    public PeRelease release() { return release; }
    public int biomeAt(int x, int z) { return biomes.biomeAt(x, z); }
    public synchronized Spawn initialSpawn() {
        if (initialSpawn == null && earlyTerrain != null) initialSpawn = findEarlySpawn();
        if (initialSpawn == null) {
            PeBiomes spawnBiomes = nativeBiomeSeeds ? biomes : new PeBiomes(worldSeed, old, true, release);
            initialSpawn = spawnBiomes.initialSpawn();
        }
        return initialSpawn;
    }

    // Level::setInitialSpawn starts at the center and queries Dimension::isValidSpawn.
    private Spawn findEarlySpawn() {
        int x = 128, z = 128;
        PeRandom random = new PeRandom(1); // Level constructor initializes its own MT with 1.
        var columns = new java.util.HashMap<Integer, byte[]>();
        for (int attempt = 0; attempt < 1024; attempt++) {
            int cx = x >> 4, cz = z >> 4;
            byte[] blocks = columns.computeIfAbsent(cx + 16 * cz, key -> {
                byte[] chunk = new byte[16 * 16 * HEIGHT];
                earlyTerrain.fill(chunk, cx, cz);
                return chunk;
            });
            int base = ((x & 15) * 16 + (z & 15)) * HEIGHT;
            int y = 63;
            while (y < 127 && blocks[base + y + 1] != 0) y++;
            int tile = Byte.toUnsignedInt(blocks[base + y]);
            if (tile != 0 && tile != 9 && tile != 79) return new Spawn(x, 64, z);
            x = advanceEarlySpawn(x, random);
            z = advanceEarlySpawn(z, random);
        }
        return new Spawn(128, 64, 128);
    }

    private static int advanceEarlySpawn(int coordinate, PeRandom random) {
        coordinate += (random.nextRawInt() & 31) - (random.nextRawInt() & 31);
        if (coordinate < 4) return coordinate + 32;
        if (coordinate > 251) return coordinate - 32;
        return coordinate;
    }

    private PeWorldBorderLocation border;
    private boolean customBorder;

    /** Called before publishing this generator to chunk workers. Null means unbounded. */
    public void configureBorder(PeWorldBorderLocation location) {
        customBorder = true;
        border = location;
    }

    public PeWorldBorderLocation finiteBorder() {
        if (customBorder) return border;
        return defaultBorder();
    }

    private synchronized PeWorldBorderLocation defaultBorder() {
        if (!old) return null;
        if (border == null) {
            Spawn anchor = earlyTerrain != null ? new Spawn(128, 64, 128)
                : useSpawnBounds ? initialSpawn() : new Spawn(0, 0, 0);
            border = new PeWorldBorderLocation(anchor.x() & ~15, anchor.z() & ~15);
        }
        return border;
    }

    public boolean isInsideChunk(int chunkX, int chunkZ) {
        var bounds = finiteBorder();
        return bounds == null || bounds.containsChunk(chunkX, chunkZ);
    }

    public int[] biomeArea(int x, int z, int width, int height) {
        return biomes.zoomedArea(x, z, width, height);
    }

    public synchronized byte[] generateChunk(int chunkX, int chunkZ) {
        return terrainChunk(chunkX,chunkZ).blocks();
    }

    public record TerrainChunk(byte[] blocks, byte[] data) {}
    public synchronized TerrainChunk terrainChunk(int chunkX,int chunkZ) {
        byte[] blocks = new byte[16 * 16 * HEIGHT];
        byte[] metadata = new byte[blocks.length];
        if (!isInsideChunk(chunkX, chunkZ)) {
            return new TerrainChunk(blocks,metadata);
        }
        if (earlyTerrain != null) {
            earlyTerrain.fill(blocks, chunkX, chunkZ);
            return new TerrainChunk(blocks, metadata);
        }
        float[] densities = densities(chunkX, chunkZ);
        interpolate(blocks, densities);
        buildCommonSurface(blocks,metadata,chunkX,chunkZ);
        if (caves != null) caves.carve(blocks, chunkX, chunkZ);
        return new TerrainChunk(blocks,metadata);
    }

    private float[] densities(int chunkX, int chunkZ) {
        int x = chunkX * 4, z = chunkZ * 4;
        int[] raw = biomes.rawArea(x - 2, z - 2, 10, 10);
        float[] d = depth.getRegion2D(null, x, z, 5, 5, 200.0f, 200.0f);
        float[] main = selector.getRegion(null, x, 0, z, 5, 17, 5,
            f(0x4108e1e5), f(0x4088e1e5), f(0x4108e1e5));
        float[] low = lower.getRegion(null, x, 0, z, 5, 17, 5,
            f(0x442b1a5e), f(0x4455e0f6), f(0x442b1a5e));
        float[] high = upper.getRegion(null, x, 0, z, 5, 17, 5,
            f(0x442b1a5e), f(0x442b1a5e), f(0x442b1a5e));
        float[] result = new float[5 * 5 * 17];
        for (int gx = 0; gx < 5; gx++) for (int gz = 0; gz < 5; gz++) {
            float total = 0, blendedDepth = 0, blendedScale = 0;
            float center = PeBiomes.depth(raw[gx + 2 + (gz + 2) * 10]);
            // Native combines three rows per iteration, not a generic sum over 25 neighbors.
            for (int dx = 0; dx < 3; dx++) {
                int id0 = raw[gx + dx + 1 + (gz + 1) * 10];
                int id1 = raw[gx + dx + 1 + (gz + 2) * 10];
                int id2 = raw[gx + dx + 1 + (gz + 3) * 10];
                float h0 = PeBiomes.depth(id0), h1 = PeBiomes.depth(id1), h2 = PeBiomes.depth(id2);
                float w0 = KERNEL[dx] / (h0 + 2.0f);
                float w1 = KERNEL[dx + 3] / (h1 + 2.0f);
                float w2 = KERNEL[dx + 6] / (h2 + 2.0f);
                if (center < h0) w0 *= 0.5f;
                if (center < h1) w1 *= 0.5f;
                if (center < h2) w2 *= 0.5f;
                blendedDepth = blendedDepth + w0 * h0 + w1 * h1 + w2 * h2;
                total = total + w1 + w0 + w2;
                blendedScale = blendedScale + w0 * PeBiomes.scale(id0)
                    + w1 * PeBiomes.scale(id1) + w2 * PeBiomes.scale(id2);
            }
            float dn = d[gx * 5 + gz] * 0.000125f;
            if (dn < 0) dn = -(dn * 0.3f);
            dn = dn * 3.0f - 2.0f;
            if (dn < 0) {
                dn *= 0.5f;
                if (dn < -1.0f) dn = -1.0f;
                dn *= 0.35714287f;
            } else {
                if (dn > 1.0f) dn = 1.0f;
                dn *= 0.125f;
            }
            for (int gy = 0; gy < 17; gy++) {
                int index = (gx * 5 + gz) * 17 + gy;
                float gradient = (12.0f / ((blendedScale / total) * 0.9f + 0.1f))
                    * (gy + (-8.5f - (((blendedDepth / total) * 4.0f - 1.0f) * 0.125f + dn * 0.2f) * 4.25f));
                if (gradient < 0) gradient *= 4.0f;
                float blend = (main[index] * 0.1f + 1.0f) * 0.5f;
                float lo = low[index] * 0.00390625f;
                float hi = high[index] * 0.001953125f;
                float value = blend < 0 ? lo : blend > 1 ? hi : lo + (hi - lo) * blend;
                value -= gradient;
                if (gy >= 14) {
                    float fade = (gy - 13) * 0.33333334f;
                    value = fade * -10.0f + value * (1.0f - fade);
                }
                result[index] = value;
            }
        }
        return result;
    }

    private static void interpolate(byte[] blocks, float[] density) {
        for (int gx = 0; gx < 4; gx++) for (int gz = 0; gz < 4; gz++) for (int gy = 0; gy < 16; gy++) {
            int i00 = (gx * 5 + gz) * 17 + gy;
            int i01 = (gx * 5 + gz + 1) * 17 + gy;
            int i10 = ((gx + 1) * 5 + gz) * 17 + gy;
            int i11 = ((gx + 1) * 5 + gz + 1) * 17 + gy;
            float a = density[i00], b = density[i01], c = density[i10], d = density[i11];
            float da = (density[i00 + 1] - a) * 0.125f;
            float db = (density[i01 + 1] - b) * 0.125f;
            float dc = (density[i10 + 1] - c) * 0.125f;
            float dd = (density[i11 + 1] - d) * 0.125f;
            for (int iy = 0; iy < 8; iy++) {
                float near = a, far = b;
                for (int ix = 0; ix < 4; ix++) {
                    float step = (far - near) * 0.25f;
                    float v1 = step + near;
                    float v2 = step + step + near;
                    float v3 = v2 + step;
                    int y = gy * 8 + iy, x = gx * 4 + ix, z = gz * 4;
                    blocks[(x * 16 + z) * 128 + y] = block(near, y);
                    blocks[(x * 16 + z + 1) * 128 + y] = block(v1, y);
                    blocks[(x * 16 + z + 2) * 128 + y] = block(v2, y);
                    blocks[(x * 16 + z + 3) * 128 + y] = block(v3, y);
                    near += (c - a) * 0.25f;
                    far += (d - b) * 0.25f;
                }
                a += da; b += db; c += dc; d += dd;
            }
        }
    }

    private static byte block(float density, int y) {
        return density > 0 ? (byte) 1 : y < SEA_LEVEL ? (byte) 9 : 0;
    }

    /** TLS state for lake placement when postProcess immediately follows loadChunk. */
    public synchronized PeRandom lakeRandom(int chunkX,int chunkZ) {
        if (earlyTerrain != null) return earlyTerrain.fill(new byte[16 * 16 * HEIGHT], chunkX, chunkZ);
        if (caves!=null) return caves.lastSourceRandom(chunkX,chunkZ);
        byte[] blocks=new byte[16*16*HEIGHT];
        interpolate(blocks,densities(chunkX,chunkZ));
        return buildCommonSurface(blocks,new byte[blocks.length],chunkX,chunkZ);
    }

    private PeRandom buildCommonSurface(byte[] blocks, byte[] metadata, int chunkX, int chunkZ) {
        boolean planarSurface = release.ordinal() >= PeRelease.V0_14_3.ordinal();
        float[] noise = planarSurface ? surface.surface2D(chunkX * 16, chunkZ * 16)
            : surface.getRegion2D(null, chunkX * 16, chunkZ * 16, 16, 16, 0.0625f, 0.0625f, 1.0f);
        int[] ids = biomes.zoomedArea(chunkX * 16, chunkZ * 16, 16, 16);
        PeRandom random = new PeRandom(chunkX * 0x14609048 + chunkZ * 0x07ebe2d5);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int biome = ids[x + z * 16];
            int base = (x * 16 + z) * 128;
            int originalTop = top(biome), originalFiller = filler(biome);
            int noiseIndex = planarSurface ? x + z * 16 : x * 16 + z;
            int topData=0;
            if(specialSurfaces!=null) {
                float n=noise[noiseIndex];
                int wx=chunkX*16+x,wz=chunkZ*16+z;
                if(PeSpecialSurfaces.mesa(biome)) {
                    specialSurfaces.mesaColumn(blocks,metadata,base,wx,wz,biome,n,random,release.compareTo(PeRelease.V0_11_1) < 0);
                    continue;
                }
                if(biome==6 || biome==134) specialSurfaces.swamp(blocks,base,wx,wz);
                if(biome==3 || biome==20 || biome==34 || biome==131 || biome==162) {
                    if((biome==131 || biome==162) && (n<-.1f || n>.2f)) originalTop=originalFiller=13;
                    else if(n>1f && biome!=34 && biome!=20) originalTop=originalFiller=1;
                } else if(biome==163 || biome==164) {
                    if(n>1.75f) originalTop=originalFiller=1;
                    else if(n>-.5f) {originalTop=3;topData=1;}
                } else if(biome==32 || biome==33 || biome==160 || biome==161) {
                    if(n>1.75f) {originalTop=3;topData=1;}
                    else if(n>-.95) originalTop=243;
                }
            }
            int top = originalTop, filler = originalFiller;
            int thickness = (int)(noise[noiseIndex] * 0.33333334f + 3.0f + random.nextFloat() * 0.25f);
            // Early Alpha rolls for bedrock at every Y, including the air above terrain.
            boolean earlyBedrock = release.compareTo(PeRelease.V0_11_1) < 0;
            int bedrockHeight = earlyBedrock ? 0 : (random.nextRawInt() & 3) + 2;
            for (int y = 0; y < bedrockHeight; y++) blocks[base + y] = 7;
            int remaining = -1;
            for (int y = 127; y >= bedrockHeight; y--) {
                if (earlyBedrock && y <= random.nextInt(5)) {
                    blocks[base + y] = 7;
                    continue;
                }
                int id = Byte.toUnsignedInt(blocks[base + y]);
                if (id == 0) { remaining = -1; continue; }
                if (id != 1) continue;
                if (remaining == -1) {
                    if (thickness < 1) { top = 0; filler = 1; }
                    else if (y >= 59 && y < 65) { top = originalTop; filler = originalFiller; }
                    if (y < 63 && top == 0) top = cold(biome) ? 79 : 9;
                    remaining = thickness;
                    if (y >= 62) {blocks[base+y]=(byte)top; if(top==originalTop)metadata[base+y]=(byte)topData;}
                    else if (y < 56 - thickness) { filler = 1; top = 0; blocks[base + y] = 13; }
                    else blocks[base + y] = (byte) filler;
                } else if (remaining > 0) {
                    remaining--;
                    blocks[base + y] = (byte) filler;
                    if (remaining == 0 && filler == 12) {
                        remaining = (random.nextRawInt() & 3) + Math.max(y - 63, 0);
                        filler = 24;
                    }
                }
            }
        }
        return random;
    }

    private static boolean cold(int id) { return id == 10 || id == 11 || id == 12 || id == 13 || id == 26 || id == 30 || id == 31 || id == 140 || id == 158; }
    private static int top(int id) {
        if (id == 140) return 80;
        if (id == 2 || id == 17 || id == 16 || id == 26 || id == 130) return 12;
        if (id == 14 || id == 15) return 110;
        if (id == 25) return 1;
        return 2;
    }
    private static int filler(int id) {
        if (id == 2 || id == 17 || id == 16 || id == 26 || id == 130) return 12;
        if (id == 25) return 1;
        return 3;
    }
}
