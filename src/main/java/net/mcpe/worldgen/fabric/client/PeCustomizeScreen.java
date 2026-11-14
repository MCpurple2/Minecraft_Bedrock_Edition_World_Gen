package net.mcpe.worldgen.fabric.client;

import java.util.LinkedHashMap;
import net.mcpe.worldgen.fabric.PeBiomeSource;
import net.mcpe.worldgen.fabric.PeChunkGenerator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;

/** Edits a local draft; only Done writes the chosen generator into world creation. */
public final class PeCustomizeScreen extends Screen {
    private final CreateWorldScreen parent;
    private boolean old;

    public PeCustomizeScreen(CreateWorldScreen parent, GeneratorOptionsHolder options) {
        super(Text.translatable("generator.pe_worldgen.mcpe_alpha"));
        this.parent = parent;
        old = options.selectedDimensions().getChunkGenerator() instanceof PeChunkGenerator generator && generator.isOld();
    }

    @Override protected void init() {
        int rowWidth = Math.min(310, width - 40);
        int left = (width - rowWidth) / 2;
        var version = CyclingButtonWidget.builder((String value) -> Text.literal(value), "0.11.1")
            .values("0.11.1")
            .build(left, 56, rowWidth, 20, Text.translatable("pe_worldgen.customize.version"));
        // Display only implemented presets; additional versions can be added here.
        version.active = false;
        addDrawableChild(version);
        addDrawableChild(CyclingButtonWidget.builder((Boolean value) -> Text.translatable(
                value ? "pe_worldgen.customize.old" : "pe_worldgen.customize.infinite"), old)
            .values(false, true)
            .build(left, 84, rowWidth, 20, Text.translatable("pe_worldgen.customize.world_type"),
                (button, value) -> old = value));
        int buttonWidth = (rowWidth - 8) / 2;
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> apply())
            .dimensions(left, height - 28, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
            .dimensions(left + buttonWidth + 8, height - 28, buttonWidth, 20).build());
    }

    private void apply() {
        boolean selectedOld = old;
        parent.getWorldCreator().applyModifier((registries, dimensions) -> {
            var previous = dimensions.dimensions().get(DimensionOptions.OVERWORLD);
            if (previous == null) throw new IllegalStateException("Missing overworld in Pocket Edition Alpha preset");
            var generator = new PeChunkGenerator(new PeBiomeSource(registries.getOrThrow(RegistryKeys.BIOME), selectedOld), selectedOld);
            var updated = new LinkedHashMap<>(dimensions.dimensions());
            // Preserve the PE dimension type (0..127) and all other dimensions.
            updated.put(DimensionOptions.OVERWORLD, new DimensionOptions(previous.dimensionTypeEntry(), generator));
            return new DimensionOptionsRegistryHolder(updated);
        });
        close();
    }

    @Override public void close() { client.setScreen(parent); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xffffffff);
    }
}
