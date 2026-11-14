package net.mcpe.worldgen.fabric;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mcpe.worldgen.core.PeWorldGenerator;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Fabric adapter only: Java worldgen never supplies noise, caves or decoration. */
public final class PeChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(PeChunkGenerator::getBiomeSource),
        Codec.BOOL.optionalFieldOf("old", false).forGetter(generator -> generator.old)
    ).apply(instance, PeChunkGenerator::new));
    private final boolean old;
    private volatile PeWorldGenerator engine;
    private volatile long worldSeed;
    private final Map<Long, byte[]> chunks = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) { return size() > 128; }
    };

    public PeChunkGenerator(BiomeSource biomeSource, boolean old) {
        super(biomeSource);
        this.old = old;
    }

    public boolean isOld() { return old; }

    @Override
    public synchronized StructurePlacementCalculator createStructurePlacementCalculator(RegistryWrapper<StructureSet> structures, NoiseConfig noiseConfig, long seed) {
        worldSeed = seed;
        engine = new PeWorldGenerator(seed, old);
        if (biomeSource instanceof PeBiomeSource peBiomes) peBiomes.attach(engine);
        chunks.clear();
        // Do not run Java stronghold-ring biome searches for a PE-only generator.
        return StructurePlacementCalculator.create(noiseConfig, seed, biomeSource, java.util.stream.Stream.empty());
    }

    private PeWorldGenerator engine() {
        PeWorldGenerator result = engine;
        if (result == null) throw new IllegalStateException("PE generator used before the world seed was supplied");
        return result;
    }

    private synchronized byte[] generate(int chunkX, int chunkZ) {
        PeWorldGenerator core = engine();
        // PE generators may own mutable PRNG/scratch state. Keep chunk order from introducing concurrent races.
        long key = ((long)chunkX << 32) | (chunkZ & 0xffffffffL);
        return chunks.computeIfAbsent(key, ignored -> core.generateChunk(chunkX, chunkZ));
    }

    @Override protected MapCodec<? extends ChunkGenerator> getCodec() { return CODEC; }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noise, StructureAccessor structures, Chunk chunk) {
        byte[] data = generate(chunk.getPos().x, chunk.getPos().z);
        if (data.length != 32768) throw new IllegalStateException("Expected 16x16x128 PE chunk");
        BlockPos.Mutable position = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 128; y++) {
            int id = Byte.toUnsignedInt(data[(x * 16 + z) * 128 + y]);
            if (id != 0) chunk.setBlockState(position.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z), LegacyBlocks.state(id));
        }
        Heightmap.populateHeightmaps(chunk, java.util.EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG, Heightmap.Type.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noise, Chunk chunk) {}
    @Override public void carve(ChunkRegion region, long seed, NoiseConfig noise, BiomeAccess access, StructureAccessor structures, Chunk chunk) {}
    @Override public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structures) {}
    @Override public void populateEntities(ChunkRegion region) {}
    @Override public void addStructureReferences(StructureWorldAccess world, StructureAccessor structures, Chunk chunk) {}
    @Override public void setStructureStarts(DynamicRegistryManager registries, StructurePlacementCalculator placements,
        StructureAccessor structures, Chunk chunk, StructureTemplateManager templates, RegistryKey<World> dimension) {}

    @Override public int getWorldHeight() { return 128; }
    @Override public int getMinimumY() { return 0; }
    @Override public int getSeaLevel() { return PeWorldGenerator.SEA_LEVEL; }

    @Override
    public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView world, NoiseConfig noise) {
        VerticalBlockSample column = getColumnSample(x, z, world, noise);
        for (int y = 127; y >= 0; y--) {
            if (type.getBlockPredicate().test(column.getState(y))) return y + 1;
        }
        return 0;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noise) {
        byte[] data = generate(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        BlockState[] states = new BlockState[128];
        int offset = (Math.floorMod(x, 16) * 16 + Math.floorMod(z, 16)) * 128;
        for (int y = 0; y < 128; y++) states[y] = LegacyBlocks.state(Byte.toUnsignedInt(data[offset + y]));
        return new VerticalBlockSample(0, states);
    }

    @Override public void appendDebugHudText(List<String> text, NoiseConfig noise, BlockPos pos) {
        text.add("PE 0.11.1 experimental / " + (old ? "old" : "infinite") + " / seed=" + worldSeed);
    }
}
