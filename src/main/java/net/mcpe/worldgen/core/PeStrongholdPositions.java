package net.mcpe.worldgen.core;

import java.util.function.BiPredicate;

/** StrongholdFeature::isFeatureChunk's three native village-anchored positions.
 * Unlike Java's ring placement, each angular window searches PE village starts.
 * The supplied RNG is deliberately mutated by every village candidate check. */
public final class PeStrongholdPositions {
    private PeStrongholdPositions() {}
    public static int[][] find(long seed,PeRandom random,BiPredicate<Integer,Integer> allowedRawBiome) {
        random.setSeed(seed);
        float angle=random.nextFloat()*6.2831855f;
        int radius=(random.nextRawInt()&15)+40;
        int[][] result=new int[3][2];int count=0;
        while(count<3) {
            int centerX=floor((float)radius*PeMth.cos(angle));
            int centerZ=floor((float)radius*PeMth.sin(angle));
            boolean found=false;
            outer:for(int x=centerX-8;x<=centerX+7;x++)for(int z=centerZ-8;z<=centerZ+7;z++) {
                if(PeVillagePlacement.isFeatureChunk(seed,x,z,1,random)&&allowedRawBiome.test((x*16+8)>>2,(z*16+8)>>2)) {
                    result[count][0]=x;result[count][1]=z;count++;found=true;break outer;
                }
            }
            radius+=found?8:4;
            angle+=found?1.8849558f:0.7853982f;
        }
        return result;
    }
    private static int floor(float value){int n=(int)value;return value<(float)n?n-1:n;}
}
