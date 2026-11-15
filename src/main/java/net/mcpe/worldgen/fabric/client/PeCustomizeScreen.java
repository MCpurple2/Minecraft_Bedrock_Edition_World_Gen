package net.mcpe.worldgen.fabric.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import net.mcpe.worldgen.core.PeRelease;
import net.mcpe.worldgen.fabric.PeContentSettings;
import net.mcpe.worldgen.fabric.PeNetherChunkGenerator;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.mcpe.worldgen.fabric.PeBiomeSource;
import net.mcpe.worldgen.fabric.PeChunkGenerator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
    private final CreateWorldScreen parent;
    private boolean old;
    private PeContentSettings settings = PeContentSettings.DEFAULT;
    private PeRelease release = PeRelease.V0_11_1;

    public PeCustomizeScreen(CreateWorldScreen parent, GeneratorOptionsHolder options) {
        super(label("title"));
        this.parent = parent;
        if (options.selectedDimensions().getChunkGenerator() instanceof PeChunkGenerator generator) {
            old = generator.isOld();
            release = generator.release();
            settings = generator.settings();
        }
    }

    static Text label(String key, Object... args) {
        return Text.translatable("pe_worldgen.customize." + key, args);
    }

    private void openCategories() {
        var choices = new ArrayList<PePresetScreen.Choice>();
        for (int i = 0; i < CATEGORIES.length; i++) {
            boolean finite = i == 0;
            Text description = i < DESCRIPTIONS.length ? Text.literal(DESCRIPTIONS[i]) : Text.empty();
            Runnable open = i < DESCRIPTIONS.length ? () -> openVersions(finite) : null;
            choices.add(new PePresetScreen.Choice(CATEGORIES[i], description, open));
        }
        client.setScreen(new PePresetScreen(this, label("presets"), choices));
    }

    private void openVersions(boolean finite) {
        String category = finite ? FINITE : INFINITE;
        var choices = new ArrayList<PePresetScreen.Choice>();
        for (PeRelease candidate : PeRelease.values()) {
            if (candidate.supports(finite)) {
                choices.add(new PePresetScreen.Choice(candidate.id(), Text.literal(category + " " + candidate.id()),
                    () -> importPreset(finite, candidate)));
            }
        }
        client.setScreen(new PePresetScreen(client.currentScreen, Text.literal(category), choices));
    }

    private ButtonWidget button(Text text, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(text, b -> action.run()).dimensions(x, y, w, 20).build());
    }

    private int top() { return Math.max(24, Math.min(33, height - 185)); }
    private int half() { return Math.min(150, (width - 22) / 2); }

    @Override protected void init() {
        int mid = width / 2;
        int y = top();
        int half = half();
        int presetWidth = Math.min(width - 16, Math.max(200, textRenderer.getWidth(presetLabel()) + 18));
        button(presetLabel(), mid - presetWidth / 2, y, presetWidth, this::openCategories);
        for (int i = 0; i < 3; i++) {
            var setting = button(Text.literal(i == 0 ? "Setting" : "WIP"), mid - half + 102, y + 24 + i * 24,
                Math.max(65, half * 2 - 140), () -> client.setScreen(new PeContentScreen(this, old, settings, value -> settings = value)));
            setting.active = i == 0;
            var advanced = button(Text.literal(i == 0 ? "✎" : "…"), setting.getX() + setting.getWidth() + 5, setting.getY(), 20,
                () -> client.setScreen(new PeContentJsonScreen(this, settings, value -> settings = value)));
            advanced.active = i == 0;
            if (advanced.active) advanced.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("edit_json")));
        }
        int actions = y + 100;
        button(label("reset"), mid - half - 5, actions, half, () -> client.setScreen(new ConfirmScreen(answer -> {
            if (answer) {
                old = false;
                release = PeRelease.V0_11_1;
                settings = PeContentSettings.DEFAULT;
            }
            client.setScreen(this);
        }, label("reset"), label("reset_prompt"), label("reset"), ScreenTexts.CANCEL)));
        button(label("preview"), mid + 5, actions, half, () -> {
            long seed = GeneratorOptions.parseSeed(parent.getWorldCreator().getSeed())
                .orElse(parent.getWorldCreator().getGeneratorOptionsHolder().generatorOptions().getSeed());
            client.setScreen(new PeBiomePreviewScreen(this, seed, old, release, settings));
        });
        button(label("quick"), mid - half - 5, actions + 24, half,
            () -> client.setScreen(new PePresetTransferScreen(this, old, release, settings, false)));
        button(label("export_pack"), mid + 5, actions + 24, half,
            () -> client.setScreen(new PePresetTransferScreen(this, old, release, settings, true)));
        button(ScreenTexts.DONE, mid - half - 5, height - 27, half, this::apply);
        button(ScreenTexts.CANCEL, mid + 5, height - 27, half, this::close);
    }

    private Text presetLabel() {
        return label("preset", Text.literal(release.id()).formatted(Formatting.YELLOW));
    }

    void importPreset(boolean selectedOld, PeRelease selectedRelease, PeContentSettings imported) {
        if (!selectedRelease.supports(selectedOld)) throw new IllegalArgumentException("Unsupported finite preset");
        settings = imported;
        importPreset(selectedOld,selectedRelease);
    }
    void importPreset(boolean selectedOld, PeRelease selectedRelease) {
        if (!selectedRelease.supports(selectedOld)) throw new IllegalArgumentException("Unsupported finite preset");
        release = selectedRelease;
        old = selectedOld;
        client.setScreen(this);
    }

    private void apply() {
        boolean selectedOld = old;
        PeRelease selectedRelease = release;
        PeContentSettings selectedSettings = settings;
        parent.getWorldCreator().applyModifier((registries, dimensions) -> {
            var generator = new PeChunkGenerator(new PeBiomeSource(registries.getOrThrow(RegistryKeys.BIOME), selectedOld), selectedOld, true, true, selectedRelease, selectedSettings);
            var updated = new LinkedHashMap<>(dimensions.dimensions());
            var overworld = registries.getOrThrow(RegistryKeys.DIMENSION_TYPE).getOrThrow(DimensionTypes.OVERWORLD);
            updated.put(DimensionOptions.OVERWORLD, new DimensionOptions(overworld, generator));
            var nether = updated.get(DimensionOptions.NETHER);
            if (nether != null) {
                var replacement = selectedSettings.netherUpdate()
                    ? new NoiseChunkGenerator(MultiNoiseBiomeSource.create(registries.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)), registries.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
                        .getOrThrow(ChunkGeneratorSettings.NETHER))
                    : new PeNetherChunkGenerator(new FixedBiomeSource(registries.getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.NETHER_WASTES)));
                updated.put(DimensionOptions.NETHER, new DimensionOptions(nether.dimensionTypeEntry(), replacement));
            }
            return new DimensionOptionsRegistryHolder(updated);
        });
        client.setScreen(parent);
    }

    @Override public void close() { client.setScreen(parent); }

    @Override public void renderBackground(DrawContext context, int x, int y, float delta) {
        renderBackgroundTexture(context, Identifier.ofVanilla("textures/block/dirt.png"), 0, 0, 0, 0, width, height);
        context.fill(0, 0, width, height, 0xc0000000);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, top() - 16, 0xffffffff);
        int right = width / 2 - half() + 95;
        String[] labels = {"Java Edition Content", "WIP", "WIP"};
        for (int i = 0; i < labels.length; i++) {
            context.drawTextWithShadow(textRenderer, labels[i], right - textRenderer.getWidth(labels[i]),
                top() + 30 + i * 24, 0xffffffff);
        }
    }
}
