package net.mcpe.worldgen.fabric.client;

import com.google.gson.*;
import net.mcpe.worldgen.core.PeContent;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Consumer;
import net.mcpe.worldgen.fabric.PeContentSettings;
import net.mcpe.worldgen.fabric.PeContentSettings.Aquatic;
import net.mcpe.worldgen.fabric.client.mixin.EditBoxAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.input.CursorMovement;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.util.Formatting;

// Port of Moderner Beta's ModernBetaJSONEditScreen and ModernBetaSettingsScreen (MIT).
final class PeContentJsonScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls()
        .setStrictness(Strictness.STRICT).create();
    private final Screen parent;
    private final Consumer<PeContentSettings> onDone;
    private String settingsString;
    private PeContentSettings parsed;
    private ButtonWidget widgetDone;
    private TextWidget widgetInvalid;

    PeContentJsonScreen(Screen parent, PeContentSettings settings, Consumer<PeContentSettings> onDone) {
        super(PeCustomizeScreen.label("content"));
        this.parent = parent;
        this.onDone = onDone;
        settingsString = format(settings);
    }

    // Iteration order is the order shown in the editor, from earliest to latest release.
    private enum Update {
        EARLY("classic","early_java_features"),
        ADVENTURE_I("adventure_part1","adventure_update_part_i_features"),
        ADVENTURE_II("adventure","adventure_update_part_ii_features"),
        SCARY("scary","scary_update_features"),
        REDSTONE("redstone","redstone_update_features"),
        BOUNTIFUL("bountiful","bountiful_update_features"),
        FROSTBURN("frostburn","frostburn_update_features"),
        EXPLORATION("exploration","exploration_update_features"),
        AQUATIC("aquatic_group","aquatic_update_features"),
        PILLAGE("pillage","village_and_pillage_update_features"),
        BEES("buzzy","buzzy_bees_update_features"),
        NETHER("nether_group","nether_update_features");

        final String group, key;
        Update(String group,String key) { this.group=group; this.key=key; }
    }

    private static String editorKey(PeContent feature) {
        return feature == PeContent.VILLAGES ? "legacy_villages" : feature.key();
    }

    static String format(PeContentSettings settings) {
        var json = new JsonObject();
        for (var update : Update.values()) {
            var fields = new JsonObject();
            if (update == Update.AQUATIC) {
                for (var item : Aquatic.values()) fields.addProperty(item.asString(),settings.water().contains(item));
            } else {
                for (var feature : PeContent.values()) if (feature.group.equals(update.group))
                    fields.addProperty(editorKey(feature),settings.flags().contains(feature));
            }
            json.add(update.key,fields);
        }
        json.add("allow_nether_portals",settings.portalOverride().map(JsonPrimitive::new)
            .<JsonElement>map(v -> v).orElse(JsonNull.INSTANCE));
        return GSON.toJson(json);
    }

    private static boolean flag(JsonObject object,String key) {
        var field = object.get(key);
        if (!(field instanceof JsonPrimitive p) || !p.isBoolean()) throw new JsonParseException(key);
        return p.getAsBoolean();
    }

    private static JsonObject group(JsonObject root,String key) {
        if (root.get(key) instanceof JsonObject object) return object;
        throw new JsonParseException(key);
    }

    static PeContentSettings parse(String text) {
        var json = GSON.fromJson(text, JsonObject.class);
        if (json == null || !json.has("allow_nether_portals")) throw new JsonParseException("Invalid settings");
        boolean legacy = json.has("features");
        if (json.size() != (legacy ? 3 : Update.values().length + 1)) throw new JsonParseException("Invalid groups");
        var water = EnumSet.noneOf(Aquatic.class);
        var aquatic = group(json,legacy ? "aquatic_features" : Update.AQUATIC.key);
        if (aquatic.size()!=Aquatic.values().length) throw new JsonParseException("Invalid aquatic settings");
        for (var item : Aquatic.values()) if (flag(aquatic,item.asString())) water.add(item);

        var enabled=EnumSet.noneOf(PeContent.class);
        if (legacy) {
            var fields=group(json,"features");
            if (fields.size()!=PeContent.values().length) throw new JsonParseException("Invalid feature list");
            for (var f : PeContent.values()) if (flag(fields,f.key())) enabled.add(f);
        } else {
            for (var update : Update.values()) {
                if (update == Update.AQUATIC) continue;
                var fields=group(json,update.key);
                int expected=0;
                for (var f : PeContent.values()) if (f.group.equals(update.group)) {
                    expected++;
                    if (flag(fields,editorKey(f))) enabled.add(f);
                }
                if (fields.size()!=expected) throw new JsonParseException(update.key);
            }
        }
        Optional<Boolean> portals = json.get("allow_nether_portals").isJsonNull() ? Optional.empty()
            : Optional.of(flag(json,"allow_nether_portals"));
        return PeContentSettings.configured(water,enabled,portals);
    }

    @Override protected void init() {
        int half = Math.min(150,(width-24)/2);
        var widgetSettings = addDrawableChild(EditBoxWidget.builder().x(8).y(40)
            .build(textRenderer,width-16,Math.max(32,height-90),title));
        widgetSettings.setText(settingsString);
        ((EditBoxAccessor)widgetSettings).peWorldgen$getEditBox().moveCursor(CursorMovement.ABSOLUTE,0);
        widgetInvalid = addDrawableChild(new TextWidget(0,height-48,width,16,
            PeCustomizeScreen.label("invalid_settings").copy().formatted(Formatting.RED),textRenderer));
        widgetDone = addDrawableChild(ButtonWidget.builder(PeCustomizeScreen.label("save_settings"),button -> {
            onDone.accept(parsed);
            close();
        }).dimensions(width/2-half-5,height-27,half,20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL,button -> close())
            .dimensions(width/2+5,height-27,half,20).build());
        widgetSettings.setChangeListener(this::onChange);
        onChange(settingsString);
        setInitialFocus(widgetSettings);
    }
    private void onChange(String value) {
        settingsString = value;
        try { parsed = parse(value); }
        catch (JsonParseException ex) { parsed = null; }
        widgetInvalid.visible = parsed == null;
        widgetDone.active = parsed != null;
    }
    @Override public void render(DrawContext context,int x,int y,float delta) {
        super.render(context,x,y,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,10,-1);
        context.drawCenteredTextWithShadow(textRenderer,PeCustomizeScreen.label("edit_navigation"),width/2,23,-1);
    }
    @Override public void close() { client.setScreen(parent); }
}
