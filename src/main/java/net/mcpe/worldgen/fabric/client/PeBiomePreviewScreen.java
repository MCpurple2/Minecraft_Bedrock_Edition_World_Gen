package net.mcpe.worldgen.fabric.client;

import net.mcpe.worldgen.core.PeWorldGenerator;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Display a cached sample from the same PE layer chain used to build the world. */
final class PeBiomePreviewScreen extends Screen {
    private static final Identifier MAP = Identifier.of("pe_worldgen", "dynamic/biome_preview");
    private static final String[] NAMES = {"Ocean", "Plains", "Desert", "Extreme Hills", "Forest", "Taiga",
        "Swampland", "River", "Hell", "Sky", "Frozen Ocean", "Frozen River", "Ice Plains", "Ice Mountains",
        "Mushroom Island", "Mushroom Shore", "Beach", "Desert Hills", "Forest Hills", "Taiga Hills",
        "Extreme Hills Edge", "Jungle", "Jungle Hills", "Jungle Edge", "Deep Ocean", "Stone Beach", "Cold Beach",
        "Birch Forest", "Birch Forest Hills", "Roofed Forest", "Cold Taiga", "Cold Taiga Hills",
        "Mega Taiga", "Mega Taiga Hills", "Extreme Hills+", "Savanna", "Savanna Plateau", "Mesa", "Mesa Plateau F", "Mesa Plateau"};
    private static final int[] COLORS = {0x28568a,0x91bd59,0xf0d68b,0x808d82,0x46733d,0x477968,0x647547,
        0x4685be,0x77392c,0x635b46,0x97bbda,0x8bbde5,0xe4eee8,0xb9c6c9,0xa779a7,0xaa8a9f,0xdec899,
        0xdbb976,0x386538,0x416e60,0x7c9478,0x398535,0x32702e,0x57954c,0x18395e,0x97928b,0xd6d8b9,
        0x73a65d,0x638f52,0x325739,0xa0b9ab,0x92aaa0,0x637c58,0x576f50,0x65856a,0xc3b663,
        0xb5a556,0xc87952,0xad7852,0xb66f44};
    private final Screen parent;
    private final long seed;
    private final int originX, originZ, extent;
    private final int[] biomes;
    private boolean registered;

    PeBiomePreviewScreen(Screen parent, long seed, boolean old) {
        super(PeCustomizeScreen.label("preview"));
        this.parent = parent;
        this.seed = seed;
        var generator = new PeWorldGenerator(seed, old, true, true);
        var spawn = generator.initialSpawn();
        extent = old ? 256 : 512;
        originX = (spawn.x() & ~15) - extent / 2;
        originZ = (spawn.z() & ~15) - extent / 2;
        biomes = generator.biomeArea(originX, originZ, extent, extent);
    }

    @Override protected void init() {
        if (!registered) {
            var image = new NativeImage(extent, extent, false);
            for (int z = 0; z < extent; z++) for (int x = 0; x < extent; x++) {
                int id = biomes[x + z * extent] & 127;
                image.setColorArgb(x, z, 0xff000000 | (id < COLORS.length ? COLORS[id] : 0x888888));
            }
            var texture = new NativeImageBackedTexture(() -> "PE biome preview", image);
            client.getTextureManager().registerTexture(MAP, texture);
            registered = true;
        }
        addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, b -> close()).dimensions(width / 2 - 75, height - 25, 150, 20).build());
    }

    @Override public void removed() {
        if (registered) client.getTextureManager().destroyTexture(MAP);
        registered = false;
    }
    @Override public void close() { client.setScreen(parent); }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int size = Math.max(1, Math.min(width - 20, height - 84));
        int x = (width - size) / 2;
        int y = 39;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 9, -1);
        context.drawCenteredTextWithShadow(textRenderer, PeCustomizeScreen.label("map_seed", seed, extent, extent), width / 2, 23, -1);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, MAP, x, y, 0, 0, size, size, extent, extent, extent, extent);
        if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
            int dx = (mouseX - x) * extent / size, dz = (mouseY - y) * extent / size;
            int id = biomes[dx + dz * extent];
            int base = id & 127;
            String name = base < NAMES.length ? NAMES[base] : "Biome";
            String caption = (originX + dx) + ", " + (originZ + dz) + "   " + name + (id >= 128 ? " M" : "") + " (" + id + ")";
            context.drawCenteredTextWithShadow(textRenderer, caption, width / 2, y + size + 3, -1);
        }
    }
}
