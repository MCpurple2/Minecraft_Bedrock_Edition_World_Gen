package net.mcpe.worldgen.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.border.WorldBorder;

/** Initialize each old world's saved vanilla border once. Subsequent /worldborder edits persist. */
public final class PeOldWorldBorder extends PersistentState {
    private static final PersistentStateType<PeOldWorldBorder> TYPE = new PersistentStateType<>(
        "pe_worldgen_old_border", PeOldWorldBorder::new, MapCodec.unit(PeOldWorldBorder::new).codec(), DataFixTypes.LEVEL);

    public static net.mcpe.worldgen.core.PeWorldBorderLocation bounds(ServerWorld world) {
        var dimension = world.getRegistryKey();
        boolean nether = dimension.equals(net.minecraft.world.World.NETHER);
        if (!nether && !dimension.equals(net.minecraft.world.World.OVERWORLD)) return null;
        var overworld = nether ? world.getServer().getOverworld() : world;
        if (overworld == null || !(overworld.getChunkManager().getChunkGenerator() instanceof PeChunkGenerator pe)) return null;
        if (nether && !(pe.isOld() && pe.bedrock().syncNether())) return null;
        return pe.finiteBorder();
    }

    static void install(ServerWorld world) {
        var location = bounds(world);
        if (location == null) return;
        var states = world.getPersistentStateManager();
        if (states.get(TYPE) != null) return;

        WorldBorder border = world.getWorldBorder();
        border.setCenter(location.centerX(), location.centerZ());
        border.setSize(location.width());
        states.getOrCreate(TYPE).markDirty();
    }
}


