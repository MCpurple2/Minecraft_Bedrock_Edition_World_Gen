package net.mcpe.worldgen.core;

/** Early PE BiomeSource, with its original float operations and 64 by 64 lookup. */
public final class PeAlphaClimate {
    public enum Type {
        TUNDRA(12), SAVANNA(35), DESERT(2), SWAMPLAND(6), TAIGA(5),
        SHRUBLAND(1), FOREST(4), PLAINS(1), SEASONAL_FOREST(4), RAINFOREST(21);

        public final int terrainBiome;
        Type(int terrainBiome) { this.terrainBiome = terrainBiome; }
        public String path() { return "pe_alpha_" + name().toLowerCase(java.util.Locale.ROOT); }
    }

    private static final Type[] TABLE = new Type[4096];
    static {
        for (int r = 0; r < 64; r++) for (int t = 0; t < 64; t++)
            TABLE[t + r * 64] = classify(t / 63f, r / 63f);
    }

    private final PeOctaveNoise temperature, rainfall, detail;
    public PeAlphaClimate(long seed) {
        temperature = new PeOctaveNoise(new PeRandom((int)seed * 9871), 4);
        rainfall = new PeOctaveNoise(new PeRandom((int)seed * 39811), 4);
        detail = new PeOctaveNoise(new PeRandom((int)seed * 543321), 2);
    }

    private static Type classify(float t, float r) {
        r *= t;
        if (t < .1f) return Type.TUNDRA;
        if (r < .2f) return t < .5f ? Type.TUNDRA : t < .95f ? Type.SAVANNA : Type.DESERT;
        if (r > .5f && t < .7f) return Type.SWAMPLAND;
        if (t < .5f) return Type.TAIGA;
        if (t < .97f) return r < .35f ? Type.SHRUBLAND : Type.FOREST;
        return r < .45f ? Type.PLAINS : r < .9f ? Type.SEASONAL_FOREST : Type.RAINFOREST;
    }

    /** Result uses the generator's x + z * width layout. */
    public Type[] area(int x, int z, int width, int height) {
        float[] temp = temperature.getRegion2D(null,x,z,width,height,.025f,.025f);
        float[] rain = rainfall.getRegion2D(null,x,z,width,height,.05f,.05f);
        float[] noise = detail.getRegion2D(null,x,z,width,height,.25f,.25f);
        Type[] result = new Type[width * height];
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < height; dz++) {
            int i = dx * height + dz;
            float d = noise[i] * 1.1f + .5f;
            float t = 1f - (d * .01f + (temp[i] * .15f + .7f) * .99f);
            t = Math.clamp(1f - t*t, 0f, 1f);
            float r = Math.clamp(d * .002f + (rain[i] * .15f + .5f) * .998f, 0f, 1f);
            result[dx + dz * width] = TABLE[(int)(t * 63f) + 64 * (int)(r * 63f)];
        }
        return result;
    }

    int[] terrainArea(int x, int z, int width, int height, int stride) {
        int spanX = (width - 1) * stride + 1;
        Type[] types = area(x * stride, z * stride, spanX, (height - 1) * stride + 1);
        int[] result = new int[width * height];
        for (int dz = 0; dz < height; dz++) for (int dx = 0; dx < width; dx++)
            result[dx + dz * width] = types[stride * (dx + dz * spanX)].terrainBiome;
        return result;
    }
}
