package net.mcpe.worldgen.fabric.mixin;

import net.mcpe.worldgen.fabric.PeBorderBiomeAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
abstract class PeWorldBiomeAccessMixin {
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/biome/source/BiomeAccess"))
    private BiomeAccess peWorldgen$createAccess(BiomeAccess.Storage storage, long seed) {
        return new PeBorderBiomeAccess(storage, seed);
    }
}
