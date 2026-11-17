package net.mcpe.worldgen.core;

/** PE Alpha finite-world clay beds and the eight rock-replacement passes. */
final class PeEarlyOres {
    private PeEarlyOres() {}

    static void generate(PePopulationWorld w, PeRandom r, int bx, int bz) {
        generate(w, r, bx, bz, true);
    }

    static void generate(PePopulationWorld w, PeRandom r, int bx, int bz, boolean writeClay) {
        legacyClay(w, r, bx, bz, writeClay);
        veins(w, r, bx, bz, 3, 20, 32, 128);
        veins(w, r, bx, bz, 13, 10, 32, 128);
        veins(w, r, bx, bz, 16, 20, 16, 128);
        veins(w, r, bx, bz, 15, 20, 8, 64);
        veins(w, r, bx, bz, 14, 2, 8, 32);
        veins(w, r, bx, bz, 73, 8, 7, 16);
        veins(w, r, bx, bz, 56, 1, 7, 16);
        veins(w, r, bx, bz, 21, 1, 6, 16);
    }

    static void legacyClay(PePopulationWorld w, PeRandom r, int bx, int bz, boolean write) {
        // Keep the original draws even when placement is disabled.
        for (int i = 0; i < 10; i++) {
            int x = bx + r.nextInt(16), y = r.nextInt(128), z = bz + r.nextInt(16);
            placeVein(w, r, x, y, z, 82, 32, write);
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
            placeVein(w, r, x, y, z, id, size);
        }
    }

    static void placeVein(PePopulationWorld w, PeRandom r, int x, int y, int z, int id, int size) {
        placeVein(w, r, x, y, z, id, size, true);
    }

    private static void placeVein(PePopulationWorld w, PeRandom r, int x, int y, int z, int id, int size, boolean enabled) {
        boolean clay = id == 82;
        if (clay && w.getBlock(x, y, z) != 8 && w.getBlock(x, y, z) != 9) return;
        float angle = r.nextFloat() * (float) Math.PI;
        float extent = size * .125f;
        boolean floatClay = clay && w.release().compareTo(PeRelease.V0_7_6) < 0;
        double dx = clay ? Math.sin(angle) : PeMth.sin(angle);
        double dz = clay ? Math.cos(angle) : PeMth.cos(angle);
        dx = floatClay ? (float) dx * extent : dx * extent;
        dz = floatClay ? (float) dz * extent : dz * extent;
        float x1 = (float) (x + 8 + dx), x2 = (float) (x + 8 - dx);
        float z1 = (float) (z + 8 + dz), z2 = (float) (z + 8 - dz);
        float y1 = y + 2 + r.nextInt(3), y2 = y + 2 + r.nextInt(3);
        boolean write = enabled && (w.usePeOreDistribution() || clay || id == 3 || id == 13);
        for (int step = 0; step <= size; step++) {
            float t = step * (float) Math.PI / size;
            float rand = r.nextFloat();
            float radius;
            if (floatClay) {
                radius = (((float) Math.sin(t) + 1f) * (size >> 4) * rand + 1f) * .5f;
            } else if (clay) {
                radius = (float) ((Math.sin(t) + 1.0) * (rand * (size >> 4)) + 1.0) * .5f;
            } else {
                float span = rand * size * .0625f;
                radius = ((PeMth.sin(t) + 1f) * span + 1f) * .5f;
            }
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

}
