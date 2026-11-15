package net.mcpe.worldgen.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.mcpe.worldgen.core.PeWorldBorderLocation;
import net.mcpe.worldgen.core.PeWorldGenerator;

/** Coordinates and side length are in chunks so even the minimum contains a complete chunk. */
public record PeBedrockSettings(boolean allowPortals, boolean syncNether, int size,
                                Optional<Integer> minX, Optional<Integer> minZ) {
    private static final int LIMIT = 1_874_998;
    public static final Codec<PeBedrockSettings> CODEC = RecordCodecBuilder.<PeBedrockSettings>create(i -> i.group(
        Codec.BOOL.fieldOf("allow_nether_portal").forGetter(PeBedrockSettings::allowPortals),
        Codec.BOOL.fieldOf("sync_nether_border_size").forGetter(PeBedrockSettings::syncNether),
        Codec.INT.fieldOf("border_size_chunks").forGetter(PeBedrockSettings::size),
        Codec.INT.optionalFieldOf("min_chunk_x").forGetter(PeBedrockSettings::minX),
        Codec.INT.optionalFieldOf("min_chunk_z").forGetter(PeBedrockSettings::minZ)
    ).apply(i, PeBedrockSettings::new)).validate(s -> s.valid() ? DataResult.success(s)
        : DataResult.error(() -> "Invalid border size or chunk coordinates"));

    public static PeBedrockSettings defaults(boolean old, Optional<Boolean> legacyPortal) {
        return new PeBedrockSettings(legacyPortal.orElse(true), true, old ? 16 : -1, Optional.empty(), Optional.empty());
    }

    public boolean valid() {
        return (size == -1 || size >= 1 && size <= 2 * LIMIT) && validStart(minX) && validStart(minZ);
    }

    private boolean validStart(Optional<Integer> start) {
        return start.isEmpty() || start.get() >= -LIMIT && start.get() <= LIMIT - Math.max(1, size);
    }

    public void configure(PeWorldGenerator core, boolean useSpawn) {
        if (size == -1) {
            core.configureBorder(null);
            return;
        }
        var anchor = useSpawn ? core.initialSpawn() : new PeWorldGenerator.Spawn(0, 0, 0);
        core.configureBorder(new PeWorldBorderLocation(center(minX, anchor.x()), center(minZ, anchor.z()), size * 16));
    }

    private int center(Optional<Integer> start, int anchor) {
        int min = start.orElse(Math.clamp((anchor >> 4) - size / 2, -LIMIT, LIMIT - size));
        return min * 16 + size * 8;
    }
}
