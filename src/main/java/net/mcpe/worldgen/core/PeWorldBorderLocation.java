package net.mcpe.worldgen.core;

/** Adapted from Moderner Beta's WorldBorderLocation and OutOfBoundsInjectionPredicate (MIT). */
public record PeWorldBorderLocation(int centerX, int centerZ, int width) {
    public static final int WIDTH = 256;

    public PeWorldBorderLocation(int centerX, int centerZ) { this(centerX, centerZ, WIDTH); }

    public boolean containsPoint(int x, int z, int margin) {
        int radius = width / 2;
        return x >= centerX - radius - margin && x < centerX + radius + margin
            && z >= centerZ - radius - margin && z < centerZ + radius + margin;
    }

    public boolean containsChunk(int x, int z) {
        int radius = width / 2;
        return x * 16 + 15 >= centerX - radius && x * 16 < centerX + radius
            && z * 16 + 15 >= centerZ - radius && z * 16 < centerZ + radius;
    }

    public boolean outsideBiomeCell(int biomeX, int biomeZ) {
        // MB's PRE injection samples the center of each quart. PE has no exterior transition strip.
        return !containsPoint((biomeX << 2) + 2, (biomeZ << 2) + 2, 0);
    }
}
