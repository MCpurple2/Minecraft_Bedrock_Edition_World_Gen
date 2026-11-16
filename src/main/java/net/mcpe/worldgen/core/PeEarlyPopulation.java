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
        veins(w, r, bx, bz, 82, 10, 32, 128);
        veins(w, r, bx, bz, 3, 20, 32, 128);
        veins(w, r, bx, bz, 13, 10, 32, 128);
        veins(w, r, bx, bz, 16, 20, 16, 128);
        veins(w, r, bx, bz, 15, 20, 8, 64);
        veins(w, r, bx, bz, 14, 2, 8, 32);
        veins(w, r, bx, bz, 73, 8, 7, 16);
        veins(w, r, bx, bz, 56, 1, 7, 16);
        veins(w, r, bx, bz, 21, 1, 6, 16);
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

    private static void veins(
            PePopulationWorld w,
            PeRandom r,
            int bx,
            int bz,
            int id,
            int count,
            int size,
            int bound) {
        for (int i = 0; i < count; i++) {
            int x = bx + r.nextInt(16), y = r.nextInt(bound);
            if (id == 21) y += r.nextInt(bound);
            int z = bz + r.nextInt(16);
            boolean clay = id == 82;
            if (clay && w.getBlock(x, y, z) != 8 && w.getBlock(x, y, z) != 9) continue;
            float angle = r.nextFloat() * (float) Math.PI;
            float extent = size * .125f;
            double dx = clay ? Math.sin(angle) * extent : PeMth.sin(angle) * extent;
            double dz = clay ? Math.cos(angle) * extent : PeMth.cos(angle) * extent;
            float x1 = (float) (x + 8 + dx), x2 = (float) (x + 8 - dx);
            float z1 = (float) (z + 8 + dz), z2 = (float) (z + 8 - dz);
            float y1 = y + 2 + r.nextInt(3), y2 = y + 2 + r.nextInt(3);
            boolean write = w.usePeOreDistribution() || id == 82 || id == 3 || id == 13;
            for (int step = 0; step <= size; step++) {
                float t = step * (float) Math.PI / size;
                float rand = r.nextFloat();
                float radius =
                        clay
                                ? (float) ((Math.sin(t) + 1.0) * (rand * (size >> 4)) + 1.0) * .5f
                                : ((PeMth.sin(t) + 1) * rand * size * .0625f + 1) * .5f;
                if (write)
                    fill(
                            w,
                            x1 + (x2 - x1) * step / size,
                            y1 + (y2 - y1) * step / size,
                            z1 + (z2 - z1) * step / size,
                            radius,
                            id);
            }
        }
    }

    private static void fill(PePopulationWorld w, float x, float y, float z, float rad, int id) {
        int substrate = id == 82 ? 12 : 1;
        for (int xx = (int) (x - rad); xx <= (int) (x + rad); xx++) {
            float dx = (xx + .5f - x) / rad;
            for (int yy = (int) (y - rad); yy <= (int) (y + rad); yy++) {
                float dy = (yy + .5f - y) / rad, xy = dx * dx + dy * dy;
                if (xy >= 1) continue;
                for (int zz = (int) (z - rad); zz <= (int) (z + rad); zz++) {
                    float dz = (zz + .5f - z) / rad;
                    if (xy + dz * dz < 1
                            && w.isInside(xx, yy, zz)
                            && w.getBlock(xx, yy, zz) == substrate) w.setBlock(xx, yy, zz, id, 0);
                }
            }
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
