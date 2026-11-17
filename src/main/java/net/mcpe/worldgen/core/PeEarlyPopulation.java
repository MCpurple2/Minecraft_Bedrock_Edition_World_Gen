package net.mcpe.worldgen.core;

import java.util.Set;

/** Finite RandomLevelSource::postProcess, before Pocket Edition 0.9. */
final class PeEarlyPopulation {
    private final PeImprovedNoise[] forestNoise = new PeImprovedNoise[8];
    private final PeAlphaClimate historicalClimate;

    PeEarlyPopulation(long seed) {
        historicalClimate = new PeAlphaClimate(seed);
        PeRandom init = new PeRandom(seed);
        // Seven earlier Perlin generators have 16+16+8+4+4+10+16 octaves.
        // Each ImprovedNoise::init consumes three offsets and 256 shuffle draws.
        for (int i = 0; i < 74 * 259; i++) init.nextRawInt();
        for (int i = 0; i < forestNoise.length; i++) forestNoise[i] = new PeImprovedNoise(init);
    }

    private float forestNoise(int x, int z) {
        float result = 0, scale = 1;
        for (PeImprovedNoise octave : forestNoise) {
            result += octave.noise(x * .5f * scale, z * .5f * scale, 0) / scale;
            scale *= .5f;
        }
        return result;
    }

    void decorate(
            PePopulationWorld w,
            PeRandom r,
            int bx,
            int bz,
            PeAlphaClimate.Type climate,
            int biome,
            Set<PeContent> flags) {
        int density = (int) ((r.nextFloat() * 4 + forestNoise(bx, bz) * .125f + 4) / 3);
        int count = r.nextInt(10) == 0 ? 1 : 0;
        PeAlphaClimate.Type local =
                climate != null ? climate : historicalClimate.area(bx + 16, bz + 16, 1, 1)[0];
        count +=
                switch (local) {
                    case FOREST, RAINFOREST -> density + 2;
                    case SEASONAL_FOREST, TAIGA -> density + 1;
                    case DESERT, TUNDRA, PLAINS -> -20;
                    default -> 0;
                };
        for (int i = 0; i < count; i++) {
            int x = bx + 8 + r.nextInt(16), z = bz + 8 + r.nextInt(16);
            PeTrees.Selected type = PeTrees.select(r, biome, climate, flags);
            PeEarlyTrees.place(type, w, r, x, w.surfaceY(x, z), z);
        }
        plants(w, r, bx, bz, 37, 2);
        if (r.nextInt(2) == 0) plants(w, r, bx, bz, 38, 1);
        if (r.nextInt(4) == 0) plants(w, r, bx, bz, 39, 1);
        if (r.nextInt(8) == 0) plants(w, r, bx, bz, 40, 1);
        plants(w, r, bx, bz, 83, 10);
        if (local == PeAlphaClimate.Type.DESERT) plants(w, r, bx, bz, 81, 5);
        PeSprings.decorate(w, r, bx + 8, bz + 8);
        coverSnow(w, bx + 8, bz + 8);
    }

    private static void plants(PePopulationWorld w, PeRandom r, int bx, int bz, int id, int count) {
        for (int i = 0; i < count; i++) {
            int x = bx + 8 + r.nextInt(16), y = r.nextInt(128), z = bz + 8 + r.nextInt(16);
            if (id == 83) PeVegetation.reeds(w, r, x, y, z);
            else if (id == 81) PeVegetation.cactus(w, r, x, y, z);
            else PeVegetation.scatter(w, r, x, y, z, id, 0, 64, 0);
        }
    }

    private void coverSnow(PePopulationWorld w, int bx, int bz) {
        float[] temperatures = historicalClimate.temperatures(bx, bz, 16, 16);
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                int x = bx + dx, z = bz + dz, y = w.topSolidY(x, z, true);
                if (y <= 0
                        || y >= 128
                        || temperatures[dx * 16 + dz] - (y - 64) * .015625f * .3f >= .5f) continue;
                int below = w.getBlock(x, y - 1, z);
                if (w.getBlock(x, y, z) == 0 && below != 79 && PeSurfacePatches.solid(below))
                    w.setBlock(x, y, z, 78, 0);
            }
    }
}
