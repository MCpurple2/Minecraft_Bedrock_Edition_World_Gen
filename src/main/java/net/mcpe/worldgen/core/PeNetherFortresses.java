package net.mcpe.worldgen.core;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.mcpe.worldgen.core.PeNetherFortressLayout.Piece;
import net.mcpe.worldgen.core.PeNetherFortressLayout.Type;
import net.mcpe.worldgen.core.PeVillageLayout.Box;

/** PE 0.12 fortress piece commands, clipped per native StructureFeature::postProcess. */
public final class PeNetherFortresses {
    private record Op(String kind,int[] values) {}
    private static final Map<Type,List<Op>> DRAWINGS=readDrawings();
    private static final int[][] STAIR_DATA={{0,2,0,2},{1,3,1,3},{2,1,3,0},{3,0,2,1}};
    private static final int[][] LOOT={{264,5,1,3},{265,5,1,5},{266,15,1,3},{283,5,1,1},
        {315,5,1,1},{259,5,1,1},{372,5,3,7},{329,10,1,1},{49,2,2,4}};
    private final long seed;
    private final Map<Long,Optional<PeNetherFortressLayout>> regions=new LinkedHashMap<>(32,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,Optional<PeNetherFortressLayout>> oldest) {
            return size()>128;
        }
    };
    public PeNetherFortresses(long seed){this.seed=seed;}

    public void populate(PePopulationWorld world, PeRandom random, int cx, int cz) {
        Box clip=new Box(cx*16,0,cz*16,cx*16+15,127,cz*16+15);
        for(int rx=(cx-9)>>4;rx<=(cx+9)>>4;rx++)for(int rz=(cz-9)>>4;rz<=(cz+9)>>4;rz++) {
            long key=((long)rx<<32)|(rz&0xffffffffL);
            var layout=regions.get(key);
            if(layout==null){layout=Optional.ofNullable(PeNetherFortressLayout.region(seed,rx,rz));regions.put(key,layout);}
            if(layout.isEmpty())continue;
            for(Piece p:layout.get().pieces) if(p.box().intersects(clip)) paint(p,clip,world,random);
        }
    }
    private static Map<Type,List<Op>> readDrawings() {
        var drawings=new EnumMap<Type,List<Op>>(Type.class);
        var input=PeNetherFortresses.class.getResourceAsStream("/data/pe_worldgen/legacy/nether_fortress.json");
        if(input==null)throw new IllegalStateException("Missing PE Nether fortress drawings");
        try(var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            var json=JsonParser.parseReader(reader).getAsJsonObject();
            for(var entry:json.entrySet()){
                var ops=new ArrayList<Op>();
                for(var element:entry.getValue().getAsJsonArray()){
                    var row=element.getAsJsonArray();
                    int[] values=new int[row.size()-1];
                    for(int i=0;i<values.length;i++)values[i]=row.get(i+1).getAsInt();
                    ops.add(new Op(row.get(0).getAsString(),values));
                }
                drawings.put(Type.valueOf(entry.getKey()),List.copyOf(ops));
            }
        } catch(IOException e){throw new IllegalStateException("Cannot read PE Nether fortress drawings",e);}
        return drawings;
    }
    private static void paint(Piece p,Box clip,PePopulationWorld world,PeRandom random) {
        if(p.type()==Type.BridgeEndFiller) {
            PeRandom runs=new PeRandom(p.variant());
            for(int x=0;x<5;x++)for(int y=3;y<5;y++)endRun(p,clip,world,x,y,runs.nextInt(8));
            endRun(p,clip,world,0,5,runs.nextInt(8)); endRun(p,clip,world,4,5,runs.nextInt(8));
            for(int x=0;x<5;x++)endRun(p,clip,world,x,2,runs.nextInt(5));
            for(int x=0;x<5;x++)for(int y=0;y<2;y++)endRun(p,clip,world,x,y,runs.nextInt(3));
            return;
        }
        for(Op op:DRAWINGS.get(p.type())) {
            int[] v=op.values;
            if(op.kind.equals("box")){
                for(int x=v[0];x<=v[3];x++)for(int z=v[2];z<=v[5];z++){
                    int wx=wx(p,x,z),wz=wz(p,x,z);
                    if(wx<clip.minX()||wx>clip.maxX()||wz<clip.minZ()||wz>clip.maxZ())continue;
                    for(int y=v[1];y<=v[4];y++){
                        int iy=p.box().minY()+y;
                        if(!world.isInside(wx,iy,wz) || v[10]!=0&&world.getBlock(wx,iy,wz)==0)continue;
                        int k=x==v[0]||x==v[3]||y==v[1]||y==v[4]||z==v[2]||z==v[5]?6:8;
                        world.setBlock(wx,iy,wz,v[k],v[k+1]);
                    }
                }
            } else {
                int x=wx(p,v[0],v[2]),y=p.box().minY()+v[1],z=wz(p,v[0],v[2]);
                if(!clip.contains(x,y,z))continue;
                switch(op.kind){
                    case "down" -> {
                        for(;y>1;y--){
                            int block=world.getBlock(x,y,z);
                            if(block!=0 && (block<8 || block>11))break;
                            world.setBlock(x,y,z,v[3],v[4]);
                        }
                    }
                    case "set" -> {
                        int data=v[3]==114?STAIR_DATA[v[4]][p.facing()]:v[4];
                        world.setBlock(x,y,z,v[3],data);
                        if(v[3]==10)world.scheduleLiquidTick(x,y,z,10,0);
                    }
                    case "chest" -> {if(p.variant()!=0)chest(world,random,x,y,z,p.facing());}
                    default -> throw new IllegalStateException("Unknown fortress command "+op.kind);
                }
            }
        }
        if(p.type()==Type.MonsterThrone){
            int x=wx(p,3,5),y=p.box().minY()+5,z=wz(p,3,5);
            if(clip.contains(x,y,z)){world.setBlock(x,y,z,52,0);world.setSpawner(x,y,z,43);}
        }
    }
    private static int wx(Piece p,int x,int z){
        return switch(p.facing()){case 1->p.box().maxX()-z;case 3->p.box().minX()+z;default->p.box().minX()+x;};
    }
    private static int wz(Piece p,int x,int z){
        return switch(p.facing()){case 1,3->p.box().minZ()+x;case 2->p.box().maxZ()-z;default->p.box().minZ()+z;};
    }
    private static void endRun(Piece p,Box clip,PePopulationWorld world,int x,int y,int end) {
        for(int z=0;z<=end;z++){
            int px=wx(p,x,z),py=p.box().minY()+y,pz=wz(p,x,z);
            if(clip.contains(px,py,pz))world.setBlock(px,py,pz,112,0);
        }
    }
    private static void chest(PePopulationWorld world,PeRandom random,int x,int y,int z,int facing){
        int rolls=2+random.nextInt(4);
        if(world.getBlock(x,y,z)==54)return;
        world.setBlock(x,y,z,54,(facing&1)==0?4:2);
        for(int i=0;i<rolls;i++){
            int ticket=random.nextInt(57);
            for(int[] item:LOOT){
                ticket-=item[1];
                if(ticket>=0)continue;
                int count=item[2]==item[3]?item[2]:item[2]+random.nextInt(item[3]-item[2]+1);
                world.setChestSlot(x,y,z,random.nextInt(27),item[0],count);
                break;
            }
        }
    }
}
