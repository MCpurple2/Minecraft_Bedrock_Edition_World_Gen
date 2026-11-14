package net.mcpe.worldgen.core;

/**
 * Float surface SimplexNoise from PE 0.11.1 ARMv7 constructor (0x001c5748)
 * and add (0x001c5f64). Preserves Z/X output ordering and native operation order.
 */
public final class PeSimplexNoise {
    private static final int[][] GRADIENTS = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1},
        {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}
    };
    // APK _INIT_1: F2 differs by one ULP from evaluating the usual double expression.
    private static final float F2 = Float.intBitsToFloat(0x3ebb67ae);
    private static final float G2 = Float.intBitsToFloat(0x3e58658c);
    private final int[] permutations = new int[512];
    public final float offsetX;
    public final float offsetY;
    public final float offsetZ;

    public PeSimplexNoise(PeRandom random) {
        offsetX = random.nextFloat() * 256.0f;
        offsetY = random.nextFloat() * 256.0f;
        offsetZ = random.nextFloat() * 256.0f;
        for (int i = 0; i < 256; i++) permutations[i] = i;
        for (int i = 0; i < 256; i++) {
            int j = i + random.nextInt(256 - i);
            int old = permutations[i];
            permutations[i] = permutations[j];
            permutations[j] = old;
            permutations[i + 256] = permutations[i];
        }
    }

    /**
     * Native 3D add (0x001c6294), X/Z/Y layout. The PE 2D octave wrapper
     * samples this route at y=10; it does not call the separate 2D add above.
     * Native object offsets map +4 -> x, +8 -> y, +12 -> z.
     */
    public void add3D(float[] out, float x, float y, float z, int nx, int ny, int nz,
                      float sx, float sy, float sz, float amplitude) {
        if (out.length < Math.multiplyExact(Math.multiplyExact(nx, ny), nz)) {
            throw new IllegalArgumentException("Output array too small");
        }
        final float g3 = 0.16666667f;
        final float scaledAmplitude = amplitude * 32.0f;
        int index = 0;
        for (int dx = 0; dx < nx; dx++) {
            float px = offsetX + ((float) dx + x) * sx;
            for (int dz = 0; dz < nz; dz++) {
                float pz = offsetZ + ((float) dz + z) * sz;
                for (int dy = 0; dy < ny; dy++) {
                    float py = offsetY + ((float) dy + y) * sy;
                    // Assembly precomputes z+x outside the inner Y loop.
                    float skew = (py + (pz + px)) * 0.33333334f;
                    int ix = fastFloor(skew + px), iy = fastFloor(skew + py), iz = fastFloor(skew + pz);
                    float unskew = (float) (ix + iy + iz) * g3;
                    float lx = px - ((float) ix - unskew);
                    float ly = py - ((float) iy - unskew);
                    float lz = pz - ((float) iz - unskew);
                    int i1, j1, k1, i2, j2, k2;
                    if (lx >= ly) {
                        if (ly >= lz) {
                            i1=1; j1=0; k1=0; i2=1; j2=1; k2=0;
                        } else if (lx >= lz) {
                            i1=1; j1=0; k1=0; i2=1; j2=0; k2=1;
                        } else {
                            i1=0; j1=0; k1=1; i2=1; j2=0; k2=1;
                        }
                    } else {
                        if (ly < lz) {
                            i1=0; j1=0; k1=1; i2=0; j2=1; k2=1;
                        } else if (lx < lz) {
                            i1=0; j1=1; k1=0; i2=0; j2=1; k2=1;
                        } else {
                            i1=0; j1=1; k1=0; i2=1; j2=1; k2=0;
                        }
                    }
                    float x1=(lx+g3)-(float)i1, y1=(ly+g3)-(float)j1, z1=(lz+g3)-(float)k1;
                    float x2=(lx+0.33333334f)-(float)i2, y2=(ly+0.33333334f)-(float)j2,
                        z2=(lz+0.33333334f)-(float)k2;
                    float x3=lx-0.5f, y3=ly-0.5f, z3=lz-0.5f;
                    int xx=ix&255, yy=iy&255, zz=iz&255;
                    int h0=permutations[xx+permutations[yy+permutations[zz]]]%12;
                    int h1=permutations[xx+i1+permutations[yy+j1+permutations[zz+k1]]]%12;
                    int h2=permutations[xx+i2+permutations[yy+j2+permutations[zz+k2]]]%12;
                    int h3=permutations[xx+1+permutations[yy+1+permutations[zz+1]]]%12;
                    float t0=((0.6f-lz*lz)-ly*ly)-lx*lx;
                    float t1=((0.6f-z1*z1)-x1*x1)-y1*y1;
                    float t2=((0.6f-z2*z2)-y2*y2)-x2*x2;
                    float t3=((0.6f-z3*z3)-y3*y3)-x3*x3;
                    float c0=contribution3D(h0,lx,ly,lz,t0);
                    float c1=contribution3D(h1,x1,y1,z1,t1);
                    float c2=contribution3D(h2,x2,y2,z2,t2);
                    float c3=contribution3D(h3,x3,y3,z3,t3);
                    out[index] = out[index] + (c1+c0+c2+c3)*scaledAmplitude;
                    index++;
                }
            }
        }
    }

    private static float contribution3D(int hash, float x, float y, float z, float t) {
        if (t < 0.0f) return 0.0f;
        int[] g=GRADIENTS[hash];
        float square = t * t;
        return square * square * (x*(float)g[0]+y*(float)g[1]+z*(float)g[2]);
    }

    /** Adds output in row-major Z/X order: out[dz*nx+dx]. */
    public void add(float[] out, float x, float z, int nx, int nz, float sx, float sz, float amplitude) {
        if (out.length < Math.multiplyExact(nx, nz)) throw new IllegalArgumentException("Output array too small");
        final float scaledAmplitude = amplitude * 70.0f;
        int index = 0;
        for (int dz = 0; dz < nz; dz++) {
            // Separate native 2D overload deliberately uses the second object offset.
            float pz = offsetY + ((float) dz + z) * sz;
            for (int dx = 0; dx < nx; dx++) {
                float px = offsetX + ((float) dx + x) * sx;
                float skew = (px + pz) * F2;
                int ix = fastFloor(px + skew), iz = fastFloor(pz + skew);
                float unskew = (float) (ix + iz) * G2;
                float lx = px - ((float) ix - unskew);
                float lz = pz - ((float) iz - unskew);
                int ox = lx > lz ? 1 : 0, oz = lx > lz ? 0 : 1;
                float mx = (lx + G2) - (float) ox;
                float mz = (lz + G2) - (float) oz;
                float lastOffset = G2 + G2 - 1.0f;
                float ex = lx + lastOffset, ez = lz + lastOffset;
                int xx = ix & 255, zz = iz & 255;
                int h0 = permutations[xx + permutations[zz]] % 12;
                int h1 = permutations[xx + ox + permutations[zz + oz]] % 12;
                int h2 = permutations[xx + 1 + permutations[zz + 1]] % 12;
                float c0 = contribution(h0, lx, lz);
                float c1 = contribution(h1, mx, mz);
                float c2 = contribution(h2, ex, ez);
                out[index] = out[index] + (c1 + c0 + c2) * scaledAmplitude;
                index++;
            }
        }
    }

    private static float contribution(int hash, float x, float z) {
        float t = 0.5f - (z * z + x * x);
        if (t < 0.0f) return 0.0f;
        float square = t * t;
        return square * square * (x * (float) GRADIENTS[hash][0] + z * (float) GRADIENTS[hash][1]);
    }

    /** Original Simplex fastfloor also decrements zero and exact negative integers. */
    private static int fastFloor(float value) {
        return value > 0.0f ? (int) value : (int) value - 1;
    }
}
