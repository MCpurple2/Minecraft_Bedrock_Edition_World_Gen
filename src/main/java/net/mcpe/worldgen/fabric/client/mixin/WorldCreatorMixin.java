package net.mcpe.worldgen.fabric.client.mixin;

import net.mcpe.worldgen.fabric.client.PeCustomizeScreen;
import net.minecraft.client.gui.screen.world.LevelScreenProvider;
import net.minecraft.client.gui.screen.world.WorldCreator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enables the vanilla Customize button for the MCPE Alpha world type. */
@Mixin(WorldCreator.class)
public abstract class WorldCreatorMixin {
    @Shadow public abstract WorldCreator.WorldType getWorldType();

    @Inject(method = "getLevelScreenProvider", at = @At("HEAD"), cancellable = true)
    private void peWorldgen$customize(CallbackInfoReturnable<LevelScreenProvider> result) {
        var preset = getWorldType().preset();
        if (preset == null) return;
        preset.getKey().ifPresent(key -> {
            var id = key.getValue();
            // Keep the old preset IDs editable when recreating a previous world.
            if (id.getNamespace().equals("pe_worldgen") && switch (id.getPath()) {
                case "mcpe_alpha", "pe_0111_infinite", "pe_0111_old" -> true;
                default -> false;
            }) result.setReturnValue(PeCustomizeScreen::new);
        });
    }
}
