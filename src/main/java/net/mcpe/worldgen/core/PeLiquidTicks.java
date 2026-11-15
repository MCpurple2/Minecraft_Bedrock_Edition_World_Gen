package net.mcpe.worldgen.core;

import java.util.Comparator;
import java.util.PriorityQueue;

/** PE 0.11.1 LiquidTileDynamic::tick and the temporary queue drained at the end of postProcess. */
public final class PeLiquidTicks {
    private static final int[][] SIDE = {{-1,0},{1,0},{0,-1},{0,1}};
    private record Tick(int x, int y, int z, int id, int level, int time, int order) {}
    private final PriorityQueue<Tick> pending = new PriorityQueue<>(
        Comparator.comparingInt(Tick::time).thenComparingInt(Tick::order));
    private final PePopulationWorld world;
    private final int minX, maxX, minZ, maxZ;
    private int time, order;

    public PeLiquidTicks(PePopulationWorld world, int chunkX, int chunkZ) {
        this.world = world;
        minX = (chunkX - 1) * 16;
        maxX = (chunkX + 2) * 16 - 1;
        minZ = (chunkZ - 1) * 16;
        maxZ = (chunkZ + 2) * 16 - 1;
    }

    // TileTickingQueue::add checks that the source has chunks at position +/- 8.
    public void schedule(int x, int y, int z, int id, int level) {
        if (x-8 >= minX && x+8 <= maxX && z-8 >= minZ && z+8 <= maxZ)
            pending.add(new Tick(x,y,z,id,level,time + (id == 8 ? 5 : 30),order++));
    }

    public void drain() {
        while (!pending.isEmpty()) {
            Tick next = pending.remove();
            if (depth(next.x,next.y,next.z,next.id) != next.level) continue;
            time = next.time;
            tick(next.x,next.y,next.z,next.id,next.level);
        }
    }

    private boolean inside(int x,int y,int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && world.isInside(x,y,z);
    }

    private int depth(int x,int y,int z,int id) {
        if (!inside(x,y,z)) return -1;
        int block = world.getBlock(x,y,z);
        return block == id || block == id+1 ? world.getData(x,y,z) : -1;
    }

    private boolean blocked(int x,int y,int z) {
        if (!inside(x,y,z)) return true;
        int block = world.getBlock(x,y,z);
        return block == 63 || block == 68 || block == 65 || block == 83 || PeSmallStructures.solid(block);
    }

    private boolean accessible(int x,int y,int z,int id) {
        return !blocked(x,y,z) && depth(x,y,z,id) != 0;
    }

    private int slope(int x,int y,int z,int id,int distance,int previous) {
        int best = 1000;
        for (int i=0;i<4;i++) {
            if (i == (previous ^ 1)) continue;
            int nx=x+SIDE[i][0], nz=z+SIDE[i][1];
            if (!accessible(nx,y,nz,id)) continue;
            if (!blocked(nx,y-1,nz)) return distance;
            if (distance < 4) best = Math.min(best,slope(nx,y,nz,id,distance+1,i));
        }
        return best;
    }

    private void spread(int x,int y,int z,int id,int level) {
        if (blocked(x,y,z) || depth(x,y,z,id) >= 0 || depth(x,y,z,10) >= 0) return;
        world.setBlock(x,y,z,id,level);
        schedule(x,y,z,id,level);
    }

    public void tick(int x,int y,int z,int id,int level) {
        int step = id == 8 ? 1 : 2;
        if (level != 0) {
            int minimum=8, sources=0;
            for (int[] side : SIDE) {
                int neighbor=depth(x+side[0],y,z+side[1],id);
                if (neighbor < 0) continue;
                if (neighbor == 0) sources++;
                minimum=Math.min(minimum,neighbor >= 8 ? 0 : neighbor);
            }
            int next=minimum+step;
            if (next > 7) next=-1;
            int above=depth(x,y+1,z,id);
            if (above >= 0) next=above < 8 ? above+8 : above;
            if (id==8 && sources>=2 && (blocked(x,y-1,z) || depth(x,y-1,z,id)==0)) next=0;
            if (next < 0) {
                world.setBlock(x,y,z,0,0);
                return;
            }
            if (next != level) {
                world.setBlock(x,y,z,id,next);
                schedule(x,y,z,id,next);
            }
            level=next;
        }
        if (!blocked(x,y-1,z) && depth(x,y-1,z,id)<0 && depth(x,y-1,z,10)<0) {
            if (id==10 && depth(x,y-1,z,8)>=0) world.setBlock(x,y-1,z,1,0);
            else spread(x,y-1,z,id,level<8 ? level+8 : level);
            return;
        }
        if (level!=0 && !blocked(x,y-1,z)) return;
        int sideLevel=level>=8 ? 1 : level+step;
        if (sideLevel>7) return;
        int[] cost=new int[4];
        int minimum=1000;
        for (int i=0;i<4;i++) {
            int nx=x+SIDE[i][0], nz=z+SIDE[i][1];
            cost[i]=1000;
            if (accessible(nx,y,nz,id))
                cost[i]=blocked(nx,y-1,nz) ? slope(nx,y,nz,id,1,i) : 0;
            minimum=Math.min(minimum,cost[i]);
        }
        for (int i=0;i<4;i++) if (cost[i]==minimum)
            spread(x+SIDE[i][0],y,z+SIDE[i][1],id,sideLevel);
    }
}
