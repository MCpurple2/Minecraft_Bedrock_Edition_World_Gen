package net.mcpe.worldgen.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** Empty portal override follows the selected release/world type. All JE content is opt-in. */
public record PeContentSettings(boolean aquatic, boolean villages, boolean bees, boolean netherUpdate,
                                Optional<Boolean> portalOverride) {
    public static final PeContentSettings DEFAULT = new PeContentSettings(false, false, false, false, Optional.empty());
    public static final Codec<PeContentSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("aquatic", false).forGetter(PeContentSettings::aquatic),
        Codec.BOOL.optionalFieldOf("village_and_pillage", false).forGetter(PeContentSettings::villages),
        Codec.BOOL.optionalFieldOf("buzzy_bees", false).forGetter(PeContentSettings::bees),
        Codec.BOOL.optionalFieldOf("nether_update", false).forGetter(PeContentSettings::netherUpdate),
        Codec.BOOL.optionalFieldOf("allow_nether_portals").forGetter(PeContentSettings::portalOverride)
    ).apply(i, PeContentSettings::new));
    public boolean allowPortals(boolean finite) { return portalOverride.orElse(!finite); }
    public boolean overworldContent() { return aquatic || villages || bees; }
}
