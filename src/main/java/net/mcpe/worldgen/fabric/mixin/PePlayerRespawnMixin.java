package net.mcpe.worldgen.fabric.mixin;

import net.mcpe.worldgen.core.PeContent;
import net.mcpe.worldgen.core.PeInitialPlayer;
import net.mcpe.worldgen.fabric.PeChunkGenerator;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
abstract class PePlayerRespawnMixin {
    // Personal respawn targets (beds, anchors and /spawnpoint) are resolved before
    // this fallback. A changed world spawn keeps the normal Java search.
    @Inject(method = "getWorldSpawnPos", at = @At("HEAD"), cancellable = true)
    private void peWorldgen$worldSpawn(ServerWorld world, BlockPos anchor,
            CallbackInfoReturnable<BlockPos> result) {
        if (!(world.getChunkManager().getChunkGenerator() instanceof PeChunkGenerator pe)
                || !pe.usesPeSpawn()) return;
        if (pe.release().hasWorldTypes() && !pe.settings().has(PeContent.DEEP_TERRAIN)) return;
        var spawn = world.getSpawnPoint();
        if (spawn.getDimension().equals(world.getRegistryKey()) && anchor.equals(pe.initialSpawnAnchor())
                && spawn.yaw() == PeInitialPlayer.YAW && spawn.pitch() == PeInitialPlayer.PITCH) {
            result.setReturnValue(BlockPos.ofFloored(pe.initialPlayerPosition(world, anchor)));
        }
    }
}
