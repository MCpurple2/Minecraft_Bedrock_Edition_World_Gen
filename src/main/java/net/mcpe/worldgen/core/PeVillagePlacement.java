package net.mcpe.worldgen.core;

/** PE 0.11.1 VillageFeature::isFeatureChunk, native ARM address 0x002a0bd8.
 * This primitive only identifies start chunks; it does not claim village pieces
 * have been generated. In particular the seed uses adjusted chunk coordinates,
 * not the divided cell coordinates used by some Java generators. */
public final class PeVillagePlacement {
    private PeVillagePlacement() {}

    public static boolean isAllowedBiome(int biomeId) {
        return isAllowedBiome(biomeId, PeRelease.V0_11_1);
    }

    public static boolean isAllowedBiome(int biomeId, PeRelease release) {
        return biomeId == 1 || biomeId == 2 || biomeId == 35 ||
            (biomeId == 12 && release.compareTo(PeRelease.V0_15_10) >= 0);
    }

    public static int candidateSeed(long worldSeed, int chunkX, int chunkZ) {
        int adjustedX = chunkX < 0 ? chunkX - 39 : chunkX;
        int adjustedZ = chunkZ < 0 ? chunkZ - 39 : chunkZ;
        return adjustedZ * -0x0ea9a42b + adjustedX * -0x66c60af8 + (int) worldSeed + 0x009e7f70;
    }

    /** Leaves random at the exact native state after the two candidate draws. */
    public static boolean isFeatureChunk(long worldSeed, int chunkX, int chunkZ, int centerBiomeId, PeRandom random) {
        return isFeatureChunk(worldSeed, chunkX, chunkZ, centerBiomeId, random, PeRelease.V0_11_1);
    }
    public static boolean isFeatureChunk(long worldSeed, int chunkX, int chunkZ, int centerBiomeId, PeRandom random, PeRelease release) {
        int adjustedX = chunkX < 0 ? chunkX - 39 : chunkX;
        int adjustedZ = chunkZ < 0 ? chunkZ - 39 : chunkZ;
        random.setSeed(candidateSeed(worldSeed, chunkX, chunkZ));
        int selectedX = random.nextInt(28) + (adjustedX / 40) * 40;
        // Native consumes the second draw even if X does not match.
        int selectedZ = random.nextInt(28) + (adjustedZ / 40) * 40;
        return selectedX == chunkX && selectedZ == chunkZ && isAllowedBiome(centerBiomeId, release);
    }
}
