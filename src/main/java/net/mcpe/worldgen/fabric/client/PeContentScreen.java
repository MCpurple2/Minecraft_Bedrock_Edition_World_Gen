package net.mcpe.worldgen.fabric.client;

import net.mcpe.worldgen.core.PeContent;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.mcpe.worldgen.fabric.PeContentSettings;
import net.mcpe.worldgen.fabric.PeContentSettings.Aquatic;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.screen.ScreenTexts;

final class PeContentScreen extends Screen {
    private final Screen parent;
    private final Consumer<PeContentSettings> apply;
    private final EnumSet<Aquatic> water = EnumSet.noneOf(Aquatic.class);
    private final List<ClickableWidget> dependent = new ArrayList<>();
    private final EnumSet<PeContent> flags=EnumSet.noneOf(PeContent.class);
    private final EnumMap<PeContent,ClickableWidget> buttons=new EnumMap<>(PeContent.class);
    private Optional<Boolean> portals;

    PeContentScreen(Screen parent, PeContentSettings initial, Consumer<PeContentSettings> apply) {
        super(PeCustomizeScreen.label("content"));
        this.parent = parent;
        this.apply = apply;
        water.addAll(initial.water());
        flags.addAll(initial.flags());
        portals = initial.portalOverride();
    }

    @Override protected void init() {
        int w = Math.min(310, width - 32);
        var body = DirectionalLayoutWidget.vertical().spacing(5);
        body.getMainPositioner().alignHorizontalCenter();
        buttons.clear();
        for (String group : List.of("classic","adventure_part1","adventure","scary","redstone","changed_world","bountiful","frostburn","exploration"))
            section(body,group,w);
        heading(body, "aquatic_group");
        dependent.clear();
        DirectionalLayoutWidget row = null;
        for (Aquatic item : Aquatic.values()) {
            if (item.ordinal() % 2 == 0 || item == Aquatic.CANYONS) {
                row = body.add(DirectionalLayoutWidget.horizontal().spacing(8));
            }
            int buttonWidth = item == Aquatic.CANYONS || item == Aquatic.UNDERWATER_CAVE || item == Aquatic.SEAGRASS ? w : (w-8)/2;
            var button = row.add(toggle(item.asString(), water.contains(item), buttonWidth, value -> {
                if (value) water.add(item); else water.remove(item);
                if (item == Aquatic.OCEAN_BIOMES) updateDependencies();
            }));
            if (item.requiresOceanBiomes()) dependent.add(button);
        }
        updateDependencies();
        for (String group : List.of("pillage","buzzy","nether_group","caves_cliffs_i")) section(body,group,w);
        refreshFeatures();
        var scroll = new ScrollableLayoutWidget(client, body, height - 68);
        scroll.setHeight(height - 68);
        scroll.refreshPositions();
        scroll.setX((width-scroll.getWidth())/2);
        scroll.setY(34);
        scroll.forEachChild(this::addDrawableChild);
        int half = (w-8)/2;
        addDrawableChild(ButtonWidget.builder(PeCustomizeScreen.label("save_settings"), b -> {
            apply.accept(PeContentSettings.configured(water,flags,portals)); close();
        }).dimensions(width/2-half-4,height-26,half,20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL,b -> close()).dimensions(width/2+4,height-26,half,20).build());
    }
    private void section(DirectionalLayoutWidget body,String group,int width) {
        heading(body,group);
        for (var f : PeContent.values()) if (f.group.equals(group)) {
            var button=body.add(toggle("feature_"+f.key(), flags.contains(f), width, value -> {
                if(value) flags.add(f); else flags.remove(f);
                refreshFeatures();
            }));
            buttons.put(f,button);
        }
    }
    private void refreshFeatures() { buttons.forEach((f,b) -> b.active=f.available(flags)); }
    private void updateDependencies() {
        dependent.forEach(button -> button.active = water.contains(Aquatic.OCEAN_BIOMES));
    }
    private void heading(DirectionalLayoutWidget body, String key) {
        body.add(new TextWidget(PeCustomizeScreen.label(key), textRenderer), p -> p.marginY(6).alignHorizontalCenter());
    }
    private ClickableWidget toggle(String key, boolean initial, int w, Consumer<Boolean> change) {
        return CyclingButtonWidget.onOffBuilder(initial).build(0,0,w,20,
            PeCustomizeScreen.label(key),(button,value) -> change.accept(value));
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,12,-1);
        context.fill(0,31,width,32,0xff544c44);
        context.fill(0,height-32,width,height-31,0xff544c44);
    }
}
