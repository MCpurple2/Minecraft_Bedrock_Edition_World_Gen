package net.mcpe.worldgen.fabric.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.resource.ResourceType;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

final class PePresetTransferScreen extends Screen {
    private final PeCustomizeScreen parent;
    private final boolean old;
    private final boolean pack;
    private TextFieldWidget field;
    private Text status = Text.empty();
    private final String encoded;
    private String draft;
    private Path exported;

    PePresetTransferScreen(PeCustomizeScreen parent, boolean old, boolean pack) {
        super(PeCustomizeScreen.label(pack ? "export_pack" : "quick"));
        this.parent = parent;
        this.old = old;
        this.pack = pack;
        var json = new JsonObject();
        json.addProperty("version", "0.11.1");
        json.addProperty("world_type", old ? "finite" : "infinite");
        encoded = json.toString();
        draft = encoded;
    }

    @Override protected void init() {
        int w = Math.min(320, width - 24);
        int x = (width - w) / 2;
        int y = height / 2;
        if (pack) {
            addDrawableChild(ButtonWidget.builder(PeCustomizeScreen.label(exported == null ? "export_pack" : "folder"),
                b -> {
                    if (exported != null) { Util.getOperatingSystem().open(exported.getParent()); return; }
                    try {
                        exported = exportPack();
                        status = Text.literal(exported.getFileName().toString());
                        b.setMessage(PeCustomizeScreen.label("folder"));
                    } catch (IOException ex) { status = PeCustomizeScreen.label("export_error", ex.getMessage()); }
                }).dimensions(x, y, w, 20).build());
        } else {
            field = new TextFieldWidget(textRenderer, x, y - 22, w, 20, title);
            field.setMaxLength(4096);
            field.setText(draft);
            field.setChangedListener(value -> draft = value);
            addDrawableChild(field);
            setInitialFocus(field);
            int half = (w - 6) / 2;
            addDrawableChild(ButtonWidget.builder(PeCustomizeScreen.label("copy"), b -> {
                // Export the selected preset even if the user has typed an unfinished import.
                client.keyboard.setClipboard(encoded);
                status = PeCustomizeScreen.label("copied");
            }).dimensions(x, y + 3, half, 20).build());
            addDrawableChild(ButtonWidget.builder(PeCustomizeScreen.label("import"), b -> {
                try {
                    var json = JsonParser.parseString(field.getText()).getAsJsonObject();
                    if (!"0.11.1".equals(json.get("version").getAsString())) throw new IllegalArgumentException();
                    switch (json.get("world_type").getAsString()) {
                        case "finite" -> parent.importPreset(true);
                        case "infinite" -> parent.importPreset(false);
                        default -> throw new IllegalArgumentException();
                    }
                } catch (RuntimeException ex) { status = PeCustomizeScreen.label("invalid"); }
            }).dimensions(x + half + 6, y + 3, half, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, b -> close()).dimensions(width / 2 - 75, height - 27, 150, 20).build());
    }

    private Path exportPack() throws IOException {
        String id = old ? "pe_0111_old" : "pe_0111_infinite";
        var directory = client.runDirectory.toPath().resolve("data-pack-exports");
        Files.createDirectories(directory);
        var format = SharedConstants.getGameVersion().packVersion(ResourceType.SERVER_DATA);
        var meta = new JsonObject();
        var version = new com.google.gson.JsonArray();
        version.add(format.major());
        version.add(format.minor());
        meta.add("min_format", version);
        meta.add("max_format", version);
        meta.addProperty("description", (old ? PeCustomizeScreen.FINITE : PeCustomizeScreen.INFINITE) + " 0.11.1");
        var root = new JsonObject();
        root.add("pack", meta);
        Path output = Files.createTempFile(directory, id + "-", ".zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(output));
             var preset = getClass().getResourceAsStream("/data/pe_worldgen/worldgen/world_preset/" + id + ".json")) {
            if (preset == null) throw new IOException("Missing preset " + id);
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write(root.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("data/pe_worldgen/worldgen/world_preset/mcpe_alpha.json"));
            preset.transferTo(zip);
            zip.closeEntry();
        } catch (IOException ex) { Files.deleteIfExists(output); throw ex; }
        return output;
    }

    @Override public void close() { client.setScreen(parent); }

    @Override public void render(DrawContext context, int x, int y, float delta) {
        super.render(context, x, y, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 17, -1);
        int w = Math.min(320, width - 24);
        context.drawWrappedTextWithShadow(textRenderer, PeCustomizeScreen.label(pack ? "pack_help" : "quick_help"),
            (width - w) / 2, Math.max(35, height / 2 - 56), w, 0xffdddddd);
        context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height / 2 + 29, -1);
    }
}
