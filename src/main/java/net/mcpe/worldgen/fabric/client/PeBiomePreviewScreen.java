package net.mcpe.worldgen.fabric.client;

import net.mcpe.worldgen.core.PeRelease;
import net.mcpe.worldgen.fabric.PeContentSettings;
import net.mcpe.worldgen.fabric.PeOceanClimate;
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
    private static final java.util.List<String> OVERLAYS=java.util.List.of("ocean","frozen_ocean","deep_ocean","warm_ocean","lukewarm_ocean","deep_lukewarm_ocean","cold_ocean","deep_cold_ocean","deep_frozen_ocean","bamboo_jungle","bamboo_jungle_hills","the_void");
    private static final int[] OVERLAY_COLORS={0x28568a,0x97bbda,0x18395e,0x22b6ac,0x3e9eb5,0x296c85,0x365a90,0x263f68,0x687f9e,0x52782d,0x416123,0x16161a};
    private final Screen parent;
    private final long seed;
    private final int originX, originZ, extent;
    private final int[] biomes;
    private boolean registered;

    PeBiomePreviewScreen(Screen parent, long seed, boolean old, PeRelease release, PeContentSettings settings, net.mcpe.worldgen.fabric.PeBedrockSettings bedrock) {
        super(PeCustomizeScreen.label("preview"));
        this.parent = parent;
        this.seed = seed;
        var generator = new PeWorldGenerator(seed, old, true, true, release);
        var spawn = generator.initialSpawn();
        bedrock.configure(generator, true);
        var bounds = generator.finiteBorder();
        extent = bounds == null ? 512 : Math.min(512, bounds.width());
        originX = (bounds == null ? spawn.x() & ~15 : bounds.centerX()) - extent / 2;
        originZ = (bounds == null ? spawn.z() & ~15 : bounds.centerZ()) - extent / 2;
        int margin=36, stride=extent+2*margin;
        var alpha = generator.alphaClimate();
        int[] raw=alpha == null ? generator.biomeArea(originX-margin,originZ-margin,stride,stride) : null;
        var early = alpha == null ? null : alpha.area(originX,originZ,extent,extent);
        var climate=new PeOceanClimate(seed);
        java.util.function.IntBinaryOperator sample=(qx,qz)->raw[(qx*4-originX+margin)+(qz*4-originZ+margin)*stride];
        biomes=new int[extent*extent];
        for(int z=0; z<extent; z+=4) for(int x=0; x<extent; x+=4) {
            int qx=(originX+x)>>2,qz=(originZ+z)>>2,id=early == null ? sample.applyAsInt(qx,qz) : 0;
            String name=bounds != null && bounds.outsideBiomeCell(qx,qz) ? "the_void" : early == null ? climate.biome(id,qx,qz,settings,sample) : null;
            int value=early != null && !"the_void".equals(name) ? 300 + early[x+z*extent].ordinal() : name==null?id:256+OVERLAYS.indexOf(name);
            for(int dz=0;dz<4;dz++) java.util.Arrays.fill(biomes,(z+dz)*extent+x,(z+dz)*extent+x+4,value);
        }
    }

    @Override protected void init() {
        if (!registered) {
            var image = new NativeImage(extent, extent, false);
            for (int z = 0; z < extent; z++) for (int x = 0; x < extent; x++) {
                int raw = biomes[x + z * extent], id = raw & 127;
                image.setColorArgb(x, z, 0xff000000 | (raw>=300 ? COLORS[net.mcpe.worldgen.core.PeAlphaClimate.Type.values()[raw-300].terrainBiome] : raw>=256 ? OVERLAY_COLORS[raw-256] : id < COLORS.length ? COLORS[id] : 0x888888));
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
            if(id>=300) {
                String path=net.mcpe.worldgen.core.PeAlphaClimate.Type.values()[id-300].path();
                caption=(originX+dx)+", "+(originZ+dz)+"   "+Text.translatable("biome.pe_worldgen."+path).getString();
            } else if(id>=256) {
                String path=OVERLAYS.get(id-256);
                String label=Text.translatable("biome."+(path.equals("bamboo_jungle_hills")?"pe_worldgen.":"minecraft.")+path).getString();
                caption=(originX+dx)+", "+(originZ+dz)+"   "+label;
            }
            context.drawCenteredTextWithShadow(textRenderer, caption, width / 2, y + size + 3, -1);
        }
    }
}
