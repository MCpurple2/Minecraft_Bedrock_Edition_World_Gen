package net.mcpe.worldgen.fabric.client;

import java.util.function.Consumer;
import java.util.function.Function;
import net.mcpe.worldgen.fabric.client.mixin.EditBoxAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.input.CursorMovement;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

// Shared editor adapted from Moderner Beta's ModernBetaJSONEditScreen (MIT).
class PeSettingsJsonScreen<T> extends Screen {
    private final Screen parent;
    private final Consumer<T> onDone;
    private final Function<String,T> parser;
    private String settingsString;
    private T parsed;
    private ButtonWidget widgetDone;
    private TextWidget widgetInvalid;

    PeSettingsJsonScreen(Screen parent, Text title, String initial, Function<String,T> parser, Consumer<T> onDone) {
        super(title);
        this.parent = parent;
        this.onDone = onDone;
        this.parser = parser;
        settingsString = initial;
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
        try { parsed = parser.apply(value); }
        catch (RuntimeException ex) { parsed = null; }
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
