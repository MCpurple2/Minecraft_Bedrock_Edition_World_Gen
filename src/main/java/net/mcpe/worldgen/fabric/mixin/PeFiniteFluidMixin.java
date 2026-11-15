package net.mcpe.worldgen.fabric.mixin;

import net.mcpe.worldgen.fabric.PeOldWorldBorder;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowableFluid.class)
abstract class PeFiniteFluidMixin {
    @Inject(method="flow",at=@At("HEAD"),cancellable=true)
    private void peWorldgen$keepFluidInsideBorder(WorldAccess world, BlockPos target, BlockState state,
            Direction direction, FluidState fluid, CallbackInfo ci) {
        if(world instanceof ServerWorld server) {
            var bounds=PeOldWorldBorder.bounds(server);
            if(bounds!=null && !bounds.containsPoint(target.getX(),target.getZ(),0))ci.cancel();
        }
    }
}
