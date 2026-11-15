package net.mcpe.worldgen.fabric;

import net.mcpe.worldgen.fabric.PeContentSettings.Aquatic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import net.minecraft.util.math.noise.PerlinNoiseSampler;
import net.minecraft.util.math.random.LocalRandom;

/** Update Aquatic's ocean-temperature layer: Perlin base, six seeded zooms and coastal moderation. */
public final class PeOceanClimate {
    private final PerlinNoiseSampler noise;
    private final PerlinNoiseSampler bamboo;
    private final long[] layerSeed = new long[7];
    private final java.util.List<Map<Long,Integer>> caches = new java.util.ArrayList<>();

    public PeOceanClimate(long seed) {
        var random = new LocalRandom(seed);
        noise = new PerlinNoiseSampler(random);
        bamboo = new PerlinNoiseSampler(random);
        for (int i=0; i<=6; i++) {
            long salt=2000+i-1, layer=salt;
            for(int j=0;j<3;j++) layer=mix(layer,salt);
            long world=seed;
            for(int j=0;j<3;j++) world=mix(world,layer);
            layerSeed[i]=world;
            caches.add(new LinkedHashMap<>(512,.75f,true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long,Integer> e) { return size()>4096; }
            });
        }
    }
    private static long mix(long a,long b) { return a*(a*6364136223846793005L+1442695040888963407L)+b; }
    private static int draw(long s,int bound) { return (int)Math.floorMod(s>>24,(long)bound); }
    private int temperature(int x,int z,int i) {
        long key=((long)x<<32)|Integer.toUnsignedLong(z);
        var cache=caches.get(i);
        Integer found=cache.get(key);
        if(found!=null) return found;
        int result;
        if(i==0) {
            double t=noise.sample(x/8.0,z/8.0,0);
            result=t>.4?2:t>.2?1:t<-.4?-2:t<-.2?-1:0;
        } else {
            int px=x>>1,pz=z>>1;
            int a=temperature(px,pz,i-1);
            if((x&1)==0 && (z&1)==0) result=a;
            else {
                long s=layerSeed[i];
                for(int j=0;j<2;j++) s=mix(mix(s,px*2),pz*2);
                int b=temperature(px,pz+1,i-1), c=temperature(px+1,pz,i-1);
                if((x&1)==0) result=draw(s,2)==0?a:b;
                else {
                    s=mix(s,layerSeed[i]);
                    if((z&1)==0) result=draw(s,2)==0?a:c;
                    else {
                        int d=temperature(px+1,pz+1,i-1);
                        s=mix(s,layerSeed[i]);
                        result=mode(a,c,b,d,s);
                    }
                }
            }
        }
        cache.put(key,result);
        return result;
    }
    private static int mode(int a,int b,int c,int d,long s) {
        if(b==c && c==d) return b;
        if(a==b && (c!=d || a==c) || a==c && b!=d || a==d && b!=c) return a;
        if(b==c && a!=d || b==d && a!=c) return b;
        if(c==d && a!=b) return c;
        return switch(draw(s,4)) { case 0->a; case 1->b; case 2->c; default->d; };
    }
    public static boolean ocean(int id) { return id==0 || id==10 || id==24; }
    /** Coordinates and coast samples are in Java biome quarts. */
    public String biome(int id,int x,int z,PeContentSettings settings,IntBinaryOperator sample) {
        if(ocean(id)) {
            if(!settings.enabled(Aquatic.OCEAN_BIOMES)) return "ocean";
            int t=temperature(x,z,6);
            if(Math.abs(t)==2) {
                for(int dz=-8;dz<=8;dz+=4) for(int dx=-8;dx<=8;dx+=4)
                    if(!ocean(sample.applyAsInt(x+dx,z+dz))) return t>0?"lukewarm_ocean":"cold_ocean";
            }
            boolean deep=id==24;
            return switch(t) {
                case 2 -> "warm_ocean";
                case 1 -> deep?"deep_lukewarm_ocean":"lukewarm_ocean";
                case -1 -> deep?"deep_cold_ocean":"cold_ocean";
                case -2 -> deep?"deep_frozen_ocean":"frozen_ocean";
                default -> deep?"deep_ocean":"ocean";
            };
        }
        if(settings.villages() && (id==21 || id==22) && bamboo.sample(x/32.0,z/32.0,0)>0.36)
            return id==22?"bamboo_jungle_hills":"bamboo_jungle";
        return null;
    }
}
