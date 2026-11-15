package net.mcpe.worldgen.core;

/** Native ForestBiome::decorate 002aa1bc and PlainsBiome::decorate 002a7d30
 * prepasses, which precede ores and the common decorator. */
public final class PeBiomePopulation {
    private PeBiomePopulation() {}
    static final PeBiomeNoise FLOWER_NOISE=new PeBiomeNoise(new PeRandom(2345));
    /** Returns native FullTile packed as id | metadata << 8. */
    public static int flower(PePopulationWorld w,PeRandom r,int id,int x,int z){
        int b=id>=128?id-128:id;
        if(b==6)return 38|(1<<8);
        if(id==132){
            float f=FLOWER_NOISE.value((float)x*Float.intBitsToFloat(0x3caaaaab),(float)z*Float.intBitsToFloat(0x3caaaaab))*.5f+.5f;
            int data=f>.9999f?8:f<=0?0:(int)(f*9f);if(data==1)data=0;
            return 38|(data<<8);
        }
        if(b==1){
            if((double)FLOWER_NOISE.value((float)x*.005f,(float)z*.005f)<-.8)return 38|(((r.nextRawInt()&3)+4)<<8);
            if(r.nextInt(3)==0)return 37;
            int data=switch(r.nextInt(3)){case 0->0;case 1->3;default->8;};return 38|(data<<8);
        }
        return 37;
    }
    public static int grassData(PeRandom r,int id){
        int b=id>=128?id-128:id;
        if(b==21||b==22||b==23)return (r.nextRawInt()&3)==0?2:1;
        if(b==5||b==19||b==30||b==31||b==32||b==33)return r.nextInt(5)==0?1:2;
        return 1;
    }
    public static void preDecorate(PePopulationWorld w,PeRandom r,int id,int bx,int bz,boolean old,PeDecorationSettings settings){
        preDecorate(w,r,id,bx,bz,old,settings,PeContent.defaults(w.release(),old));
    }

    static void preDecorate(PePopulationWorld w,PeRandom r,int id,int bx,int bz,boolean old,
                            PeDecorationSettings settings,java.util.Set<PeContent> flags) {
        int b=id>=128?id-128:id;
        PeBiomeExtras.pre(w,r,id,bx,bz,old);
        if(id==4||id==18||id==27||id==28||id==29||id==132){
            if(b==29)for(int gx=1;gx<17;gx+=4)for(int gz=1;gz<17;gz+=4){
                int x=bx+gx+r.nextInt(3),z=bz+gz+r.nextInt(3),y=w.surfaceY(x,z);
                if(r.nextInt(20)==0)PeTrees.hugeMushroom(w,r,x,y,z,-1);
                else {PeTrees.select(r,id,null,flags).place(w,r,x,y,z);if(!old)PePopulation.grassAroundTree(w,r,x,y,z,20);}
            }
            int count=r.nextInt(5)-(id==132?1:3);
            for(int i=0;i<count;i++){
                int type=switch(r.nextInt(3)){case 0->1;case 1->4;default->5;};
                for(int attempt=0;attempt<5;attempt++){
                    int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=r.nextInt(w.surfaceY(x,z)+32);
                    if(doublePlant(w,r,x,y,z,type))break;
                }
            }
        }else if(b==1){
            // ARM literal 002a7fe0=0x3ba3d70a, multiplication before scalar noise.
            float noise=FLOWER_NOISE.value((float)(bx+8)*.005f,(float)(bz+8)*.005f);
            if((double)noise<-.8){settings.flowers=15;settings.grass=5;}
            else if(!old){
                settings.flowers=4;settings.grass=10;
                for(int i=0;i<7;i++){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=r.nextInt(w.surfaceY(x,z)+32);doublePlant(w,r,x,y,z,2);}
            }
            if(id==129)for(int i=0;i<10;i++){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=r.nextInt(w.surfaceY(x,z)+32);doublePlant(w,r,x,y,z,0);}
        }
    }
    public static void postDecorate(PePopulationWorld w,PeRandom r,int id,int bx,int bz,boolean old){
        PeBiomeExtras.post(w,r,id,bx,bz);
    }
    /** DoublePlantFeature::place 001f5510: all 64 candidates are sampled even
     * after success. Upper half is metadata 8 regardless of the lower species. */
    public static boolean doublePlant(PePopulationWorld w,PeRandom r,int x,int y,int z,int type){
        boolean success=false;
        for(int i=0;i<64;i++){
            int first = r.nextInt(8)-r.nextInt(8);
            int py = y+r.nextInt(4)-r.nextInt(4);
            int last = r.nextInt(8)-r.nextInt(8);
            boolean zFirst = w.release().drawsZFirst();
            int px = x+(zFirst ? last : first), pz = z+(zFirst ? first : last);
            if(py<0||py>=126||w.getBlock(px,py,pz)!=0||w.getBlock(px,py+1,pz)!=0)continue;
            int floor=w.getBlock(px,py-1,pz);
            if(floor==2||floor==3||floor==60){w.setBlock(px,py,pz,175,type);w.setBlock(px,py+1,pz,175,8);success=true;}
        }return success;
    }
}
