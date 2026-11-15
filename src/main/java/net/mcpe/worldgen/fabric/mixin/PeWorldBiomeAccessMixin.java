package net.mcpe.worldgen.fabric.mixin;

import net.mcpe.worldgen.fabric.PeBorderBiomeAccess;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
abstract class PeWorldBiomeAccessMixin {
    @Shadow @Final @Mutable private BiomeAccess biomeAccess;

    // Install after vanilla initialization; no factory redirect for MixinExtras to transform.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void peWorldgen$installBiomeAccess(MutableWorldProperties properties, RegistryKey<World> dimension,
            DynamicRegistryManager registries, RegistryEntry<DimensionType> type, boolean client, boolean debug,
            long biomeSeed, int maxChainDepth, CallbackInfo ci) {
        if (World.OVERWORLD.equals(dimension)) {
            biomeAccess = new PeBorderBiomeAccess((World)(Object)this, biomeSeed);
        }
    }
}
