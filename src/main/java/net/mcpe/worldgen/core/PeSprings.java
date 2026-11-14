package net.mcpe.worldgen.core;

/** BiomeDecorator spring tail and SpringFeature::place 00171bc0.
 * The immediate LiquidTileDynamic::tick is specialized to the source-level
 * spring invariant: above/below rock, exactly three rock sides and one air side.
 * Consequently slope-distance selection can only write that one open side.
 * Subsequent queued liquid ticks are intentionally separate from this first tick. */
public final class PeSprings {
    private PeSprings() {}
    private static final int[][] SIDES={{-1,0},{1,0},{0,-1},{0,1}};
    private static final int[][] FIRE_SIDES={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    public static void decorate(PePopulationWorld w,PeRandom r,int bx,int bz) {
        // Normal ThreadData+0x2210 is null, making the decorator radius Float.MAX_VALUE.
        for(int i=0;i<50;i++) {
            int x=bx+(r.nextRawInt()&15),bound=r.nextInt(120)+8,y=r.nextInt(bound),z=bz+(r.nextRawInt()&15);
            place(w,r,x,y,z,8);
        }
        for(int i=0;i<20;i++) {
            int x=bx+(r.nextRawInt()&15),bound=r.nextInt(112)+8,inner=r.nextInt(bound)+8,y=r.nextInt(inner),z=bz+(r.nextRawInt()&15);
            place(w,r,x,y,z,10);
        }
    }
    public static boolean place(PePopulationWorld w,PeRandom r,int x,int y,int z,int flowingId) {
        if(!w.isInside(x,y,z)||w.getBlock(x,y+1,z)!=1||w.getBlock(x,y-1,z)!=1)return false;
        int current=w.getBlock(x,y,z);if(current!=0&&current!=1)return false;
        int rock=0,air=0,open=-1;
        for(int i=0;i<4;i++){int id=w.getBlock(x+SIDES[i][0],y,z+SIDES[i][1]);if(id==1)rock++;if(id==0){air++;open=i;}}
        if(rock!=3||air!=1)return true;
        w.setBlock(x,y,z,flowingId,0);
        if(flowingId==10)spreadFire(w,r,x,y,z);
        // Dynamic source data=0 calls _setStatic before checking side spread.
        w.setBlock(x,y,z,flowingId+1,0);
        int px=x+SIDES[open][0],pz=z+SIDES[open][1],id=w.getBlock(px,y,pz);
        if(id==0||id==51)w.setBlock(px,y,pz,flowingId,flowingId==8?1:2);
        return true;
    }
    /** LiquidTile::_trySpreadFire 002e2074, local generation queue flag+0x14=0. */
    private static void spreadFire(PePopulationWorld w,PeRandom r,int x,int y,int z) {
        for(int i=0;i<10;i++) {
            int px=x+r.nextInt(5)-2,py=y+r.nextInt(5)-2,pz=z+r.nextInt(5)-2;
            if(!flammable(w.getBlock(px,py,pz)))continue;
            for(int[] side:FIRE_SIDES) {
                int nx=px+side[0],ny=py+side[1],nz=pz+side[2];
                if(w.isInside(nx,ny,nz)&&w.getBlock(nx,ny,nz)==0){w.setBlock(nx,ny,nz,51,0);return;}
            }
        }
    }
    /** Material::initMaterials marks wood, leaves, cloth, explosive and
     * replaceable_plant flammable; ordinary flower/vegetable materials are not. */
    private static boolean flammable(int id) {
        return switch(id) {
            case 5,17,18,31,32,35,46,47,53,54,58,63,64,68,72,85,96,99,100,
                106,107,125,126,134,135,136,146,151,161,162,163,164,171,175 -> true;
            default -> false;
        };
    }
}
