package net.mcpe.worldgen.core;

import java.util.Map;
import java.util.HashMap;

/** RandomLevelSource::postProcess phase order (001e9ea4) and the common
 * BiomeDecorator tree phase (00196500). Feature ports remain separate so missing
 * stages can be audited rather than replaced by modern Java features. */
public final class PePopulation {
    private final long seed;
    private final boolean old;
    private final PeWorldGenerator core;
    private final PeVillageGenerator villages;
    private final PeMineshafts mineshafts;
    private final PeStrongholds strongholds;
    // Native BiomeDecorator fields are mutable. Keep them per generator/world,
    // never static across Java dimensions, seeds or newly opened worlds.
    private final Map<Integer,PeDecorationSettings> biomeSettings=new HashMap<>();
    public PePopulation(long seed,boolean old,PeWorldGenerator core) {
        this.seed=seed;this.old=old;this.core=core;
        villages=new PeVillageGenerator(seed,old);
        mineshafts=new PeMineshafts(seed);
        strongholds=new PeStrongholds(seed);
    }
    /** Native explicitly resets to this seed before structures and again before biome decoration. */
    public static int seed(long worldSeed,int chunkX,int chunkZ) {
        PeRandom world=new PeRandom(worldSeed);
        int a=(world.nextRawInt()>>>2)*2+1,b=(world.nextRawInt()>>>2)*2+1;
        return (chunkX*a+chunkZ*b)^(int)worldSeed;
    }
    public void populate(PePopulationWorld world,int cx,int cz,boolean structures) {
        if(old&&(cx< -8||cx>=8||cz< -8||cz>=8))return;
        // postProcess reads LevelChunk+0x10, the inclusive max TilePos, not min.
        // LevelChunk constructor 001f4430 stores min+15, y127, minZ+15 here.
        int bx=cx*16,bz=cz*16,biome=world.biomeAt(bx+15,bz+15);
        // This models postProcess immediately following loadChunk on the same native TLS.
        // PE's actual thread scheduling can leave a different preceding chunk's RNG here.
        lakes(world,core.lakeRandom(cx,cz),biome,bx,bz);
        PeRandom random=new PeRandom(Integer.toUnsignedLong(seed(seed,cx,cz)));
        if(structures) {
            villages.generate(world,random,cx,cz);
            if(!old){mineshafts.populate(world,random,cx,cz);strongholds.populate(world,random,cx,cz);}
        }
        if(!old)for(int i=0;i<8;i++) {
            int x=bx+8+(random.nextRawInt()&15),y=random.nextRawInt()&127,z=bz+8+(random.nextRawInt()&15);
            PeSmallStructures.dungeon(world,random,x,y,z);
        }
        random.setSeed(Integer.toUnsignedLong(seed(seed,cx,cz)));
        PeDecorationSettings settings=biomeSettings.computeIfAbsent(biome,PeDecorationSettings::fromBiome);
        PeBiomePopulation.preDecorate(world,random,biome,bx,bz,old,settings);
        PeOres.decorate(world,random,bx,bz);
        PeBiomeExtras.mesaOres(world,random,biome,bx,bz);
        PeSurfacePatches.decorate(world,random,bx,bz,settings.sand,settings.clay,settings.gravel);
        int grass=settings.grass;
        if(settings.treeDensity>0.0f) {
            int count;
            if(random.nextInt(10)==0){
                count=(int)(random.nextFloat()*settings.treeDensity);
                grass=biomeSettings.computeIfAbsent(1,PeDecorationSettings::fromBiome).grass;
            }
            else count=(int)settings.treeDensity+Math.max(random.nextInt(10)-7,0);
            for(int i=0;i<count;i++) {
                PeTrees.Selected tree=PeTrees.select(random,biome);
                int x=bx+1+random.nextInt(14),z=bz+1+random.nextInt(14),y=world.surfaceY(x,z);
                tree.place(world,random,x,y,z);
                if(!old)grassAroundTree(world,random,x,y,z,23);
            }
        }
        int configuredGrass=settings.grass;
        settings.grass=grass;
        try{PeVegetation.decorate(world,random,biome,bx,bz,old,settings);}
        finally{settings.grass=configuredGrass;}
        PeSprings.decorate(world,random,bx,bz);
        PeBiomePopulation.postDecorate(world,random,biome,bx,bz,old);
        PeSnow.apply(world,bx,bz);
        // Native foliage-color byte generation and subsequent queued liquid ticks
        // are not replaced with modern Java decoration or fluid ticks.
    }
    private void lakes(PePopulationWorld w,PeRandom r,int biome,int bx,int bz) {
        if(biome!=2&&biome!=17&&(r.nextRawInt()&3)==0) {
            int x=bx+3+r.nextInt(9),y=r.nextRawInt()&127,z=bz+3+r.nextInt(9);
            if(!old||y>=60)PeSmallStructures.lake(w,r,x,y,z,9);
        } else if(!old&&(r.nextRawInt()&7)==0) {
            int x=bx+3+r.nextInt(9),bound=r.nextInt(120)+8,y=r.nextInt(bound),z=bz+3+r.nextInt(9);
            if(y>=60&&(y<=63||r.nextInt(10)==0))PeSmallStructures.lake(w,r,x,y,z,11);
        }
    }
    public static void grassAroundTree(PePopulationWorld w,PeRandom r,int x,int y,int z,int attempts) {
        int id;
        while(y>0&&((id=w.getBlock(x,y,z))==0||id==18||id==161))y--;
        for(int i=0;i<attempts;i++) {
            int px=x+r.nextInt(3)-r.nextInt(3),py=y+(r.nextRawInt()&3)-(r.nextRawInt()&3),pz=z+r.nextInt(3)-r.nextInt(3);
            if(w.isInside(px,py,pz)&&w.getBlock(px,py,pz)==0) {
                int ground=w.getBlock(px,py-1,pz);
                if(ground==2||ground==3||ground==60)w.setBlock(px,py,pz,31,1);
            }
        }
    }
}
