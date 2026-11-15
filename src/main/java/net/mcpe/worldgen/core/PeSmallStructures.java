package net.mcpe.worldgen.core;

/** The two standalone room features called by PE 0.11.1 RandomLevelSource::postProcess. */
public final class PeSmallStructures {
    private PeSmallStructures() {}

    static boolean solid(int id) {
        return PeSurfacePatches.solid(id);
    }

    public static boolean dungeon(PePopulationWorld world, PeRandom random, int x, int y, int z) {
        int rx = 2 + random.nextInt(2), rz = 2 + random.nextInt(2);
        int minX = x-rx-1, maxX = x+rx+1, minZ = z-rz-1, maxZ = z+rz+1;
        int entrances = 0;
        for (int px = minX; px <= maxX; px++) for (int pz = minZ; pz <= maxZ; pz++) {
            if (!solid(world.getBlock(px,y-1,pz)) || !solid(world.getBlock(px,y+4,pz))) return false;
            if ((px == minX || px == maxX || pz == minZ || pz == maxZ)
                    && world.getBlock(px,y,pz) == 0 && world.getBlock(px,y+1,pz) == 0) entrances++;
        }
        if (entrances < 1 || entrances > 5) return false;
        for (int px = minX; px <= maxX; px++) for (int py = y+3; py >= y-1; py--) {
            for (int pz = minZ; pz <= maxZ; pz++) {
                boolean wall = px == minX || px == maxX || pz == minZ || pz == maxZ || py == y-1;
                if (!wall || (py >= 0 && !solid(world.getBlock(px,py-1,pz)))) world.setBlock(px,py,pz,0,0);
                else if (solid(world.getBlock(px,py,pz))) {
                    int id = py == y-1 && random.nextInt(4) != 0 ? 48 : 4;
                    world.setBlock(px,py,pz,id,0);
                }
            }
        }
        for (int chest = 0; chest < 2; chest++) for (int attempt = 0; attempt < 3; attempt++) {
            int px = x-rx+random.nextInt(rx*2+1), pz = z-rz+random.nextInt(rz*2+1);
            if (world.getBlock(px,y,pz) != 0) continue;
            int walls = (solid(world.getBlock(px-1,y,pz)) ? 1 : 0) + (solid(world.getBlock(px+1,y,pz)) ? 1 : 0)
                      + (solid(world.getBlock(px,y,pz-1)) ? 1 : 0) + (solid(world.getBlock(px,y,pz+1)) ? 1 : 0);
            if (walls != 1) continue;
            world.setBlock(px,y,pz,54,0);
            for (int draw = 0; draw < 8; draw++) {
                int idx = random.nextInt(55) / 10;
                int count = idx == 1 || idx == 5 ? 1 : 1 + random.nextInt(4);
                world.setChestSlot(px,y,pz,random.nextInt(27),DUNGEON_ITEMS[idx],count);
            }
            break;
        }
        world.setBlock(x,y,z,52,0);
        int mob = random.nextInt(4);
        world.setSpawner(x,y,z,mob == 0 ? 34 : mob == 3 ? 35 : 32);
        return true;
    }

    private static final int[] DUNGEON_ITEMS = {265,297,296,289,287,325};

    public static boolean lake(PePopulationWorld world, PeRandom random, int x, int y, int z, int fluidId) {
        int baseX = x-8, baseZ = z-8;
        while (y > 0 && world.getBlock(baseX,y,baseZ) == 0) y--;
        int baseY = y-4;
        boolean[] mask = new boolean[2048];
        int blobs = random.nextInt(4)+4;
        for (int i = 0; i < blobs; i++) {
            float sx = random.nextFloat()*6+3, sy = random.nextFloat()*4+2, sz = random.nextFloat()*6+3;
            float offsetX = (-1-sx*0.5f) - (14-sx)*random.nextFloat();
            float centerY = (sy*0.5f+2) + (4-sy)*random.nextFloat();
            float offsetZ = (-1-sz*0.5f) - (14-sz)*random.nextFloat();
            float ix = 1/(sx*0.5f), iy = 1/(sy*0.5f), iz = 1/(sz*0.5f);
            for (int xx=1; xx<15; xx++) for (int zz=1; zz<15; zz++) {
                float dx=(xx+offsetX)*ix, dz=(zz+offsetZ)*iz;
                for (int yy=1; yy<7; yy++) {
                    float dy=(yy-centerY)*iy;
                    if (dz*dz + dx*dx + dy*dy < 1) mask[(xx*16+zz)*8+yy] = true;
                }
            }
        }
        for (int i=0; i<mask.length; i++) {
            if (!lakeBoundary(mask,i)) continue;
            int dy=i&7, id=world.getBlock(baseX+(i>>7),baseY+dy,baseZ+((i>>3)&15));
            if (dy < 4 ? !solid(id) && id != fluidId : id >= 8 && id <= 11) return false;
        }
        for (int i=0; i<mask.length; i++) if (mask[i]) {
            int dy=i&7;
            world.setBlock(baseX+(i>>7),baseY+dy,baseZ+((i>>3)&15),dy<4 ? fluidId : 0,0);
        }
        for (int i=0; i<mask.length; i++) if (mask[i] && (i&7)>=4) {
            int px=baseX+(i>>7), pz=baseZ+((i>>3)&15), py=baseY+(i&7)-1;
            if (world.getBlock(px,py,pz)==3 && world.skyBrightness(px,py+1,pz)>0) world.setBlock(px,py,pz,2,0);
        }
        if (fluidId == 11) for (int i=0; i<mask.length; i++) {
            if (!lakeBoundary(mask,i) || ((i&7)>=4 && random.nextInt(2)==0)) continue;
            int px=baseX+(i>>7), pz=baseZ+((i>>3)&15), py=baseY+(i&7);
            if (solid(world.getBlock(px,py,pz))) world.setBlock(px,py,pz,1,0);
        }
        return true;
    }

    private static boolean lakeBoundary(boolean[] mask, int i) {
        if (mask[i]) return false;
        int x=i>>7, z=(i>>3)&15, y=i&7;
        return (x<15 && mask[i+128]) || (x>0 && mask[i-128]) || (z<15 && mask[i+8]) ||
               (z>0 && mask[i-8]) || (y<7 && mask[i+1]) || (y>0 && mask[i-1]);
    }
}
