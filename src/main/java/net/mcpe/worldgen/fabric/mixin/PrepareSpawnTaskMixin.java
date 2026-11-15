package net.mcpe.worldgen.fabric.mixin;

import java.util.concurrent.CompletableFuture;
import net.mcpe.worldgen.fabric.PeChunkGenerator;
import net.mcpe.worldgen.core.PeInitialPlayer;
import net.minecraft.server.network.PrepareSpawnTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PrepareSpawnTask.class)
abstract class PrepareSpawnTaskMixin {
    // 1.21.11 calls this supplier only when the joining player has no saved position.
    @Inject(method = "method_72293", at = @At("HEAD"), cancellable = true)
    private static void peWorldgen$initialPlayerPosition(ServerWorld world, WorldProperties.SpawnPoint spawn,
            CallbackInfoReturnable<CompletableFuture<Vec3d>> cir) {
        if (world.getChunkManager().getChunkGenerator() instanceof PeChunkGenerator pe
                && pe.usesPeSpawn() && spawn.getDimension().equals(world.getRegistryKey())
                && spawn.getPos().equals(pe.initialSpawnAnchor()) && spawn.yaw() == PeInitialPlayer.YAW
                && spawn.pitch() == PeInitialPlayer.PITCH) {
            cir.setReturnValue(CompletableFuture.supplyAsync(
                () -> pe.initialPlayerPosition(world, spawn.getPos()), world.getServer()));
        }
    }
}
