package net.mcpe.worldgen.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.mcpe.worldgen.core.PeVillageLayout.Box;
import net.mcpe.worldgen.core.PeStrongholdLayout.Piece;

/** Original PE room instructions captured at StructurePiece helper boundaries.
 * Geometry and per-orientation block metadata come from ARM postProcess runs,
 * not Java Edition templates. Random stone selection, doors and torches run
 * against the actual population RNG during replay. Capture source/evidence:
 * work/stronghold/capture_rooms.py and decompiled-rooms.json. */
public final class PeStrongholdRooms {
    private PeStrongholdRooms() {}
    private static final JsonObject ROOMS=load();
    private static JsonObject load(){
        String path="/data/pe_worldgen/legacy/0_11_1/stronghold/rooms.json";
        try(InputStream in=PeStrongholdRooms.class.getResourceAsStream(path)){
            if(in==null)throw new IllegalStateException("Missing original PE stronghold room operations");
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot read PE room operations",e);}
    }
    public static boolean paint(Piece p,Box clip,PePopulationWorld world,PeRandom random){
        PeStrongholdCanvas c=new PeStrongholdCanvas(world,random,clip,p.box,p.orientation);
        if(p.type==PeStrongholdLayout.Type.FILLER_CORRIDOR){filler(c,p);return true;}
        String type=p.type==PeStrongholdLayout.Type.START?"STAIRS_DOWN":p.type.name();
        int variant=p.type==PeStrongholdLayout.Type.ROOM_CROSSING?p.variant:0;
        int mask=0;
        if(p.type==PeStrongholdLayout.Type.STRAIGHT||p.type==PeStrongholdLayout.Type.FIVE_CROSSING)
            for(int i=0;i<(p.type==PeStrongholdLayout.Type.STRAIGHT?2:4);i++)if(p.connections[i])mask|=1<<i;
        String key=type+":"+variant+":"+mask+":"+p.orientation;
        JsonArray operations=ROOMS.getAsJsonArray(key);
        if(operations==null)throw new IllegalArgumentException("No native PE room operations for "+key);
        for(JsonElement element:operations)replay(c,p,element.getAsJsonArray());
        if(p.type==PeStrongholdLayout.Type.CHEST_CORRIDOR)chest(c,p);
        return true;
    }
    private static void replay(PeStrongholdCanvas c,Piece p,JsonArray a){
        int[] v=new int[a.size()];for(int i=0;i<v.length;i++)v[i]=a.get(i).getAsInt();
        switch(v[0]){
            case 0->c.set(v[1],v[2],v[3],v[4],v[5]);
            case 1->{
                for(int y=v[2];y<=v[5];y++)for(int x=v[1];x<=v[4];x++)for(int z=v[3];z<=v[6];z++){
                    if(v[11]!=0&&c.get(x,y,z)==0)continue;
                    boolean outer=x==v[1]||x==v[4]||y==v[2]||y==v[5]||z==v[3]||z==v[6];
                    c.set(x,y,z,outer?v[7]:v[9],outer?v[8]:v[10]);
                }
            }
            case 2->c.stones(v[1],v[2],v[3],v[4],v[5],v[6],v[7]!=0);
            case 3->c.door(v[1]==-1?p.doorType:v[1],v[2],v[3],v[4]);
            case 4->c.maybe(Float.intBitsToFloat(v[1]),v[2],v[3],v[4],v[5],v[6]);
            default->throw new IllegalStateException("Unknown captured PE operation "+v[0]);
        }
    }
    /** SHFillerCorridor 002c9104: no stone-selector random draws. */
    private static void filler(PeStrongholdCanvas c,Piece p){
        int length=(p.orientation&1)==0?p.box.spanZ():p.box.spanX();
        for(int z=0;z<length;z++){
            for(int x=0;x<5;x++)c.set(x,0,z,98,0);
            for(int y=1;y<4;y++){c.set(0,y,z,98,0);for(int x=1;x<4;x++)c.set(x,y,z,0,0);c.set(4,y,z,98,0);}
            for(int x=0;x<5;x++)c.set(x,4,z,98,0);
        }
    }
    // Native SHChestCorridor::postProcess 00316140; no chest exists in any
    // SHRoomCrossing variant in PE 0.11.1 (002fb3e8), so no loot is added there.
    private static final int[][] CORRIDOR_LOOT={{388,3,1,3},{265,10,1,5},{266,5,1,3},
        {297,15,1,3},{260,15,1,3},{257,5,1,1},{267,5,1,1},{307,5,1,1},
        {306,5,1,1},{308,5,1,1},{309,5,1,1},{351,15,3,14}};
    private static void chest(PeStrongholdCanvas c,Piece p){
        if(p.generated[0]||!c.inside(3,2,3))return;
        p.generated[0]=true;int rolls=(c.random.nextRawInt()&1)+2;
        if(c.get(3,2,3)==54)return;
        c.set(3,2,3,54,c.orient(54,1));
        for(int i=0;i<rolls;i++){
            int ticket=c.random.nextInt(93);
            for(int[] item:CORRIDOR_LOOT){
                ticket-=item[1];if(ticket>=0)continue;
                int count=item[2]==item[3]?item[2]:item[2]+c.random.nextInt(item[3]-item[2]+1);
                c.world.setChestSlot(c.wx(3,3),c.wy(2),c.wz(3,3),c.random.nextInt(27),item[0],count);break;
            }
        }
    }
}
