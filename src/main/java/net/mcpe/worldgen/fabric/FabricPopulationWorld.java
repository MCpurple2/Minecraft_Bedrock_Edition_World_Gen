package net.mcpe.worldgen.fabric;

import net.mcpe.worldgen.core.PePopulationWorld;
import net.mcpe.worldgen.core.PeLiquidTicks;
import net.mcpe.worldgen.core.PeSprings;
import net.mcpe.worldgen.core.PeRandom;
import net.mcpe.worldgen.core.PeRelease;
import net.mcpe.worldgen.core.PeWorldGenerator;
import net.mcpe.worldgen.core.PeSurfacePatches;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.LightType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerProfession;
import net.minecraft.state.property.Properties;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;

/** The only population code which knows Minecraft/Fabric types. */
final class FabricPopulationWorld implements PePopulationWorld {
    private boolean peOres = true;
    @Override public boolean usePeOreDistribution() { return peOres; }
    private PeLiquidTicks liquids;
    private final java.util.Set<BlockPos> newEarlyLeaves = new java.util.HashSet<>();
    private final java.util.ArrayDeque<BlockPos> leafFrontier = new java.util.ArrayDeque<>();
    private final StructureWorldAccess world;
    private final PeWorldGenerator core;
    private final Map<Long,int[]> biomeAreas=new HashMap<>();
    private final Map<Long,Integer> columnHeights=new HashMap<>();
    FabricPopulationWorld(StructureWorldAccess world,PeWorldGenerator core){this.world=world;this.core=core;
        if(world.toServerWorld().getChunkManager().getChunkGenerator() instanceof PeChunkGenerator pe)
            peOres=!pe.settings().has(net.mcpe.worldgen.core.PeContent.MODERN_ORES);}
    /** Nether uses the PE 0.12 engine and a single fixed biome. */
    FabricPopulationWorld(StructureWorldAccess world){this.world=world;this.core=null;}
    @Override public PeRelease release(){return core==null?PeRelease.V0_12_3:core.release();}
    @Override public void populateMonumentRooms(int x, int z, int direction, int rotationSeed, PeRandom random,
                                               int chunkX, int chunkZ) {
        FabricMonumentRooms.populate(world, x, z, direction, rotationSeed, random, chunkX, chunkZ);
    }
    @Override public void beginLiquidTicks(int cx,int cz){liquids=new PeLiquidTicks(this,cx,cz,core==null);}
    @Override public void finishLiquidTicks(int cx,int cz){
        connectEarlyLeaves();
        liquids.drain();
    }

    /** Natural PE leaves are written before their trunks. Supply JE's decay distance
     * once the full crown is present, before newly loaded chunks start random ticks. */
    private void connectEarlyLeaves() {
        for (int distance=1; distance<=6 && !leafFrontier.isEmpty() && !newEarlyLeaves.isEmpty(); distance++) {
            int count = leafFrontier.size();
            for (int i=0; i<count; i++) {
                BlockPos source = leafFrontier.removeFirst();
                for (Direction side : Direction.values()) {
                    BlockPos pos = source.offset(side);
                    if (!newEarlyLeaves.remove(pos)) continue;
                    var state = world.getBlockState(pos);
                    if (!state.contains(net.minecraft.block.LeavesBlock.DISTANCE)) continue;
                    int oldDistance = state.get(net.minecraft.block.LeavesBlock.DISTANCE);
                    if (oldDistance > distance)
                        world.setBlockState(pos, state.with(net.minecraft.block.LeavesBlock.DISTANCE, distance), 2);
                    leafFrontier.addLast(pos);
                }
            }
        }
        leafFrontier.clear();
        newEarlyLeaves.clear();
    }
    @Override public void scheduleLiquidTick(int x,int y,int z,int id,int level){liquids.schedule(x,y,z,id,level);}
    @Override public void tickStructureLiquid(int x,int y,int z,int id){
        // StructurePiece::placeBlock seeds a separate MT for its immediate tick.
        if(id==10)PeSprings.spreadFire(this,new PeRandom(0x88f9fa*x+0xef88b*y+z),x,y,z);
        liquids.tick(x,y,z,id,0);
    }
    @Override public boolean isInside(int x,int y,int z){return y>=0&&y<128&&(core==null||core.isInsideChunk(x >> 4, z >> 4));}
    @Override public int getBlock(int x,int y,int z){
        if(y<0||y>=128)return 0;
        // EmptyLevelChunk::getTile exposes invisible bedrock to the PE generator at its horizontal limit.
        if(!isInside(x,y,z))return core.usesNativeEarlyTerrain() ? 95 : 0;
        return LegacyBlocks.id(world.getBlockState(new BlockPos(x,y,z)));
    }
    @Override public int getData(int x,int y,int z){
        if(!isInside(x,y,z))return 0;
        return LegacyBlocks.data(world.getBlockState(new BlockPos(x,y,z)));
    }
    @Override public void setBlock(int x,int y,int z,int id,int data){
        if(!isInside(x,y,z))return;
        BlockPos pos=new BlockPos(x,y,z);
        if(id==175&&(data&8)!=0)data=world.getBlockState(pos.down()).contains(Properties.DOUBLE_BLOCK_HALF)?LegacyBlocks.data(world.getBlockState(pos.down()))|8:data;
        // Tile 38 (Rose) used the blue flower in the pre-0.9 terrain atlas.
        BlockState state = id == 38 && data == 0 && !release().hasWorldTypes()
                ? PeWorldgenMod.CYAN_ROSE.getDefaultState() : LegacyBlocks.state(id,data);
        if(id==64||id==71||id==193||id==196){
            if((data&8)!=0){
                BlockState lower=world.getBlockState(pos.down());
                if(lower.getBlock()==state.getBlock()){
                    state=state.with(DoorBlock.FACING,lower.get(DoorBlock.FACING)).with(DoorBlock.OPEN,lower.get(DoorBlock.OPEN));
                    world.setBlockState(pos.down(),lower.with(DoorBlock.HINGE,state.get(DoorBlock.HINGE)),2);
                }
            }
        }
        world.setBlockState(pos,state,2);
        if ((id==17 || id==18 || id==161 || id==162) && release().compareTo(PeRelease.V0_9_5)<0) {
            if (id==18 || id==161) newEarlyLeaves.add(pos);
            else if (id==17 || id==162) {
                newEarlyLeaves.remove(pos);
                leafFrontier.addLast(pos);
            }
        }
        long column=columnKey(x,z);
        Integer height=columnHeights.get(column);
        if (height != null && y < 127) {
            boolean blocks = PeSurfacePatches.blocksSkylight(id);
            if (!blocks && y == height - 1) columnHeights.remove(column);
            else if (blocks && y >= height) columnHeights.put(column, y + 1);
        }
        if(id==53||id==67||id==109||id==113||id==114||id==128||id==134||id==135||id==136||id==163||id==164||id==85||id==101||id==102||id==139||id==55||id==131||id==132)
            world.getChunk(x>>4,z>>4).markBlockForPostProcessing(pos);
    }
    private static long columnKey(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    @Override public int surfaceY(int x,int z){
        long key=columnKey(x,z);Integer cached=columnHeights.get(key);if(cached!=null)return cached;
        int height = 0;
        for (int y = 126; y >= 0; y--) {
            if (PeSurfacePatches.blocksSkylight(getBlock(x,y,z))) {
                height = y + 1;
                break;
            }
        }
        columnHeights.put(key,height);return height;
    }
    @Override public int biomeAt(int x,int z){
        if(core==null)return 8;
        int cx=x>>4,cz=z>>4;
        int[] area=biomeAreas.computeIfAbsent(columnKey(cx,cz),ignored->core.biomeArea(cx<<4,cz<<4,16,16));
        return area[(x&15)+(z&15)*16];
    }
    @Override public boolean canSeeSky(int x,int y,int z){return y>=surfaceY(x,z);}
    @Override public int rawBrightness(int x,int y,int z){
        // Population runs before Java finishes its column light map.
        if (core != null && canSeeSky(x,y,z)) return 15;
        int light = world.getLightLevel(new BlockPos(x,y,z));
        if (core == null || release().hasWorldTypes() || light == 15 || !isInside(x,y,z)) return light;
        // PE updates the skylight column as the crown is placed. JE's light map is still
        // empty during decoration. Include the direct column and light from touching columns.
        light = Math.max(light, earlyColumnLight(x,y,z));
        if (light < 14 && getBlock(x,y,z) == 0) {
            light = Math.max(light, earlyColumnLight(x-1,y,z)-1);
            light = Math.max(light, earlyColumnLight(x+1,y,z)-1);
            light = Math.max(light, earlyColumnLight(x,y,z-1)-1);
            light = Math.max(light, earlyColumnLight(x,y,z+1)-1);
        }
        return light;
    }

    private int earlyColumnLight(int x, int y, int z) {
        if (!isInside(x,y,z)) return 0;
        int light = 15;
        for (int h=surfaceY(x,z)-1; h>=y && light>0; h--) {
            int block = getBlock(x,h,z);
            if (block==8 || block==9 || block==79) light-=3;
            else if (block==18 || !PeSurfacePatches.blocksSkylight(block)) light--;
            else return 0;
        }
        return Math.max(light,0);
    }

    @Override public int blockBrightness(int x,int y,int z){return world.getLightLevel(LightType.BLOCK,new BlockPos(x,y,z));}
    @Override public int skyBrightness(int x,int y,int z){
        if(canSeeSky(x,y,z))return 15;
        return world.getLightLevel(LightType.SKY,new BlockPos(x,y,z));
    }
    @Override public boolean solidRender(int x,int y,int z){return world.getBlockState(new BlockPos(x,y,z)).isOpaqueFullCube();}
    @Override public boolean solidTop(int x,int y,int z){BlockPos p=new BlockPos(x,y,z);return world.getBlockState(p).isSideSolidFullSquare(world,p,Direction.UP);}

    @Override public void setChestSlot(int x,int y,int z,int slot,int legacyItemId,int count){
        if(!isInside(x,y,z))return;
        BlockPos pos=new BlockPos(x,y,z);
        BlockEntity entity=world.getBlockEntity(pos);
        if(entity==null){
            var state=world.getBlockState(pos);
            entity=state.isOf(net.minecraft.block.Blocks.DISPENSER) ? new DispenserBlockEntity(pos,state) : new ChestBlockEntity(pos,state);
            world.getChunk(x>>4,z>>4).setBlockEntity(entity);
        }
        if(!(entity instanceof Inventory inventory))throw new IllegalStateException("Chest inventory missing at "+pos);
        inventory.setStack(slot,new ItemStack(item(legacyItemId),count));
        entity.markDirty();
    }
    @Override public void setSpawner(int x,int y,int z,int legacyMobId){
        if(!isInside(x,y,z))return;
        BlockPos pos=new BlockPos(x,y,z);
        BlockEntity entity=world.getBlockEntity(pos);
        if(entity==null){entity=new MobSpawnerBlockEntity(pos,world.getBlockState(pos));world.getChunk(x>>4,z>>4).setBlockEntity(entity);}
        if(!(entity instanceof MobSpawnerBlockEntity spawner))throw new IllegalStateException("Spawner missing at "+pos);
        spawner.setEntityType(switch(legacyMobId){case 32->EntityType.ZOMBIE;case 34->EntityType.SKELETON;case 35->EntityType.SPIDER;case 39->EntityType.SILVERFISH;case 40->EntityType.CAVE_SPIDER;case 43->EntityType.BLAZE;default->throw new IllegalArgumentException("Unknown PE mob "+legacyMobId);},world.getRandom());
        spawner.markDirty();
    }
    @Override public void spawnVillager(int x,int y,int z,int profession){spawnVillager(x,y,z,profession,"position:"+x+":"+y+":"+z);}
    @Override public void spawnHutWitch(int x,int y,int z){
        if(!isInside(x,y,z))return;
        String key="pe_worldgen:hut:"+world.toServerWorld().getRegistryKey().getValue()+":"+x+":"+z;
        UUID uuid=UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        if(loadVillageState(key)!=null || world.toServerWorld().getEntity(uuid)!=null)return;
        var witch=EntityType.WITCH.create(world.toServerWorld(),SpawnReason.STRUCTURE);
        if(witch==null)return;
        witch.setUuid(uuid);
        witch.refreshPositionAndAngles(x,y,z,0,0);
        witch.setPersistent();
        if(world.spawnEntity(witch))saveVillageState(key,"1");
    }
    public void spawnVillager(int x,int y,int z,int profession,String key){
        if(!isInside(x,y,z))return;
        UUID uuid=UUID.nameUUIDFromBytes(("pe_worldgen:"+world.toServerWorld().getRegistryKey().getValue()+":"+key).getBytes(StandardCharsets.UTF_8));
        if(world.toServerWorld().getEntity(uuid)!=null)return;
        if(profession==-1) {
            var zombie=EntityType.ZOMBIE_VILLAGER.create(world.toServerWorld(),SpawnReason.STRUCTURE);
            if(zombie!=null) {
                zombie.setUuid(uuid);
                zombie.refreshPositionAndAngles(x+0.5,y,z+0.5,0,0);
                zombie.setPersistent();
                world.spawnEntity(zombie);
            }
            return;
        }
        VillagerEntity villager=EntityType.VILLAGER.create(world.toServerWorld(),SpawnReason.STRUCTURE);
        if(villager==null)throw new IllegalStateException("Cannot construct villager");
        villager.setUuid(uuid);
        villager.refreshPositionAndAngles(x+0.5,y,z+0.5,0,0);
        var job=switch(profession){case 0->VillagerProfession.FARMER;case 1->VillagerProfession.LIBRARIAN;case 2->VillagerProfession.CLERIC;case 3->VillagerProfession.ARMORER;case 4->VillagerProfession.BUTCHER;default->throw new IllegalArgumentException("Unknown PE profession "+profession);};
        villager.setVillagerData(villager.getVillagerData().withProfession(world.getRegistryManager(),job));
        villager.setPersistent();
        world.spawnEntity(villager);
    }
    public String loadVillageState(String key){return persistent().get(key);}
    public void saveVillageState(String key,String value){persistent().put(key,value);}
    private PeVillageState persistent(){
        var manager=world.toServerWorld().getPersistentStateManager();
        synchronized(manager){return manager.getOrCreate(PeVillageState.TYPE);}
    }
    private static Item item(int id){return switch(id){
        case 265->Items.IRON_INGOT;case 297->Items.BREAD;case 296->Items.WHEAT;case 289->Items.GUNPOWDER;
        case 287->Items.STRING;case 325->Items.BUCKET;case 388->Items.EMERALD;case 266->Items.GOLD_INGOT;
        case 260->Items.APPLE;case 257->Items.IRON_PICKAXE;case 267->Items.IRON_SWORD;case 307->Items.IRON_CHESTPLATE;
        case 306->Items.IRON_HELMET;case 308->Items.IRON_LEGGINGS;case 309->Items.IRON_BOOTS;case 49->Items.OBSIDIAN;
        case 6->Items.OAK_SAPLING;case 351->Items.INK_SAC;case 329->Items.SADDLE;
        case 340->Items.BOOK;case 339->Items.PAPER;
        case 264->Items.DIAMOND;case 352->Items.BONE;case 367->Items.ROTTEN_FLESH;case 262->Items.ARROW;
        case 417->Items.IRON_HORSE_ARMOR;case 418->Items.GOLDEN_HORSE_ARMOR;case 419->Items.DIAMOND_HORSE_ARMOR;
        case 283->Items.GOLDEN_SWORD;case 315->Items.GOLDEN_CHESTPLATE;case 259->Items.FLINT_AND_STEEL;case 372->Items.NETHER_WART;
        case 403->Items.ENCHANTED_BOOK;
        default->throw new IllegalArgumentException("Unmapped PE inventory item "+id);
    };}
}
