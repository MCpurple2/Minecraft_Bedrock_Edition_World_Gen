package net.mcpe.worldgen.core;

/** Native post-tree BiomeDecorator passes (00196500) and small scatter features.
 * Light/solid-top queries are explicitly provided by the world adapter; their
 * parity with PE's pre-light-update state remains a separate integration test.
 * Immediate flowing-liquid ticks and grass-color byte generation are not here. */
public final class PeVegetation {
    private PeVegetation() {}
    public static void decorate(PePopulationWorld w,PeRandom r,int biome,int bx,int bz,boolean old,PeDecorationSettings s){
        for(int i=0;i<s.bigMushrooms;i++){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15);PeTrees.hugeMushroom(w,r,x,PeSurfacePatches.solidSurface(w,x,z),z,-1);}
        for(int i=0;i<s.flowers;i++){
            int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=bounded(r,w.surfaceY(x,z)+32);
            int flower=PeBiomePopulation.flower(w,r,biome,x,z);if((flower&255)!=0)scatter(w,r,x,y,z,flower&255,flower>>>8,64,0);
        }
        if(!old)for(int i=0;i<s.grass;i++){
            int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=randomHeight(w,r,x,z);
            int data=PeBiomePopulation.grassData(r,biome);scatter(w,r,x,descend(w,x,y,z),z,31,data,90,0);
        }
        for(int i=0;i<s.deadBush;i++){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=randomHeight(w,r,x,z);scatter(w,r,x,descend(w,x,y,z),z,32,0,4,0);}
        for(int i=0;i<s.lilies;i++){
            int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=randomHeight(w,r,x,z);
            while(y>0&&w.getBlock(x,y-1,z)==0)y--;scatter(w,r,x,y,z,111,0,10,0);
        }
        for(int i=0;i<s.mushrooms;i++){
            if((r.nextRawInt()&3)==0){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15);scatter(w,r,x,w.surfaceY(x,z),z,39,0,64,0);}
            if((r.nextRawInt()&7)==0){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=randomHeight(w,r,x,z);scatter(w,r,x,y,z,40,0,64,0);}
        }
        if((r.nextRawInt()&3)==0){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=randomHeight(w,r,x,z);scatter(w,r,x,y,z,39,0,64,0);}
        if((r.nextRawInt()&7)==0){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=randomHeight(w,r,x,z);scatter(w,r,x,y,z,40,0,64,0);}
        for(int i=0;i<s.reeds;i++){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=randomHeight(w,r,x,z);reeds(w,r,x,y,z);}
        for(int i=0;i<10;i++){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=randomHeight(w,r,x,z);reeds(w,r,x,y,z);}
        if((r.nextRawInt()&31)==0){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=bounded(r,w.surfaceY(x,z));scatter(w,r,x,y,z,86,0,64,1);}
        for(int i=0;i<s.cacti;i++){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=randomHeight(w,r,x,z);cactus(w,r,x,y,z);}
    }
    private static int bounded(PeRandom r,int n){return n==0?0:r.nextInt(n);}
    private static int randomHeight(PePopulationWorld w,PeRandom r,int x,int z){return bounded(r,w.surfaceY(x,z)*2);}
    private static int descend(PePopulationWorld w,int x,int y,int z){int id;while(y>0&&((id=w.getBlock(x,y,z))==0||id==18||id==161))y--;return y;}
    private static boolean water(int id){return id==8||id==9;}
    private static boolean ground(int id){return id==2||id==3||id==60||id==243;}
    private static boolean survive(PePopulationWorld w,int x,int y,int z,int id){
        int below=w.getBlock(x,y-1,z);
        if(id==111)return y>=0&&y<128&&water(below)&&w.getData(x,y-1,z)==0;
        if(id==39||id==40)return y>=0&&y<128&&(below==110||below==243||w.rawBrightness(x,y,z)<13&&w.solidTop(x,y-1,z));
        if(id==32)return (below==12||below==159||below==172)&&(w.canSeeSky(x,y,z)||w.rawBrightness(x,y,z)>7);
        if(id==31)return ground(below);
        if(id==86)return below==2&&w.solidTop(x,y-1,z);
        return ground(below)&&(w.canSeeSky(x,y,z)||w.rawBrightness(x,y,z)>7);
    }
    private static void scatter(PePopulationWorld w,PeRandom r,int x,int y,int z,int id,int data,int attempts,int pumpkin){
        for(int i=0;i<attempts;i++){
            int px=x+(r.nextRawInt()&7)-(r.nextRawInt()&7),py=y+(r.nextRawInt()&3)-(r.nextRawInt()&3),pz=z+(r.nextRawInt()&7)-(r.nextRawInt()&7);
            if(w.isInside(px,py,pz)&&w.getBlock(px,py,pz)==0&&survive(w,px,py,pz,id))w.setBlock(px,py,pz,id,pumpkin==0?data:r.nextRawInt()&3);
        }
    }
    private static void reeds(PePopulationWorld w,PeRandom r,int x,int y,int z){
        for(int i=0;i<20;i++){
            int px=x+(r.nextRawInt()&3)-(r.nextRawInt()&3),pz=z+(r.nextRawInt()&3)-(r.nextRawInt()&3);
            if(w.getBlock(px,y,pz)!=0)continue;
            if(!water(w.getBlock(px-1,y-1,pz))&&!water(w.getBlock(px+1,y-1,pz))&&!water(w.getBlock(px,y-1,pz-1))&&!water(w.getBlock(px,y-1,pz+1)))continue;
            int height=2+r.nextInt(r.nextInt(3)+1);
            for(int j=0;j<height;j++){
                int below=w.getBlock(px,y+j-1,pz);
                if(below==83||below==2||below==3||below==12)w.setBlock(px,y+j,pz,83,0);
            }
        }
    }
    private static void cactus(PePopulationWorld w,PeRandom r,int x,int y,int z){
        for(int i=0;i<10;i++){
            int px=x+(r.nextRawInt()&7)-(r.nextRawInt()&7),py=y+(r.nextRawInt()&3)-(r.nextRawInt()&3),pz=z+(r.nextRawInt()&7)-(r.nextRawInt()&7);
            if(w.getBlock(px,py,pz)!=0)continue;
            int height=1+r.nextInt(r.nextInt(3)+1);
            for(int j=0;j<height;j++){
                int below=w.getBlock(px,py+j-1,pz);
                if((below==12||below==81)&&!PeSurfacePatches.solid(w.getBlock(px-1,py+j,pz))&&!PeSurfacePatches.solid(w.getBlock(px+1,py+j,pz))&&!PeSurfacePatches.solid(w.getBlock(px,py+j,pz-1))&&!PeSurfacePatches.solid(w.getBlock(px,py+j,pz+1)))w.setBlock(px,py+j,pz,81,0);
            }
        }
    }
}
