package net.mcpe.worldgen.fabric.mixin;

import net.mcpe.worldgen.fabric.PeChunkGenerator;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRegion.class)
abstract class PeFeatureRegionMixin {
    @Inject(method="setBlockState",at=@At("HEAD"),cancellable=true)
    private void peWorldgen$clipFeatures(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> result) {
        var region=(ChunkRegion)(Object)this;
        var world = region.toServerWorld();
        var bounds = net.mcpe.worldgen.fabric.PeOldWorldBorder.bounds(world);
        if ((world.getChunkManager().getChunkGenerator() instanceof PeChunkGenerator pe && ((pos.getY()<0 && !pe.settings().has(net.mcpe.worldgen.core.PeContent.DEEP_TERRAIN)) || (pos.getY()>=128 && !pe.settings().has(net.mcpe.worldgen.core.PeContent.MOUNTAIN_TERRAIN))))
                || bounds != null && !bounds.containsPoint(pos.getX(), pos.getZ(), 0))
            result.setReturnValue(false);
    }
}
