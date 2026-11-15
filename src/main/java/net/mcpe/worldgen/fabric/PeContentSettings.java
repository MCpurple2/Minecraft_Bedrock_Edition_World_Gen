package net.mcpe.worldgen.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mcpe.worldgen.core.PeContent;
import net.mcpe.worldgen.core.PeRelease;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.StringIdentifiable;

public record PeContentSettings(Set<Aquatic> water, boolean villages, boolean bees, boolean netherUpdate,
                                Optional<Boolean> portalOverride, Optional<Set<PeContent>> selection, boolean explicitTreeSettings) {
    public PeContentSettings(Set<Aquatic> water, boolean villages, boolean bees, boolean netherUpdate, Optional<Boolean> portals) {
        this(water,villages,bees,netherUpdate,portals,Optional.empty(),false);
    }
    public PeContentSettings resolve(PeRelease release, boolean finite) {
        if (selection.isPresent()) {
            if (explicitTreeSettings) return this;
            var migrated = EnumSet.noneOf(PeContent.class);
            migrated.addAll(flags());
            if (release.hasWorldTypes() && java.util.Collections.disjoint(migrated,PeContent.NATURAL_TREES))
                migrated.addAll(PeContent.NATURAL_TREES);
            return configured(water,migrated,portalOverride);
        }
        var flags=PeContent.defaults(release,finite);
        if (villages) flags.addAll(Set.of(PeContent.MODERN_VILLAGES,PeContent.SNOWY,PeContent.OUTPOSTS,PeContent.BAMBOO,PeContent.PANDAS));
        if (bees) flags.addAll(Set.of(PeContent.NESTS,PeContent.GIANT_BERRIES));
        if (netherUpdate) flags.add(PeContent.JE_NETHER);
        return configured(water,flags,portalOverride);
    }
    public static PeContentSettings configured(Set<Aquatic> water,Set<PeContent> flags,Optional<Boolean> portals) {
        return new PeContentSettings(water,flags.contains(PeContent.MODERN_VILLAGES),flags.contains(PeContent.NESTS),
            flags.contains(PeContent.JE_NETHER),portals,Optional.of(Set.copyOf(flags)),true);
    }
    public Set<PeContent> flags() { return selection.orElseThrow(); }
    public boolean has(PeContent item) { return flags().contains(item) && item.available(flags()); }
    private static final Codec<PeContent> FEATURE_CODEC = Codec.STRING.comapFlatMap(name -> {
        for (var f : PeContent.values()) if (f.key().equals(name)) return com.mojang.serialization.DataResult.success(f);
        return com.mojang.serialization.DataResult.error(() -> "Unknown JE content " + name);
    }, PeContent::key);
    public enum Aquatic implements StringIdentifiable {
        OCEAN_RUINS, SHIPWRECK, BURIED_TREASURE, OCEAN_BIOMES, ICEBERG, CORAL_REEF, UNDERWATER_CAVE, CANYONS,
        KELP, SEA_PICKLES, SEAGRASS;
        public String asString() { return name().toLowerCase(Locale.ROOT); }
        public boolean requiresOceanBiomes() {
            return this == ICEBERG || this == CORAL_REEF || this == SEA_PICKLES;
        }
    }

    public PeContentSettings {
        water = Set.copyOf(water);
        selection = selection.map(Set::copyOf);
        if(selection.isPresent()) {
            villages=selection.get().contains(PeContent.MODERN_VILLAGES);
            bees=selection.get().contains(PeContent.NESTS);
            netherUpdate=selection.get().contains(PeContent.JE_NETHER);
        }
    }
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
        Codec.BOOL.optionalFieldOf("allow_nether_portals").forGetter(PeContentSettings::portalOverride),
        Codec.BOOL.optionalFieldOf("explicit_tree_settings", false).forGetter(PeContentSettings::explicitTreeSettings),
        FEATURE_CODEC.listOf().optionalFieldOf("features").forGetter(s -> s.selection.map(v -> v.stream().sorted().toList()))
    ).apply(i, (legacy, items, villages, bees, nether, portals, explicitTrees, features) -> new PeContentSettings(
        Set.copyOf(items.orElse(legacy ? LEGACY : List.of())), villages, bees, nether, portals,features.map(Set::copyOf),explicitTrees)));
    public boolean overworldContent() { return !water.isEmpty() || villages || has(PeContent.BAMBOO) || has(PeContent.FOSSILS)
            || has(PeContent.OUTPOSTS) || has(PeContent.MANSIONS) || has(PeContent.OVERWORLD_PORTALS); }
}
