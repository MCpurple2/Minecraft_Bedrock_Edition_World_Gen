package net.mcpe.worldgen.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.mcpe.worldgen.core.PeVillageLayout.Box;
import net.mcpe.worldgen.core.PeVillageLayout.Piece;
import net.mcpe.worldgen.core.PeVillageLayout.Type;

/** Legacy piece drawing operations. Each four-value metadata field follows the PE orientation order. */
public final class PeVillagePainter {
    private static final Map<String,JsonArray> TEMPLATES=new ConcurrentHashMap<>();
    private final Piece piece;
    private final Box clip;
    private final PePopulationWorld world;
    private final PeRandom random;

    private PeVillagePainter(Piece p, Box clip, PePopulationWorld w, PeRandom r) {
        this.piece=p; this.clip=clip; this.world=w; this.random=r;
    }

    public static void paint(Piece piece, Box clip, PePopulationWorld world, PeRandom random) {
        if (!piece.box.intersects(clip)) return;
        var painter=new PeVillagePainter(piece,clip,world,random);
        if (piece.type==Type.ROAD) { painter.road(); return; }
        if (piece.groundY<0 && !painter.align()) return;
        String key=piece.type.name().toLowerCase(Locale.ROOT);
        if (piece.type==Type.SIMPLE_HOUSE || piece.type==Type.SMALL_HUT) key+="_"+piece.variant;
        if (piece.type==Type.SMALL_HUT) key+="_"+piece.hutStyle;
        for (JsonElement op:TEMPLATES.computeIfAbsent(key, PeVillagePainter::load)) painter.draw(op.getAsJsonArray());
        painter.villagers();
    }

    private static JsonArray load(String name) {
        String path="/data/pe_worldgen/legacy/0_11_1/village/"+name+".json";
        var input=PeVillagePainter.class.getResourceAsStream(path);
        if (input==null) throw new IllegalStateException("Missing village template "+name);
        try (var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonArray();
        } catch (java.io.IOException ex) { throw new IllegalStateException(name,ex); }
    }

    private static boolean liquid(int id) { return id>=8 && id<=11; }
    private int top(int x,int z,boolean water) {
        for (int y=Math.min(127,world.surfaceY(x,z)-1);y>1;y--) {
            int id=world.getBlock(x,y,z);
            if (water && (id==8 || id==9)) return y+1;
            if (id!=18 && id!=161 && PeSmallStructures.solid(id)) return y+1;
        }
        return 0;
    }

    private boolean align() {
        Box b=piece.box;
        int sum=0,count=0;
        for(int x=Math.max(clip.minX(),b.minX());x<=Math.min(clip.maxX(),b.maxX());x++)
            for(int z=Math.max(clip.minZ(),b.minZ());z<=Math.min(clip.maxZ(),b.maxZ());z++) {
                sum+=Math.max(top(x,z,false),64);count++;
            }
        if(count==0)return false;
        int offset=switch(piece.type){
            case START, LIGHT_POST, FARMLAND, DOUBLE_FARMLAND -> 3;
            case SIMPLE_HOUSE, SMALL_HUT, SMITHY -> 5;
            case PIG_HOUSE, TWO_ROOM_HOUSE -> 6;
            case BOOK_HOUSE -> 8;
            case SMALL_TEMPLE -> 11;
            default -> throw new IllegalStateException("Unexpected piece "+piece.type);
        };
        piece.groundY=sum/count;
        piece.box=b.move(0,piece.groundY-b.maxY()+offset,0);
        return true;
    }

    private void road() {
        var b=piece.box;
        for(int x=Math.max(clip.minX(),b.minX());x<=Math.min(clip.maxX(),b.maxX());x++)
            for(int z=Math.max(clip.minZ(),b.minZ());z<=Math.min(clip.maxZ(),b.maxZ());z++) {
                int y=top(x,z,true)-1;
                if(y<0)continue;
                boolean bridge=liquid(world.getBlock(x,y,z));
                world.setBlock(x,y,z,piece.desert?24:bridge?5:198,piece.desert&&bridge?2:0);
            }
    }

    private void draw(JsonArray op) {
        int[] v=new int[op.size()-1];
        for(int i=0;i<v.length;i++) {
            var n=op.get(i+1);v[i]=(n.isJsonArray()?n.getAsJsonArray().get(piece.orientation):n).getAsInt();
        }
        switch(op.get(0).getAsString()) {
            case "set" -> put(v[0],v[1],v[2],v[3],v[4]);
            case "up" -> clearUp(v[0],v[1],v[2]);
            case "step" -> { if(get(v[0],v[1],v[2])==0 && get(v[0],v[1]-1,v[2])!=0) put(v[0],v[1],v[2],v[3],v[4]); }
            case "crop" -> put(v[0],v[1],v[2],piece.crops[v[3]],2+random.nextInt(6));
            case "door" -> door(v[0],v[1],v[2],v[3]);
            case "chest" -> chest(v[0],v[1],v[2]);
            case "box" -> {
                for(int y=v[1];y<=v[4];y++)for(int x=v[0];x<=v[3];x++)for(int z=v[2];z<=v[5];z++) {
                    boolean outer=x==v[0]||x==v[3]||y==v[1]||y==v[4]||z==v[2]||z==v[5];
                    put(x,y,z,outer?v[6]:v[8],outer?v[7]:v[9]);
                }
            }
            case "foundation" -> {
                for(int z=v[1];z<=v[3];z++)for(int x=v[0];x<=v[2];x++){
                    clearUp(x,v[4],z); fillDown(x,v[5],z,v[6],v[7]);
                }
            }
            default -> throw new IllegalStateException("Unknown PE piece operation "+op);
        }
    }

    private void put(int x,int y,int z,int id,int meta) {
        int wx=piece.worldX(x,z), wy=piece.worldY(y), wz=piece.worldZ(x,z);
        if(!clip.contains(wx,wy,wz) || !world.isInside(wx,wy,wz))return;
        if (piece.desert) {
            switch(id) {
                case 4,17,162,198 -> { id=24;meta=0; }
                case 5 -> { id=24;meta=2; }
                case 53,67 -> id=128;
            }
        }
        boolean source = (id==8 || id==10) && meta==0;
        boolean changed = source && (world.getBlock(wx,wy,wz)!=id || world.getData(wx,wy,wz)!=meta);
        world.setBlock(wx,wy,wz,id,meta);
        if (changed) world.tickStructureLiquid(wx,wy,wz,id);
    }

    private int get(int x,int y,int z) {
        int wx=piece.worldX(x,z), wy=piece.worldY(y), wz=piece.worldZ(x,z);
        return clip.contains(wx,wy,wz)?world.getBlock(wx,wy,wz):0;
    }

    private void clearUp(int x,int y,int z) {
        while(piece.worldY(y)<127 && get(x,y,z)!=0) put(x,y++,z,0,0);
    }

    private void fillDown(int x,int y,int z,int id,int meta) {
        if(!clip.contains(piece.worldX(x,z),piece.worldY(y),piece.worldZ(x,z)))return;
        while(piece.worldY(y)>1 && (get(x,y,z)==0 || liquid(get(x,y,z)))) put(x,y--,z,id,meta);
        if(get(x,y,z)==2)put(x,y,z,3,0);
    }

    private void door(int x,int y,int z,int dir) {
        int wx=piece.worldX(x,z),wy=piece.worldY(y),wz=piece.worldZ(x,z);
        if(!clip.contains(wx,wy,wz))return;
        int dx=dir==1?-1:dir==3?1:0, dz=dir==0?1:dir==2?-1:0;
        int left=0,right=0; boolean leftDoor=false,rightDoor=false;
        for(int h=0;h<2;h++){
            int a=world.getBlock(wx-dx,wy+h,wz-dz),b=world.getBlock(wx+dx,wy+h,wz+dz);
            left+=PeSmallStructures.solid(a)?1:0;right+=PeSmallStructures.solid(b)?1:0;
            leftDoor|=a==64;rightDoor|=b==64;
        }
        put(x,y,z,64,dir);put(x,y+1,z,64,(leftDoor&&!rightDoor)||right>left?9:8);
    }

    // Item id, weight, inclusive minimum and maximum stack size.
    private static final int[][] SMITHY_LOOT={{388,3,1,3},{265,10,1,5},{266,5,1,3},{297,15,1,3},
        {260,15,1,3},{257,5,1,1},{267,5,1,1},{307,5,1,1},{306,5,1,1},{308,5,1,1},{309,5,1,1},
        {49,5,3,7},{6,5,3,7},{351,15,3,14},{329,3,1,1}};
    private void chest(int x,int y,int z) {
        int wx=piece.worldX(x,z),wy=piece.worldY(y),wz=piece.worldZ(x,z);
        if(piece.chestCreated || !clip.contains(wx,wy,wz))return;
        piece.chestCreated=true;
        int rolls=random.nextInt(6)+3;
        if(world.getBlock(wx,wy,wz)==54)return;
        // Direction::DIRECTION_FACING[1] = west (4); side orientations map to north (2).
        put(x,y,z,54,(piece.orientation&1)==0?4:2);
        for(int draw=0;draw<rolls;draw++) {
            int ticket=random.nextInt(106);
            for(int[] it:SMITHY_LOOT) {
                ticket-=it[1];if(ticket>=0)continue;
                int count=it[2]==it[3]?it[2]:it[2]+random.nextInt(it[3]-it[2]+1);
                world.setChestSlot(wx,wy,wz,random.nextInt(27),it[0],count);break;
            }
        }
    }

    private void villagers() {
        int x,z=2,count=1,profession=0;
        switch(piece.type) {
            case SIMPLE_HOUSE,SMALL_HUT -> x=1;
            case BOOK_HOUSE -> { x=2;profession=1; }
            case SMALL_TEMPLE -> { x=2;profession=2; }
            case SMITHY -> { x=7;z=1;profession=3; }
            case PIG_HOUSE,TWO_ROOM_HOUSE -> { x=4;count=2; }
            default -> {return;}
        }
        for(int i=piece.villagersSpawned;i<count;i++) {
            int wx=piece.worldX(x+i,z), wy=piece.worldY(1), wz=piece.worldZ(x+i,z);
            if(!clip.contains(wx,wy,wz) || !world.isInside(wx,wy,wz))break;
            piece.villagersSpawned++;
            world.spawnVillager(wx,wy,wz,piece.type==Type.PIG_HOUSE && i==0?4:profession);
        }
    }
}
