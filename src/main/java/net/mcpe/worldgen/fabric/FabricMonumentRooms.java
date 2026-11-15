package net.mcpe.worldgen.fabric;

import java.util.Collections;
import java.util.List;
import net.mcpe.worldgen.core.PeRandom;
import net.mcpe.worldgen.fabric.mixin.MonumentRoomsAccessor;
import net.minecraft.structure.OceanMonumentGenerator;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.world.StructureWorldAccess;

/** Reuses the historical monument room pieces after the PE exterior has been painted. */
public final class FabricMonumentRooms {
    private FabricMonumentRooms() {}

    public static final class RoomRandom extends CheckedRandom {
        private final PeRandom pe;
        RoomRandom(PeRandom pe) { super(0); this.pe = pe; }
        @Override public int next(int bits) { return pe.nextRawInt() >>> (32 - bits); }
        @Override public int nextInt(int bound) { return pe.nextInt(bound); }
        @Override public int nextInt() { return pe.nextRawInt(); }
        @Override public boolean nextBoolean() { return (pe.nextRawInt() & 1) != 0; }
        @Override public float nextFloat() { return pe.nextFloat(); }
    }

    public static <T> void shuffleRooms(List<T> rooms) {
        // Alpha 0.16.2 uses the process-global Android lrand48 stream for std::random_shuffle.
        // Recreate the initial stream per structure so chunk scheduling cannot affect the layout.
        long state = 0x1234abcd330eL;
        for (int i = 1; i < rooms.size(); i++) {
            state = (state * 0x5deece66dL + 11) & 0xffffffffffffL;
            Collections.swap(rooms, i, (int)((state >>> 17) % (i + 1)));
        }
    }

    static void populate(StructureWorldAccess world, int x, int z, int direction, int rotationSeed,
                         PeRandom random, int chunkX, int chunkZ) {
        var layout = new RoomRandom(new PeRandom(rotationSeed));
        layout.nextInt(); // Orientation was already drawn by OceanMonumentStart.
        var base = new OceanMonumentGenerator.Base(layout, x, z, Direction.byIndex(direction));
        var rooms = ((MonumentRoomsAccessor)base).peWorldgen$getRooms();
        var pos = new ChunkPos(chunkX, chunkZ);
        var clip = new BlockBox(pos.getStartX(), 0, pos.getStartZ(), pos.getEndX(), 127, pos.getEndZ());
        var server = world.toServerWorld();
        var placement = new RoomRandom(random);
        for (var room : rooms) {
            if (room.getBoundingBox().intersects(clip)) {
                room.generate(world, server.getStructureAccessor(), server.getChunkManager().getChunkGenerator(),
                    placement, clip, pos, new BlockPos(x,39,z));
            }
        }
    }
}
