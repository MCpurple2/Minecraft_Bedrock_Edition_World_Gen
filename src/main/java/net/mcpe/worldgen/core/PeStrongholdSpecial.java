package net.mcpe.worldgen.core;

import net.mcpe.worldgen.core.PeVillageLayout.Box;

/** PE 0.11.1 portal room and the single- and double-height libraries. */
final class PeStrongholdSpecial {
    private static final int[][] FRAMES={{0,1,2,3},{2,3,0,1},{3,0,3,0},{1,2,1,2}};

    static void paint(PeStrongholdLayout.Piece piece,Box clip,PePopulationWorld world,PeRandom random,boolean library){
        PeStrongholdCanvas c=new PeStrongholdCanvas(world,random,clip,piece.box,piece.orientation);
        if(library)library(c,piece.doorType,piece.tallLibrary);
        else portalRoom(c,piece.generated);
    }

    private static void portalRoom(PeStrongholdCanvas c,boolean[] generated){
        c.stones(0,0,0,10,7,15,false);
        c.door(2,4,1,0);
        c.stones(1,6,1,1,6,14,false);
        c.stones(9,6,1,9,6,14,false);
        c.stones(2,6,1,8,6,2,false);
        c.stones(2,6,14,8,6,14,false);
        c.stones(1,1,1,2,1,4,false);
        c.stones(8,1,1,9,1,4,false);
        c.box(1,1,1,1,1,3,11,0);
        c.box(9,1,1,9,1,3,11,0);
        c.stones(3,1,8,7,1,12,false);
        c.box(4,1,9,6,1,11,11,0);
        for(int z=3;z<15;z+=2){
            c.box(0,3,z,0,4,z,101,0);
            c.box(10,3,z,10,4,z,101,0);
        }
        for(int x=2;x<10;x+=2)c.box(x,3,15,x,4,15,101,0);
        c.stones(4,1,5,6,1,7,false);
        c.stones(4,2,6,6,2,7,false);
        c.stones(4,3,7,6,3,7,false);
        for(int x=4;x<=6;x++)for(int y=1;y<=3;y++)c.set(x,y,y+3,109,c.orient(109,3));
        for(int side=0;side<4;side++)for(int i=0;i<3;i++){
            int x=switch(side){case 0,1->4+i;case 2->3;default->7;};
            int z=switch(side){case 0->8;case 1->12;default->9+i;};
            c.set(x,3,z,120,FRAMES[side][c.dir]+(c.random.nextFloat()>0.9f?4:0));
        }
        if(!generated[0]&&c.inside(5,3,6)){
            c.set(5,3,6,52,0);
            c.world.setSpawner(c.wx(5,6),c.wy(3),c.wz(5,6),39);
            generated[0]=true;
        }
    }

    private static void library(PeStrongholdCanvas c,int door,boolean tall){
        c.stones(0,0,0,13,tall?10:5,14,false);
        c.door(door,4,1,0);
        for(int y=1;y<=4;y++)for(int x=2;x<=11;x++)for(int z=1;z<=13;z++)
            if(c.random.nextFloat()<=0.07f)c.set(x,y,z,30,0);
        for(int z=1;z<=13;z++){
            boolean pillar=(z-1)%4==0;
            int material=pillar?5:47;
            for(int x:new int[]{1,12}){
                c.box(x,1,z,x,4,z,material,0);
                if(tall)c.box(x,6,z,x,9,z,material,0);
            }
            if(pillar){c.set(2,3,z,50,c.orient(50,1));c.set(11,3,z,50,c.orient(50,3));}
        }
        for(int z=3;z<=11;z+=2)for(int x=3;x<=9;x+=3)c.box(x,1,z,x+1,3,z,47,0);
        if(tall)balcony(c);
        libraryChest(c,3,3,5,1);
        if(tall)libraryChest(c,12,8,1,3);
    }

    private static void balcony(PeStrongholdCanvas c){
        c.box(1,5,1,3,5,13,5,0);
        c.box(10,5,1,12,5,13,5,0);
        c.box(4,5,1,9,5,2,5,0);
        c.box(4,5,12,9,5,13,5,0);
        c.box(3,6,2,3,6,12,85,0);
        c.box(10,6,2,10,6,10,85,0);
        c.box(4,6,2,9,6,2,85,0);
        c.box(4,6,12,8,6,12,85,0);
        for(int[] corner:new int[][]{{9,11},{8,11},{9,10}}){
            c.set(corner[0],5,corner[1],5,0);
            c.set(corner[0],6,corner[1],85,0);
        }
        c.box(10,1,13,10,7,13,65,c.orient(65,3));
        c.box(6,7,7,7,9,7,85,0);
        for(int[] arm:new int[][]{{5,7},{8,7},{6,6},{6,8},{7,6},{7,8}}){
            c.set(arm[0],7,arm[1],85,0);
            c.set(arm[0],8,arm[1],50,5);
        }
    }

    private static void libraryChest(PeStrongholdCanvas c,int x,int y,int z,int direction){
        if(!c.inside(x,y,z))return;
        int rolls=1+(c.random.nextRawInt()&3);
        if(c.get(x,y,z)==54)return;
        c.set(x,y,z,54,c.orient(54,direction));
        for(int i=0;i<rolls;i++){
            boolean book=c.random.nextInt(40)<20;
            int count=book?1+c.random.nextInt(3):2+c.random.nextInt(6);
            c.world.setChestSlot(c.wx(x,z),c.wy(y),c.wz(x,z),c.random.nextInt(27),book?340:339,count);
        }
    }
}
