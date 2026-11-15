package net.mcpe.worldgen.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mcpe.worldgen.core.PeWorldGenerator;
import net.mcpe.worldgen.core.PeAlphaClimate;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** PE distribution mapped to the closest surviving Java biome identifiers.
 * Java stores quart-resolution biome cells; terrain itself still samples the
 * original PE layers at their original resolution inside the core. */
public final class PeBiomeSource extends BiomeSource {
    public static final MapCodec<PeBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        RegistryOps.<Biome, PeBiomeSource>getEntryLookupCodec(RegistryKeys.BIOME),
        Codec.BOOL.optionalFieldOf("old", false).forGetter(source -> source.old)
    ).apply(instance, PeBiomeSource::new));
    private final boolean old;
    private int tileShift;
    private final Map<Long,PeAlphaClimate.Type[]> alphaChunks = new LinkedHashMap<>(256,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,PeAlphaClimate.Type[]> entry) { return size() > 256; }
    };
    private final Map<Integer, RegistryEntry<Biome>> palette = new LinkedHashMap<>();
    private final RegistryEntry<Biome> voidBiome;
    private final Map<String,RegistryEntry<Biome>> extra = new LinkedHashMap<>();
    private PeContentSettings options = PeContentSettings.DEFAULT;
    private PeOceanClimate climate;
    private volatile PeWorldGenerator core;
    private final Map<Long, int[]> areas = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) { return size() > 256; }
    };

    public PeBiomeSource(RegistryEntryLookup<Biome> biomes, boolean old) {
        this(biomes,old,2);
    }

    private PeBiomeSource(RegistryEntryLookup<Biome> biomes, boolean old, int tileShift) {
        this.old = old;
        this.tileShift = tileShift;
        for (var type : PeAlphaClimate.Type.values())
            extra.put(type.path(),biomes.getOrThrow(RegistryKey.of(RegistryKeys.BIOME,Identifier.of("pe_worldgen",type.path()))));
        voidBiome = lookup(biomes, "the_void");
        for (String name : new String[]{"ocean","frozen_ocean","deep_ocean","warm_ocean","lukewarm_ocean","cold_ocean","deep_lukewarm_ocean","deep_cold_ocean","deep_frozen_ocean","bamboo_jungle"})
            extra.put(name,lookup(biomes,name));
        extra.put("bamboo_jungle_hills",biomes.getOrThrow(RegistryKey.of(RegistryKeys.BIOME, Identifier.of("pe_worldgen","bamboo_jungle_hills"))));
        String[] names = {
            "ocean", "plains", "desert", "windswept_hills", "forest", "taiga", "swamp", "river",
            "nether_wastes", "the_end", "frozen_ocean", "frozen_river", "snowy_plains", "snowy_plains",
            "mushroom_fields", "mushroom_fields", "beach", "desert", "forest", "taiga",
            "windswept_hills", "jungle", "jungle", "sparse_jungle", "deep_ocean", "stony_shore",
            "snowy_beach", "birch_forest", "birch_forest", "dark_forest", "snowy_taiga", "snowy_taiga",
            "old_growth_pine_taiga", "old_growth_pine_taiga", "windswept_forest", "savanna",
            "savanna_plateau", "badlands", "wooded_badlands", "badlands"
        };
        for (int id = 0; id < names.length; id++) palette.put(id, lookup(biomes, names[id]));
        for (int id = 128; id < 168; id++) palette.put(id, palette.getOrDefault(id - 128, palette.get(1)));
        map(biomes, 129, "sunflower_plains"); map(biomes, 131, "windswept_gravelly_hills");
        map(biomes, 132, "flower_forest"); map(biomes, 140, "ice_spikes");
        map(biomes, 155, "old_growth_birch_forest"); map(biomes, 156, "old_growth_birch_forest");
        map(biomes, 160, "old_growth_spruce_taiga"); map(biomes, 161, "old_growth_spruce_taiga");
        map(biomes, 163, "windswept_savanna"); map(biomes, 164, "windswept_savanna");
        map(biomes, 165, "eroded_badlands");
    }

    public static PeBiomeSource preview(RegistryEntryLookup<Biome> biomes, long seed, boolean old,
                                       net.mcpe.worldgen.core.PeRelease release, PeContentSettings settings,
                                       PeBedrockSettings bedrock) {
        var source = new PeBiomeSource(biomes,old,4);
        var generator = new PeWorldGenerator(seed,old,true,true,release);
        bedrock.configure(generator,true);
        source.configure(settings.resolve(release,old));
        source.attach(generator,seed);
        return source;
    }

    // A distant view skips most points in a large biome tile. Use smaller tiles
    // there so zooming out does not generate thousands of discarded samples per pixel.
    public synchronized void setPreviewStride(int stride) {
        int shift = Math.clamp(4 - Integer.numberOfTrailingZeros(stride), 0, 4);
        if (tileShift == shift) return;
        tileShift = shift;
        areas.clear();
        alphaChunks.clear();
    }

    public net.mcpe.worldgen.core.PeWorldBorderLocation previewBounds() { return core.finiteBorder(); }

    private static RegistryEntry<Biome> lookup(RegistryEntryLookup<Biome> registry, String path) {
        return registry.getOrThrow(RegistryKey.of(RegistryKeys.BIOME, Identifier.ofVanilla(path)));
    }
    private void map(RegistryEntryLookup<Biome> registry, int id, String name) { palette.put(id, lookup(registry, name)); }
    void configure(PeContentSettings settings) { options = settings; }
    synchronized void attach(PeWorldGenerator generator,long seed) {
        core = generator;
        climate = new PeOceanClimate(seed);
        areas.clear();
        alphaChunks.clear();
    }
    @Override protected MapCodec<? extends BiomeSource> getCodec() { return CODEC; }
    @Override protected Stream<RegistryEntry<Biome>> biomeStream() {
        Stream<RegistryEntry<Biome>> inside = Stream.concat(palette.values().stream(), extra.values().stream()).distinct();
        return Stream.concat(inside, Stream.of(voidBiome));
    }
    @Override public synchronized RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        PeWorldGenerator generator = core;
        if (generator == null) return palette.get(1);
        // Moderner Beta PRE rule: apply the out-of-bounds predicate before resolving any PE biome.
        var border = generator.finiteBorder();
        if (border != null && border.outsideBiomeCell(x, z)) return voidBiome;
        var alpha = generator.alphaClimate();
        if (alpha != null) {
            int cx = x >> tileShift, cz = z >> tileShift;
            int span = 4 << tileShift;
            long key = ((long)cx << 32) | Integer.toUnsignedLong(cz);
            var chunk = alphaChunks.computeIfAbsent(key, unused -> alpha.area(cx*span,cz*span,span,span));
            return extra.get(chunk[tileIndex(x,z)].path());
        }
        int id = sample(x,z);
        String replacement=climate.biome(id,x,z,options,this::sample);
        return replacement==null ? palette.getOrDefault(id,palette.get(1)) : extra.get(replacement);
    }

    private int sample(int x,int z) {
        int chunkX=x>>tileShift, chunkZ=z>>tileShift;
        int span = 4 << tileShift;
        long key=((long)chunkX<<32)|Integer.toUnsignedLong(chunkZ);
        int[] area=areas.computeIfAbsent(key, ignored -> core.biomeArea(chunkX*span,chunkZ*span,span,span));
        return area[tileIndex(x,z)];
    }

    private int tileIndex(int x,int z) {
        int mask = (1 << tileShift) - 1;
        return 4 * (x & mask) + (4 << tileShift) * (4 * (z & mask));
    }
}
