package net.mcpe.worldgen.fabric.mixin;

import java.util.concurrent.CompletableFuture;
import net.mcpe.worldgen.fabric.PeOldWorldBorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.collection.BoundedRegionArray;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerating.class)
abstract class PeNetherBoundsMixin {
    private static boolean exterior(ChunkGenerationContext context, Chunk chunk) {
        if (!context.world().getRegistryKey().equals(World.NETHER)) return false;
        var border = PeOldWorldBorder.bounds(context.world());
        return border != null && !border.containsChunk(chunk.getPos().x, chunk.getPos().z);
    }

    @Inject(method={"generateStructures","generateStructureReferences","populateNoise","buildSurface","carve",
        "generateFeatures","generateEntities"}, at=@At("HEAD"), cancellable=true)
    private static void peWorldgen$clipNether(ChunkGenerationContext context, ChunkGenerationStep step,
            BoundedRegionArray<AbstractChunkHolder> region, Chunk chunk, CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        if (exterior(context, chunk)) cir.setReturnValue(CompletableFuture.completedFuture(chunk));
    }

    @Inject(method="populateBiomes", at=@At("HEAD"), cancellable=true)
    private static void peWorldgen$voidNether(ChunkGenerationContext context, ChunkGenerationStep step,
            BoundedRegionArray<AbstractChunkHolder> region, Chunk chunk, CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        if (!exterior(context, chunk)) return;
        var outside = context.world().getRegistryManager().getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.THE_VOID);
        var sampler = context.world().getChunkManager().getNoiseConfig().getMultiNoiseSampler();
        chunk.populateBiomes((x,y,z,noise) -> outside, sampler);
        cir.setReturnValue(CompletableFuture.completedFuture(chunk));
    }
}
