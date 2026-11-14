package net.mcpe.worldgen.fabric;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.RailShape;
import net.minecraft.block.RailBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;

/** Original numeric PE IDs are kept inside the engine; conversion happens at this boundary. */
final class LegacyBlocks {
    private LegacyBlocks() {}
    private static final Map<Block, Integer> IDS = new IdentityHashMap<>();
    private static final Map<BlockState, Integer> DATA = new HashMap<>();
    static {
        for(int id=0;id<256;id++)for(int data=0;data<16;data++)try {
            BlockState state=state(id,data);
            IDS.putIfAbsent(state.getBlock(),id);
            DATA.putIfAbsent(state,data);
        } catch(IllegalArgumentException ignored) {}
    }
    static int id(BlockState state) {
        if(state.isOf(Blocks.WATER))return state.get(Properties.LEVEL_15)==0?9:8;
        if(state.isOf(Blocks.LAVA))return state.get(Properties.LEVEL_15)==0?11:10;
        if(state.isOf(Blocks.PODZOL))return 243;
        if(state.isOf(Blocks.SMOOTH_STONE_SLAB))return state.get(SlabBlock.TYPE)==SlabType.DOUBLE?43:44;
        Integer id=IDS.get(state.getBlock());
        if(id==null)throw new IllegalArgumentException("No PE mapping for "+state);
        return id;
    }
    static int data(BlockState state) { return DATA.getOrDefault(state,0); }
    static BlockState state(int id) { return state(id,0); }
    static BlockState state(int id,int data) {
        data&=15;
        BlockState state;
        switch(id) {
            case 1: return new Block[]{Blocks.STONE,Blocks.GRANITE,Blocks.POLISHED_GRANITE,Blocks.DIORITE,Blocks.POLISHED_DIORITE,Blocks.ANDESITE,Blocks.POLISHED_ANDESITE}[Math.min(data,6)].getDefaultState();
            case 3: return (data==1?Blocks.COARSE_DIRT:data==2?Blocks.PODZOL:Blocks.DIRT).getDefaultState();
            case 5: return new Block[]{Blocks.OAK_PLANKS,Blocks.SPRUCE_PLANKS,Blocks.BIRCH_PLANKS,Blocks.JUNGLE_PLANKS,Blocks.ACACIA_PLANKS,Blocks.DARK_OAK_PLANKS}[Math.min(data,5)].getDefaultState();
            case 8,9: return Blocks.WATER.getDefaultState().with(Properties.LEVEL_15,id==9?0:data);
            case 10,11: return Blocks.LAVA.getDefaultState().with(Properties.LEVEL_15,id==11?0:data);
            case 12: return (data==1?Blocks.RED_SAND:Blocks.SAND).getDefaultState();
            case 17,162: {
                Block[] logs=id==17?new Block[]{Blocks.OAK_LOG,Blocks.SPRUCE_LOG,Blocks.BIRCH_LOG,Blocks.JUNGLE_LOG}:new Block[]{Blocks.ACACIA_LOG,Blocks.DARK_OAK_LOG};
                int species=data&(id==17?3:1);
                return logs[species].getDefaultState().with(PillarBlock.AXIS,(data&12)==4?Direction.Axis.X:(data&12)==8?Direction.Axis.Z:Direction.Axis.Y);
            }
            case 18,161: {
                Block[] leaves=id==18?new Block[]{Blocks.OAK_LEAVES,Blocks.SPRUCE_LEAVES,Blocks.BIRCH_LEAVES,Blocks.JUNGLE_LEAVES}:new Block[]{Blocks.ACACIA_LEAVES,Blocks.DARK_OAK_LEAVES};
                return leaves[data&(id==18?3:1)].getDefaultState().with(Properties.PERSISTENT,(data&4)!=0);
            }
            case 24: return (data==1?Blocks.CHISELED_SANDSTONE:data==2?Blocks.CUT_SANDSTONE:Blocks.SANDSTONE).getDefaultState();
            case 31: return (data==2?Blocks.FERN:data==0?Blocks.DEAD_BUSH:Blocks.SHORT_GRASS).getDefaultState();
            case 38: return new Block[]{Blocks.POPPY,Blocks.BLUE_ORCHID,Blocks.ALLIUM,Blocks.AZURE_BLUET,Blocks.RED_TULIP,Blocks.ORANGE_TULIP,Blocks.WHITE_TULIP,Blocks.PINK_TULIP,Blocks.OXEYE_DAISY}[Math.min(data,8)].getDefaultState();
            case 35: return (data==15?Blocks.BLACK_WOOL:Blocks.WHITE_WOOL).getDefaultState();
            case 43: return Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.DOUBLE);
            case 44: return ((data&7)==1?Blocks.SANDSTONE_SLAB:Blocks.SMOOTH_STONE_SLAB).getDefaultState().with(SlabBlock.TYPE,(data&8)!=0?SlabType.TOP:SlabType.BOTTOM);
            case 50:
                if(data>=1&&data<=4)return Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING,new Direction[]{Direction.EAST,Direction.WEST,Direction.SOUTH,Direction.NORTH}[data-1]);
                return Blocks.TORCH.getDefaultState();
            case 53,67,109,128,134,135,136,163,164:
                return base(id).with(StairsBlock.FACING,new Direction[]{Direction.EAST,Direction.WEST,Direction.SOUTH,Direction.NORTH}[data&3]).with(StairsBlock.HALF,(data&4)!=0?BlockHalf.TOP:BlockHalf.BOTTOM);
            case 54:
                return Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING,data==2?Direction.NORTH:data==3?Direction.SOUTH:data==4?Direction.WEST:Direction.EAST);
            case 59: return Blocks.WHEAT.getDefaultState().with(Properties.AGE_7,data&7);
            case 60: return Blocks.FARMLAND.getDefaultState().with(Properties.MOISTURE,data&7);
            case 61,62: return Blocks.FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING,data==2?Direction.NORTH:data==3?Direction.SOUTH:data==4?Direction.WEST:Direction.EAST).with(Properties.LIT,id==62);
            case 64,71:
                return (id==64?Blocks.OAK_DOOR:Blocks.IRON_DOOR).getDefaultState()
                    .with(DoorBlock.HALF,(data&8)!=0?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER)
                    .with(DoorBlock.FACING,(data&8)==0?new Direction[]{Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH}[data&3]:Direction.NORTH)
                    .with(DoorBlock.OPEN,(data&8)==0&&(data&4)!=0)
                    .with(DoorBlock.HINGE,(data&8)!=0&&(data&1)!=0?DoorHinge.RIGHT:DoorHinge.LEFT);
            case 65: return Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING,data==2?Direction.NORTH:data==3?Direction.SOUTH:data==4?Direction.WEST:Direction.EAST);
            case 66: return Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE,new RailShape[]{RailShape.NORTH_SOUTH,RailShape.EAST_WEST,RailShape.ASCENDING_EAST,RailShape.ASCENDING_WEST,RailShape.ASCENDING_NORTH,RailShape.ASCENDING_SOUTH,RailShape.SOUTH_EAST,RailShape.SOUTH_WEST,RailShape.NORTH_WEST,RailShape.NORTH_EAST}[Math.min(data,9)]);
            case 78: return Blocks.SNOW.getDefaultState().with(Properties.LAYERS,Math.min(data+1,8));
            case 81: return Blocks.CACTUS.getDefaultState().with(Properties.AGE_15,data);
            case 83: return Blocks.SUGAR_CANE.getDefaultState().with(Properties.AGE_15,data);
            case 85: return new Block[]{Blocks.OAK_FENCE,Blocks.SPRUCE_FENCE,Blocks.BIRCH_FENCE,Blocks.JUNGLE_FENCE,Blocks.ACACIA_FENCE,Blocks.DARK_OAK_FENCE}[Math.min(data,5)].getDefaultState();
            case 98: return new Block[]{Blocks.STONE_BRICKS,Blocks.MOSSY_STONE_BRICKS,Blocks.CRACKED_STONE_BRICKS,Blocks.CHISELED_STONE_BRICKS}[data&3].getDefaultState();
            case 97: return (data==2?Blocks.INFESTED_STONE_BRICKS:data==1?Blocks.INFESTED_COBBLESTONE:Blocks.INFESTED_STONE).getDefaultState();
            case 106:
                return Blocks.VINE.getDefaultState().with(Properties.SOUTH,(data&1)!=0).with(Properties.WEST,(data&2)!=0).with(Properties.NORTH,(data&4)!=0).with(Properties.EAST,(data&8)!=0);
            case 99,100:
                if(data==10||data==15)return Blocks.MUSHROOM_STEM.getDefaultState();
                return (id==99?Blocks.BROWN_MUSHROOM_BLOCK:Blocks.RED_MUSHROOM_BLOCK).getDefaultState()
                    .with(Properties.UP,data>=1&&data<=9||data==14)
                    .with(Properties.DOWN,data==14)
                    .with(Properties.NORTH,data>=1&&data<=3||data==14)
                    .with(Properties.SOUTH,data>=7&&data<=9||data==14)
                    .with(Properties.WEST,data==1||data==4||data==7||data==14)
                    .with(Properties.EAST,data==3||data==6||data==9||data==14);
            case 127:
                return Blocks.COCOA.getDefaultState().with(Properties.HORIZONTAL_FACING,new Direction[]{Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST}[data&3]).with(Properties.AGE_2,Math.min(data>>2,2));
            case 120: return Blocks.END_PORTAL_FRAME.getDefaultState().with(Properties.HORIZONTAL_FACING,new Direction[]{Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST}[data&3]).with(Properties.EYE,(data&4)!=0);
            case 141: return Blocks.CARROTS.getDefaultState().with(Properties.AGE_7,data&7);
            case 142: return Blocks.POTATOES.getDefaultState().with(Properties.AGE_7,data&7);
            case 171: return (data==12?Blocks.BROWN_CARPET:Blocks.WHITE_CARPET).getDefaultState();
            case 175: {
                Block[] plants={Blocks.SUNFLOWER,Blocks.LILAC,Blocks.TALL_GRASS,Blocks.LARGE_FERN,Blocks.ROSE_BUSH,Blocks.PEONY};
                return plants[Math.min(data&7,5)].getDefaultState().with(Properties.DOUBLE_BLOCK_HALF,(data&8)!=0?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER);
            }
            default: return base(id);
        }
    }
    private static BlockState base(int id) {
        return switch (id) {
            case 0 -> Blocks.AIR.getDefaultState();
            case 1 -> Blocks.STONE.getDefaultState();
            case 2 -> Blocks.GRASS_BLOCK.getDefaultState();
            case 3 -> Blocks.DIRT.getDefaultState();
            case 4 -> Blocks.COBBLESTONE.getDefaultState();
            case 5 -> Blocks.OAK_PLANKS.getDefaultState();
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
            case 20 -> Blocks.GLASS.getDefaultState();
            case 21 -> Blocks.LAPIS_ORE.getDefaultState();
            case 24 -> Blocks.SANDSTONE.getDefaultState();
            case 37 -> Blocks.DANDELION.getDefaultState();
            case 38 -> Blocks.POPPY.getDefaultState();
            case 39 -> Blocks.BROWN_MUSHROOM.getDefaultState();
            case 40 -> Blocks.RED_MUSHROOM.getDefaultState();
            case 30 -> Blocks.COBWEB.getDefaultState();
            case 31 -> Blocks.SHORT_GRASS.getDefaultState();
            case 32 -> Blocks.DEAD_BUSH.getDefaultState();
            case 35 -> Blocks.WHITE_WOOL.getDefaultState();
            case 43 -> Blocks.SMOOTH_STONE.getDefaultState();
            case 44 -> Blocks.SMOOTH_STONE_SLAB.getDefaultState();
            case 45 -> Blocks.BRICKS.getDefaultState();
            case 47 -> Blocks.BOOKSHELF.getDefaultState();
            case 48 -> Blocks.MOSSY_COBBLESTONE.getDefaultState();
            case 49 -> Blocks.OBSIDIAN.getDefaultState();
            case 50 -> Blocks.TORCH.getDefaultState();
            case 51 -> Blocks.FIRE.getDefaultState();
            case 52 -> Blocks.SPAWNER.getDefaultState();
            case 53 -> Blocks.OAK_STAIRS.getDefaultState();
            case 54 -> Blocks.CHEST.getDefaultState();
            case 56 -> Blocks.DIAMOND_ORE.getDefaultState();
            case 58 -> Blocks.CRAFTING_TABLE.getDefaultState();
            case 59 -> Blocks.WHEAT.getDefaultState();
            case 60 -> Blocks.FARMLAND.getDefaultState();
            case 61,62 -> Blocks.FURNACE.getDefaultState();
            case 64 -> Blocks.OAK_DOOR.getDefaultState();
            case 65 -> Blocks.LADDER.getDefaultState();
            case 66 -> Blocks.RAIL.getDefaultState();
            case 67 -> Blocks.COBBLESTONE_STAIRS.getDefaultState();
            case 71 -> Blocks.IRON_DOOR.getDefaultState();
            case 73, 74 -> Blocks.REDSTONE_ORE.getDefaultState();
            case 78 -> Blocks.SNOW.getDefaultState();
            case 79 -> Blocks.ICE.getDefaultState();
            case 80 -> Blocks.SNOW_BLOCK.getDefaultState();
            case 81 -> Blocks.CACTUS.getDefaultState();
            case 82 -> Blocks.CLAY.getDefaultState();
            case 83 -> Blocks.SUGAR_CANE.getDefaultState();
            case 87 -> Blocks.NETHERRACK.getDefaultState();
            case 89 -> Blocks.GLOWSTONE.getDefaultState();
            case 85 -> Blocks.OAK_FENCE.getDefaultState();
            case 86 -> Blocks.CARVED_PUMPKIN.getDefaultState();
            case 91 -> Blocks.JACK_O_LANTERN.getDefaultState();
            case 95 -> Blocks.BARRIER.getDefaultState();
            case 96 -> Blocks.OAK_TRAPDOOR.getDefaultState();
            case 97 -> Blocks.INFESTED_STONE.getDefaultState();
            case 98 -> Blocks.STONE_BRICKS.getDefaultState();
            case 99 -> Blocks.BROWN_MUSHROOM_BLOCK.getDefaultState();
            case 100 -> Blocks.RED_MUSHROOM_BLOCK.getDefaultState();
            case 101 -> Blocks.IRON_BARS.getDefaultState();
            case 102 -> Blocks.GLASS_PANE.getDefaultState();
            case 103 -> Blocks.MELON.getDefaultState();
            case 106 -> Blocks.VINE.getDefaultState();
            case 107 -> Blocks.OAK_FENCE_GATE.getDefaultState();
            case 109 -> Blocks.STONE_BRICK_STAIRS.getDefaultState();
            case 110 -> Blocks.MYCELIUM.getDefaultState();
            case 111 -> Blocks.LILY_PAD.getDefaultState();
            case 120 -> Blocks.END_PORTAL_FRAME.getDefaultState();
            case 125 -> Blocks.OAK_PLANKS.getDefaultState();
            case 126 -> Blocks.OAK_SLAB.getDefaultState();
            case 127 -> Blocks.COCOA.getDefaultState();
            case 128 -> Blocks.SANDSTONE_STAIRS.getDefaultState();
            case 129 -> Blocks.EMERALD_ORE.getDefaultState();
            case 134 -> Blocks.SPRUCE_STAIRS.getDefaultState();
            case 135 -> Blocks.BIRCH_STAIRS.getDefaultState();
            case 136 -> Blocks.JUNGLE_STAIRS.getDefaultState();
            case 139 -> Blocks.COBBLESTONE_WALL.getDefaultState();
            case 141 -> Blocks.CARROTS.getDefaultState();
            case 142 -> Blocks.POTATOES.getDefaultState();
            case 161 -> Blocks.ACACIA_LEAVES.getDefaultState();
            case 162 -> Blocks.ACACIA_LOG.getDefaultState();
            case 163 -> Blocks.ACACIA_STAIRS.getDefaultState();
            case 164 -> Blocks.DARK_OAK_STAIRS.getDefaultState();
            case 175 -> Blocks.SUNFLOWER.getDefaultState();
            case 174 -> Blocks.PACKED_ICE.getDefaultState();
            case 171 -> Blocks.WHITE_CARPET.getDefaultState();
            case 198 -> Blocks.DIRT_PATH.getDefaultState();
            case 243 -> Blocks.PODZOL.getDefaultState();
            default -> throw new IllegalArgumentException("Unmapped PE block ID: " + id);
        };
    }
}
