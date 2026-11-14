package net.mcpe.worldgen.core;

/**
 * Float ImprovedNoise port, based on PE 0.11.1 ARMv7 init (0x001f81b8),
 * noise (0x001f83bc), and readArea (0x001f8630).
 * Array sampling intentionally preserves the original Y-cell interpolation
 * cache and the special ny==1 route; scalar sampling is not a replacement.
 */
public final class PeImprovedNoise {
    private final int[] permutations = new int[512];
    public final float offsetX;
    public final float offsetY;
    public final float offsetZ;

    public PeImprovedNoise(PeRandom random) {
        offsetX = random.nextFloat() * 256.0f;
        offsetY = random.nextFloat() * 256.0f;
        offsetZ = random.nextFloat() * 256.0f;
        for (int i = 0; i < 256; i++) permutations[i] = i;
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i) + i;
            int old = permutations[i];
            permutations[i] = permutations[j];
            permutations[j] = old;
            permutations[i + 256] = permutations[i];
        }
    }

    public float noise(float x, float y, float z) {
        float px = x + offsetX;
        float py = y + offsetY;
        float pz = z + offsetZ;
        int ix = floor(px), iy = floor(py), iz = floor(pz);
        float lx = px - (float) ix, ly = py - (float) iy, lz = pz - (float) iz;
        int xx = ix & 255, yy = iy & 255, zz = iz & 255;
        int a = permutations[xx] + yy, b = permutations[xx + 1] + yy;
        int aa = permutations[a] + zz, ab = permutations[a + 1] + zz;
        int ba = permutations[b] + zz, bb = permutations[b + 1] + zz;
        float u = fade(lx), v = fade(ly), w = fade(lz);
        float g0 = lerp(u, grad(permutations[aa], lx, ly, lz), grad(permutations[ba], lx - 1.0f, ly, lz));
        float g1 = lerp(u, grad(permutations[ab], lx, ly - 1.0f, lz), grad(permutations[bb], lx - 1.0f, ly - 1.0f, lz));
        float g2 = lerp(u, grad(permutations[aa + 1], lx, ly, lz - 1.0f), grad(permutations[ba + 1], lx - 1.0f, ly, lz - 1.0f));
        float g3 = lerp(u, grad(permutations[ab + 1], lx, ly - 1.0f, lz - 1.0f), grad(permutations[bb + 1], lx - 1.0f, ly - 1.0f, lz - 1.0f));
        return lerp(w, lerp(v, g0, g1), lerp(v, g2, g3));
    }

    /** Adds into out in X/Z/Y order. octaveScale is divided out of the result. */
    public void readArea(float[] out, float x, float y, float z, int nx, int ny, int nz,
                         float sx, float sy, float sz, float octaveScale) {
        if (out.length < Math.multiplyExact(Math.multiplyExact(nx, ny), nz)) {
            throw new IllegalArgumentException("Output array too small");
        }
        float inverseScale = 1.0f / octaveScale;
        int outIndex = 0;
        if (ny == 1) {
            for (int dx = 0; dx < nx; dx++) {
                float px = offsetX + ((float) dx + x) * sx;
                int floorX = floor(px);
                int xx = floorX & 255;
                float lx = px - (float) floorX;
                float u = fade(lx);
                for (int dz = 0; dz < nz; dz++) {
                    float pz = offsetZ + ((float) dz + z) * sz;
                    int floorZ = floor(pz);
                    int zz = floorZ & 255;
                    float lz = pz - (float) floorZ;
                    float w = fade(lz);
                    int aa = permutations[permutations[xx]] + zz;
                    int ba = permutations[permutations[xx + 1]] + zz;
                    float g0 = lerp(u, grad2(permutations[aa], lx, lz),
                        grad(permutations[ba], lx - 1.0f, 0.0f, lz));
                    float g1 = lerp(u, grad(permutations[aa + 1], lx, 0.0f, lz - 1.0f),
                        grad(permutations[ba + 1], lx - 1.0f, 0.0f, lz - 1.0f));
                    out[outIndex] = out[outIndex] + lerp(w, g0, g1) * inverseScale;
                    outIndex++;
                }
            }
            return;
        }
        int lastY = -1;
        float g0 = 0.0f, g1 = 0.0f, g2 = 0.0f, g3 = 0.0f;
        for (int dx = 0; dx < nx; dx++) {
            float px = offsetX + ((float) dx + x) * sx;
            int floorX = floor(px);
            int xx = floorX & 255;
            float lx = px - (float) floorX;
            float u = fade(lx);
            for (int dz = 0; dz < nz; dz++) {
                float pz = offsetZ + ((float) dz + z) * sz;
                int floorZ = floor(pz);
                int zz = floorZ & 255;
                float lz = pz - (float) floorZ;
                float w = fade(lz);
                for (int dy = 0; dy < ny; dy++) {
                    float py = offsetY + ((float) dy + y) * sy;
                    int floorY = floor(py);
                    int yy = floorY & 255;
                    float ly = py - (float) floorY;
                    float v = fade(ly);
                    if (dy == 0 || yy != lastY) {
                        lastY = yy;
                        int a = permutations[xx] + yy, b = permutations[xx + 1] + yy;
                        int aa = permutations[a] + zz, ab = permutations[a + 1] + zz;
                        int ba = permutations[b] + zz, bb = permutations[b + 1] + zz;
                        g0 = lerp(u, grad(permutations[aa], lx, ly, lz), grad(permutations[ba], lx - 1.0f, ly, lz));
                        g1 = lerp(u, grad(permutations[ab], lx, ly - 1.0f, lz), grad(permutations[bb], lx - 1.0f, ly - 1.0f, lz));
                        g2 = lerp(u, grad(permutations[aa + 1], lx, ly, lz - 1.0f), grad(permutations[ba + 1], lx - 1.0f, ly, lz - 1.0f));
                        g3 = lerp(u, grad(permutations[ab + 1], lx, ly - 1.0f, lz - 1.0f), grad(permutations[bb + 1], lx - 1.0f, ly - 1.0f, lz - 1.0f));
                    }
                    out[outIndex] = out[outIndex] + lerp(w, lerp(v, g0, g1), lerp(v, g2, g3)) * inverseScale;
                    outIndex++;
                }
            }
        }
    }

    private static int floor(float value) {
        int integer = (int) value;
        return value < (float) integer ? integer - 1 : integer;
    }

    private static float fade(float value) {
        return value * value * value * (value * (value * 6.0f - 15.0f) + 10.0f);
    }

    private static float lerp(float amount, float start, float end) {
        return start + amount * (end - start);
    }

    private static float grad(int hash, float x, float y, float z) {
        int h = hash & 15;
        float u = h < 8 ? x : y;
        float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static float grad2(int hash, float x, float z) {
        int h = hash & 15;
        float u = (float) (1 - ((h & 8) >> 3)) * x;
        float v = h < 4 ? 0.0f : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
