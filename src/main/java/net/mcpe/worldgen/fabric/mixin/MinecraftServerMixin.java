package net.mcpe.worldgen.fabric.mixin;

import net.mcpe.worldgen.fabric.PeChunkGenerator;
import net.mcpe.worldgen.core.PeInitialPlayer;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.chunk.ChunkLoadProgress;
import net.minecraft.world.gen.feature.MiscConfiguredFeatures;
import net.minecraft.world.level.ServerWorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Inject(method = "setupSpawn", at = @At("HEAD"), cancellable = true)
    private static void peWorldgen$setupSpawn(ServerWorld world, ServerWorldProperties properties,
            boolean bonusChest, boolean debug, ChunkLoadProgress progress, CallbackInfo ci) {
        if (debug || !(world.getChunkManager().getChunkGenerator() instanceof PeChunkGenerator pe)
                || !pe.usesPeSpawn()) return;
        BlockPos anchor = pe.initialSpawnAnchor();
        var stage = ChunkLoadProgress.Stage.PREPARE_GLOBAL_SPAWN;
        progress.init(stage, 0);
        progress.initSpawnPos(world.getRegistryKey(), new ChunkPos(anchor));
        properties.setSpawnPoint(WorldProperties.SpawnPoint.create(world.getRegistryKey(), anchor,
            PeInitialPlayer.YAW, PeInitialPlayer.PITCH));
        if (bonusChest) {
            world.getRegistryManager().getOptional(RegistryKeys.CONFIGURED_FEATURE)
                .flatMap(registry -> registry.getOptional(MiscConfiguredFeatures.BONUS_CHEST))
                .ifPresent(entry -> entry.value().generate(world, pe, world.random, anchor));
        }
        progress.finish(stage);
        ci.cancel();
    }
}
