package net.mcpe.worldgen.core;

/** Final RandomLevelSource::postProcess rain-height freeze/snow pass.
 * shouldFreezeIgnoreNeighbors 002a517c calls shouldFreeze(...,false); no shore
 * predicate is used. Temperature is the native biome's constant virtual getter,
 * not modern Java's altitude-adjusted biome temperature. */
public final class PeSnow {
    private PeSnow() {}
    public static void apply(PePopulationWorld w,int bx,int bz) {
        apply(w,bx,bz,true,true);
    }
    static void freeze(PePopulationWorld w,int bx,int bz){apply(w,bx,bz,true,false);}
    static void cover(PePopulationWorld w,int bx,int bz){apply(w,bx,bz,false,true);}
    private static void apply(PePopulationWorld w,int bx,int bz,boolean freeze,boolean snow) {
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++) {
            int x=bx+dx,z=bz+dz;
            if(!cold(w.biomeAt(x,z)))continue;
            int y=rainY(w,x,z);
            int below=w.getBlock(x,y-1,z);
            if(freeze&&y>0&&y<=128&&(below==8||below==9)&&w.getData(x,y-1,z)==0&&w.blockBrightness(x,y-1,z)<10)
                w.setBlock(x,y-1,z,79,0);
            if(!snow||y<0||y>=128||w.blockBrightness(x,y,z)>=10||w.getBlock(x,y,z)!=0)continue;
            below=w.getBlock(x,y-1,z);
            if((below==18||below==161||w.solidRender(x,y-1,z))&&PeSurfacePatches.solid(below))w.setBlock(x,y,z,78,0);
        }
    }
    private static int rainY(PePopulationWorld w,int x,int z) {
        // LevelChunk::getTopRainTilePos 001f53f4: blocksMotion || liquid.
        for(int y=127;y>0;y--){int id=w.getBlock(x,y,z);if(PeSurfacePatches.solid(id)||id>=8&&id<=11)return y+1;}
        return -1;
    }
    private static boolean cold(int id){return switch(id){case 10,11,12,13,26,30,31,140,158->true;default->false;};}
}
