package net.mcpe.worldgen.fabric.client;

import java.util.LinkedHashMap;
import net.mcpe.worldgen.fabric.PeBiomeSource;
import net.mcpe.worldgen.fabric.PeChunkGenerator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.ScrollableLayoutWidget;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.GeneratorOptions;

/** Settings grid and two-level preset selection adapted from Moderner Beta's client GUI. */
public final class PeCustomizeScreen extends Screen {
    static final String FINITE = "Pocket Edition Alpha Finite";
    static final String INFINITE = "Pocket Edition Alpha";
    private static final String[] CATEGORIES = {FINITE, INFINITE, "Pocket Edition", "Xbox 360 Edition",
        "Xbox One Edition", "PlayStation 3 Edition", "PlayStation 4 Edition", "PlayStation Vita Edition",
        "Wii U Edition", "Nintendo Switch Edition"};
    private static final String[] DESCRIPTIONS = {
        "World generation from Pocket Edition Alpha 0.1.3 to Alpha 0.8.1. Also includes world generation from Pocket Edition Alpha 0.9.5 to Alpha 0.16.2 using the Old world type.",
        "World generation from Pocket Edition Alpha 0.9.5 and Alpha 0.16.2."
    };
    private enum Page { MAIN, CATEGORIES, VERSIONS }
    private final CreateWorldScreen parent;
    private boolean old;
    private boolean viewingFinite;
    private Page page = Page.MAIN;

    public PeCustomizeScreen(CreateWorldScreen parent, GeneratorOptionsHolder options) {
        super(label("title"));
        this.parent = parent;
        old = options.selectedDimensions().getChunkGenerator() instanceof PeChunkGenerator generator && generator.isOld();
    }

    static Text label(String key, Object... args) {
        return Text.translatable("pe_worldgen.customize." + key, args);
    }

    private void navigate(Page target) {
        page = target;
        clearAndInit();
    }

    private ButtonWidget button(Text text, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(text, b -> action.run()).dimensions(x, y, w, 20).build());
    }

    private int top() { return Math.max(24, Math.min(33, height - 185)); }
    private int half() { return Math.min(150, (width - 22) / 2); }

    @Override protected void init() {
        int mid = width / 2;
        if (page != Page.MAIN) {
            int listWidth = Math.min(340, width - 32);
            if (page == Page.CATEGORIES) {
                var rows = DirectionalLayoutWidget.vertical().spacing(3);
                for (int index = 0; index < CATEGORIES.length; index++) {
                    boolean finite = index == 0;
                    var choice = ButtonWidget.builder(Text.literal(CATEGORIES[index]), b -> {
                        viewingFinite = finite;
                        navigate(Page.VERSIONS);
                    }).width(listWidth).build();
                    choice.active = index < DESCRIPTIONS.length;
                    if (choice.active) choice.setTooltip(Tooltip.of(Text.literal(DESCRIPTIONS[index])));
                    rows.add(choice);
                }
                rows.refreshPositions();
                var scroll = new ScrollableLayoutWidget(client, rows, height - 70);
                scroll.setWidth(listWidth + 12);
                scroll.setHeight(height - 70);
                scroll.refreshPositions();
                scroll.setPosition(mid - scroll.getWidth() / 2, 34);
                scroll.forEachChild(this::addDrawableChild);
            } else {
                Text description = Text.literal(DESCRIPTIONS[viewingFinite ? 0 : 1]);
                int lines = textRenderer.wrapLines(description, listWidth).size();
                button(Text.literal("0.11.1"), mid - listWidth / 2, 49 + lines * textRenderer.fontHeight,
                    listWidth, () -> { old = viewingFinite; navigate(Page.MAIN); });
            }
            button(ScreenTexts.BACK, mid - 75, height - 27, 150, this::close);
            return;
        }

        int y = top();
        int half = half();
        int presetWidth = Math.min(width - 16, Math.max(200, textRenderer.getWidth(presetLabel()) + 18));
        button(presetLabel(), mid - presetWidth / 2, y, presetWidth, () -> navigate(Page.CATEGORIES));
        for (int i = 0; i < 3; i++) {
            var setting = button(Text.literal(i == 0 ? "Setting" : "WIP"), mid - half + 102, y + 24 + i * 24,
                Math.max(65, half * 2 - 140), () -> {});
            setting.active = false;
            var advanced = button(Text.literal("…"), setting.getX() + setting.getWidth() + 5, setting.getY(), 20, () -> {});
            advanced.active = false;
        }
        int actions = y + 100;
        button(label("reset"), mid - half - 5, actions, half, () -> client.setScreen(new ConfirmScreen(answer -> {
            if (answer) old = false;
            client.setScreen(this);
        }, label("reset"), label("reset_prompt"), label("reset"), ScreenTexts.CANCEL)));
        button(label("preview"), mid + 5, actions, half, () -> {
            long seed = GeneratorOptions.parseSeed(parent.getWorldCreator().getSeed())
                .orElse(parent.getWorldCreator().getGeneratorOptionsHolder().generatorOptions().getSeed());
            client.setScreen(new PeBiomePreviewScreen(this, seed, old));
        });
        button(label("quick"), mid - half - 5, actions + 24, half,
            () -> client.setScreen(new PePresetTransferScreen(this, old, false)));
        button(label("export_pack"), mid + 5, actions + 24, half,
            () -> client.setScreen(new PePresetTransferScreen(this, old, true)));
        button(ScreenTexts.DONE, mid - half - 5, height - 27, half, this::apply);
        button(ScreenTexts.CANCEL, mid + 5, height - 27, half, this::close);
    }

    private Text presetLabel() {
        return label("preset", Text.literal(old ? FINITE : INFINITE).formatted(Formatting.YELLOW));
    }

    void importPreset(boolean selectedOld) {
        old = selectedOld;
        client.setScreen(this);
    }

    private void apply() {
        boolean selectedOld = old;
        parent.getWorldCreator().applyModifier((registries, dimensions) -> {
            var generator = new PeChunkGenerator(new PeBiomeSource(registries.getOrThrow(RegistryKeys.BIOME), selectedOld), selectedOld, true, true);
            var updated = new LinkedHashMap<>(dimensions.dimensions());
            var overworld = registries.getOrThrow(RegistryKeys.DIMENSION_TYPE).getOrThrow(DimensionTypes.OVERWORLD);
            updated.put(DimensionOptions.OVERWORLD, new DimensionOptions(overworld, generator));
            return new DimensionOptionsRegistryHolder(updated);
        });
        client.setScreen(parent);
    }

    @Override public void close() {
        switch (page) {
            case MAIN -> client.setScreen(parent);
            case CATEGORIES -> navigate(Page.MAIN);
            case VERSIONS -> navigate(Page.CATEGORIES);
        }
    }

    @Override public void renderBackground(DrawContext context, int x, int y, float delta) {
        renderBackgroundTexture(context, Identifier.ofVanilla("textures/block/dirt.png"), 0, 0, 0, 0, width, height);
        context.fill(0, 0, width, height, 0xc0000000);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Text heading = switch (page) {
            case MAIN -> title;
            case CATEGORIES -> label("presets");
            case VERSIONS -> Text.literal(viewingFinite ? FINITE : INFINITE);
        };
        context.drawCenteredTextWithShadow(textRenderer, heading, width / 2, top() - 16, 0xffffffff);
        if (page == Page.VERSIONS) {
            int w = Math.min(340, width - 32);
            context.drawWrappedTextWithShadow(textRenderer, Text.literal(DESCRIPTIONS[viewingFinite ? 0 : 1]),
                (width - w) / 2, 36, w, 0xffdddddd);
        }
        if (page == Page.MAIN) {
            int right = width / 2 - half() + 95;
            String[] labels = {"Java Edition Content", "WIP", "WIP"};
            for (int i = 0; i < labels.length; i++) {
                context.drawTextWithShadow(textRenderer, labels[i], right - textRenderer.getWidth(labels[i]),
                    top() + 30 + i * 24, 0xffffffff);
            }
        }
    }
}
