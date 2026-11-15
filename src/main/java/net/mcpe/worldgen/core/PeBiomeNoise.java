package net.mcpe.worldgen.core;

/** Scalar simplex used by PE biomes. Coordinates omit the octave offsets. */
final class PeBiomeNoise {
        private static final int[][] G={{1,1},{-1,1},{1,-1},{-1,-1},{1,0},{-1,0},{1,0},{-1,0},{0,1},{0,-1},{0,1},{0,-1}};
        private final int[] p=new int[512];
        PeBiomeNoise(PeRandom r){r.nextRawInt();r.nextRawInt();r.nextRawInt();for(int i=0;i<256;i++)p[i]=i;for(int i=0;i<256;i++){int j=i+r.nextInt(256-i),t=p[i];p[i]=p[j];p[j]=t;p[i+256]=p[i];}}
        float value(float x,float z){
            float sqrt3=Float.intBitsToFloat(0x3fddb3d7),f=(sqrt3*.5f-.5f)*(x+z),g=(3f-sqrt3)*.16666667f;
            int ix=floor(f+x),iz=floor(f+z);float unskew=(float)(ix+iz)*g;
            float lx=x-((float)ix-unskew),lz=z-((float)iz-unskew);
            int ox=lx>lz?1:0,oz=lx>lz?0:1;float mx=(lx+g)-(float)ox,mz=(lz+g)-(float)oz,last=g+g-1f,ex=lx+last,ez=lz+last;
            int xx=ix&255,zz=iz&255;
            float c0=contribution(p[xx+p[zz]]%12,lx,lz),c1=contribution(p[xx+ox+p[zz+oz]]%12,mx,mz),c2=contribution(p[xx+1+p[zz+1]]%12,ex,ez);
            return (c1+c0+c2)*70f;
        }
        private static int floor(float f){return f>0?(int)f:(int)f-1;}
        private static float contribution(int hash,float x,float z){float t=.5f-(z*z+x*x);if(t<0)return 0;float square=t*t;return square*square*(x*(float)G[hash][0]+z*(float)G[hash][1]);}
    }
