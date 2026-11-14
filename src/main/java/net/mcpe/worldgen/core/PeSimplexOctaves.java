package net.mcpe.worldgen.core;

import java.util.Arrays;

/** PE 0.11.1 PerlinSimplexNoise::_init and getRegion float octave pipeline. */
public final class PeSimplexOctaves {
    private final PeSimplexNoise[] octaves;

    public PeSimplexOctaves(PeRandom random, int count) {
        if (count < 0) throw new IllegalArgumentException("Negative octave count");
        octaves=new PeSimplexNoise[count];
        for (int i=0;i<count;i++) octaves[i]=new PeSimplexNoise(random);
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

    /** Native integer 2D wrapper discards its last argument and samples 3D at y=10. */
    public float[] getRegion2D(float[] out,int x,int z,int nx,int nz,float sx,float sz,float ignoredAmplitude) {
        return getRegion(out,(float)x,10.0f,(float)z,nx,1,nz,sx,1.0f,sz);
    }
}
