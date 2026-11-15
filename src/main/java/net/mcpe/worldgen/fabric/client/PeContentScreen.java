package net.mcpe.worldgen.fabric.client;

import java.util.Optional;
import java.util.function.Consumer;
import net.mcpe.worldgen.fabric.PeContentSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;

final class PeContentScreen extends Screen {
    private final Screen parent;
    private final boolean finite;
    private final Consumer<PeContentSettings> apply;
    private boolean aquatic, villages, bees, nether;
    private Optional<Boolean> portals;

    PeContentScreen(Screen parent, boolean finite, PeContentSettings initial, Consumer<PeContentSettings> apply) {
        super(PeCustomizeScreen.label("content"));
        this.parent = parent;
        this.finite = finite;
        this.apply = apply;
        aquatic = initial.aquatic();
        villages = initial.villages();
        bees = initial.bees();
        nether = initial.netherUpdate();
        portals = initial.portalOverride();
    }

    @Override protected void init() {
        toggle(0,"aquatic",aquatic,v -> aquatic = v);
        toggle(1,"villages",villages,v -> villages = v);
        toggle(2,"bees",bees,v -> bees = v);
        toggle(3,"nether",nether,v -> nether = v);
        int w = Math.min(320,width - 24);
        addDrawableChild(CyclingButtonWidget.builder((Integer mode) -> PeCustomizeScreen.label(
            mode == 0 ? finite ? "default_off" : "default_on" : mode == 1 ? "on" : "off"),
            portals.map(v -> v ? 1 : 2).orElse(0)).values(0,1,2)
            .build((width-w)/2, 32+4*24,w,20,PeCustomizeScreen.label("portals"),
                (b,mode) -> portals = mode == 0 ? Optional.empty() : Optional.of(mode == 1)));
        int half = Math.min(150,(width-20)/2);
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE,b -> {
            apply.accept(new PeContentSettings(aquatic,villages,bees,nether,portals)); close();
        }).dimensions(width/2-half-5,height-27,half,20).build());
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL,b -> close()).dimensions(width/2+5,height-27,half,20).build());
    }
    private void toggle(int row,String key,boolean initial,Consumer<Boolean> change) {
        int w = Math.min(320,width-24);
        addDrawableChild(CyclingButtonWidget.onOffBuilder(initial).build((width-w)/2,32+24*row,w,20,
            PeCustomizeScreen.label(key),(button,value) -> change.accept(value)));
    }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,12,-1);
    }
}
