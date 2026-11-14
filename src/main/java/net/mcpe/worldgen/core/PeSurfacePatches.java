package net.mcpe.worldgen.core;

/** The three pre-tree disk passes in BiomeDecorator::decorate (00196500).
 * Counts/order/radii are the native constructor defaults: sand3, clay1, gravel1. */
public final class PeSurfacePatches {
    private PeSurfacePatches() {}
    public static void decorate(PePopulationWorld w,PeRandom r,int bx,int bz) {
        decorate(w,r,bx,bz,3,1,1);
    }
    public static void decorate(PePopulationWorld w,PeRandom r,int bx,int bz,int sand,int clay,int gravel) {
        disks(w,r,bx,bz,sand,12,7,false);
        disks(w,r,bx,bz,clay,82,4,true);
        disks(w,r,bx,bz,gravel,13,6,false);
    }
    private static void disks(PePopulationWorld w,PeRandom r,int bx,int bz,int count,int id,int max,boolean clay) {
        for(int i=0;i<count;i++) {
            int x=bx+(r.nextRawInt()&15),z=bz+(r.nextRawInt()&15),y=solidSurface(w,x,z);
            int center=w.getBlock(x,y,z);
            if(center!=8&&center!=9&&!(clay&&center==82))continue;
            int radius=max==2?2:r.nextInt(max-2)+2;
            for(int xx=x-radius;xx<=x+radius;xx++)for(int zz=z-radius;zz<=z+radius;zz++) {
                if((xx-x)*(xx-x)+(zz-z)*(zz-z)>radius*radius)continue;
                int bottom=y-(clay?1:2),top=y+(clay?1:2);
                for(int yy=bottom;yy<=top;yy++) {
                    int current=w.getBlock(xx,yy,zz);
                    if(current==3||(!clay&&current==2))w.setBlock(xx,yy,zz,id,0);
                }
            }
        }
    }
    /** LevelChunk::getTopSolidBlock(false), 001f50b0: ignore leaves and fluids. */
    public static int solidSurface(PePopulationWorld w,int x,int z) {
        return w.topSolidY(x,z,false);
    }
    public static boolean solid(int id) {
        return switch(id) {
            case 0,6,8,9,10,11,27,30,31,32,37,38,39,40,50,51,59,63,65,66,68,
                78,83,104,105,106,111,127,141,142,175 -> false;
            default -> true;
        };
    }
}
