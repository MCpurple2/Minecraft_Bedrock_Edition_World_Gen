package net.mcpe.worldgen.fabric.client.mixin;

import net.mcpe.worldgen.fabric.client.PeCustomizeScreen;
import net.minecraft.client.gui.screen.world.LevelScreenProvider;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import java.util.Arrays;
import net.mcpe.worldgen.core.PeRelease;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enables the vanilla Customize button for the Pocket Edition Alpha world type. */
@Mixin(WorldCreator.class)
public abstract class WorldCreatorMixin {
    @Shadow public abstract WorldCreator.WorldType getWorldType();
    @Shadow public abstract GeneratorOptionsHolder getGeneratorOptionsHolder();
    @Shadow public abstract List<WorldCreator.WorldType> getNormalWorldTypes();
    @Shadow public abstract List<WorldCreator.WorldType> getExtendedWorldTypes();

    // The screen rebuilds both lists on creation and on a data pack reload.
    // A pack that replaces the vanilla tag can omit a registered preset.
    @Inject(method = "updateWorldTypeLists", at = @At("TAIL"))
    private void peWorldgen$includePreset(CallbackInfo info) {
        var registry = getGeneratorOptionsHolder().getCombinedRegistryManager().getOrThrow(RegistryKeys.WORLD_PRESET);
        registry.getEntry(Identifier.of("pe_worldgen", "mcpe_alpha")).ifPresent(entry -> {
            var type = new WorldCreator.WorldType(entry);
            if (!getNormalWorldTypes().contains(type)) getNormalWorldTypes().add(type);
            if (!getExtendedWorldTypes().contains(type)) getExtendedWorldTypes().add(type);
        });
    }

    @Inject(method = "getLevelScreenProvider", at = @At("HEAD"), cancellable = true)
    private void peWorldgen$customize(CallbackInfoReturnable<LevelScreenProvider> result) {
        var preset = getWorldType().preset();
        if (preset == null) return;
        preset.getKey().ifPresent(key -> {
            var id = key.getValue();
            // Keep the old preset IDs editable when recreating a previous world.
            if (id.getNamespace().equals("pe_worldgen") && (id.getPath().equals("mcpe_alpha") || id.getPath().equals(PeRelease.V0_11_1.presetPath(true))
                || Arrays.stream(PeRelease.values()).anyMatch(release -> id.getPath().equals(release.presetPath(false))))) result.setReturnValue(PeCustomizeScreen::new);
        });
    }
}
