package net.mcpe.worldgen.core;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Java update groups with Pocket Edition release defaults. */
public enum PeContent {
    FORTRESS("adventure"),
    VILLAGES("adventure_part1"),
    DESERT_VILLAGES("adventure"),
    DESERT_PYRAMIDS("adventure"),
    JUNGLE_PYRAMIDS("adventure"),
    MINESHAFTS("adventure_part1"),
    STRONGHOLDS("adventure_part1"),
    DESERT_WELLS("adventure"),
    DUNGEONS("classic"),
    HUTS("scary"),
    QUARTZ("redstone"),
    JUNGLE_TREES("adventure"),
    MEGA_JUNGLE_TREES("adventure"),
    ACACIA_TREES("changed_world"),
    DARK_OAK_TREES("changed_world"),
    MEGA_PINE_TREES("changed_world"),
    MEGA_SPRUCE_TREES("changed_world"),
    MONUMENTS("bountiful"),
    FOSSILS("frostburn"),
    MAGMA("frostburn"),
    BADLANDS_MINES("frostburn"),
    SAVANNA("frostburn"),
    TAIGA("frostburn"),
    ABANDONED("frostburn"),
    MANSIONS("exploration"),
    FLOWERS("pillage"),
    BERRIES("pillage"),
    BAMBOO("pillage"),
    PANDAS("pillage"),
    MODERN_VILLAGES("pillage"),
    SNOWY("pillage"),
    OUTPOSTS("pillage"),
    LOW_NETHER("pillage"),
    NESTS("buzzy"),
    GIANT_BERRIES("buzzy"),
    DARK_BIRCH("buzzy"),
    JE_NETHER("nether_group"),
    BASTIONS("nether_group"),
    NETHER_FOSSILS("nether_group"),
    NETHER_PORTALS("nether_group"),
    OVERWORLD_PORTALS("nether_group"),
    COPPER_ORE("caves_cliffs_i"),
    AMETHYST_GEODE("caves_cliffs_i"),
    TUFF_BLOBS("caves_cliffs_i"),
    DEEPSLATE_BLOBS("caves_cliffs_i"),
    GLOW_LICHEN("caves_cliffs_i"),
    NOISE_CAVES("caves_cliffs_ii"),
    AQUIFERS("caves_cliffs_ii"),
    LUSH_CAVES("caves_cliffs_ii"),
    DRIPSTONE_CAVES("caves_cliffs_ii"),
    DEEP_TERRAIN("caves_cliffs_ii"),
    MODERN_ORES("caves_cliffs_ii"),
    COPPER_VEINS("caves_cliffs_ii"),
    IRON_VEINS("caves_cliffs_ii"),
    MOUNTAIN_BIOMES("caves_cliffs_ii"),
    MOUNTAIN_TERRAIN("caves_cliffs_ii"),
    DEEP_DARK("wild_update"),
    ANCIENT_CITIES("wild_update"),
    MANGROVE_SWAMPS("wild_update");
    public static final Set<PeContent> NATURAL_TREES = Set.of(JUNGLE_TREES, MEGA_JUNGLE_TREES,
        ACACIA_TREES, DARK_OAK_TREES, MEGA_PINE_TREES, MEGA_SPRUCE_TREES);

    public final String group;
    PeContent(String group) { this.group=group; }
    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static Set<PeContent> defaults(PeRelease release, boolean finite) {
        var flags = EnumSet.noneOf(PeContent.class);
        if (release.hasWorldTypes()) {
            flags.addAll(Set.of(VILLAGES, DESERT_VILLAGES, DESERT_WELLS));
            flags.addAll(NATURAL_TREES);
        }
        if (release.compareTo(PeRelease.V0_12_3)>=0) flags.addAll(Set.of(FORTRESS, QUARTZ));
        if (!finite) {
            flags.addAll(Set.of(STRONGHOLDS, MINESHAFTS, DUNGEONS));
            if (release.compareTo(PeRelease.V0_10_2)>=0) flags.add(BADLANDS_MINES);
            if (release.compareTo(PeRelease.V0_13_2)>=0) flags.add(DESERT_PYRAMIDS);
            if (release.compareTo(PeRelease.V0_14_3)>=0) flags.add(HUTS);
            if (release.compareTo(PeRelease.V0_15_10)>=0) flags.add(JUNGLE_PYRAMIDS);
            if (release.compareTo(PeRelease.V0_16_2)>=0) flags.add(MONUMENTS);
        }
        if (release.compareTo(PeRelease.V0_15_10)>=0) flags.addAll(Set.of(SAVANNA,TAIGA,ABANDONED));
        return flags;
    }
    public boolean available(Set<PeContent> flags) {
        return switch(this) {
            case COPPER_VEINS -> flags.contains(COPPER_ORE);
            case IRON_VEINS -> flags.contains(DEEP_TERRAIN);
            case ANCIENT_CITIES -> flags.contains(DEEP_TERRAIN) && flags.contains(DEEP_DARK);
            case SAVANNA,TAIGA,ABANDONED,DESERT_VILLAGES -> flags.contains(VILLAGES);
            case PANDAS -> flags.contains(BAMBOO);
            case SNOWY -> flags.contains(MODERN_VILLAGES);
            case BASTIONS,NETHER_FOSSILS,NETHER_PORTALS -> !flags.contains(JE_NETHER);
            default -> true;
        };
    }
    public static boolean villageBiome(int biome, Set<PeContent> flags, PeRelease release) {
        if (flags.contains(MODERN_VILLAGES)) return switch(biome) {
            case 1,2,5,30,35 -> true;
            case 12 -> flags.contains(SNOWY);
            default -> false;
        };
        return flags.contains(VILLAGES) && switch(biome) {
            case 1 -> true;
            case 2 -> flags.contains(DESERT_VILLAGES);
            case 35 -> flags.contains(SAVANNA);
            case 5,30 -> flags.contains(TAIGA);
            case 12 -> release.compareTo(PeRelease.V0_15_10)>=0;
            default -> false;
        };
    }
}
