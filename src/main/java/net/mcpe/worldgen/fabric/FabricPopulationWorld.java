package net.mcpe.worldgen.fabric;

import net.mcpe.worldgen.core.PePopulationWorld;
import net.mcpe.worldgen.core.PeWorldGenerator;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.LightType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
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
    private final StructureWorldAccess world;
    private final PeWorldGenerator core;
    private final boolean old;
    private final Map<Long,int[]> biomeAreas=new HashMap<>();
    private final Map<Long,Integer> columnHeights=new HashMap<>();
    FabricPopulationWorld(StructureWorldAccess world,PeWorldGenerator core,boolean old){this.world=world;this.core=core;this.old=old;}
    @Override public boolean isInside(int x,int y,int z){return y>=0&&y<128&&(!old||(x>=-128&&x<128&&z>=-128&&z<128));}
    @Override public int getBlock(int x,int y,int z){
        if(y<0||y>=128)return 0;
        if(!isInside(x,y,z))return 95;
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
        BlockState state=LegacyBlocks.state(id,data);
        if(id==64||id==71){
            if((data&8)!=0){
                BlockState lower=world.getBlockState(pos.down());
                if(lower.getBlock()==state.getBlock()){
                    state=state.with(DoorBlock.FACING,lower.get(DoorBlock.FACING)).with(DoorBlock.OPEN,lower.get(DoorBlock.OPEN));
                    world.setBlockState(pos.down(),lower.with(DoorBlock.HINGE,state.get(DoorBlock.HINGE)),2);
                }
            }
        }
        world.setBlockState(pos,state,2);
        long column=columnKey(x,z);
        Integer height=columnHeights.get(column);
        if(height!=null){if(id==0&&y==height-1)columnHeights.remove(column);else if(id!=0&&y>=height)columnHeights.put(column,y+1);}
        if(id==53||id==67||id==109||id==128||id==134||id==135||id==136||id==163||id==164||id==85||id==101||id==102||id==139)
            world.getChunk(x>>4,z>>4).markBlockForPostProcessing(pos);
    }
    private static long columnKey(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    @Override public int surfaceY(int x,int z){
        long key=columnKey(x,z);Integer cached=columnHeights.get(key);if(cached!=null)return cached;
        int height=0;for(int y=127;y>=0;y--)if(getBlock(x,y,z)!=0){height=y+1;break;}
        columnHeights.put(key,height);return height;
    }
    @Override public int biomeAt(int x,int z){
        int cx=x>>4,cz=z>>4;
        int[] area=biomeAreas.computeIfAbsent(columnKey(cx,cz),ignored->core.biomeArea(cx<<4,cz<<4,16,16));
        return area[(x&15)+(z&15)*16];
    }
    @Override public boolean canSeeSky(int x,int y,int z){return y>=surfaceY(x,z);}
    @Override public int rawBrightness(int x,int y,int z){return world.getLightLevel(new BlockPos(x,y,z));}
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
            entity=new ChestBlockEntity(pos,world.getBlockState(pos));
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
        spawner.setEntityType(switch(legacyMobId){case 32->EntityType.ZOMBIE;case 34->EntityType.SKELETON;case 35->EntityType.SPIDER;case 39->EntityType.SILVERFISH;case 40->EntityType.CAVE_SPIDER;default->throw new IllegalArgumentException("Unknown PE mob "+legacyMobId);},world.getRandom());
        spawner.markDirty();
    }
    @Override public void spawnVillager(int x,int y,int z,int profession){spawnVillager(x,y,z,profession,"position:"+x+":"+y+":"+z);}
    public void spawnVillager(int x,int y,int z,int profession,String key){
        if(!isInside(x,y,z))return;
        UUID uuid=UUID.nameUUIDFromBytes(("pe_worldgen:"+world.toServerWorld().getRegistryKey().getValue()+":"+key).getBytes(StandardCharsets.UTF_8));
        if(world.toServerWorld().getEntity(uuid)!=null)return;
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
        default->throw new IllegalArgumentException("Unmapped PE inventory item "+id);
    };}
}
