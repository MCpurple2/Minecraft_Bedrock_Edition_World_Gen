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
            int data=PeBiomePopulation.grassData(r,biome); tallGrass(w,r,x,y,z,data,90,8);
        }
        for(int i=0;i<s.deadBush;i++){int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=randomHeight(w,r,x,z);scatter(w,r,x,Math.max(0,descend(w,x,y,z)-1),z,32,0,4,0);}
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
    static void tallGrass(PePopulationWorld w, PeRandom r, int x, int y, int z,
                         int data, int attempts, int spread) {
        boolean zFirst = w.release().drawsZFirst();
        // Older ARM builds use the descended height. Later ones read the original BlockPos.
        int centerY = zFirst ? y : descend(w,x,y,z);
        for (int i=0; i<attempts; i++) {
            int first = r.nextInt(spread) - r.nextInt(spread);
            int py = centerY + r.nextInt(4) - r.nextInt(4);
            int last = r.nextInt(spread) - r.nextInt(spread);
            int px = x + (zFirst ? last : first);
            int pz = z + (zFirst ? first : last);
            if (w.isInside(px,py,pz) && w.getBlock(px,py,pz)==0 && ground(w.getBlock(px,py-1,pz))) {
                w.setBlock(px,py,pz,31,data);
            }
        }
    }
    private static boolean water(int id){return id==8||id==9;}
    static boolean ground(int id){return id==2||id==3||id==60||id==243;}
    private static boolean survive(PePopulationWorld w,int x,int y,int z,int id){
        int below=w.getBlock(x,y-1,z);
        if(id==111)return y>=0&&y<128&&water(below)&&w.getData(x,y-1,z)==0;
        if (id==39 || id==40) {
            if (w.release().hasBushMushrooms()) return (below==2 || below==3 || below==60) && bushLight(w,x,y,z);
            return y>=0 && y<128 && (below==110 || below==243 || w.rawBrightness(x,y,z)<13 && w.solidTop(x,y-1,z));
        }
        if (id == 32) {
            boolean podzol = below == 243 && w.release().compareTo(PeRelease.V0_12_3) >= 0;
            return (below == 12 || below == 159 || below == 172 || podzol) && bushLight(w,x,y,z);
        }
        if(id==31)return ground(below);
        if(id==86)return below==2&&w.solidTop(x,y-1,z);
        return ground(below) && bushLight(w,x,y,z);
    }
    private static boolean bushLight(PePopulationWorld w,int x,int y,int z) {
        // BushBlock::canSurvive stopped querying brightness in PE 0.13.
        return w.release().compareTo(PeRelease.V0_13_2) >= 0 || w.canSeeSky(x,y,z) || w.rawBrightness(x,y,z)>7;
    }
    static void scatter(PePopulationWorld w,PeRandom r,int x,int y,int z,int id,int data,int attempts,int pumpkin){
        boolean late = w.release().drawsZFirst();
        // Later builds sample pumpkins X,Z,Y; flowers, lilies and dead bushes Z,Y,X.
        boolean pumpkinOrder = late && pumpkin != 0;
        boolean zFirst = late && !pumpkinOrder;
        for(int i=0;i<attempts;i++){
            int first = r.nextInt(8)-r.nextInt(8);
            int middle = pumpkinOrder ? r.nextInt(8)-r.nextInt(8) : r.nextInt(4)-r.nextInt(4);
            int end = pumpkinOrder ? r.nextInt(4)-r.nextInt(4) : r.nextInt(8)-r.nextInt(8);
            int py = y + (pumpkinOrder ? end : middle);
            int last = pumpkinOrder ? middle : end;
            int px = x+(zFirst ? last : first), pz = z+(zFirst ? first : last);
            if(w.isInside(px,py,pz)&&w.getBlock(px,py,pz)==0&&survive(w,px,py,pz,id))w.setBlock(px,py,pz,id,pumpkin==0?data:r.nextRawInt()&3);
        }
    }
    static void reeds(PePopulationWorld w,PeRandom r,int x,int y,int z){
        int mask = w.release().drawsZFirst() ? 7 : 3;
        for(int i=0;i<20;i++){
            int px=x+(r.nextRawInt()&mask)-(r.nextRawInt()&mask),pz=z+(r.nextRawInt()&mask)-(r.nextRawInt()&mask);
            if(w.getBlock(px,y,pz)!=0)continue;
            if(!water(w.getBlock(px-1,y-1,pz))&&!water(w.getBlock(px+1,y-1,pz))&&!water(w.getBlock(px,y-1,pz-1))&&!water(w.getBlock(px,y-1,pz+1)))continue;
            int height=2+r.nextInt(r.nextInt(3)+1);
            for(int j=0;j<height;j++){
                int below=w.getBlock(px,y+j-1,pz);
                if(below==83||below==2||below==3||below==12)w.setBlock(px,y+j,pz,83,0);
            }
        }
    }
    static void cactus(PePopulationWorld w,PeRandom r,int x,int y,int z){
        for(int i=0;i<10;i++){
            int first = r.nextInt(8)-r.nextInt(8);
            int py = y+r.nextInt(4)-r.nextInt(4);
            int last = r.nextInt(8)-r.nextInt(8);
            int px = x+(w.release().drawsZFirst()?last:first), pz = z+(w.release().drawsZFirst()?first:last);
            if(w.getBlock(px,py,pz)!=0)continue;
            int height=1+r.nextInt(r.nextInt(3)+1);
            for(int j=0;j<height;j++){
                int below=w.getBlock(px,py+j-1,pz);
                if((below==12||below==81)&&!PeSurfacePatches.solid(w.getBlock(px-1,py+j,pz))&&!PeSurfacePatches.solid(w.getBlock(px+1,py+j,pz))&&!PeSurfacePatches.solid(w.getBlock(px,py+j,pz-1))&&!PeSurfacePatches.solid(w.getBlock(px,py+j,pz+1)))w.setBlock(px,py+j,pz,81,0);
            }
        }
    }
}
