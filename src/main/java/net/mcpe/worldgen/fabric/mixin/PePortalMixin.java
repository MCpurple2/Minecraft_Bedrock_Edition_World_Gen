package net.mcpe.worldgen.fabric.mixin;

import java.util.Optional;
import net.mcpe.worldgen.fabric.PeChunkGenerator;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.dimension.NetherPortal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetherPortal.class)
abstract class PePortalMixin {
    @Inject(method="getNewPortal",at=@At("HEAD"),cancellable=true)
    private static void peWorldgen$allowFrame(WorldAccess access, BlockPos pos, Direction.Axis axis,
            CallbackInfoReturnable<Optional<NetherPortal>> result) {
        if (access instanceof ServerWorld world && world.getServer().getOverworld().getChunkManager().getChunkGenerator()
                instanceof PeChunkGenerator pe && !pe.bedrock().allowPortals()) result.setReturnValue(Optional.empty());
    }
}
