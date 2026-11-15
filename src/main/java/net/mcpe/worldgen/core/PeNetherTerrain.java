package net.mcpe.worldgen.core;

/** PE 0.12.3 HellRandomLevelSource. Coarse lattice 5 x 17 x 5; solids occupy Y=0..127. */
public final class PeNetherTerrain {
    private final PeOctaveNoise low, high, blend, patches, thickness;

    private final PeCaves caves;

    public PeNetherTerrain(long seed) {
        caves = new PeCaves(seed,true);
        PeRandom random = new PeRandom(seed);
        low = new PeOctaveNoise(random, 16);
        high = new PeOctaveNoise(random, 16);
        blend = new PeOctaveNoise(random, 8);
        patches = new PeOctaveNoise(random, 4);
        thickness = new PeOctaveNoise(random, 4);
        // The remaining two octaves in the native constructor have no influence on density.
    }

    float[] densities(int cx, int cz) {
        float x = cx * 4, z = cz * 4;
        float horizontal = Float.intBitsToFloat(0x442b1a5e), vertical = Float.intBitsToFloat(0x450053c6);
        float[] a = low.getRegion(null,x,0,z,5,17,5,horizontal,vertical,horizontal);
        float[] b = high.getRegion(null,x,0,z,5,17,5,horizontal,vertical,horizontal);
        float[] values = blend.getRegion(null,x,0,z,5,17,5,Float.intBitsToFloat(0x4108e1e5),
            Float.intBitsToFloat(0x4208e1e5),Float.intBitsToFloat(0x4108e1e5));
        float angleStep = ((float)Math.PI * 10430.378f) * (6f / 17f);
        float[] curve = new float[17];
        for(int y=0;y<17;y++) {
            int index = (int)(16384f + angleStep * y) & 65535;
            curve[y] = 2f * PeMth.indexed(index);
            int distance = Math.min(y, 16-y);
            if(distance<4) {int d=4-distance;curve[y]-=10*d*d*d;}
        }
        for(int i=0;i<values.length;i++) {
            int y=i%17;
            float t=(1f+values[i]*.1f)*.5f;
            float lo=a[i]/512f, hi=b[i]/512f;
            float v=(t<0?lo:t>1?hi:lo+(hi-lo)*t)-curve[y];
            if(y>13) {float fade=(y-13)*(1f/3f);v=-10f*fade+v*(1f-fade);}
            values[i]=v;
        }
        return values;
    }

    public byte[] chunk(int cx, int cz) {
        float[] noise=densities(cx,cz);
        byte[] blocks=new byte[32768];
        for(int gx=0;gx<4;gx++) for(int gz=0;gz<4;gz++) for(int gy=0;gy<16;gy++) {
            int i=(gx*5+gz)*17+gy;
            float a=noise[i],b=noise[i+17],c=noise[i+85],d=noise[i+102];
            float da=(noise[i+1]-a)/8,db=(noise[i+18]-b)/8,dc=(noise[i+86]-c)/8,dd=(noise[i+103]-d)/8;
            for(int dy=0;dy<8;dy++){
                int y=gy*8+dy;
                float left=a,right=b;
                for(int dx=0;dx<4;dx++){
                    float n=left,step=(right-left)/4;
                    for(int dz=0;dz<4;dz++) {
                        blocks[((gx*4+dx)*16+gz*4+dz)*128+y]=(byte)(n>0?87:y<32?10:0);
                        n+=step;
                    }
                    left+=(c-a)/4;right+=(d-b)/4;
                }
                a+=da;b+=db;c+=dc;d+=dd;
            }
        }
        buildSurfaces(blocks,cx,cz);
        caves.carve(blocks,cx,cz);
        return blocks;
    }

    private void buildSurfaces(byte[] blocks,int cx,int cz) {
        // Native buildSurfaces converts ChunkPos to TilePos, then multiplies those coordinates by 16.
        float x=cx*256,z=cz*256;
        float[] soul=patches.getRegion(null,x,z,0,16,16,1,1f/32,1f/32,1);
        float[] gravel=patches.getRegion(null,x,109,z,16,1,16,1f/32,1,1f/32);
        float[] depth=thickness.getRegion(null,x,z,0,16,16,1,1f/16,1f/16,1f/16);
        PeRandom r=new PeRandom(cx*0x9939f508+cz*0xf1565bd5);
        for(int dz=0;dz<16;dz++) for(int dx=0;dx<16;dx++) {
            int i=dx*16+dz;
            boolean sand=soul[i]+r.nextFloat()*.2f>0;
            boolean pebbles=gravel[i]+r.nextFloat()*.2f>0;
            int layers=(int)(depth[i]*(1f/3f)+3f+r.nextFloat()*.25f);
            int remain=-1,top=87,under=87;
            for(int y=127;y>=0;y--) {
                int pos=i*128+y;
                if(y>=127-r.nextInt(5)||y<=r.nextInt(5)) {blocks[pos]=7;continue;}
                int id=blocks[pos];
                if(id==0) {remain=-1;continue;}
                if(id!=87) continue;
                if(remain<0){
                    if(layers<=0){top=0;under=87;}
                    else if(y>=60 && y<=65){top=sand?88:pebbles?13:87;under=sand?88:87;}
                    if(top==0&&y<64)top=11;
                    remain=layers;
                    blocks[pos]=(byte)(y>=63?top:under);
                }else if(remain>0){remain--;blocks[pos]=(byte)under;}
            }
        }
    }
}

