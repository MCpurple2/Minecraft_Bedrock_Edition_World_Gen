package net.mcpe.worldgen.core;

import java.util.Arrays;

/**
 * PE 0.11.1 float PerlinNoise octave wrapper. Native getRegion at 0x001cb9e4
 * and 2D overload at 0x001cbaac. firstOctave controls skipped sampling levels;
 * all noise instances still consume their constructor draws in order.
 */
public final class PeOctaveNoise {
    private final PeImprovedNoise[] octaves;
    private final int firstOctave;

    public PeOctaveNoise(PeRandom random, int count) {
        this(random, count, 0);
    }

    public PeOctaveNoise(PeRandom random, int count, int firstOctave) {
        if (count < 0) throw new IllegalArgumentException("Negative octave count");
        this.firstOctave = Math.max(0, firstOctave);
        this.octaves = new PeImprovedNoise[count];
        for (int i = 0; i < count; i++) octaves[i] = new PeImprovedNoise(random);
    }

    public float[] getRegion(float[] out, float x, float y, float z, int nx, int ny, int nz,
                             float sx, float sy, float sz) {
        int length = Math.multiplyExact(Math.multiplyExact(nx, ny), nz);
        if (out == null || out.length < length) out = new float[length];
        else Arrays.fill(out, 0, length, 0.0f);
        float amplitude = 1.0f;
        for (int i = 0; i < firstOctave; i++) amplitude *= 0.5f;
        for (int i = firstOctave; i < octaves.length; i++) {
            octaves[i].readArea(out, x, y, z, nx, ny, nz,
                amplitude * sx, amplitude * sy, amplitude * sz, amplitude);
            amplitude *= 0.5f;
        }
        return out;
    }

    /** The original 2D overload ignores its final float argument and uses y=10. */
    public float[] getRegion2D(float[] out, int x, int z, int nx, int nz, float sx, float sz) {
        return getRegion(out, (float) x, 10.0f, (float) z, nx, 1, nz, sx, 1.0f, sz);
    }
}
