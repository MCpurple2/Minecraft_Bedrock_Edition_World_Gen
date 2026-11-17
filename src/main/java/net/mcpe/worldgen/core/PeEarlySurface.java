package net.mcpe.worldgen.core;

/** Surface pass shared by the 0.1.3 through 0.8.1 ARM generators (RandomLevelSource::buildSurfaces). */
final class PeEarlySurface {
    private final PeOctaveNoise beach;
    private final PeOctaveNoise soil;

    PeEarlySurface(PeRandom random) {
        beach = new PeOctaveNoise(random, 4);
        soil = new PeOctaveNoise(random, 4);
    }

    PeRandom apply(byte[] blocks, int chunkX, int chunkZ, PeAlphaClimate.Type[] biomes) {
        int originX = chunkX * 16, originZ = chunkZ * 16;
        float[] sand = beach.getRegion(null, originX, originZ, 0, 16, 16, 1, .03125f, .03125f, 1f);
        float[] gravel = beach.getRegion(null, originX, 109.0134f, originZ, 16, 1, 16, .03125f, 1f, .03125f);
        float[] depth = soil.getRegion(null, originX, originZ, 0, 16, 16, 1, .0625f, .0625f, .0625f);
        PeRandom random = new PeRandom(chunkX * 0x14609048 + chunkZ * 0x07ebe2d5);
        // Native column traversal increments X before Z. RNG draws depend on this order.
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int column = x * 16 + z;
                boolean sandy = sand[column] + random.nextFloat() * .2f > 0f;
                boolean gravelly = gravel[column] + random.nextFloat() * .2f > 3f;
                int thickness = (int)(depth[column] / 3f + 3f + random.nextFloat() * .25f);
                boolean desert = biomes[x + z * 16] == PeAlphaClimate.Type.DESERT;
                fillColumn(blocks, column * 128, desert, sandy, gravelly, thickness, random);
            }
        }
        return random;
    }

    private static void fillColumn(byte[] blocks, int base, boolean desert, boolean sandy,
                                   boolean gravelly, int thickness, PeRandom random) {
        byte top = (byte)(desert ? 12 : 2);
        byte filler = (byte)(desert ? 12 : 3);
        int remaining = -1;
        for (int y = 127; y >= 0; y--) {
            int index = base + y;
            if (y <= random.nextInt(5)) {
                blocks[index] = 7;
            } else if (blocks[index] == 0) {
                remaining = -1;
            } else if (blocks[index] == 1) {
                if (remaining == -1) {
                    if (thickness <= 0) {
                        top = 0;
                        filler = 1;
                    } else if (y >= 60 && y <= 65) {
                        top = (byte)(desert ? 12 : 2);
                        filler = (byte)(desert ? 12 : 3);
                        if (gravelly) {
                            top = 0;
                            filler = 13;
                        }
                        if (sandy) {
                            top = 12;
                            filler = 12;
                        }
                    }
                    if (y < 64 && top == 0) top = 9;
                    blocks[index] = y >= 63 ? top : filler;
                    remaining = thickness;
                } else if (remaining > 0) {
                    blocks[index] = filler;
                    if (--remaining == 0 && filler == 12) {
                        remaining = random.nextRawInt() & 3;
                        filler = 24;
                    }
                }
            }
        }
    }
}
