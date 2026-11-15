package net.mcpe.worldgen.fabric;

import net.mcpe.worldgen.core.PeWorldBorderLocation;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeAccess;

/** Keep vanilla/C2ME smoothing except where it would cross a finite PE map's edge. */
public final class PeBorderBiomeAccess extends BiomeAccess {
    private volatile PeWorldBorderLocation bounds;

    public PeBorderBiomeAccess(Storage storage, long seed) {
        super(storage, seed);
    }

    public void setBounds(PeFiniteBounds bounds) {
        this.bounds = bounds == null ? null : bounds.location();
    }

    @Override public RegistryEntry<Biome> getBiome(BlockPos pos) {
        var edge = bounds;
        if (edge == null) return super.getBiome(pos);
        if (!edge.containsPoint(pos.getX(), pos.getZ(), 0)) return getBiomeForNoiseGen(pos);
        var smoothed = super.getBiome(pos);
        return smoothed.matchesKey(BiomeKeys.THE_VOID) ? getBiomeForNoiseGen(pos) : smoothed;
    }
}
