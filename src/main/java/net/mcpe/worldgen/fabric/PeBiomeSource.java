package net.mcpe.worldgen.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mcpe.worldgen.core.PeWorldGenerator;
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
    private final Map<Integer, RegistryEntry<Biome>> palette = new LinkedHashMap<>();
    private volatile PeWorldGenerator core;
    private final Map<Long, int[]> areas = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) { return size() > 256; }
    };

    public PeBiomeSource(RegistryEntryLookup<Biome> biomes, boolean old) {
        this.old = old;
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

    private static RegistryEntry<Biome> lookup(RegistryEntryLookup<Biome> registry, String path) {
        return registry.getOrThrow(RegistryKey.of(RegistryKeys.BIOME, Identifier.ofVanilla(path)));
    }
    private void map(RegistryEntryLookup<Biome> registry, int id, String name) { palette.put(id, lookup(registry, name)); }
    synchronized void attach(PeWorldGenerator generator) { core = generator; areas.clear(); }
    @Override protected MapCodec<? extends BiomeSource> getCodec() { return CODEC; }
    @Override protected Stream<RegistryEntry<Biome>> biomeStream() { return palette.values().stream().distinct(); }
    @Override public synchronized RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        PeWorldGenerator generator = core;
        if (generator == null) return palette.get(1);
        int chunkX = x >> 2, chunkZ = z >> 2;
        long key = ((long)chunkX << 32) | (chunkZ & 0xffffffffL);
        int[] area = areas.computeIfAbsent(key, ignored -> generator.biomeArea(chunkX << 4, chunkZ << 4, 16, 16));
        int id = area[(x & 3) * 4 + (z & 3) * 64];
        return palette.getOrDefault(id, palette.get(1));
    }
}
