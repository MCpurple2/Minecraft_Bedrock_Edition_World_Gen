package net.mcpe.worldgen.core;

/** Mth::_sin initialization recovered from MinecraftClient::init, 00234f6c..00234fae.
 * PE rounds the angular step and multiplication to float before calling double sin.
 * This is deliberately not Java's usual double-precision table construction. */
public final class PeMth {
    public static final float SIN_SCALE = Float.intBitsToFloat(0x4622f983);
    private static final float[] SIN = new float[65536];
    static {
        float step = 1.0f / SIN_SCALE;
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) StrictMath.sin((double)((float)i * step));
    }
    private PeMth() {}
    public static float sin(float radians) { return SIN[(int)(radians * SIN_SCALE) & 65535]; }
    public static float cos(float radians) { return SIN[(int)(radians * SIN_SCALE + 16384.0f) & 65535]; }
    public static float indexed(int index) { return SIN[index & 65535]; }
}
