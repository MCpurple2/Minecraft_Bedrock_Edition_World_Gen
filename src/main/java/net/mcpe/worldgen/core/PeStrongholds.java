package net.mcpe.worldgen.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mcpe.worldgen.core.PeVillageLayout.Box;

/** Native three-position stronghold discovery and saved per-chunk postProcess. */
public final class PeStrongholds {
    private static final Pattern NUMBER=Pattern.compile("-?\\d+");
    private final long seed;
    private final PeBiomes biomes;
    private final PeRelease release;
    private final int multiplierX,multiplierZ;
    private final String prefix;
    private int[][] positions;
    private final Start[] starts=new Start[3];
    private boolean storageLoaded;

    private static final class Start {
        final PeStrongholdLayout layout;final boolean initialRandom;
        final Set<Integer> processed=new LinkedHashSet<>();
        Start(PeStrongholdLayout layout,boolean initialRandom){this.layout=layout;this.initialRandom=initialRandom;}
        boolean intersects(Box clip){for(var p:layout.pieces())if(p.box.intersects(clip))return true;return false;}
    }
    public PeStrongholds(long worldSeed) {
        this(worldSeed, new PeBiomes(worldSeed, false),PeRelease.V0_11_1);
    }
    PeStrongholds(long worldSeed, PeBiomes biomes, PeRelease release) {
        seed=Integer.toUnsignedLong((int)worldSeed);this.biomes=biomes;this.release=release;
        PeRandom r=new PeRandom(seed);multiplierX=(r.nextRawInt()>>>2)*2+1;multiplierZ=(r.nextRawInt()>>>2)*2+1;
        prefix="pe0111:stronghold:v1:"+Long.toUnsignedString(seed)+":";
    }
    private boolean allowedRaw(int x,int z){return PeVillagePlacement.isAllowedBiome(biomes.rawArea(x,z,1,1)[0],release);}
    private int[][] findPositions(PeRandom r){return PeStrongholdPositions.find(seed,r,this::allowedRaw);}

    public synchronized void populate(PePopulationWorld world,PeRandom populationRandom,int cx,int cz) {
        load(world);
        for(int x=cx-8;x<=cx+8;x++)for(int z=cz-8;z<=cz+8;z++) {
            boolean wasCold=positions==null;
            PeRandom r=null;
            if(wasCold){r=normalRandom(x,z);positions=findPositions(r);world.saveVillageState(prefix+"positions",java.util.Arrays.deepToString(positions));}
            for(int i=0;i<3;i++)if(positions[i][0]==x&&positions[i][1]==z&&starts[i]==null) {
                if(r==null)r=normalRandom(x,z);
                starts[i]=new Start(PeStrongholdLayout.create(r,x,z),wasCold);
                world.saveVillageState(prefix+"start:"+i,serialize(starts[i]));
            }
        }
        Box clip=new Box(cx*16,1,cz*16,cx*16+15,512,cz*16+15);
        int hash=cx*0x1f1f1f1f^cz;
        for(int i=0;i<starts.length;i++) {
            Start start=starts[i];if(start==null||start.processed.contains(hash)||!start.intersects(clip))continue;
            for(var piece:start.layout.pieces())if(piece.box.intersects(clip)) {
                if(piece.type==PeStrongholdLayout.Type.LIBRARY)PeStrongholdSpecial.paint(piece,clip,world,populationRandom,true);
                else if(piece.type==PeStrongholdLayout.Type.PORTAL_ROOM)PeStrongholdSpecial.paint(piece,clip,world,populationRandom,false);
                else if(!PeStrongholdRooms.paint(piece,clip,world,populationRandom))throw new IllegalStateException("Unexpected native stronghold paint failure");
                world.saveVillageState(prefix+"start:"+i,serialize(start));
            }
            start.processed.add(hash);world.saveVillageState(prefix+"start:"+i,serialize(start));
        }
    }
    private PeRandom normalRandom(int x,int z){
        PeRandom r=new PeRandom((x*multiplierX+z*multiplierZ)^(int)seed);
        r.nextRawInt(); // StructureFeature::addFeature advances once before isFeatureChunk.
        return r;
    }
    private void load(PePopulationWorld world) {
        if(storageLoaded)return;
        String saved=world.loadVillageState(prefix+"positions");
        if(saved!=null) {
            List<Integer> values=new ArrayList<>();Matcher m=NUMBER.matcher(saved);while(m.find())values.add(Integer.parseInt(m.group()));
            if(values.size()!=6)throw new IllegalStateException("Corrupt PE stronghold position state");
            positions=new int[3][2];for(int i=0;i<3;i++){positions[i][0]=values.get(i*2);positions[i][1]=values.get(i*2+1);}
            for(int i=0;i<3;i++) {
                String state=world.loadVillageState(prefix+"start:"+i);if(state==null)continue;
                boolean initial=state.startsWith("{\"version\":1,\"initial\":1,");
                PeRandom r=initial?new PeRandom(0):normalRandom(positions[i][0],positions[i][1]);
                if(initial)findPositions(r);
                Start start=new Start(PeStrongholdLayout.create(r,positions[i][0],positions[i][1]),initial);
                restore(start,state);starts[i]=start;
            }
        }
        storageLoaded=true;
    }
    private static String serialize(Start start) {
        StringBuilder s=new StringBuilder("{\"version\":1,\"initial\":").append(start.initialRandom?1:0).append(",\"generated\":[");
        boolean first=true;for(var p:start.layout.pieces()) {
            if(!first)s.append(',');first=false;s.append('[');
            for(int i=0;i<p.generated.length;i++){if(i>0)s.append(',');s.append(p.generated[i]?1:0);}s.append(']');
        }
        s.append("],\"processed\":[");first=true;
        for(int hash:start.processed){if(!first)s.append(',');first=false;s.append(hash);}
        return s.append("]}").toString();
    }
    private static void restore(Start start,String state) {
        if(!state.startsWith("{\"version\":1,"))throw new IllegalStateException("Unsupported PE stronghold state");
        String marker="],\"processed\":[";int a=state.indexOf("\"generated\":[")+13,b=state.indexOf(marker);
        if(a<13||b<a)throw new IllegalStateException("Malformed PE stronghold state");
        Matcher m=NUMBER.matcher(state.substring(a,b));
        for(var p:start.layout.pieces())for(int i=0;i<p.generated.length;i++) {
            if(!m.find())throw new IllegalStateException("Truncated PE stronghold state");p.generated[i]=Integer.parseInt(m.group())!=0;
        }
        if(m.find())throw new IllegalStateException("PE stronghold piece count changed");
        m=NUMBER.matcher(state.substring(b+marker.length()));while(m.find())start.processed.add(Integer.parseInt(m.group()));
    }
}
