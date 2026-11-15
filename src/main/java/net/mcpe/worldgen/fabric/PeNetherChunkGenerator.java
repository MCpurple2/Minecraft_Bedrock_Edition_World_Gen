package net.mcpe.worldgen.fabric;

import net.mcpe.worldgen.fabric.PeContentSettings.Aquatic;

import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import net.mcpe.worldgen.core.PeContent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.mcpe.worldgen.core.PeNetherTerrain;
import net.mcpe.worldgen.core.PeNetherPopulation;
import net.mcpe.worldgen.core.PeNetherFortresses;
import net.mcpe.worldgen.core.PePopulation;
import net.mcpe.worldgen.core.PeRandom;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;

/** Adapts the 128-block-high PE 0.12 Nether to Java's Nether dimension. */
public final class PeNetherChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PeNetherChunkGenerator> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(PeNetherChunkGenerator::getBiomeSource),
        PeContentSettings.CODEC.optionalFieldOf("java_content",PeContentSettings.DEFAULT).forGetter(g->g.settings)
    ).apply(i,PeNetherChunkGenerator::new));
    private final PeContentSettings settings;
    private PeNetherTerrain terrain;
    private PeNetherFortresses fortresses;
    private final Map<Long, byte[]> chunks = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,byte[]> eldest) { return size() > 64; }
    };

    public PeNetherChunkGenerator(BiomeSource biomes, PeContentSettings options) {
        super(biomes, biome -> netherFeatures(biome,options.resolve(net.mcpe.worldgen.core.PeRelease.V0_12_3,false)));
        settings=options.resolve(net.mcpe.worldgen.core.PeRelease.V0_12_3,false);
    }
    private static net.minecraft.world.biome.GenerationSettings netherFeatures(
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.biome.Biome> biome,PeContentSettings options) {
        var builder=new net.minecraft.world.biome.GenerationSettings.Builder();
        var steps=biome.value().getGenerationSettings().getFeatures();
        for(int i=0;i<steps.size();i++) for(var feature:steps.get(i)) {
            String key=feature.getKey().map(k->k.getValue().toString()).orElse("");
            if(options.has(PeContent.MAGMA) && key.equals("minecraft:ore_magma")) builder.addFeature(i,feature);
        }
        return builder.build();
    }
    @Override protected MapCodec<PeNetherChunkGenerator> getCodec() { return CODEC; }

    @Override public synchronized StructurePlacementCalculator createStructurePlacementCalculator(
            RegistryWrapper<StructureSet> structures, NoiseConfig config, long seed) {
        terrain = new PeNetherTerrain(seed);
        fortresses = new PeNetherFortresses(seed);
        chunks.clear();
        return StructurePlacementCalculator.create(config, seed, biomeSource, structures.streamEntries().<net.minecraft.registry.entry.RegistryEntry<StructureSet>>mapMulti((entry,out)->{
            String id=entry.registryKey().getValue().toString();
            if(id.equals("minecraft:nether_complexes") && settings.has(PeContent.BASTIONS)) {
                var bastion=entry.value().structures().stream().filter(e->e.structure().matchesId(net.minecraft.util.Identifier.ofVanilla("bastion_remnant"))).toList();
                if(!bastion.isEmpty()) out.accept(net.minecraft.registry.entry.RegistryEntry.of(new StructureSet(bastion,entry.value().placement())));
            }
            if(id.equals("minecraft:ruined_portals") && settings.has(PeContent.NETHER_PORTALS)) out.accept(entry);
        }));
    }
    private synchronized byte[] chunk(int x, int z) {
        return chunks.computeIfAbsent(((long)x << 32) | (z & 0xffffffffL), ignored -> terrain.chunk(x,z));
    }
    private static BlockState state(byte id) {
        return LegacyBlocks.state(Byte.toUnsignedInt(id));
    }
    @Override public CompletableFuture<Chunk> populateNoise(Blender blend, NoiseConfig config, StructureAccessor structures, Chunk chunk) {
        var cp = chunk.getPos();
        byte[] blocks = chunk(cp.x,cp.z);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for(int x=0;x<16;x++) for(int z=0;z<16;z++) for(int y=0;y<128;y++) {
            byte id=blocks[(x*16+z)*128+y];
            if(id!=0) chunk.setBlockState(pos.set(cp.getStartX()+x,y,cp.getStartZ()+z),state(id));
        }
        Heightmap.populateHeightmaps(chunk,EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG,Heightmap.Type.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public int getWorldHeight() { return 128; }
    @Override public int getMinimumY() { return 0; }
    @Override public int getSeaLevel() { return 32; }
    @Override public int getHeight(int x,int z,Heightmap.Type type,HeightLimitView world,NoiseConfig config) {
        byte[] data=chunk(x>>4,z>>4);
        int column=((x&15)*16+(z&15))*128;
        for(int y=Math.min(127,world.getBottomY()+world.getHeight()-1);y>=Math.max(0,world.getBottomY());y--)
            if(type.getBlockPredicate().test(state(data[column+y])))return y+1;
        return world.getBottomY();
    }
    @Override public VerticalBlockSample getColumnSample(int x,int z,HeightLimitView world,NoiseConfig config) {
        byte[] data=chunk(x>>4,z>>4);
        int column=((x&15)*16+(z&15))*128;
        BlockState[] blocks=new BlockState[world.getHeight()];
        for(int i=0;i<blocks.length;i++){
            int y=world.getBottomY()+i;
            blocks[i]=y>=0 && y<128 ? state(data[column+y]) : Blocks.AIR.getDefaultState();
        }
        return new VerticalBlockSample(world.getBottomY(),blocks);
    }
    @Override public void buildSurface(ChunkRegion region,StructureAccessor structures,NoiseConfig config,Chunk chunk) {}
    @Override public void generateFeatures(StructureWorldAccess world,Chunk chunk,StructureAccessor structures) {
        var pos=chunk.getPos();
        PeRandom random = new PeRandom(PePopulation.seed(world.getSeed(),pos.x,pos.z));
        boolean structuresEnabled=world.toServerWorld().getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures();
        synchronized(fortresses) {
            PeNetherPopulation.decorate(new FabricPopulationWorld(world),random,pos.x,pos.z,structuresEnabled && settings.has(PeContent.FORTRESS)?fortresses:null,settings.has(PeContent.QUARTZ));
        }
        if(settings.has(PeContent.MAGMA) || settings.has(PeContent.BASTIONS) || settings.has(PeContent.NETHER_PORTALS))
            super.generateFeatures(world,chunk,structures);
        var rng=new net.minecraft.util.math.random.LocalRandom(PePopulation.seed(world.getSeed(),pos.x,pos.z)^0x63e91L);
        if(settings.has(PeContent.LOW_NETHER)) {
            for(var block : List.of(Blocks.SOUL_SAND,Blocks.GRAVEL)) {
                int x=pos.getStartX()+rng.nextInt(12), z=pos.getStartZ()+rng.nextInt(12);
                for(int dx=0;dx<4;dx++) for(int dz=0;dz<4;dz++) for(int y=37;y>=27;y--) {
                    var at=new BlockPos(x+dx,y,z+dz);
                    if(world.getBlockState(at).isOf(Blocks.NETHERRACK) && !world.getBlockState(at.up()).isSolidBlock(world,at.up())) {
                        world.setBlockState(at,block.getDefaultState(),2);
                        break;
                    }
                }
            }
        }
        if(structuresEnabled && settings.has(PeContent.NETHER_FOSSILS) && rng.nextInt(12)==0) {
            int x=pos.getStartX()+4,z=pos.getStartZ()+4;
            for(int y=34+rng.nextInt(60);y>32;y--) {
                var at=new BlockPos(x,y,z);
                if(!world.isAir(at) || !world.getBlockState(at.down()).isSolidBlock(world,at.down())) continue;
                var id=net.minecraft.util.Identifier.ofVanilla("nether_fossils/fossil_"+(1+rng.nextInt(14)));
                world.toServerWorld().getStructureTemplateManager().getTemplate(id).ifPresent(template ->
                    template.place(world,at,at,new net.minecraft.structure.StructurePlacementData(),rng,2));
                break;
            }
        }
    }
    @Override public void carve(ChunkRegion region,long seed,NoiseConfig config,BiomeAccess access,StructureAccessor structures,Chunk chunk) {
        var overworld = region.toServerWorld().getServer().getOverworld();
        if (overworld.getChunkManager().getChunkGenerator() instanceof PeChunkGenerator pe && pe.settings().enabled(Aquatic.CANYONS))
            PeAquaticCarvers.carve(seed,chunk,null,false,true);
    }
    @Override public void setStructureStarts(DynamicRegistryManager registries,StructurePlacementCalculator placement,
        StructureAccessor accessor,Chunk chunk,StructureTemplateManager templates,RegistryKey<World> dimension) {
        if(settings.has(PeContent.BASTIONS) || settings.has(PeContent.NETHER_PORTALS))
            super.setStructureStarts(registries,placement,accessor,chunk,templates,dimension);
    }
    @Override public void populateEntities(ChunkRegion region) {}
    @Override public void appendDebugHudText(List<String> text,NoiseConfig config,BlockPos pos) { text.add("PE 0.12.3 Nether"); }
}
