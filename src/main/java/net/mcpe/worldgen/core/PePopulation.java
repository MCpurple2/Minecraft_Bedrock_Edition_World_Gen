package net.mcpe.worldgen.core;

import java.util.Map;
import java.util.HashMap;

/** RandomLevelSource::postProcess phase order (001e9ea4) and the common
 * BiomeDecorator tree phase (00196500). Feature ports remain separate so missing
 * stages can be audited rather than replaced by modern Java features. */
public final class PePopulation {
    private final long seed;
    private final boolean old;
    private final boolean replaceVillages;
    private final java.util.Set<PeContent> flags;
    private final PeWorldGenerator core;
    private final PeVillageGenerator villages;
    private final PeScatteredStructures scattered;
    private final PeOceanMonuments monuments;
    private final PeMineshafts mineshafts;
    private final PeStrongholds strongholds;
    // Native BiomeDecorator fields are mutable. Keep them per generator/world,
    // never static across Java dimensions, seeds or newly opened worlds.
    private final Map<Integer,PeDecorationSettings> biomeSettings=new HashMap<>();
    public PePopulation(long seed,boolean old,PeWorldGenerator core) {
        this(seed,old,core,false);
    }
    public PePopulation(long seed,boolean old,PeWorldGenerator core,boolean replaceVillages) {
        this(seed,old,core,replaceVillages,PeContent.defaults(core.release(),old));
    }
    public PePopulation(long seed,boolean old,PeWorldGenerator core,boolean replaceVillages,java.util.Set<PeContent> flags) {
        this.flags=flags;
        this.replaceVillages = replaceVillages;
        this.seed=seed;this.old=old;this.core=core;
        villages=new PeVillageGenerator(seed,old,core.biomes(),core.release(),flags);
        monuments=flags.contains(PeContent.MONUMENTS) ? new PeOceanMonuments(seed,core.biomes()) : null;
        scattered=new PeScatteredStructures(seed,core.release(),flags);
        mineshafts=new PeMineshafts(seed,flags.contains(PeContent.BADLANDS_MINES));
        strongholds=new PeStrongholds(seed,core.biomes(),core.release());
    }
    /** Native explicitly resets to this seed before structures and again before biome decoration. */
    public static int seed(long worldSeed,int chunkX,int chunkZ) {
        PeRandom world=new PeRandom(worldSeed);
        int a=(world.nextRawInt()>>>2)*2+1,b=(world.nextRawInt()>>>2)*2+1;
        return (chunkX*a+chunkZ*b)^(int)worldSeed;
    }
    public void populate(PePopulationWorld world,int cx,int cz,boolean structures) {
        if(!core.isInsideChunk(cx,cz))return;
        world.beginLiquidTicks(cx,cz);
        // postProcess reads LevelChunk+0x10, the inclusive max TilePos, not min.
        // LevelChunk constructor 001f4430 stores min+15, y127, minZ+15 here.
        int bx=cx*16,bz=cz*16,biome=world.biomeAt(bx+15,bz+15);
        int chunkSeed=seed(seed,cx,cz);
        boolean later=core.release().compareTo(PeRelease.V0_12_3) >= 0;
        PeRandom random=new PeRandom(chunkSeed);
        // 0.12 reseeds before lakes. 0.11 inherits the preceding native TLS terrain RNG.
        lakes(world,later?random:core.lakeRandom(cx,cz),biome,bx,bz);
        if(later)random.setSeed(chunkSeed);
        if(structures) {
            if (flags.contains(PeContent.VILLAGES) && !replaceVillages) villages.generate(world,random,cx,cz);
            if(flags.contains(PeContent.MINESHAFTS)) mineshafts.populate(world,random,cx,cz);
            if(flags.contains(PeContent.STRONGHOLDS)) strongholds.populate(world,random,cx,cz);
            scattered.populate(world,random,cx,cz);
            if (monuments!=null) monuments.populate(world,random,cx,cz);
        }
        if(flags.contains(PeContent.DUNGEONS))for(int i=0;i<8;i++) {
            int x=bx+8+(random.nextRawInt()&15),y=random.nextRawInt()&127,z=bz+8+(random.nextRawInt()&15);
            PeSmallStructures.dungeon(world,random,x,y,z);
        }
        if(later)PeSnow.freeze(world,bx,bz);
        random.setSeed(chunkSeed);
        PeDecorationSettings settings=biomeSettings.computeIfAbsent(biome,PeDecorationSettings::fromBiome);
        PeBiomePopulation.preDecorate(world,random,biome,bx,bz,old,settings);
        PeOres.decorate(world,random,bx,bz);
        if (core.release().compareTo(PeRelease.V0_10_2) >= 0) PeBiomeExtras.mesaOres(world,random,biome,bx,bz);
        PeSurfacePatches.decorate(world,random,bx,bz,settings.sand,settings.clay,settings.gravel);
        int grass=settings.grass;
        if(settings.treeDensity>0.0f) {
            int count;
            if(random.nextInt(10)==0){
                count=(int)(random.nextFloat()*settings.treeDensity);
                grass=biomeSettings.computeIfAbsent(1,PeDecorationSettings::fromBiome).grass;
            }
            else if (settings.treeDensity < 1.0f) {
                // ARM 0x18669c: sparse biomes make a single fractional-density roll.
                count = random.nextFloat() < settings.treeDensity ? 1 : 0;
            } else count=(int)settings.treeDensity+Math.max(random.nextInt(10)-7,0);
            for(int i=0;i<count;i++) {
                PeTrees.Selected tree=PeTrees.select(random,biome);
                int first=random.nextInt(14)+1,second=random.nextInt(14)+1;
                boolean zFirst=core.release().drawsZFirst();
                int x=bx+(zFirst?second:first),z=bz+(zFirst?first:second),y=world.surfaceY(x,z);
                tree.place(world,random,x,y,z);
                if(!old)grassAroundTree(world,random,x,y,z,23);
            }
        }
        int configuredGrass=settings.grass;
        settings.grass=grass;
        try{PeVegetation.decorate(world,random,biome,bx,bz,old,settings);}
        finally{settings.grass=configuredGrass;}
        PeSprings.decorate(world,random,bx,bz);
        PeBiomeExtras.post(world,random,biome,bx,bz,flags.contains(PeContent.DESERT_WELLS));
        if(later)PeSnow.cover(world,bx,bz);else PeSnow.apply(world,bx,bz);
        world.finishLiquidTicks(cx,cz);
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
        PeVegetation.tallGrass(w,r,x,y,z,1,attempts,3);
    }
}
