package net.mcpe.worldgen.core;

/** Narrow legacy-world boundary shared by native population/structure ports.
 * Coordinates are global block coordinates. Y is restricted to 0..127.
 * surfaceY returns PE's skylight height, the first Y above light-blocking tiles
 * (0 for an empty column, capped at 127). */
public interface PePopulationWorld {
    default boolean usePeOreDistribution() { return true; }
    default PeRelease release() { return PeRelease.V0_11_1; }
    default void spawnHutWitch(int x, int y, int z) {}
    default void populateMonumentRooms(int x, int z, int direction, int rotationSeed, PeRandom random, int chunkX, int chunkZ) {}
    default String loadVillageState(String key) { return null; }
    default void saveVillageState(String key,String state) {
        throw new UnsupportedOperationException("Persistent PE village state is unavailable");
    }
    default void spawnVillager(int x,int y,int z,int profession,String stableKey) {
        throw new UnsupportedOperationException("Persistent PE villager identity is unavailable");
    }
    default void beginLiquidTicks(int chunkX,int chunkZ) {}
    default void finishLiquidTicks(int chunkX,int chunkZ) {}
    default void scheduleLiquidTick(int x,int y,int z,int id,int level) {}
    default void tickStructureLiquid(int x,int y,int z,int id) {}
    int getBlock(int x, int y, int z);
    void setBlock(int x, int y, int z, int id, int data);
    int surfaceY(int x, int z);
    int biomeAt(int x, int z);
    default int getData(int x, int y, int z) { return 0; }
    default boolean isInside(int x, int y, int z) { return y >= 0 && y < 128; }
    default boolean canSeeSky(int x,int y,int z) { return y>=surfaceY(x,z); }
    default int skyBrightness(int x,int y,int z) { return canSeeSky(x,y,z)?15:0; }
    default int rawBrightness(int x,int y,int z) {
        throw new UnsupportedOperationException("Native plant survival requires a light query");
    }
    default int blockBrightness(int x,int y,int z) {
        throw new UnsupportedOperationException("Native snow/ice requires block-light query");
    }
    default boolean solidRender(int x,int y,int z) {
        throw new UnsupportedOperationException("Native snow support requires solid-render query");
    }
    default boolean solidTop(int x,int y,int z) { return PeSurfacePatches.solid(getBlock(x,y,z)); }
    default int topSolidY(int x,int z,boolean includeWater) {
        for(int y=127;y>0;y--){int id=getBlock(x,y,z);if(id!=18&&id!=161&&(PeSurfacePatches.solid(id)||includeWater&&(id==8||id==9)))return y+1;}
        return 0;
    }
    /** Optional hooks; an implementation must not silently claim populated loot/entities. */
    default void setChestLoot(int x, int y, int z, long seed, String table) {
        throw new UnsupportedOperationException("PE chest loot has not been ported");
    }
    default void spawnVillager(int x, int y, int z, int profession) {
        throw new UnsupportedOperationException("PE villager population has not been ported");
    }
    default void setChestSlot(int x, int y, int z, int slot, int legacyItemId, int count) {
        throw new UnsupportedOperationException("PE chest inventory adapter is unavailable");
    }
    default void setSpawner(int x, int y, int z, int legacyMobId) {
        throw new UnsupportedOperationException("PE mob spawner adapter is unavailable");
    }
}
