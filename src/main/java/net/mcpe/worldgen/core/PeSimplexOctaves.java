package net.mcpe.worldgen.core;

import java.util.Arrays;

/** PE 0.11.1 PerlinSimplexNoise::_init and getRegion float octave pipeline. */
public final class PeSimplexOctaves {
    private final PeSimplexNoise[] octaves;

    public PeSimplexOctaves(PeRandom random, int count) {
        this(random, count, false);
    }

    PeSimplexOctaves(PeRandom random, int count, boolean reverseOffsets) {
        if (count < 0) throw new IllegalArgumentException("Negative octave count");
        octaves=new PeSimplexNoise[count];
        for (int i=0;i<count;i++) octaves[i]=new PeSimplexNoise(random, reverseOffsets);
    }

    public float[] getRegion(float[] out,float x,float y,float z,int nx,int ny,int nz,
                             float sx,float sy,float sz) {
        int length=Math.multiplyExact(Math.multiplyExact(nx,ny),nz);
        if (out==null||out.length<length) out=new float[length];
        else Arrays.fill(out,0,length,0.0f);
        float scale=1.0f;
        for (PeSimplexNoise octave:octaves) {
            octave.add3D(out,x,y,z,nx,ny,nz,scale*sx,scale*sy,scale*sz,scale);
            scale*=0.5f;
        }
        return out;
    }

    /** PE 0.14+ uses the actual 2D simplex overload, scale 1/8 and four weighted octaves. */
    public float[] surface2D(int x, int z) {
        float[] samples = new float[16 * 16];
        float octaveScale = 1.0f;
        for (PeSimplexNoise octave : octaves) {
            octave.add(samples, x, z, 16, 16, octaveScale * 0.125f, octaveScale * 0.125f,
                0.55f / octaveScale);
            octaveScale *= 0.5f;
        }
        return samples;
    }

    /** Native integer 2D wrapper discards its last argument and samples 3D at y=10. */
    public float[] getRegion2D(float[] out,int x,int z,int nx,int nz,float sx,float sz,float ignoredAmplitude) {
        return getRegion(out,(float)x,10.0f,(float)z,nx,1,nz,sx,1.0f,sz);
    }
}
