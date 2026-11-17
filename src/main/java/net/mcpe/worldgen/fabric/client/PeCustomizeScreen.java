package net.mcpe.worldgen.fabric.client;

import net.mcpe.worldgen.fabric.PeBedrockSettings;
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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
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
    private static final String[] CATEGORIES = {"category.alpha_finite", "category.alpha", "category.pocket",
        "category.xbox_360", "category.xbox_one", "category.ps3", "category.ps4", "category.ps_vita",
        "category.wii_u", "category.switch"};
    private final CreateWorldScreen parent;
    private boolean old;
    private PeContentSettings settings = PeContentSettings.DEFAULT.resolve(PeRelease.V0_11_1,false);
    private PeBedrockSettings bedrock = PeBedrockSettings.defaults(PeRelease.V0_11_1,false, java.util.Optional.empty());
    private PeRelease release = PeRelease.V0_11_1;

    public PeCustomizeScreen(CreateWorldScreen parent, GeneratorOptionsHolder options) {
        super(label("title"));
        this.parent = parent;
        if (options.selectedDimensions().getChunkGenerator() instanceof PeChunkGenerator generator) {
            old = generator.isOld();
            release = generator.release();
            settings = generator.settings();
            bedrock = generator.bedrock();
        }
    }

    static MutableText label(String key, Object... args) {
        return Text.translatable("pe_worldgen.customize." + key, args);
    }

    private void openCategories() {
        var choices = new ArrayList<PePresetScreen.Choice>();
        for (int i = 0; i < CATEGORIES.length; i++) {
            boolean finite = i == 0;
            Text description = i < 2 ? label(CATEGORIES[i] + ".description") : Text.empty();
            Runnable open = i < 2 ? () -> openVersions(finite) : null;
            choices.add(new PePresetScreen.Choice(label(CATEGORIES[i]).getString(), description, open));
        }
        client.setScreen(new PePresetScreen(this, label("presets"), choices));
    }

    private void openVersions(boolean finite) {
        Text category = label(finite ? "versions.finite" : "versions.infinite");
        var choices = new ArrayList<PePresetScreen.Choice>();
        for (PeRelease candidate : PeRelease.values()) {
            // Old 0.16.2 remains readable for saved settings. New finite worlds use 0.16.5.
            if (candidate.supports(finite) && !(finite && candidate == PeRelease.V0_16_2)) {
                Text description = label(finite && candidate.hasWorldTypes() ? "version.finite_description"
                    : "version.description", candidate.id());
                choices.add(new PePresetScreen.Choice(candidate.id(), description,
                    () -> importPreset(finite, candidate), presetIcon(candidate,finite)));
            }
        }
        client.setScreen(new PePresetScreen(client.currentScreen, category, choices));
    }

    private Identifier presetIcon(PeRelease release, boolean finite) {
        var icon = Identifier.of("pe_worldgen", "textures/gui/presets/" + release.presetPath(finite) + ".png");
        return client.getResourceManager().getResource(icon).isPresent() ? icon : null;
    }

    private ButtonWidget button(Text text, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(text, b -> action.run()).dimensions(x, y, w, 20).build());
    }

    private void infoButton(String key, String value, Formatting color, int x, int y, int width) {
        Text text = Text.empty().append(label(key).formatted(Formatting.BOLD))
            .append(Text.literal(value).formatted(color));
        // Render a normal button without registering it for mouse or keyboard input.
        addDrawable(ButtonWidget.builder(text, button -> {}).dimensions(x, y, width, 20).build());
    }

    private int top() { return Math.max(19, Math.min(33, height - 201)); }
    private int rowStep() { return Math.clamp((height - top() - 51) / 6, 20, 24); }
    private int half() { return Math.min(150, (width - 22) / 2); }

    @Override protected void init() {
        int mid = width / 2;
        int y = top();
        int half = half();
        int presetWidth = Math.min(width - 16, Math.max(200, textRenderer.getWidth(presetLabel()) + 18));
        button(presetLabel(), mid - presetWidth / 2, y, presetWidth, this::openCategories);
        int step = rowStep();
        for (int i = 0; i < 2; i++) {
            boolean exclusive = i == 1;
            int left = mid - half;
            int start = left + Math.min(174, half * 2 - 70);
            var setting = button(label("settings"), start, y + (1 + i) * step,
                mid + half - start - 25, () -> client.setScreen(exclusive
                    ? new PeBedrockScreen(this, bedrock, value -> bedrock = value)
                    : new PeContentScreen(this, settings, value -> settings = value)));
            var advanced = button(Text.literal("✎"), setting.getX() + setting.getWidth() + 5, setting.getY(), 20,
                () -> client.setScreen(exclusive ? PeBedrockScreen.editor(this, bedrock, value -> bedrock = value)
                    : new PeContentJsonScreen(this, release, settings, value -> settings = value)));
            advanced.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("edit_json")));
        }
        int configureX = mid - half + Math.min(174, half * 2 - 70);
        button(label("configure"), configureX, y + 3 * step, mid + half - configureX - 25, () ->
            client.setScreen(new net.minecraft.client.gui.screen.NoticeScreen(() -> client.setScreen(this),
                label("mod_compatibility"), label("no_compatibility_options"))));
        var compatibilityJson = button(Text.literal("✎"), mid + half - 20, y + 3 * step, 20,
            this::openCompatibilityJson);
        compatibilityJson.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(label("edit_json")));
        int actions = y + 4 * step + 4;
        button(label("reset"), mid - half - 5, actions, half, () -> client.setScreen(new ConfirmScreen(answer -> {
            if (answer) {
                old = false;
                release = PeRelease.V0_11_1;
                settings = PeContentSettings.DEFAULT.resolve(release,old);
                bedrock = PeBedrockSettings.defaults(release,old,java.util.Optional.empty());
            }
            client.setScreen(this);
        }, label("reset"), label("reset_prompt"), label("reset"), ScreenTexts.CANCEL)));
        button(label("preview"), mid + 5, actions, half, () -> {
            long seed = GeneratorOptions.parseSeed(parent.getWorldCreator().getSeed())
                .orElse(parent.getWorldCreator().getGeneratorOptionsHolder().generatorOptions().getSeed());
            var registries = parent.getWorldCreator().getGeneratorOptionsHolder().getCombinedRegistryManager();
            var source = PeBiomeSource.preview(net.mcpe.worldgen.fabric.PeModernInputs.from(registries),registries.getOrThrow(RegistryKeys.BIOME),seed,old,release,settings,bedrock);
            client.setScreen(new PeBiomePreviewScreen(this,source,seed));
        });
        button(label("quick"), mid - half - 5, actions + step, half,
            () -> client.setScreen(new PePresetTransferScreen(this, old, release, settings, bedrock, false)));
        button(label("export_pack"), mid + 5, actions + step, half,
            () -> client.setScreen(new PePresetTransferScreen(this, old, release, settings, bedrock, true)));
        String version = FabricLoader.getInstance().getModContainer("pe_worldgen").orElseThrow()
            .getMetadata().getVersion().getFriendlyString();
        boolean iwbums = version.endsWith(" IWBUMS");
        infoButton("mod_version", version.replaceFirst(" (Stable|IWBUMS)$", ""), Formatting.YELLOW,
            mid - half - 5, actions + 2 * step, half);
        infoButton("branch", iwbums ? "IWBUMS" : "Stable", iwbums ? Formatting.RED : Formatting.GREEN,
            mid + 5, actions + 2 * step, half);
        button(ScreenTexts.DONE, mid - half - 5, height - 27, half, this::apply);
        button(ScreenTexts.CANCEL, mid + 5, height - 27, half, this::close);
    }

    private void openCompatibilityJson() {
        var gson = new com.google.gson.GsonBuilder().setStrictness(com.google.gson.Strictness.STRICT).create();
        client.setScreen(new PeSettingsJsonScreen<>(this, label("mod_compatibility"), "{}", text -> {
            var json = gson.fromJson(text, com.google.gson.JsonObject.class);
            // This build has no compatibility options. Reject keys rather than silently discarding them.
            if (json == null || !json.isEmpty()) throw new com.google.gson.JsonParseException("Unknown compatibility option");
            return json;
        }, ignored -> {}));
    }

    private Text presetLabel() {
        return presetLabel(release, old, settings, bedrock);
    }

    static Text presetLabel(PeRelease release, boolean old, PeContentSettings settings, PeBedrockSettings bedrock) {
        var defaults = PeContentSettings.DEFAULT.resolve(release, old);
        if (!settings.flags().equals(defaults.flags()) || !settings.water().equals(defaults.water())
                || !bedrock.equals(PeBedrockSettings.defaults(release, old, java.util.Optional.empty()))) {
            return label("preset", label("version.custom").formatted(Formatting.AQUA));
        }
        return label("preset", label(old && release.hasWorldTypes() ? "version.finite_name" : "version.name", release.id()).formatted(Formatting.YELLOW));
    }

    void importPreset(boolean selectedOld, PeRelease selectedRelease, PeContentSettings imported, PeBedrockSettings importedBedrock) {
        if (!selectedRelease.supports(selectedOld)) throw new IllegalArgumentException("Unsupported finite preset");
        release = selectedRelease;
        old = selectedOld;
        settings = imported.resolve(release,old);
        bedrock = importedBedrock.resolveClay(selectedRelease);
        client.setScreen(this);
    }
    void importPreset(boolean selectedOld, PeRelease selectedRelease) {
        if (!selectedRelease.supports(selectedOld)) throw new IllegalArgumentException("Unsupported finite preset");
        release = selectedRelease;
        old = selectedOld;
        settings = PeContentSettings.DEFAULT.resolve(release,old);
        bedrock = PeBedrockSettings.defaults(release,old,java.util.Optional.empty());
        client.setScreen(this);
    }

    private void apply() {
        boolean selectedOld = old;
        PeRelease selectedRelease = release;
        PeContentSettings selectedSettings = settings;
        PeBedrockSettings selectedBedrock = bedrock;
        parent.getWorldCreator().applyModifier((registries, dimensions) -> {
            var generator = new PeChunkGenerator(new PeBiomeSource(net.mcpe.worldgen.fabric.PeModernInputs.from(registries),registries.getOrThrow(RegistryKeys.BIOME), selectedOld), selectedOld, true, true, selectedRelease, selectedSettings, selectedBedrock);
            var updated = new LinkedHashMap<>(dimensions.dimensions());
            var overworld = registries.getOrThrow(RegistryKeys.DIMENSION_TYPE).getOrThrow(DimensionTypes.OVERWORLD);
            updated.put(DimensionOptions.OVERWORLD, new DimensionOptions(overworld, generator));
            var nether = updated.get(DimensionOptions.NETHER);
            if (nether != null) {
                var replacement = selectedSettings.netherUpdate()
                    ? new NoiseChunkGenerator(MultiNoiseBiomeSource.create(registries.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)), registries.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
                        .getOrThrow(ChunkGeneratorSettings.NETHER))
                    : new PeNetherChunkGenerator(new FixedBiomeSource(registries.getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.NETHER_WASTES)),selectedSettings);
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
        int right = width / 2 - half() + Math.min(174, half() * 2 - 70) - 5;
        String[] labels = {label("content").getString(), label("bedrock").getString(), label("mod_compatibility").getString()};
        for (int i = 0; i < labels.length; i++) {
            context.drawTextWithShadow(textRenderer, labels[i], right - textRenderer.getWidth(labels[i]),
                top() + (1 + i) * rowStep() + 6, 0xffffffff);
        }
    }
}
