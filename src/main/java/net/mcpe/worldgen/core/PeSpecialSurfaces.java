package net.mcpe.worldgen.core;

import java.util.Arrays;

/** PE biome-specific topsoil and mesa bands. All state is fixed when the world is seeded. */
final class PeSpecialSurfaces {
    private final byte[] bands = new byte[64];
    private final PeBiomeNoise bandOffset;
    private final PeBiomeNoise[] pillars = new PeBiomeNoise[4];
    private final PeBiomeNoise pillarCap;

    PeSpecialSurfaces(long seed) {
        var random = new PeRandom(seed);
        bandOffset = new PeBiomeNoise(random);
        Arrays.fill(bands,(byte)16);
        for(int y=0; y<64; y++) {
            y+=random.nextInt(5)+1;
            if(y<64) bands[y]=1;
        }
        stripes(random,4,1);
        stripes(random,12,2);
        stripes(random,14,1);
        int y=0, count=random.nextInt(3)+3;
        for(int i=0;i<count;i++) {
            y+=4+random.nextInt(16);
            if(y>=64) continue;
            bands[y]=0;
            if(y>1 && (random.nextRawInt() & 0x8000000)!=0) bands[y-1]=8;
            if(y<63 && (random.nextRawInt() & 0x8000000)!=0) bands[y+1]=8;
        }
        random.setSeed(seed);
        for(int i=0;i<pillars.length;i++) pillars[i]=new PeBiomeNoise(random);
        pillarCap=new PeBiomeNoise(random);
    }
    private void stripes(PeRandom random,int color,int minimum) {
        int count=2+random.nextInt(4);
        for(int i=0;i<count;i++) {
            int length=minimum+random.nextInt(3), y=random.nextInt(64);
            Arrays.fill(bands,y,Math.min(64,y+length),(byte)color);
        }
    }
    static boolean mesa(int id) { return id>=37 && id<=39 || id>=165 && id<=167; }
    void swamp(byte[] blocks,int column,int x,int z) {
        float noise=PeBiomePopulation.FLOWER_NOISE.value(x*.25f,z*.25f);
        if(noise<=0) return;
        int y=127;
        while(y>62 && blocks[column+y]==0) y--;
        if(y==62 && blocks[column+y]!=0 && blocks[column+y]!=9) {
            blocks[column+y]=9;
            if(noise<.12f) blocks[column+y+1]=111;
        }
    }
    private int pillarHeight(int x,int z,float surface) {
        float value=0,scale=1f;
        for(var octave:pillars) {
            value+=octave.value(.25f*x*scale,.25f*z*scale)/scale;
            scale*=.5f;
        }
        value=Math.min(Math.abs(surface),value);
        if(value<=0) return 0;
        float cap=Math.abs(pillarCap.value(x/512f,z/512f));
        return (int)(64+Math.min(value*value*2.5f,14+Math.ceil(cap*50)));
    }
    void mesaColumn(byte[] blocks,byte[] meta,int column,int x,int z,int biome,float noise,PeRandom random) {
        int bedrock=2+random.nextInt(4);
        Arrays.fill(blocks,column,column+bedrock,(byte)7);
        int pillar=biome==165 ? pillarHeight(x,z,noise) : 0;
        int thickness=(int)(noise*(1f/3)+3f+random.nextFloat()*.25f);
        boolean smooth=Math.cos(noise*(1f/3)*(float)Math.PI)>0;
        boolean plateau=biome==38 || biome==166;
        int offset=(int)Math.rint(2f*bandOffset.value(x/512f,z/512f));
        int depth=-1,touched=0;
        boolean sandy=false;
        int filler=172;
        for(int y=127;y>=bedrock;y--) {
            int i=column+y,id=Byte.toUnsignedInt(blocks[i]);
            if((id==0 || id==9) && y<pillar) {blocks[i]=1;id=1;}
            if(touched>=15) continue;
            if(id==0) {depth=-1;continue;}
            if(id!=1) continue;
            if(depth<0) {
                sandy=false;
                if(thickness<=0) filler=1;
                else if(y>=59 && y<=64) filler=172;
                depth=thickness+Math.max(0,y-63);
                if(y<62) {blocks[i]=(byte)filler; if(filler==159)meta[i]=1;}
                else if(plateau && y>86+thickness*2) {
                    blocks[i]=(byte)(smooth?3:2);
                    if(smooth)meta[i]=1;
                } else if(y>66+thickness) {
                    int color=(y<64 || y>127)?1:(smooth?16:bands[(offset+y)&63]);
                    clay(blocks,meta,i,color);
                } else {blocks[i]=12;meta[i]=1;sandy=true;}
            } else if(depth>0) {
                depth--;
                clay(blocks,meta,i,sandy?1:bands[(offset+y)&63]);
            }
            touched++;
        }
    }
    private static void clay(byte[] blocks,byte[] meta,int index,int color) {
        blocks[index]=(byte)(color==16?172:159);
        meta[index]=(byte)(color==16?0:color);
    }
}
