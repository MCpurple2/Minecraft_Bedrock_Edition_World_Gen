package net.mcpe.worldgen.core;

import net.mcpe.worldgen.core.PeMineshafts.Piece;
import net.mcpe.worldgen.core.PeVillageLayout.Box;

/** Per-chunk application of the original four PE mineshaft piece types. */
final class PeMinePainter {
    private final PePopulationWorld world;
    private final PeRandom random;
    private final Box clip,b;
    private final Piece p;
    PeMinePainter(PePopulationWorld world,PeRandom random,Box clip,Piece piece) {
        this.world=world;this.random=random;this.clip=clip;p=piece;b=p.box;
    }

    boolean paint() {
        if(edgesLiquid())return false;
        switch(p.kind){
            case ROOM->room();
            case CROSSING->crossing();
            case STAIRS->stairs();
            case CORRIDOR->corridor();
        }
        return true;
    }

    private boolean edgesLiquid() {
        int x0=Math.max(clip.minX(),b.minX()-1),x1=Math.min(clip.maxX(),b.maxX()+1);
        int z0=Math.max(clip.minZ(),b.minZ()-1),z1=Math.min(clip.maxZ(),b.maxZ()+1);
        int y0=Math.max(clip.minY(),b.minY()-1),y1=Math.min(clip.maxY(),b.maxY()+1);
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++) {
            boolean edge=x==x0||z==z0||x==x1||z==z1;
            for(int y=y0;y<=y1;y++){
                int id=world.getBlock(x,y,z);if(id>=8&&id<=11)return true;
                if(!edge && y==y0 && y1>y0+1)y=y1-1;
            }
        }
        return false;
    }

    private int wx(int x,int z){return switch(p.dir){case 1->b.maxX()-z;case 3->b.minX()+z;default->b.minX()+x;};}
    private int wz(int x,int z){return switch(p.dir){case 1,3->b.minZ()+x;case 2->b.maxZ()-z;default->b.minZ()+z;};}
    private int at(int x,int y,int z){return clip.contains(x,y,z)?world.getBlock(x,y,z):0;}
    private void put(int x,int y,int z,int id,int meta){if(clip.contains(x,y,z)&&world.isInside(x,y,z))world.setBlock(x,y,z,id,meta);}
    private void local(int x,int y,int z,int id,int meta){put(wx(x,z),b.minY()+y,wz(x,z),id,meta);}
    private int get(int x,int y,int z){return at(wx(x,z),b.minY()+y,wz(x,z));}
    // StructurePiece::getBrightness increments local Y before transforming it
    // and increments world Y once more for LightLayer::Sky.
    private boolean dark(int x,int y,int z){return world.skyBrightness(x,y+2,z)<8;}
    private void floor(int x,int z){
        int y=b.minY()-1;
        if(at(x,y,z)==0 && dark(x,y,z))put(x,y,z,5,p.surface?1:0);
    }

    private void room(){
        for(int x=b.minX();x<=b.maxX();x++)for(int z=b.minZ();z<=b.maxZ();z++){
            if(at(x,b.minY(),z)!=0)put(x,b.minY(),z,3,0);
            for(int y=b.minY()+1;y<=Math.min(b.minY()+3,b.maxY());y++)put(x,y,z,0,0);
            float dx=(x-(b.minX()+b.spanX()*0.5f))*(2f/b.spanX());
            float dz=(z-(b.minZ()+b.spanZ()*0.5f))*(2f/b.spanZ());
            for(int y=b.minY()+4;y<=b.maxY();y++){
                float dy=(y-b.minY()-4)*(1f/(b.spanY()-4));
                if(dy*dy+dx*dx+dz*dz<=1.05f)put(x,y,z,0,0);
            }
        }
        for(Box e:p.exits)for(int x=e.minX();x<=e.maxX();x++)for(int z=e.minZ();z<=e.maxZ();z++)
            for(int y=e.maxY()-2;y<=e.maxY();y++)put(x,y,z,0,0);
    }

    private void crossing(){
        for(int x=b.minX();x<=b.maxX();x++)for(int z=b.minZ();z<=b.maxZ();z++){
            boolean innerX=x>b.minX()&&x<b.maxX(), innerZ=z>b.minZ()&&z<b.maxZ();
            for(int y=b.minY();y<=b.maxY();y++){
                boolean middle=b.spanY()>3&&y==b.minY()+3;
                if(middle?innerX&&innerZ:innerX||innerZ)put(x,y,z,0,0);
            }
            if((x==b.minX()+1||x==b.maxX()-1)&&(z==b.minZ()+1||z==b.maxZ()-1)&&at(x,b.maxY()+1,z)!=0)
                for(int y=b.minY();y<=b.maxY();y++)put(x,y,z,5,p.surface?1:0);
            floor(x,z);
        }
    }

    private void stairs(){
        for(int z=0;z<9;z++){
            int lower=z<2?5:z>=7?0:z==6?1:6-z;
            int upper=z<2?7:z>=7?2:9-z;
            for(int x=0;x<=2;x++)for(int y=lower;y<=upper;y++)local(x,y,z,0,0);
        }
    }

    private void corridor(){
        int length=(p.dir%2==0?b.spanZ():b.spanX()),wood=p.surface?1:0;
        for(int y=0;y<=1;y++)for(int x=0;x<3;x++)for(int z=0;z<length;z++)local(x,y,z,0,0);
        for(int x=0;x<3;x++)for(int z=0;z<length;z++)if(random.nextFloat()<=0.8f)local(x,2,z,0,0);
        if(p.spiders)for(int y=0;y<=1;y++)for(int x=0;x<3;x++)for(int z=0;z<length;z++)
            if(random.nextFloat()<=0.6f && dark(wx(x,z),b.minY()+y,wz(x,z)))local(x,y,z,30,0);
        for(int beam=2;beam<length;beam+=5){
            support(beam,wood);
            int[] offsets={0,-1,1,1,-2,-2,2,2};
            for(int k=0;k<offsets.length;k++){
                int x=(k&1)*2,z=beam+offsets[k];
                if(dark(wx(x,z),b.minY()+2,wz(x,z)) && random.nextFloat()<(k<4?0.1f:0.05f))
                    local(x,2,z-1,30,0);
            }
            random.nextRawInt();random.nextRawInt();
            if(p.spiders && !p.spawnerPlaced){
                int z=beam-1+random.nextInt(3),x=wx(1,z),wz=wz(1,z),y=b.minY();
                if(clip.contains(x,y,wz)&&dark(x,y,wz)){
                    put(x,y,wz,52,0);world.setSpawner(x,y,wz,40);p.spawnerPlaced=true;
                }
            }
        }
        for(int x=0;x<3;x++)for(int z=0;z<length;z++)floor(wx(x,z),wz(x,z));
        if(p.rails)for(int z=0;z<length;z++){
            int id=get(1,-1,z);
            if(PeSmallStructures.solid(id) && random.nextFloat()<(world.skyBrightness(wx(1,z),b.minY()+1,wz(1,z))<9?0.7f:0.9f))
                local(1,0,z,66,p.dir&1);
        }
    }

    private void support(int z,int wood){
        for(int x=wx(0,z);x<=wx(2,z);x++)for(int zz=wz(0,z);zz<=wz(2,z);zz++)
            if(world.getBlock(x,b.minY()+3,zz)==0)return;
        for(int x:new int[]{0,2})for(int y=0;y<2;y++)local(x,y,z,85,wood);
        boolean partial=random.nextInt(4)==0;
        local(0,2,z,5,wood);local(2,2,z,5,wood);
        if(!partial){
            local(1,2,z,5,wood);
            if(random.nextFloat()<0.05f)local(1,2,z-1,50,new int[]{4,1,3,2}[p.dir]);
            if(random.nextFloat()<0.05f)local(1,2,z+1,50,new int[]{3,2,4,1}[p.dir]);
        }
    }
}
