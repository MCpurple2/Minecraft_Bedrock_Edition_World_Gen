package net.mcpe.worldgen.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Keep vanilla biome smoothing inside the map, with a sharp transition at the void edge. */
@Mixin(BiomeAccess.class)
abstract class PeBorderBiomeMixin {
    @Unique private static final Identifier PE_DIMENSION = Identifier.of("pe_worldgen", "overworld");

    @WrapOperation(method = "getBiome", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/biome/source/BiomeAccess$Storage;getBiomeForNoiseGen(III)Lnet/minecraft/registry/entry/RegistryEntry;"))
    private RegistryEntry<Biome> peWorldgen$sampleEdge(BiomeAccess.Storage storage, int x, int y, int z,
            Operation<RegistryEntry<Biome>> original, BlockPos pos) {
        RegistryEntry<Biome> smoothed = original.call(storage, x, y, z);
        if ((x >> 2) == (pos.getX() >> 4) && (z >> 2) == (pos.getZ() >> 4)) return smoothed;
        if (!(storage instanceof World world) || !world.getDimensionEntry().matchesId(PE_DIMENSION)) return smoothed;
        RegistryEntry<Biome> local = storage.getBiomeForNoiseGen(pos.getX() >> 2, y, pos.getZ() >> 2);
        return local.matchesKey(BiomeKeys.THE_VOID) || smoothed.matchesKey(BiomeKeys.THE_VOID) ? local : smoothed;
    }
}
