package net.mcpe.worldgen.core;

/** PE Alpha 0.1.3 through 0.8.1 ARMv7 terrain pipeline; 0.1.3 addresses, from getHeights (0xc39e0) and prepareHeights (0xc3e40). */
final class PeEarlyTerrain {
    private final PeOctaveNoise lower, upper, selector, scale, depth;
    private final PeEarlySurface surface;
    private final PeAlphaClimate climate;

    PeEarlyTerrain(long seed) {
        PeRandom random = new PeRandom(seed);
        lower = new PeOctaveNoise(random, 16);
        upper = new PeOctaveNoise(random, 16);
        selector = new PeOctaveNoise(random, 8);
        surface = new PeEarlySurface(random);
        scale = new PeOctaveNoise(random, 10);
        depth = new PeOctaveNoise(random, 16);
        climate = new PeAlphaClimate(seed);
    }

    PeRandom fill(byte[] blocks, int chunkX, int chunkZ) {
        var samples = climate.samples(chunkX * 16, chunkZ * 16, 16, 16);
        float[] grid = densities(chunkX, chunkZ, samples);
        interpolate(blocks, grid, samples.temperature());
        return surface.apply(blocks, chunkX, chunkZ, samples.types(16, 16));
    }

    private float[] densities(int chunkX, int chunkZ, PeAlphaClimate.Samples samples) {
        int x = chunkX * 4, z = chunkZ * 4;
        float[] relief = scale.getRegion2D(null, x, z, 5, 5, 1.121f, 1.121f);
        float[] depths = depth.getRegion2D(null, x, z, 5, 5, 200f, 200f);
        float[] blend = selector.getRegion(null, x, 0, z, 5, 17, 5, 8.55515f, 4.277575f, 8.55515f);
        float[] low = lower.getRegion(null, x, 0, z, 5, 17, 5, 684.412f, 684.412f, 684.412f);
        float[] high = upper.getRegion(null, x, 0, z, 5, 17, 5, 684.412f, 684.412f, 684.412f);
        float[] grid = new float[5 * 5 * 17];
        for (int gx = 0; gx < 5; gx++) for (int gz = 0; gz < 5; gz++) {
            // The original uses integer division 16 / 5 for the climate stride.
            int sample = (gx * 3 + 1) * 16 + gz * 3 + 1;
            float dry = 1f - samples.rainfall()[sample] * samples.temperature()[sample];
            dry *= dry;
            int col = gx * 5 + gz;
            float variation = Math.min(1f, (relief[col] + 256f) / 512f * (1f - dry * dry));
            float h = depths[col] / 8000f;
            if (h < 0) h *= -.3f;
            h = h * 3f - 2f;
            if (h < 0) {
                h = Math.max(-1f, h * .5f) / 1.4f * .5f;
                variation = 0f;
            } else {
                h = Math.min(1f, h) * .125f;
                variation = Math.max(0f, variation);
            }
            float mid = 8.5f + 17f * h * .0625f * 4f;
            for (int gy = 0; gy < 17; gy++) {
                int i = col * 17 + gy;
                float slope = ((gy - mid) * 12f) / (variation + .5f);
                if (slope < 0) slope *= 4f;
                float alpha = (blend[i] / 10f + 1f) * .5f;
                float lo = low[i] / 512f, hi = high[i] / 512f;
                float value = (alpha < 0 ? lo : alpha > 1 ? hi : lo + (hi - lo) * alpha) - slope;
                if (gy >= 14) {
                    float t = (gy - 13) / 3f;
                    value = t * -10f + (1f - t) * value;
                }
                grid[i] = value;
            }
        }
        return grid;
    }

    private static void interpolate(byte[] blocks, float[] grid, float[] temperature) {
        for (int gx = 0; gx < 4; gx++) for (int gz = 0; gz < 4; gz++) for (int gy = 0; gy < 16; gy++) {
            int p = (gx * 5 + gz) * 17 + gy;
            float a = grid[p], b = grid[p + 17], c = grid[p + 85], d = grid[p + 102];
            float da = (grid[p + 1] - a) * .125f, db = (grid[p + 18] - b) * .125f;
            float dc = (grid[p + 86] - c) * .125f, dd = (grid[p + 103] - d) * .125f;
            for (int iy = 0; iy < 8; iy++) {
                int y = gy * 8 + iy;
                float near = a, far = b;
                for (int ix = 0; ix < 4; ix++) {
                    float value = near, step = (far - near) * .25f;
                    int first = (gx * 4 + ix) * 16 + gz * 4;
                    for (int iz = 0; iz < 4; iz++) {
                        int column = first + iz;
                        int block = value > 0f ? 1 : y >= 64 ? 0 : y == 63 && temperature[column] < .5f ? 79 : 9;
                        blocks[column * 128 + y] = (byte) block;
                        value += step;
                    }
                    near += (c - a) * .25f;
                    far += (d - b) * .25f;
                }
                a += da;
                b += db;
                c += dc;
                d += dd;
            }
        }
    }
}
