package net.mcpe.worldgen.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.StringIdentifiable;

public record PeContentSettings(Set<Aquatic> water, boolean villages, boolean bees, boolean netherUpdate,
                                Optional<Boolean> portalOverride) {
    public enum Aquatic implements StringIdentifiable {
        OCEAN_RUINS, SHIPWRECK, BURIED_TREASURE, OCEAN_BIOMES, ICEBERG, CORAL_REEF, UNDERWATER_CAVE, CANYONS,
        KELP, SEA_PICKLES, SEAGRASS;
        public String asString() { return name().toLowerCase(Locale.ROOT); }
        public boolean requiresOceanBiomes() {
            return this == ICEBERG || this == CORAL_REEF || this == SEA_PICKLES;
        }
    }

    public PeContentSettings { water = Set.copyOf(water); }
    public boolean enabled(Aquatic item) {
        return water.contains(item) && (!item.requiresOceanBiomes() || water.contains(Aquatic.OCEAN_BIOMES));
    }
    public static final PeContentSettings DEFAULT = new PeContentSettings(Set.of(), false, false, false, Optional.empty());
    // Build 3.1 enabled these features together. Explicit selections take precedence.
    private static final List<Aquatic> LEGACY = List.of(Aquatic.OCEAN_RUINS, Aquatic.SHIPWRECK,
        Aquatic.BURIED_TREASURE, Aquatic.OCEAN_BIOMES, Aquatic.CORAL_REEF, Aquatic.KELP, Aquatic.SEAGRASS, Aquatic.SEA_PICKLES);
    public static final Codec<PeContentSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("aquatic", false).forGetter(s -> false),
        StringIdentifiable.createCodec(Aquatic::values).listOf().optionalFieldOf("aquatic_features")
            .forGetter((PeContentSettings s) -> Optional.of(s.water.stream().sorted().toList())),
        Codec.BOOL.optionalFieldOf("village_and_pillage", false).forGetter(PeContentSettings::villages),
        Codec.BOOL.optionalFieldOf("buzzy_bees", false).forGetter(PeContentSettings::bees),
        Codec.BOOL.optionalFieldOf("nether_update", false).forGetter(PeContentSettings::netherUpdate),
        Codec.BOOL.optionalFieldOf("allow_nether_portals").forGetter(PeContentSettings::portalOverride)
    ).apply(i, (legacy, items, villages, bees, nether, portals) -> new PeContentSettings(
        Set.copyOf(items.orElse(legacy ? LEGACY : List.of())), villages, bees, nether, portals)));
    public boolean allowPortals(boolean finite) { return portalOverride.orElse(!finite); }
    public boolean overworldContent() { return !water.isEmpty() || villages || bees; }
}
