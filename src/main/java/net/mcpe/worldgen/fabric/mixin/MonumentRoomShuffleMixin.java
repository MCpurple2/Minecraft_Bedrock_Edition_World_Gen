package net.mcpe.worldgen.fabric.mixin;

import java.util.List;
import net.mcpe.worldgen.fabric.FabricMonumentRooms;
import net.minecraft.util.Util;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Util.class)
abstract class MonumentRoomShuffleMixin {
    @Inject(method="shuffle(Ljava/util/List;Lnet/minecraft/util/math/random/Random;)V",at=@At("HEAD"),cancellable=true)
    private static <T> void peWorldgen$shuffleRooms(List<T> rooms, Random random, CallbackInfo ci) {
        if(random instanceof FabricMonumentRooms.RoomRandom) {
            FabricMonumentRooms.shuffleRooms(rooms);
            ci.cancel();
        }
    }
}
