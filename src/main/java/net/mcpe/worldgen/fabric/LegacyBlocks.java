package net.mcpe.worldgen.fabric;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/** Original numeric PE IDs are kept inside the engine; conversion happens at this boundary. */
final class LegacyBlocks {
    private LegacyBlocks() {}
    static BlockState state(int id) {
        return switch (id) {
            case 0 -> Blocks.AIR.getDefaultState();
            case 1 -> Blocks.STONE.getDefaultState();
            case 2 -> Blocks.GRASS_BLOCK.getDefaultState();
            case 3 -> Blocks.DIRT.getDefaultState();
            case 7 -> Blocks.BEDROCK.getDefaultState();
            case 8, 9 -> Blocks.WATER.getDefaultState();
            case 10, 11 -> Blocks.LAVA.getDefaultState();
            case 12 -> Blocks.SAND.getDefaultState();
            case 13 -> Blocks.GRAVEL.getDefaultState();
            case 14 -> Blocks.GOLD_ORE.getDefaultState();
            case 15 -> Blocks.IRON_ORE.getDefaultState();
            case 16 -> Blocks.COAL_ORE.getDefaultState();
            case 17 -> Blocks.OAK_LOG.getDefaultState();
            case 18 -> Blocks.OAK_LEAVES.getDefaultState();
            case 24 -> Blocks.SANDSTONE.getDefaultState();
            case 37 -> Blocks.DANDELION.getDefaultState();
            case 38 -> Blocks.POPPY.getDefaultState();
            case 39 -> Blocks.BROWN_MUSHROOM.getDefaultState();
            case 40 -> Blocks.RED_MUSHROOM.getDefaultState();
            case 56 -> Blocks.DIAMOND_ORE.getDefaultState();
            case 73, 74 -> Blocks.REDSTONE_ORE.getDefaultState();
            case 78 -> Blocks.SNOW.getDefaultState();
            case 79 -> Blocks.ICE.getDefaultState();
            case 80 -> Blocks.SNOW_BLOCK.getDefaultState();
            case 81 -> Blocks.CACTUS.getDefaultState();
            case 82 -> Blocks.CLAY.getDefaultState();
            case 83 -> Blocks.SUGAR_CANE.getDefaultState();
            case 87 -> Blocks.NETHERRACK.getDefaultState();
            case 89 -> Blocks.GLOWSTONE.getDefaultState();
            case 95 -> Blocks.BARRIER.getDefaultState();
            case 110 -> Blocks.MYCELIUM.getDefaultState();
            case 129 -> Blocks.EMERALD_ORE.getDefaultState();
            default -> throw new IllegalArgumentException("Unmapped PE block ID: " + id);
        };
    }
}
