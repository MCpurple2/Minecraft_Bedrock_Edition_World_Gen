package net.mcpe.worldgen.fabric.client.mixin;

import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.widget.EditBoxWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// From Moderner Beta's MultiLineEditBoxAccessor (MIT), adapted to Yarn names.
@Mixin(EditBoxWidget.class)
public interface EditBoxAccessor {
    @Accessor("editBox")
    EditBox peWorldgen$getEditBox();
}
