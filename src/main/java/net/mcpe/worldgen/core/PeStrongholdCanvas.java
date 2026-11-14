package net.mcpe.worldgen.core;

import net.mcpe.worldgen.core.PeVillageLayout.Box;

/** Local coordinates and the PE StructurePiece clipping and stone-selector rules. */
final class PeStrongholdCanvas {
    final PePopulationWorld world;
    final PeRandom random;
    final Box clip, piece;
    final int dir;
    private static final int[][] STAIRS={{0,1,3,2},{2,3,0,1},{0,1,2,3},{2,3,1,0}};
    private static final int[][] TORCH={{3,1,4,2},{2,3,1,4},{4,1,3,2},{1,3,2,4}};
    private static final int[][] CHEST={{2,4,3,5},{5,2,4,3},{3,4,2,5},{4,2,5,3}};
    private static final int[][] LADDER={{3,2,4,5},{4,5,2,3},{2,3,4,5},{5,4,2,3}};

    PeStrongholdCanvas(PePopulationWorld world,PeRandom random,Box clip,Box piece,int dir){
        this.world=world;this.random=random;this.clip=clip;this.piece=piece;this.dir=dir;
    }
    int wx(int x,int z){return switch(dir){case 1->piece.maxX()-z;case 3->piece.minX()+z;default->piece.minX()+x;};}
    int wz(int x,int z){return switch(dir){case 1,3->piece.minZ()+x;case 2->piece.maxZ()-z;default->piece.minZ()+z;};}
    int wy(int y){return piece.minY()+y;}
    boolean inside(int x,int y,int z){return clip.contains(wx(x,z),wy(y),wz(x,z))&&world.isInside(wx(x,z),wy(y),wz(x,z));}
    int get(int x,int y,int z){return inside(x,y,z)?world.getBlock(wx(x,z),wy(y),wz(x,z)):0;}
    void set(int x,int y,int z,int id,int data){if(inside(x,y,z))world.setBlock(wx(x,z),wy(y),wz(x,z),id,data);}
    int orient(int id,int data){
        return switch(id){
            case 53,67,109,128 -> STAIRS[dir][data&3];
            case 50 -> data==255?5:TORCH[dir][data&3];
            case 54 -> CHEST[dir][data&3];
            case 65 -> LADDER[dir][data-2];
            case 64,71 -> dir==0&&((data&1)==0)?data^2:dir==1?(data+1)&3:dir==3?(data+3)&3:data;
            default -> data;
        };
    }
    void maybe(float chance,int x,int y,int z,int id,int data){if(random.nextFloat()<chance)set(x,y,z,id,data);}
    void box(int x0,int y0,int z0,int x1,int y1,int z1,int id,int data){
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)set(x,y,z,id,data);
    }
    void stones(int x0,int y0,int z0,int x1,int y1,int z1,boolean onlyNonAir){
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            if(onlyNonAir&&get(x,y,z)==0)continue;
            if(x!=x0&&x!=x1&&y!=y0&&y!=y1&&z!=z0&&z!=z1)set(x,y,z,0,0);
            else {
                float v=random.nextFloat();
                boolean infested=v>=0.5f&&v<0.55f;
                set(x,y,z,infested?97:98,infested||v<0.2f?2:v<0.5f?1:0);
            }
        }
    }
    void door(int type,int x,int y,int z){
        if(type==0){box(x,y,z,x+2,y+2,z,0,0);return;}
        int frame=type==2?101:98;
        box(x,y,z,x,y+2,z,frame,0);
        box(x+2,y,z,x+2,y+2,z,frame,0);
        set(x+1,y+2,z,frame,0);
        if(type==1){set(x+1,y,z,64,0);set(x+1,y+1,z,64,8);}
        if(type==2){set(x+1,y,z,0,0);set(x+1,y+1,z,0,0);}
    }
}
