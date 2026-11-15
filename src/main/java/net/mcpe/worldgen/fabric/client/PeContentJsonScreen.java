package net.mcpe.worldgen.fabric.client;

import com.google.gson.*;
import net.mcpe.worldgen.core.PeContent;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Consumer;
import net.mcpe.worldgen.fabric.PeContentSettings;
import net.mcpe.worldgen.fabric.PeContentSettings.Aquatic;
import net.minecraft.client.gui.screen.Screen;

// Port of Moderner Beta's ModernBetaJSONEditScreen and ModernBetaSettingsScreen (MIT).
final class PeContentJsonScreen extends PeSettingsJsonScreen<PeContentSettings> {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls()
        .setStrictness(Strictness.STRICT).create();
    PeContentJsonScreen(Screen parent, PeContentSettings settings, Consumer<PeContentSettings> onDone) {
        super(parent, PeCustomizeScreen.label("content"), format(settings), PeContentJsonScreen::parse, onDone);
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
        NETHER("nether_group","nether_update_features"),
        CAVES_CLIFFS_I("caves_cliffs_i","caves_cliffs_part_i_features");

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
        if (json == null) throw new JsonParseException("Invalid settings");
        boolean legacy = json.has("features");
        if (json.size() != (legacy ? 2 : Update.values().length - (json.has(Update.CAVES_CLIFFS_I.key) ? 0 : 1)) + (json.has("allow_nether_portals") ? 1 : 0)) throw new JsonParseException("Invalid groups");
        var water = EnumSet.noneOf(Aquatic.class);
        var aquatic = group(json,legacy ? "aquatic_features" : Update.AQUATIC.key);
        if (aquatic.size()!=Aquatic.values().length) throw new JsonParseException("Invalid aquatic settings");
        for (var item : Aquatic.values()) if (flag(aquatic,item.asString())) water.add(item);

        var enabled=EnumSet.noneOf(PeContent.class);
        if (legacy) {
            var fields=group(json,"features");
            if (fields.size()!=java.util.Arrays.stream(PeContent.values()).filter(f -> !f.group.equals(Update.CAVES_CLIFFS_I.group)).count()) throw new JsonParseException("Invalid feature list");
            for (var f : PeContent.values()) if (!f.group.equals(Update.CAVES_CLIFFS_I.group) && flag(fields,f.key())) enabled.add(f);
        } else {
            for (var update : Update.values()) {
                if (update == Update.AQUATIC || update == Update.CAVES_CLIFFS_I && !json.has(update.key)) continue;
                var fields=group(json,update.key);
                int expected=0;
                for (var f : PeContent.values()) if (f.group.equals(update.group)) {
                    expected++;
                    if (flag(fields,editorKey(f))) enabled.add(f);
                }
                if (fields.size()!=expected) throw new JsonParseException(update.key);
            }
        }
        Optional<Boolean> portals = !json.has("allow_nether_portals") || json.get("allow_nether_portals").isJsonNull() ? Optional.empty()
            : Optional.of(flag(json,"allow_nether_portals"));
        return PeContentSettings.configured(water,enabled,portals);
    }

}
