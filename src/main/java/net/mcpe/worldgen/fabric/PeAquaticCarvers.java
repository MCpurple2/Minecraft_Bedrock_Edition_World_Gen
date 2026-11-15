package net.mcpe.worldgen.fabric;

import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

/** Seeded paths shared across chunk edges. Only the target chunk is written. */
public final class PeAquaticCarvers {
    private PeAquaticCarvers() {}

    public static void carve(long seed, Chunk chunk, int[] biomes, boolean caves, boolean canyons) {
        if (!caves && !canyons) return;
        if (caves) paths(seed ^ 0x619ad138L, chunk, biomes, false);
        if (canyons) paths(seed ^ 0x45b1ae37L, chunk, biomes, true);
    }

    private static void paths(long seed, Chunk chunk, int[] biomes, boolean canyon) {
        var cp = chunk.getPos();
        // Maximum path length is 80 blocks. A 3x3 neighborhood of 128-block cells suffices.
        int cellX = Math.floorDiv(cp.x, 8), cellZ = Math.floorDiv(cp.z, 8);
        for (int gx = cellX-1; gx <= cellX+1; gx++) for (int gz = cellZ-1; gz <= cellZ+1; gz++) {
            Random random = new Random(seed ^ gx * 341873128712L ^ gz * 132897987541L);
            if (canyon && random.nextInt(3) != 0) continue;
            double x = gx*128 + random.nextInt(128), z = gz*128 + random.nextInt(128);
            double y = biomes == null ? 38 + random.nextInt(48) : 32 + random.nextInt(24);
            double angle = random.nextDouble() * Math.PI * 2;
            int length = 32 + random.nextInt(49);
            double phase = random.nextDouble()*Math.PI*2;
            for (int i=0; i<length; i+=2) {
                double envelope = Math.sin(Math.PI * i / length);
                double radius = 1.5 + envelope * (canyon ? 3 : 2.5);
                double h = canyon ? 3 + 12*envelope : radius*0.8;
                if (x+radius >= cp.getStartX() && x-radius <= cp.getEndX()+1 &&
                    z+radius >= cp.getStartZ() && z-radius <= cp.getEndZ()+1)
                    cut(chunk, biomes, x, y+3*Math.sin(phase+i*0.065), z, radius, h);
                x += 2*Math.cos(angle);
                z += 2*Math.sin(angle);
                angle += (random.nextDouble()-0.5)*(canyon ? 0.18 : 0.55);
            }
        }
    }

    private static void cut(Chunk chunk, int[] biomes, double cx, double cy, double cz, double radius, double height) {
        var cp = chunk.getPos();
        var pos = new BlockPos.Mutable();
        boolean ocean = biomes != null;
        int top = Math.min(ocean ? 62 : 125, (int)Math.ceil(cy+height));
        for (int x=Math.max(cp.getStartX(),(int)Math.floor(cx-radius)); x<=Math.min(cp.getEndX(),(int)Math.ceil(cx+radius)); x++)
            for (int z=Math.max(cp.getStartZ(),(int)Math.floor(cz-radius)); z<=Math.min(cp.getEndZ(),(int)Math.ceil(cz+radius)); z++) {
                if (ocean && !PeOceanClimate.ocean(biomes[(z&15)*16+(x&15)])) continue;
                double dx=(x+0.5-cx)/radius, dz=(z+0.5-cz)/radius;
                for (int y=Math.max(2,(int)Math.floor(cy-height)); y<=top; y++) {
                    double dy=(y+0.5-cy)/height;
                    if (dx*dx+dy*dy+dz*dz >= 1) continue;
                    BlockState block = chunk.getBlockState(pos.set(x,y,z));
                    boolean replace = ocean ? block.isIn(BlockTags.BASE_STONE_OVERWORLD) || block.isOf(Blocks.GRAVEL)
                        || block.isOf(Blocks.SAND) || block.isOf(Blocks.SANDSTONE) || block.isOf(Blocks.DIRT)
                        : block.isIn(BlockTags.BASE_STONE_NETHER) || block.isOf(Blocks.SOUL_SAND) || block.isOf(Blocks.SOUL_SOIL);
                    if (replace) chunk.setBlockState(pos, (ocean ? Blocks.WATER : y<32 ? Blocks.LAVA : Blocks.CAVE_AIR).getDefaultState());
                }
            }
    }
}
