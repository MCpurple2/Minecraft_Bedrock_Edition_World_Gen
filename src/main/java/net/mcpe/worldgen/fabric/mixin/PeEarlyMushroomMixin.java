package net.mcpe.worldgen.fabric.mixin;

import net.mcpe.worldgen.fabric.PeChunkGenerator;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MushroomPlantBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MushroomPlantBlock.class)
abstract class PeEarlyMushroomMixin {
    private static boolean usesPeBushRules(WorldView world) {
        return world instanceof ServerWorldAccess access
            && access.toServerWorld().getChunkManager().getChunkGenerator() instanceof PeChunkGenerator generator
            && generator.release().hasBushMushrooms();
    }

    @Inject(method="canPlaceAt", at=@At("HEAD"), cancellable=true)
    private void peWorldgen$bushSurvival(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> result) {
        if (!usesPeBushRules(world)) return;
        BlockState below = world.getBlockState(pos.down());
        result.setReturnValue((below.isOf(Blocks.GRASS_BLOCK) || below.isOf(Blocks.DIRT) || below.isOf(Blocks.FARMLAND))
            && (world.isSkyVisible(pos) || world.getLightLevel(pos) >= 8));
    }

    @Inject(method="randomTick", at=@At("HEAD"), cancellable=true)
    private void peWorldgen$bushTick(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo callback) {
        if (!usesPeBushRules(world)) return;
        // Bush::tick only rechecks support and light. It has no mushroom spreading pass.
        if (!state.canPlaceAt(world,pos)) world.breakBlock(pos,true);
        callback.cancel();
    }
}
