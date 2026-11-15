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

    static String format(PeContentSettings settings) {
        var json = new JsonObject();
        var flags = new JsonObject();
        for (var item : Aquatic.values()) flags.addProperty(item.asString(), settings.water().contains(item));
        json.add("aquatic_features", flags);
        var features=new JsonObject();
        for (var f : PeContent.values()) features.addProperty(f.key(),settings.flags().contains(f));
        json.add("features",features);
        json.add("allow_nether_portals",settings.portalOverride().map(JsonPrimitive::new)
            .<JsonElement>map(v -> v).orElse(JsonNull.INSTANCE));
        return GSON.toJson(json);
    }
    private static boolean flag(JsonObject object,String key) {
        var field = object.get(key);
        if (!(field instanceof JsonPrimitive p) || !p.isBoolean()) throw new JsonParseException(key);
        return p.getAsBoolean();
    }
    static PeContentSettings parse(String text) {
        var json = GSON.fromJson(text, JsonObject.class);
        if (json == null || json.size()!=3 || !json.has("allow_nether_portals") ||
            !(json.get("aquatic_features") instanceof JsonObject flags) || flags.size()!=Aquatic.values().length)
            throw new JsonParseException("Invalid settings fields");
        var selected = EnumSet.noneOf(Aquatic.class);
        for (var item : Aquatic.values()) if (flag(flags,item.asString())) selected.add(item);
        Optional<Boolean> portals = json.get("allow_nether_portals").isJsonNull() ? Optional.empty()
            : Optional.of(flag(json,"allow_nether_portals"));
        if (!(json.get("features") instanceof JsonObject features) || features.size()!=PeContent.values().length)
            throw new JsonParseException("Invalid feature list");
        var enabled=EnumSet.noneOf(PeContent.class);
        for (var f : PeContent.values()) if (flag(features,f.key())) enabled.add(f);
        return PeContentSettings.configured(selected,enabled,portals);
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
