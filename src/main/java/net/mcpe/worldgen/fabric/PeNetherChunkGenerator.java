package net.mcpe.worldgen.fabric;

import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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
    public static final MapCodec<PeNetherChunkGenerator> CODEC = BiomeSource.CODEC.fieldOf("biome_source")
        .xmap(PeNetherChunkGenerator::new, PeNetherChunkGenerator::getBiomeSource);
    private PeNetherTerrain terrain;
    private PeNetherFortresses fortresses;
    private final Map<Long, byte[]> chunks = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,byte[]> eldest) { return size() > 64; }
    };

    public PeNetherChunkGenerator(BiomeSource biomes) { super(biomes); }
    @Override protected MapCodec<PeNetherChunkGenerator> getCodec() { return CODEC; }

    @Override public synchronized StructurePlacementCalculator createStructurePlacementCalculator(
            RegistryWrapper<StructureSet> structures, NoiseConfig config, long seed) {
        terrain = new PeNetherTerrain(seed);
        fortresses = new PeNetherFortresses(seed);
        chunks.clear();
        return StructurePlacementCalculator.create(config, seed, biomeSource, java.util.stream.Stream.empty());
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
            PeNetherPopulation.decorate(new FabricPopulationWorld(world),random,pos.x,pos.z,structuresEnabled?fortresses:null);
        }
    }
    @Override public void carve(ChunkRegion region,long seed,NoiseConfig config,BiomeAccess access,StructureAccessor structures,Chunk chunk) {}
    @Override public void setStructureStarts(DynamicRegistryManager registries,StructurePlacementCalculator placement,
        StructureAccessor accessor,Chunk chunk,StructureTemplateManager templates,RegistryKey<World> dimension) {}
    @Override public void populateEntities(ChunkRegion region) {}
    @Override public void appendDebugHudText(List<String> text,NoiseConfig config,BlockPos pos) { text.add("PE 0.12.3 Nether"); }
}
