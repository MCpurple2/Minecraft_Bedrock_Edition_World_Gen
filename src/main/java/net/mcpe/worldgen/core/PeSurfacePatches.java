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
    /** Nonzero entries of the PE 0.11.1 Tile::lightBlock table. The native
     * heightmap includes leaves and water but omits flowers, grass and snow layers. */
    public static boolean blocksSkylight(int id) {
        return switch (id) {
            case 1,2,3,4,5,7,8,9,10,11,12,13,14,15,16,17,18,19,21,22,24,30,35,
                41,42,43,45,46,47,48,49,56,57,58,61,62,73,74,79,80,82,86,87,89,
                91,97,98,99,100,103,110,112,121,129,133,152,155,157,159,161,162,
                170,172,173,174,243,245,246,247,248,249,255 -> true;
            default -> false;
        };
    }
    /** PE Material flag at offset 0x0d, used to validate lake walls and the solid surface. */
    public static boolean solid(int id) {
        return switch(id) {
            case 0,6,8,9,10,11,27,31,32,37,38,39,40,50,51,59,65,66,
                78,83,104,105,106,111,127,141,142,175,244 -> false;
            default -> true;
        };
    }
}
