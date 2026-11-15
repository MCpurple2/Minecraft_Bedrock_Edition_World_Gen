package net.mcpe.worldgen.fabric.client;

// Adapted from Moderner Beta's ModernBetaBiomePreviewScreen and LayerRandom (MIT).
// Copyright (c) 2024-2025 BlueStaggo, 2021 B3spectacled. See LICENSES/Moderner-Beta-MIT.txt.
import net.mcpe.worldgen.fabric.PeBiomeSource;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.cursor.StandardCursors;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class PeBiomePreviewScreen extends Screen {
    private final Screen parent;
    private final PeBiomeSource source;
    private final long seed;
    private final int originX, originZ;
    private BiomeDisplayWidget display;

    PeBiomePreviewScreen(Screen parent, PeBiomeSource source, long seed) {
        super(PeCustomizeScreen.label("preview"));
        this.parent = parent;
        this.source = source;
        this.seed = seed;
        var bounds = source.previewBounds();
        originX = bounds == null ? 0 : bounds.centerX() >> 2;
        originZ = bounds == null ? 0 : bounds.centerZ() >> 2;
    }

    @Override
    protected void init() {
        if (display != null) display.close();
        int w = Math.max(1, width * 3 / 4), h = Math.max(1, height - 66);
        display = addDrawableChild(new BiomeDisplayWidget((width - w) / 2, 30, w, h));
        int wide = Math.min(100, Math.max(35, (width - 74) / 2));
        int x = (width - (2 * wide + 70)) / 2, y = height - 26;
        button(Text.literal("-"), x, y, 20, display::zoomOut);
        button(Text.literal("+"), x + 30, y, 20, display::zoomIn);
        button(PeCustomizeScreen.label("screenshot"), x + 60, y, wide, display::saveScreenshot);
        button(ScreenTexts.BACK, x + 70 + wide, y, wide, this::close);
        display.start();
    }

    private void button(Text label, int x, int y, int width, Runnable action) {
        addDrawableChild(
                ButtonWidget.builder(label, b -> action.run()).dimensions(x, y, width, 20).build());
    }

    @Override
    public void removed() {
        if (display != null) display.close();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, -1);
    }

    private static final Identifier TEXTURE_ID =
            Identifier.of("pe_worldgen", "dynamic/biome_preview");
    private static final int EMPTY_COLOR = 0x7f000000;

    final class BiomeDisplayWidget extends ClickableWidget implements AutoCloseable {
        private final NativeImage image;
        private final NativeImageBackedTexture texture;
        private final Object imageLock = new Object();
        private RegistryEntry<?>[] labels;
        private int zoomIn = 1, zoomOut = 1;
        private double offsetX, offsetY;
        private volatile long revision;
        private volatile boolean closed;
        private boolean dirty;
        private volatile String message;

        BiomeDisplayWidget(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty());
            image = new NativeImage(w, h, false);
            image.fillRect(0, 0, w, h, EMPTY_COLOR);
            labels = new RegistryEntry<?>[w * h];
            texture = new NativeImageBackedTexture(TEXTURE_ID::toString, image);
            client.getTextureManager().registerTexture(TEXTURE_ID, texture);
            dirty = true;
        }

        void start() {
            Thread worker = new Thread(this::renderMap, "PE biome preview");
            worker.setDaemon(true);
            worker.start();
        }

        void zoomOut() {
            if (zoomIn > 1) {
                zoomIn /= 2;
            } else if (zoomOut < 0x1_00_00_00) {
                zoomOut *= 2;
            } else {
                return;
            }

            offsetX /= 2;
            offsetY /= 2;
            invalidateMap(true);
        }

        void zoomIn() {
            if (zoomOut <= 1) {
                if (zoomIn >= 0x1_00_00_00) return;
                zoomIn *= 2;
            } else {
                zoomOut /= 2;
            }

            offsetX *= 2;
            offsetY *= 2;
            invalidateMap(true);
        }

        private void invalidateMap(boolean clear) {
            synchronized (imageLock) {
                if (clear) {
                    image.fillRect(0, 0, width, height, EMPTY_COLOR);
                    java.util.Arrays.fill(labels, null);
                }
                dirty = true;
                revision++;
                imageLock.notifyAll();
            }
        }

        @Override
        public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            if (vertical < 0) zoomOut();
            else if (vertical > 0) zoomIn();
            else return false;
            return true;
        }

        @Override
        protected void onDrag(Click click, double deltaX, double deltaY) {
            int prevX = (int) Math.round(offsetX), prevY = (int) Math.round(offsetY);
            offsetX -= deltaX;
            offsetY -= deltaY;
            int diffX = (int) Math.round(offsetX) - prevX,
                    diffY = (int) Math.round(offsetY) - prevY;
            if (diffX == 0 && diffY == 0) return;
            synchronized (imageLock) {
                int[] pixels = image.copyPixelsArgb();
                var previous = labels;
                labels = new RegistryEntry<?>[width * height];
                image.fillRect(0, 0, width, height, EMPTY_COLOR);
                for (int sy = 0; sy < height; sy++)
                    for (int sx = 0; sx < width; sx++) {
                        int dx = sx - diffX, dy = sy - diffY;
                        if (dx >= 0 && dx < width && dy >= 0 && dy < height) {
                            image.setColorArgb(dx, dy, pixels[sx + sy * width]);
                            labels[dx + dy * width] = previous[sx + sy * width];
                        }
                    }
                invalidateMap(false);
            }
        }

        private void renderMap() {
            long previous = -1;
            try {
                while (!closed) {
                    int ox, oy, scale, zoom;
                    long version;
                    synchronized (imageLock) {
                        while (previous == revision && !closed) imageLock.wait();
                        if (closed) return;
                        version = revision;
                        ox = (int) Math.round(offsetX);
                        oy = (int) Math.round(offsetY);
                        scale = zoomOut;
                        zoom = zoomIn;
                    }
                    previous = version;
                    source.setPreviewStride(scale);
                    int gridExponent = (Integer.numberOfTrailingZeros(scale) + 2) / 4 * 4;
                    int gridScale = (1 << gridExponent) * zoom;
                    var random = new PreviewRandom(seed);
                    for (int y = 0; y < height && revision == version && !closed; y++) {
                        for (int x = 0; x < width && revision == version && !closed; x++) {
                            synchronized (imageLock) {
                                if (closed || revision != version) break;
                                if (labels[x + y * width] != null) continue;
                            }
                            int gx = scaled(x, ox, width, scale), gy = scaled(y, oy, height, scale);
                            long point = samplePoint(gx, gy, zoom, random);
                            int qx = originX + (int) (point >> 32), qz = originZ + (int) point;
                            var biome = source.previewSurface(qx, qz);
                            int color =
                                    grid(
                                            biomeColor(biome, qx, qz),
                                            (long) originX * zoom + gx,
                                            (long) originZ * zoom + gy,
                                            gridScale);
                            synchronized (imageLock) {
                                if (closed || revision != version) break;
                                image.setColorArgb(x, y, color | 0xff000000);
                                labels[x + y * width] = biome;
                                dirty = true;
                            }
                        }
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ex) {
                message = ex.toString();
                org.slf4j.LoggerFactory.getLogger("pe_worldgen").warn("Biome preview failed", ex);
            }
        }

        @Override
        protected void renderWidget(DrawContext g, int mx, int my, float delta) {
            int x = getX(), y = getY();
            synchronized (imageLock) {
                if (dirty) {
                    texture.upload();
                    dirty = false;
                }
            }
            g.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    TEXTURE_ID,
                    x,
                    y,
                    0f,
                    0f,
                    width,
                    height,
                    width,
                    height);
            double ratio = zoomOut * 4.0 / zoomIn;
            String scale =
                    "1:"
                            + (ratio == Math.rint(ratio)
                                    ? Integer.toString((int) ratio)
                                    : Double.toString(ratio));
            int labelWidth = textRenderer.getWidth(scale);
            g.fill(x + width - labelWidth - 8, y, x + width, y + 16, 0xaa000000);
            g.drawText(textRenderer, scale, x + width - labelWidth - 4, y + 4, -1, false);
            if (message != null)
                g.drawCenteredTextWithShadow(
                        textRenderer, Text.literal(message), x + width / 2, y + height / 2, -1);
            int dx = mx - x, dy = my - y;
            if (dx >= 0 && dy >= 0 && dx < width && dy < height) {
                g.setCursor(StandardCursors.RESIZE_ALL);
                RegistryEntry<?> biome;
                synchronized (imageLock) {
                    biome = labels[dx + dy * width];
                }
                long pos =
                        samplePoint(
                                scaled(dx, (int) Math.round(offsetX), width, zoomOut),
                                scaled(dy, (int) Math.round(offsetY), height, zoomOut),
                                zoomIn,
                                new PreviewRandom(seed));
                if (biome != null) {
                    var id = biome.getKey().orElseThrow().getValue();
                    g.drawTooltip(
                            textRenderer,
                            List.of(
                                    Text.literal(
                                            (4L * (originX + (int) (pos >> 32)))
                                                    + ", "
                                                    + (4L * (originZ + (int) pos))),
                                    Text.translatable(id.toTranslationKey("biome"))),
                            mx,
                            my);
                }
            }
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }

        private void saveScreenshot() {
            var copy = new NativeImage(width, height, false);
            synchronized (imageLock) {
                copy.copyFrom(image);
            }
            Path directory = client.runDirectory.toPath().resolve("screenshots");
            net.minecraft.util.Util.getIoWorkerExecutor()
                    .execute(
                            () -> {
                                try (copy) {
                                    Files.createDirectories(directory);
                                    Path file =
                                            Files.createTempFile(
                                                    directory, "PE-biome-preview-", ".png");
                                    copy.writeTo(file);
                                } catch (java.io.IOException ex) {
                                    message = ex.toString();
                                }
                            });
        }

        @Override
        public void close() {
            synchronized (imageLock) {
                if (closed) return;
                closed = true;
                imageLock.notifyAll();
                client.getTextureManager().destroyTexture(TEXTURE_ID);
            }
        }
    }

    private static int scaled(int pixel, int offset, int extent, int scale) {
        return (int)
                Math.clamp(
                        ((long) pixel + offset - extent / 2) * scale, -500_000_000L, 500_000_000L);
    }

    private static long samplePoint(
            int sampleX, int sampleY, int voronoiZoom, PreviewRandom voronoiRandom) {
        if (voronoiZoom > 1) {
            float voronoiFactor = voronoiZoom * 0.9F;

            int scaledSampleX = Math.floorDiv(sampleX, voronoiZoom);
            int scaledSampleY = Math.floorDiv(sampleY, voronoiZoom);

            int subSampleX = Math.floorMod(sampleX, voronoiZoom);
            int subSampleY = Math.floorMod(sampleY, voronoiZoom);

            voronoiRandom.init(scaledSampleX, scaledSampleY);
            float n00x = (voronoiRandom.nextInt(1024) / 1024.0F - 0.5F) * voronoiFactor;
            float n00y = (voronoiRandom.nextInt(1024) / 1024.0F - 0.5F) * voronoiFactor;
            voronoiRandom.init(scaledSampleX + 1, scaledSampleY);
            float n10x =
                    (voronoiRandom.nextInt(1024) / 1024.0F - 0.5F) * voronoiFactor + voronoiZoom;
            float n10y = (voronoiRandom.nextInt(1024) / 1024.0F - 0.5F) * voronoiFactor;
            voronoiRandom.init(scaledSampleX, scaledSampleY + 1);
            float n01x = (voronoiRandom.nextInt(1024) / 1024.0F - 0.5F) * voronoiFactor;
            float n01y =
                    (voronoiRandom.nextInt(1024) / 1024.0F - 0.5F) * voronoiFactor + voronoiZoom;
            voronoiRandom.init(scaledSampleX + 1, scaledSampleY + 1);
            float n11x =
                    (voronoiRandom.nextInt(1024) / 1024.0F - 0.5F) * voronoiFactor + voronoiZoom;
            float n11y =
                    (voronoiRandom.nextInt(1024) / 1024.0F - 0.5F) * voronoiFactor + voronoiZoom;

            sampleX = scaledSampleX;
            sampleY = scaledSampleY;

            float dist00 =
                    MathHelper.square(subSampleX - n00x) + MathHelper.square(subSampleY - n00y);
            float dist10 =
                    MathHelper.square(subSampleX - n10x) + MathHelper.square(subSampleY - n10y);
            float dist01 =
                    MathHelper.square(subSampleX - n01x) + MathHelper.square(subSampleY - n01y);
            float dist11 =
                    MathHelper.square(subSampleX - n11x) + MathHelper.square(subSampleY - n11y);

            if (dist11 < dist10 && dist11 < dist01 && dist11 < dist00) {
                sampleX++;
                sampleY++;
            } else if (dist10 < dist00 && dist10 < dist01 && dist10 < dist11) {
                sampleX++;
            } else if (dist01 < dist00 && dist01 < dist10 && dist01 < dist11) {
                sampleY++;
            }
        }

        return ((long) sampleX << 32) | Integer.toUnsignedLong(sampleY);
    }

    private static int grid(int color, long gridSampleX, long gridSampleY, int gridScale) {
        if (gridSampleX % (64L * gridScale) == 0 || gridSampleY % (64L * gridScale) == 0) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            r = MathHelper.lerp(1.0F / 3.0F, r, 0xFF);
            g = MathHelper.lerp(1.0F / 3.0F, g, 0xFF);
            b = MathHelper.lerp(1.0F / 3.0F, b, 0xFF);
            color = r << 16 | g << 8 | b;
        } else if (gridSampleX % (4L * gridScale) == 0 || gridSampleY % (4L * gridScale) == 0) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            r = MathHelper.lerp(0.1F, r, 0xFF);
            g = MathHelper.lerp(0.1F, g, 0xFF);
            b = MathHelper.lerp(0.1F, b, 0xFF);
            color = r << 16 | g << 8 | b;
        }

        return color;
    }

    private static int biomeColor(RegistryEntry<Biome> entry, int x, int z) {
        // Moderner Beta's registered preview colors take precedence over its climate fallback.
        int registered =
                switch (entry.getKey().orElseThrow().getValue().toString()) {
                    case "minecraft:badlands" -> 0xd94515;
                    case "minecraft:bamboo_jungle" -> 0x849500;
                    case "minecraft:beach" -> 0xfade55;
                    case "minecraft:birch_forest" -> 0x307444;
                    case "minecraft:cold_ocean" -> 0x202070;
                    case "minecraft:dark_forest" -> 0x40511a;
                    case "minecraft:deep_cold_ocean" -> 0x202038;
                    case "minecraft:deep_frozen_ocean" -> 0x404090;
                    case "minecraft:deep_lukewarm_ocean" -> 0x000040;
                    case "minecraft:deep_ocean" -> 0x000030;
                    case "minecraft:desert" -> 0xfa9418;
                    case "minecraft:eroded_badlands" -> 0xff6d3d;
                    case "minecraft:flower_forest" -> 0x2d8e49;
                    case "minecraft:forest" -> 0x056621;
                    case "minecraft:frozen_ocean" -> 0x7070d6;
                    case "minecraft:frozen_river" -> 0xa0a0ff;
                    case "minecraft:ice_spikes" -> 0xb4dcdc;
                    case "minecraft:jungle" -> 0x507b0a;
                    case "minecraft:lukewarm_ocean" -> 0x000090;
                    case "minecraft:mushroom_fields" -> 0xff00ff;
                    case "minecraft:nether_wastes" -> 0x572526;
                    case "minecraft:ocean" -> 0x000070;
                    case "minecraft:old_growth_birch_forest" -> 0x589c6c;
                    case "minecraft:old_growth_pine_taiga" -> 0x596651;
                    case "minecraft:old_growth_spruce_taiga" -> 0x818e79;
                    case "minecraft:plains" -> 0x8db360;
                    case "minecraft:river" -> 0x0000ff;
                    case "minecraft:savanna" -> 0xbdb25f;
                    case "minecraft:savanna_plateau" -> 0xa79d64;
                    case "minecraft:snowy_beach" -> 0xfaf0c0;
                    case "minecraft:snowy_plains" -> 0xffffff;
                    case "minecraft:snowy_taiga" -> 0x31554a;
                    case "minecraft:sparse_jungle" -> 0x60930f;
                    case "minecraft:stony_shore" -> 0xa2a284;
                    case "minecraft:sunflower_plains" -> 0xb5db88;
                    case "minecraft:swamp" -> 0x07f9b2;
                    case "minecraft:taiga" -> 0x0b6a5f;
                    case "minecraft:the_end" -> 0x8080ff;
                    case "minecraft:the_void" -> 0x000000;
                    case "minecraft:warm_ocean" -> 0x0000ac;
                    case "minecraft:windswept_forest" -> 0x5b7352;
                    case "minecraft:windswept_gravelly_hills" -> 0x888888;
                    case "minecraft:windswept_hills" -> 0x606060;
                    case "minecraft:windswept_savanna" -> 0xe5da87;
                    case "minecraft:wooded_badlands" -> 0xb09765;
                    case "pe_worldgen:bamboo_jungle_hills" -> 0x5c6c04;
                    case "pe_worldgen:pe_alpha_desert" -> 0xfa9418;
                    case "pe_worldgen:pe_alpha_forest" -> 0x056621;
                    case "pe_worldgen:pe_alpha_plains" -> 0xffd910;
                    case "pe_worldgen:pe_alpha_rainforest" -> 0x08fa36;
                    case "pe_worldgen:pe_alpha_savanna" -> 0xd9e023;
                    case "pe_worldgen:pe_alpha_seasonal_forest" -> 0x9be023;
                    case "pe_worldgen:pe_alpha_shrubland" -> 0xa1ad20;
                    case "pe_worldgen:pe_alpha_swampland" -> 0x07f9b2;
                    case "pe_worldgen:pe_alpha_taiga" -> 0x2eb153;
                    case "pe_worldgen:pe_alpha_tundra" -> 0x57eb59;
                    default -> -1;
                };
        if (registered >= 0) return registered;
        Biome biome = entry.value();
        String path = entry.getKey().orElseThrow().getValue().getPath();
        int color;
        if (path.equals("the_void")) return 0x16161a;
        if (entry.isIn(BiomeTags.IS_OCEAN) || entry.isIn(BiomeTags.IS_RIVER)) {
            color = biome.getWaterColor();
            if (entry.isIn(BiomeTags.IS_OCEAN)) color = (color & 0xfefefe) >> 1;
            if (entry.isIn(BiomeTags.IS_DEEP_OCEAN)) color = (color & 0xfefefe) >> 1;
        } else {
            color =
                    switch (path) {
                        case "desert", "pe_alpha_desert", "beach", "snowy_beach" -> 0xd8c997;
                        case "badlands", "eroded_badlands", "wooded_badlands" -> 0xba662e;
                        case "stony_shore" -> 0x777777;
                        case "mushroom_fields" -> 0x786779;
                        default -> biome.getGrassColorAt(x, z);
                    };
        }
        float contrast = 2.0F;
        float[] hsb = new float[3];
        Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, hsb);
        hsb[1] = MathHelper.clamp(hsb[1] * contrast, 0.0F, 1.0F);
        hsb[2] = MathHelper.clamp((hsb[2] - 0.5F) * contrast + 0.5F, 0.0F, 1.0F);

        if (biome.getTemperature() < 0.15F && !entry.isIn(BiomeTags.IS_DEEP_OCEAN)) {
            hsb[1] *= 0.1F;
            hsb[2] = hsb[2] * 0.25F + 0.75F;
        }

        return Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
    }

    // LayerRandom subset used by the upstream display's Voronoi magnifier.
    private static final class PreviewRandom {
        final long baseSeed;
        long seed;

        PreviewRandom(long baseSeed) {
            this.baseSeed = baseSeed;
        }

        void init(long x, long z) {
            seed = baseSeed;
            for (int i = 0; i < 2; i++) {
                seed = mix(seed, x);
                seed = mix(seed, z);
            }
        }

        int nextInt(int bound) {
            int result = Math.floorMod((int) ((seed >> 24) % bound), bound);
            seed = mix(seed, baseSeed);
            return result;
        }

        private static long mix(long s, long salt) {
            return s * (s * 6364136223846793005L + 1442695040888963407L) + salt;
        }
    }
}
