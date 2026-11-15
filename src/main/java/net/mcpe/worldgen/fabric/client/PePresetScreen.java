package net.mcpe.worldgen.fabric.client;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Adapted from Moderner Beta's ModernBetaSettingsPresetScreen.PresetsListWidget. */
final class PePresetScreen extends Screen {
    private static final Identifier ICON = Identifier.of("pe_worldgen", "dynamic/programmer_art_icon");
    private static final Identifier FALLBACK = Identifier.ofVanilla("textures/misc/unknown_pack.png");
    private static final int ICON_SIZE = 56;
    private final Screen parent;
    private final List<Choice> choices;
    private PresetList list;
    private ButtonWidget use;
    private boolean loaded;

    record Choice(String name, Text description, Runnable select) {}

    PePresetScreen(Screen parent, Text title, List<Choice> choices) {
        super(title);
        this.parent = parent;
        this.choices = List.copyOf(choices);
    }

    @Override protected void init() {
        if (!loaded) loaded = loadIcon();
        int half = Math.min(150, (width - 20) / 2);
        use = ButtonWidget.builder(Text.translatable("createWorld.customize.presets.select"), b -> activate())
            .dimensions(width / 2 - half - 5, height - 27, half, 20).build();
        use.active = false;
        list = addDrawableChild(new PresetList(client, width, height));
        addDrawableChild(use);
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> close())
            .dimensions(width / 2 + 5, height - 27, half, 20).build());
    }

    private boolean loadIcon() {
        var profile = client.getResourcePackManager().getProfile("programmer_art");
        if (profile == null) return false;
        try (var pack = profile.createResourcePack()) {
            var icon = pack.openRoot("pack.png");
            if (icon == null) return false;
            try (var in = icon.get()) {
                var texture = new NativeImageBackedTexture(() -> "Programmer Art pack icon", NativeImage.read(in));
                client.getTextureManager().registerTexture(ICON, texture);
                return true;
            }
        } catch (IOException ex) { return false; }
    }

    @Override public void removed() {
        if (loaded) client.getTextureManager().destroyTexture(ICON);
        loaded = false;
    }

    @Override public void close() { client.setScreen(parent); }

    private void activate() {
        var row = list.getSelectedOrNull();
        if (row != null && row.choice.select() != null) row.choice.select().run();
    }

    @Override public void renderBackground(DrawContext context, int x, int y, float delta) {
        renderBackgroundTexture(context, Identifier.ofVanilla("textures/block/dirt.png"), 0, 0, 0, 0, width, height);
        context.fill(0, 0, width, height, 0xc0000000);
    }

    @Override public void render(DrawContext context, int x, int y, float delta) {
        super.render(context, x, y, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, -1);
    }

    private final class PresetList extends AlwaysSelectedEntryListWidget<Row> {
        PresetList(MinecraftClient client, int width, int height) {
            super(client, width, height - 65, 32, 60);
            for (Choice choice : choices) {
                var row = new Row(choice);
                int rowText = textRenderer.wrapLines(choice.description(), Math.max(1, getRowWidth() - 67)).size() * 11;
                addEntry(row, Math.max(60, rowText + 18));
            }
        }
        @Override public int getRowWidth() { return Math.min(getWidth() - 22, 305); }
        @Override protected int getScrollbarX() { return Math.min(getWidth() - 8, getRowRight() + 38); }
        @Override public void setSelected(Row row) {
            super.setSelected(row != null && row.choice.select() == null ? null : row);
            use.active = getSelectedOrNull() != null;
        }
        @Override protected void drawMenuListBackground(DrawContext context) {
            context.fill(0, getY(), getWidth(), getY() + getHeight(), 0x70000000);
        }
    }

    private final class Row extends AlwaysSelectedEntryListWidget.Entry<Row> {
        private final Choice choice;
        Row(Choice choice) { this.choice = choice; }
        @Override public Text getNarration() { return Text.literal(choice.name()).append(" ").append(choice.description()); }

        @Override public boolean mouseClicked(Click click, boolean twice) {
            if (click.button() != 0 || choice.select() == null) return false;
            list.setSelected(this);
            if (twice || click.x() < getContentX() + ICON_SIZE) activate();
            return true;
        }
        @Override public boolean keyPressed(KeyInput input) {
            if (input.isEnterOrSpace() && choice.select() != null) {
                choice.select().run();
                return true;
            }
            return super.keyPressed(input);
        }
        @Override public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = getContentX(), y = getContentY();
            context.drawTexture(RenderPipelines.GUI_TEXTURED, loaded ? ICON : FALLBACK, x, y, 0, 0,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            boolean enabled = choice.select() != null;
            if (!enabled) context.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, 0x88000000);
            if (enabled && hovered) {
                context.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, 0x66000000);
                var arrow = Identifier.ofVanilla(mouseX < x + ICON_SIZE ? "world_list/join_highlighted" : "world_list/join");
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, arrow, x, y, ICON_SIZE, ICON_SIZE);
            }
            int left = x + ICON_SIZE + 3;
            context.drawText(textRenderer, choice.name(), left, y + 1, enabled ? 0xff55ffff : 0xff888888, false);
            int lineY = y + 13;
            for (var line : textRenderer.wrapLines(choice.description(), Math.max(1, list.getRowWidth() - 67))) {
                context.drawText(textRenderer, line, left, lineY, 0xff999999, false);
                lineY += 11;
            }
        }
    }
}
