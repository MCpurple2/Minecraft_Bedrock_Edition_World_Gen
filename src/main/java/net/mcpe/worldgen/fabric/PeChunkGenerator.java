package net.mcpe.worldgen.fabric;

import net.mcpe.worldgen.fabric.PeContentSettings.Aquatic;

import net.mcpe.worldgen.core.PeRelease;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mcpe.worldgen.core.PeWorldGenerator;
import net.mcpe.worldgen.core.PePopulation;
import net.mcpe.worldgen.core.PeInitialPlayer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
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
import net.mcpe.worldgen.core.PeContent;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Adapts PE terrain and population to Fabric, with optional vanilla content overlays. */
public final class PeChunkGenerator extends ChunkGenerator {
    private static final Codec<PeRelease> RELEASE_CODEC = Codec.STRING.comapFlatMap(
        id -> PeRelease.byId(id).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown PE release: " + id)),
        PeRelease::id);
    public static final MapCodec<PeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(PeChunkGenerator::getBiomeSource),
        Codec.BOOL.optionalFieldOf("old", false).forGetter(generator -> generator.old),
        Codec.BOOL.optionalFieldOf("pe_spawn", false).forGetter(PeChunkGenerator::usesPeSpawn),
        Codec.BOOL.optionalFieldOf("native_biome_seeds", false).forGetter(generator -> generator.nativeBiomeSeeds),
        RELEASE_CODEC.optionalFieldOf("pe_version", PeRelease.V0_11_1).forGetter(PeChunkGenerator::release),
        PeContentSettings.CODEC.optionalFieldOf("java_content", PeContentSettings.DEFAULT).forGetter(PeChunkGenerator::settings),
        PeBedrockSettings.CODEC.optionalFieldOf("bedrock_features").forGetter(g -> java.util.Optional.of(g.bedrock))
    ).apply(instance, (biome,old,spawn,nativeSeeds,release,settings,bedrock) ->
        new PeChunkGenerator(biome,old,spawn,nativeSeeds,release,settings,
            bedrock.orElseGet(() -> PeBedrockSettings.defaults(release,old,settings.portalOverride())))));
    private final PeContentSettings settings;
    private final PeBedrockSettings bedrock;
    public PeBedrockSettings bedrock() { return bedrock; }
    private final PeRelease release;
    private final boolean old;
    private final boolean peSpawn;
    private final boolean nativeBiomeSeeds;
    private volatile PeWorldGenerator engine;
    private volatile long worldSeed;
    private volatile PePopulation population;
    private final Map<Long, PeWorldGenerator.TerrainChunk> chunks = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, PeWorldGenerator.TerrainChunk> eldest) { return size() > 128; }
    };

    public PeChunkGenerator(BiomeSource biomeSource, boolean old, boolean peSpawn) {
        this(biomeSource, old, peSpawn, false);
    }

    public PeChunkGenerator(BiomeSource biomeSource, boolean old, boolean peSpawn, boolean nativeBiomeSeeds) {
        this(biomeSource, old, peSpawn, nativeBiomeSeeds, PeRelease.V0_11_1);
    }

    public PeChunkGenerator(BiomeSource biomeSource, boolean old, boolean peSpawn, boolean nativeBiomeSeeds, PeRelease release) {
        this(biomeSource, old, peSpawn, nativeBiomeSeeds, release, PeContentSettings.DEFAULT);
    }

    public PeChunkGenerator(BiomeSource biomeSource, boolean old, boolean peSpawn, boolean nativeBiomeSeeds, PeRelease release, PeContentSettings settings) {
        this(biomeSource, old, peSpawn, nativeBiomeSeeds, release, settings, PeBedrockSettings.defaults(release,old, settings.portalOverride()));
    }

    public PeChunkGenerator(BiomeSource biomeSource, boolean old, boolean peSpawn, boolean nativeBiomeSeeds,
            PeRelease release, PeContentSettings settings, PeBedrockSettings bedrock) {
        super(biomeSource, biome -> PeJavaContent.features(biome, settings.resolve(release,old)));
        this.bedrock = bedrock;
        this.settings = settings.resolve(release,old);
        if (biomeSource instanceof PeBiomeSource peBiomes) peBiomes.configure(this.settings);
        if (!release.supports(old)) throw new IllegalArgumentException("Unsupported PE world type for release: " + release.id());
        this.release = release;
        this.old = old;
        this.peSpawn = peSpawn;
        this.nativeBiomeSeeds = nativeBiomeSeeds;
    }

    public PeContentSettings settings() { return settings; }
    public PeRelease release() { return release; }

    public boolean usesPeSpawn() { return peSpawn; }

    public BlockPos initialSpawnAnchor() {
        var spawn = engine().initialSpawn();
        var bounds = finiteBorder();
        if (bounds != null && !bounds.containsPoint(spawn.x(), spawn.z(), 0))
            return new BlockPos(bounds.centerX(), spawn.y(), bounds.centerZ());
        return new BlockPos(spawn.x(), spawn.y(), spawn.z());
    }

    public Vec3d initialPlayerPosition(ServerWorld world, BlockPos anchor) {
        int x = anchor.getX();
        int z = anchor.getZ();
        var terrain = new FabricPopulationWorld(world, engine());
        int y = PeInitialPlayer.feetY(anchor.getY(), () -> {
            int solidY = terrain.topSolidY(x, z, true) - 1;
            return solidY >= 2 ? solidY : -1;
        },
            feet -> world.getBlockCollisions(null, new Box(x, feet, z, x + 0.6F, feet + 1.8F, z + 0.6F))
                .iterator().hasNext());
        return new Vec3d(x + 0.5, y, z + 0.5);
    }

    public net.mcpe.worldgen.core.PeWorldBorderLocation finiteBorder() { return engine().finiteBorder(); }

    public boolean isOld() { return old; }

    @Override
    public synchronized StructurePlacementCalculator createStructurePlacementCalculator(RegistryWrapper<StructureSet> structures, NoiseConfig noiseConfig, long seed) {
        worldSeed = seed;
        engine = new PeWorldGenerator(seed, old, peSpawn, nativeBiomeSeeds, release);
        bedrock.configure(engine, peSpawn);
        population = new PePopulation(seed,old,engine,settings.villages(),settings.flags());
        if (biomeSource instanceof PeBiomeSource peBiomes) peBiomes.attach(engine, seed);
        chunks.clear();
        // Do not run Java stronghold-ring biome searches for a PE-only generator.
        return StructurePlacementCalculator.create(noiseConfig, seed, biomeSource, PeJavaContent.structures(structures, settings, engine));
    }

    private PeWorldGenerator engine() {
        PeWorldGenerator result = engine;
        if (result == null) throw new IllegalStateException("PE generator used before the world seed was supplied");
        return result;
    }

    private synchronized PeWorldGenerator.TerrainChunk generate(int chunkX, int chunkZ) {
        PeWorldGenerator core = engine();
        // PE generators may own mutable PRNG/scratch state. Keep chunk order from introducing concurrent races.
        long key = ((long)chunkX << 32) | (chunkZ & 0xffffffffL);
        return chunks.computeIfAbsent(key, ignored -> core.terrainChunk(chunkX, chunkZ));
    }

    @Override protected MapCodec<? extends ChunkGenerator> getCodec() { return CODEC; }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noise, StructureAccessor structures, Chunk chunk) {
        var terrain = generate(chunk.getPos().x, chunk.getPos().z);
        byte[] data=terrain.blocks();
        if (data.length != 32768) throw new IllegalStateException("Expected 16x16x128 PE chunk");
        BlockPos.Mutable position = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 128; y++) {
            int id = Byte.toUnsignedInt(data[(x * 16 + z) * 128 + y]);
            if (id != 0) chunk.setBlockState(position.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z), LegacyBlocks.state(id,terrain.data()[(x*16+z)*128+y]));
        }
        Heightmap.populateHeightmaps(chunk, java.util.EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG, Heightmap.Type.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noise, Chunk chunk) {}
    @Override public void carve(ChunkRegion region, long seed, NoiseConfig noise, BiomeAccess access, StructureAccessor structures, Chunk chunk) {
        boolean caves = settings.enabled(Aquatic.UNDERWATER_CAVE), canyons = settings.enabled(Aquatic.CANYONS);
        var pos = chunk.getPos();
        if ((!caves && !canyons) || !engine().isInsideChunk(pos.x,pos.z)) return;
        int[] biomes = engine().biomeArea(pos.getStartX(),pos.getStartZ(),16,16);
        PeAquaticCarvers.carve(seed,chunk,biomes,caves,canyons);
    }
    @Override public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structures) {
        PePopulation current=population;
        if(current==null)throw new IllegalStateException("PE population used before world seed initialization");
        boolean enabled=world.toServerWorld().getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures();
        // Serialize PE's neighboring feature writes without holding the base-generation lock.
        synchronized (current) {
            current.populate(new FabricPopulationWorld(world,engine()),chunk.getPos().x,chunk.getPos().z,enabled);
            if (!engine().isInsideChunk(chunk.getPos().x,chunk.getPos().z)) return;
            PeJavaContent.generateCavesContent(world,this,chunk.getPos(),worldSeed,settings);
            if (settings.overworldContent()) super.generateFeatures(world,chunk,structures);
            if (settings.has(PeContent.NESTS) || settings.has(PeContent.GIANT_BERRIES) || settings.has(PeContent.BERRIES)
                || settings.has(PeContent.FLOWERS) || settings.has(PeContent.DARK_BIRCH))
                PeJavaContent.beesAndBerries(world,chunk.getPos(),worldSeed,engine(),settings);
        }
    }
    @Override public net.minecraft.util.collection.WeightedPool<net.minecraft.world.biome.SpawnSettings.SpawnEntry> getEntitySpawnList(
            net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.biome.Biome> biome,StructureAccessor structures,
            net.minecraft.entity.SpawnGroup group,BlockPos position) {
        var pool=super.getEntitySpawnList(biome,structures,group,position);
        if (group!=net.minecraft.entity.SpawnGroup.CREATURE || settings.has(PeContent.PANDAS)) return pool;
        return net.minecraft.util.collection.WeightedPool.of(pool.getEntries().stream()
            .filter(entry -> entry.value().type()!=net.minecraft.entity.EntityType.PANDA).toList());
    }
    @Override public void populateEntities(ChunkRegion region) {}
    @Override public void addStructureReferences(StructureWorldAccess world, StructureAccessor structures, Chunk chunk) {
        if (settings.overworldContent()) super.addStructureReferences(world,structures,chunk);
    }
    @Override public void setStructureStarts(DynamicRegistryManager registries, StructurePlacementCalculator placements,
        StructureAccessor structures, Chunk chunk, StructureTemplateManager templates, RegistryKey<World> dimension) {
        if ((settings.overworldContent()) && engine().isInsideChunk(chunk.getPos().x,chunk.getPos().z))
            super.setStructureStarts(registries,placements,structures,chunk,templates,dimension);
    }

    @Override public int getWorldHeight() { return 384; }
    @Override public int getMinimumY() { return -64; }
    @Override public int getSeaLevel() { return PeWorldGenerator.SEA_LEVEL; }

    @Override
    public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView world, NoiseConfig noise) {
        VerticalBlockSample column = getColumnSample(x, z, world, noise);
        for (int y = 127; y >= 0; y--) {
            if (type.getBlockPredicate().test(column.getState(y))) return y + 1;
        }
        return world.getBottomY();
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noise) {
        var terrain = generate(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        byte[] data=terrain.blocks();
        BlockState[] states = new BlockState[world.getHeight()];
        java.util.Arrays.fill(states, net.minecraft.block.Blocks.AIR.getDefaultState());
        int offset = (Math.floorMod(x, 16) * 16 + Math.floorMod(z, 16)) * 128;
        int bottom = world.getBottomY();
        for (int y = Math.max(0, bottom); y < Math.min(PeWorldGenerator.HEIGHT, bottom + states.length); y++) {
            states[y - bottom] = LegacyBlocks.state(Byte.toUnsignedInt(data[offset + y]),terrain.data()[offset+y]);
        }
        return new VerticalBlockSample(bottom, states);
    }

    @Override public void appendDebugHudText(List<String> text, NoiseConfig noise, BlockPos pos) {
        text.add("PE " + release.id() + " / " + (old ? "old" : "infinite") + " / seed=" + worldSeed);
    }
}
