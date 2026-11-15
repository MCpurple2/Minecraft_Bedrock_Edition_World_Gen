package net.mcpe.worldgen.fabric.client;

import com.google.gson.*;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.mcpe.worldgen.fabric.PeBedrockSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

final class PeBedrockScreen extends Screen {
    private static final Text TITLE = Text.literal("Bedrock Exclusive Features Provider");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().setStrictness(Strictness.STRICT).create();
    private final Screen parent;
    private final Consumer<PeBedrockSettings> apply;
    private final boolean old;
    private boolean portals, sync;
    private String size, minX, minZ;
    private ButtonWidget save;

    PeBedrockScreen(Screen parent, boolean old, PeBedrockSettings settings, Consumer<PeBedrockSettings> apply) {
        super(TITLE);
        this.parent = parent;
        this.old = old;
        this.apply = apply;
        portals = settings.allowPortals();
        sync = settings.syncNether();
        size = Integer.toString(settings.size());
        minX = settings.minX().map(Object::toString).orElse("");
        minZ = settings.minZ().map(Object::toString).orElse("");
    }

    private static Optional<Integer> coordinate(String text) {
        return text.isBlank() ? Optional.empty() : Optional.of(Integer.parseInt(text));
    }

    private PeBedrockSettings value() {
        try {
            var result = new PeBedrockSettings(portals, sync, Integer.parseInt(size), coordinate(minX), coordinate(minZ));
            return result.valid() ? result : null;
        } catch (NumberFormatException ex) { return null; }
    }

    @Override protected void init() {
        int w = Math.min(310, width - 24);
        var body = DirectionalLayoutWidget.vertical().spacing(5);
        body.add(CyclingButtonWidget.onOffBuilder(portals).build(0,0,w,20,
            Text.literal("Allow Nether Portal"),(b,v) -> portals = v));
        body.add(CyclingButtonWidget.onOffBuilder(sync).build(0,0,w,20,
            Text.literal("Sync Nether Border Size with Overworld"),(b,v) -> sync = v)).active = old;
        field(body, w, "Border side (chunks, -1 = unlimited)", size, v -> size = v);
        field(body, w, "Minimum chunk X (blank = automatic)", minX, v -> minX = v);
        field(body, w, "Minimum chunk Z (blank = automatic)", minZ, v -> minZ = v);
        var scroll = new ScrollableLayoutWidget(client, body, height - 66);
        scroll.setHeight(height - 66);
        scroll.refreshPositions();
        scroll.setX((width-scroll.getWidth())/2);
        scroll.setY(32);
        scroll.forEachChild(this::addDrawableChild);
        int half = (w - 8)/2;
        save = addDrawableChild(ButtonWidget.builder(PeCustomizeScreen.label("save_settings"), b -> {
            var selected = value();
            if (selected != null) { apply.accept(selected); close(); }
        }).dimensions(width/2-half-4,height-26,half,20).build());
        save.active = value() != null;
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL,b -> close())
            .dimensions(width/2+4,height-26,half,20).build());
    }

    private void field(DirectionalLayoutWidget body, int width, String label, String initial, Consumer<String> update) {
        var row = body.add(DirectionalLayoutWidget.horizontal().spacing(4));
        var name = Text.literal(label);
        row.add(new TextWidget(width-74,20,name,textRenderer));
        var field = row.add(new TextFieldWidget(textRenderer,70,20,name));
        field.setMaxLength(10);
        field.setText(initial);
        field.setChangedListener(text -> {
            update.accept(text);
            if (save != null) save.active = value() != null;
        });
    }

    static Screen editor(Screen parent, PeBedrockSettings settings, Consumer<PeBedrockSettings> apply) {
        var obj = new JsonObject();
        obj.addProperty("allow_nether_portal",settings.allowPortals());
        obj.addProperty("sync_nether_border_size",settings.syncNether());
        obj.addProperty("border_size_chunks",settings.size());
        obj.addProperty("min_chunk_x",settings.minX().orElse(null));
        obj.addProperty("min_chunk_z",settings.minZ().orElse(null));
        return new PeSettingsJsonScreen<>(parent, TITLE, JSON.toJson(obj), PeBedrockScreen::parse, apply);
    }

    private static int integer(JsonElement element) {
        if (!(element instanceof JsonPrimitive p) || !p.isNumber()) throw new JsonParseException("Expected integer");
        return p.getAsBigDecimal().intValueExact();
    }

    private static Optional<Integer> optional(JsonElement element) {
        return element.isJsonNull() ? Optional.empty() : Optional.of(integer(element));
    }

    private static boolean flag(JsonElement element) {
        if (!(element instanceof JsonPrimitive p) || !p.isBoolean()) throw new JsonParseException("Expected boolean");
        return p.getAsBoolean();
    }

    private static PeBedrockSettings parse(String text) {
        var o = JSON.fromJson(text, JsonObject.class);
        if (!o.keySet().equals(Set.of("allow_nether_portal","sync_nether_border_size","border_size_chunks",
                "min_chunk_x","min_chunk_z"))) throw new JsonParseException("Invalid Bedrock settings");
        var result = new PeBedrockSettings(flag(o.get("allow_nether_portal")),flag(o.get("sync_nether_border_size")),
            integer(o.get("border_size_chunks")),optional(o.get("min_chunk_x")),optional(o.get("min_chunk_z")));
        if (!result.valid()) throw new JsonParseException("Invalid bounds");
        return result;
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context,int x,int y,float delta) {
        super.render(context,x,y,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,12,-1);
    }
}
