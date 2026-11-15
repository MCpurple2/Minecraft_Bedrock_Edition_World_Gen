package net.mcpe.worldgen.core;

/** HellRandomLevelSource::postProcess in PE 0.12.3. */
public final class PeNetherPopulation {
    private static final int[][] NEIGHBORS = {{-1,0,0},{1,0,0},{0,0,-1},{0,0,1},{0,-1,0},{0,1,0}};
    private PeNetherPopulation() {}

    public static void decorate(PePopulationWorld world, PeRandom random, int chunkX, int chunkZ, PeNetherFortresses fortresses) {
        decorate(world,random,chunkX,chunkZ,fortresses,true);
    }
    public static void decorate(PePopulationWorld world, PeRandom random, int chunkX, int chunkZ, PeNetherFortresses fortresses,boolean quartz) {
        int x = chunkX * 16, z = chunkZ * 16;
        world.beginLiquidTicks(chunkX,chunkZ);
        if(fortresses!=null)fortresses.populate(world,random,chunkX,chunkZ);
        for (int i=0;i<8;i++)
            spring(world,x+8+random.nextInt(16),4+random.nextInt(120),z+8+random.nextInt(16),false);
        int fires = 1+random.nextInt(1+random.nextInt(10));
        for (int i=0;i<fires;i++)
            fire(world,random,x+8+random.nextInt(16),4+random.nextInt(120),z+8+random.nextInt(16));
        int clusters = random.nextInt(1+random.nextInt(10));
        for (int i=0;i<clusters;i++)
            glowstone(world,random,x+8+random.nextInt(16),4+random.nextInt(120),z+8+random.nextInt(16));
        for (int i=0;i<10;i++)
            glowstone(world,random,x+8+random.nextInt(16),random.nextInt(128),z+8+random.nextInt(16));
        for (int mushroom=39;mushroom<=40;mushroom++) {
            random.nextRawInt(); // The native one-in-one frequency check consumes a draw.
            PeVegetation.scatter(world,random,x+8+random.nextInt(16),random.nextInt(128),
                z+8+random.nextInt(16),mushroom,0,64,0);
        }
        if(quartz) for (int i=0;i<16;i++)
            PeOres.place(world,random,x+random.nextInt(16),10+random.nextInt(108),z+random.nextInt(16),153,0,14);
        for (int i=0;i<16;i++)
            spring(world,x+random.nextInt(16),10+random.nextInt(108),z+random.nextInt(16),true);
        world.finishLiquidTicks(chunkX,chunkZ);
    }

    private static void fire(PePopulationWorld world, PeRandom random, int x, int y, int z) {
        for (int i=0;i<64;i++) {
            int px=x+random.nextInt(8)-random.nextInt(8);
            int py=y+random.nextInt(4)-random.nextInt(4);
            int pz=z+random.nextInt(8)-random.nextInt(8);
            if (world.isInside(px,py,pz) && world.getBlock(px,py,pz)==0 && world.getBlock(px,py-1,pz)==87)
                world.setBlock(px,py,pz,51,0);
        }
    }

    private static void glowstone(PePopulationWorld world, PeRandom random, int x, int y, int z) {
        if (world.getBlock(x,y,z)!=0 || world.getBlock(x,y+1,z)!=87) return;
        world.setBlock(x,y,z,89,0);
        for (int i=0;i<1500;i++) {
            int px=x+random.nextInt(8)-random.nextInt(8);
            int py=y-random.nextInt(12);
            int pz=z+random.nextInt(8)-random.nextInt(8);
            if (!world.isInside(px,py,pz) || world.getBlock(px,py,pz)!=0) continue;
            int contacts=0;
            for (int[] side:NEIGHBORS)
                if (world.getBlock(px+side[0],py+side[1],pz+side[2])==89 && ++contacts>1) break;
            if (contacts==1) world.setBlock(px,py,pz,89,0);
        }
    }

    private static void spring(PePopulationWorld world, int x, int y, int z, boolean enclosed) {
        if (world.getBlock(x,y+1,z)!=87) return;
        int current=world.getBlock(x,y,z);
        if (current!=0 && current!=87) return;
        int rock=0, air=0;
        for (int i=0;i<5;i++) {
            int[] side=NEIGHBORS[i];
            int id=world.getBlock(x+side[0],y+side[1],z+side[2]);
            if(id==87) rock++;
            if(id==0) air++;
        }
        if(rock==5 || !enclosed && rock==4 && air==1) {
            world.setBlock(x,y,z,10,0);
            world.scheduleLiquidTick(x,y,z,10,0);
        }
    }
}
