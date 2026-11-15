package net.mcpe.worldgen.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.border.WorldBorder;

/** Initialize each old world's saved vanilla border once. Subsequent /worldborder edits persist. */
final class PeOldWorldBorder extends PersistentState {
    private static final PersistentStateType<PeOldWorldBorder> TYPE = new PersistentStateType<>(
        "pe_worldgen_old_border", PeOldWorldBorder::new, MapCodec.unit(PeOldWorldBorder::new).codec(), DataFixTypes.LEVEL);

    static void install(ServerWorld world) {
        if (!(world.getChunkManager().getChunkGenerator() instanceof PeChunkGenerator generator)
                || !generator.isOld()) return;
        var states = world.getPersistentStateManager();
        if (states.get(TYPE) != null) return;

        var location = generator.finiteBorder();
        WorldBorder border = world.getWorldBorder();
        border.setCenter(location.centerX(), location.centerZ());
        border.setSize(net.mcpe.worldgen.core.PeWorldBorderLocation.WIDTH);
        states.getOrCreate(TYPE).markDirty();
    }
}


