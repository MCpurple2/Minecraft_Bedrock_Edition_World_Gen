package net.mcpe.worldgen.core;

/**
 * Early TreeFeature, BirchFeature, PineFeature and SpruceFeature grow the crown before the trunk.
 */
final class PeEarlyTrees {
    private PeEarlyTrees() {}

    static void place(PeTrees.Selected type, PePopulationWorld w, PeRandom r, int x, int y, int z) {
        switch (type) {
            case OAK, BIRCH -> broadleaf(w, r, x, y, z, type == PeTrees.Selected.BIRCH);
            case PINE, SPRUCE -> conifer(w, r, x, y, z, type == PeTrees.Selected.PINE);
            default -> type.place(w, r, x, y, z); // Explicit Java content extensions.
        }
    }

    private static boolean fits(
            PePopulationWorld w, int x, int y, int z, int h, int bare, int crown, boolean conifer) {
        if (y <= 0 || y + h + 1 >= 128) return false;
        int soil = w.getBlock(x, y - 1, z);
        if (soil != 2 && soil != 3) return false;
        for (int dy = 0; dy <= h + 1; dy++) {
            int radius = conifer ? (dy < bare ? 0 : crown) : (dy == 0 ? 0 : dy >= h - 1 ? 2 : 1);
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    // Early PE's EmptyLevelChunk returns invisible bedrock outside the map.
                    if (!w.isInside(x + dx, y + dy, z + dz)) return false;
                    int id = w.getBlock(x + dx, y + dy, z + dz);
                    if (id != 0 && id != 18) return false;
                }
        }
        w.setBlock(x, y - 1, z, 3, 0);
        return true;
    }

    private static void broadleaf(
            PePopulationWorld w, PeRandom r, int x, int y, int z, boolean birch) {
        int h = (birch ? 5 : 4) + r.nextInt(3), data = birch ? 2 : 0;
        if (!fits(w, x, y, z, h, 0, 0, false)) return;
        for (int dy = -3; dy <= 0; dy++) {
            int radius = 1 - dy / 2;
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius
                            && Math.abs(dz) == radius
                            && (r.nextInt(2) == 0 || dy == 0)) continue;
                    leaf(w, x + dx, y + h + dy, z + dz, data);
                }
        }
        // The ordinary TreeFeature hardcodes spruce log metadata in 0.2.2 and 0.3.3.
        boolean spruceLog =
                !birch && (w.release() == PeRelease.V0_2_2 || w.release() == PeRelease.V0_3_3);
        trunk(w, x, y, z, h, spruceLog ? 1 : data);
    }

    private static void conifer(
            PePopulationWorld w, PeRandom r, int x, int y, int z, boolean pine) {
        int h = pine ? 7 + r.nextInt(5) : 6 + r.nextInt(4);
        int bare = pine ? h - 3 - r.nextInt(2) : 1 + r.nextInt(2);
        int max = pine ? 1 + r.nextInt(h - bare + 1) : 2 + r.nextInt(2);
        if (!fits(w, x, y, z, h, bare, max, true)) return;
        int radius = pine ? 0 : r.nextInt(2);
        int limit = 1, reset = 0;
        for (int dy = h; dy >= bare; dy--) {
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++)
                    if (radius == 0 || Math.abs(dx) != radius || Math.abs(dz) != radius)
                        leaf(w, x + dx, y + dy, z + dz, 1);
            if (pine) {
                if (radius > 0 && dy == bare + 1) radius--;
                else if (radius < max) radius++;
            } else if (radius < limit) radius++;
            else {
                radius = reset;
                reset = 1;
                limit = Math.min(max, limit + 1);
            }
        }
        trunk(w, x, y, z, h - (pine ? 1 : r.nextInt(3)), 1);
    }

    private static void leaf(PePopulationWorld w, int x, int y, int z, int data) {
        // Tile::init registered early leaves as solid for the world generator. An
        // overlapping crown retains the species of the leaf that grew there first.
        if (w.getBlock(x, y, z) != 18 && !w.solidRender(x, y, z)) w.setBlock(x, y, z, 18, data);
    }

    private static void trunk(PePopulationWorld w, int x, int y, int z, int height, int data) {
        for (int dy = 0; dy < height; dy++) {
            int id = w.getBlock(x, y + dy, z);
            if (id == 0 || id == 18) w.setBlock(x, y + dy, z, 17, data);
        }
    }
}
