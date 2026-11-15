package net.mcpe.worldgen.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mcpe.worldgen.core.PeVillageLayout.Box;
import net.mcpe.worldgen.core.PeVillageLayout.Piece;

/** Native village discovery, cross-chunk processing and saved piece state.
 * Native bucket-front/global-front ordering and prime rehash growth are preserved
 * together with discovery history; piece ground offsets survive restarts. */
public final class PeVillageGenerator {
    private static final Pattern NUMBER=Pattern.compile("-?\\d+");
    private static final int[] BUCKET_COUNTS={11,23,47,97,199,409,823,1741,3739,7517,15173,30727,62233,126271,256279,520241,1056323};
    private final long worldSeed;
    private final PeBiomes biomes;
    private final PeRelease release;
    private final String prefix;
    private final Set<PeContent> flags;
    private final ArrayList<Start> order=new ArrayList<>();
    private final ArrayList<Long> discovery=new ArrayList<>();
    private boolean initialized;
    private int bucketIndex;

    private static final class Start {
        final int x,z;final long key;final PeVillageLayout layout;
        final Set<Integer> processed=new LinkedHashSet<>();
        Start(int x,int z,PeVillageLayout layout){this.x=x;this.z=z;this.key=key(x,z);this.layout=layout;}
        boolean intersects(Box clip){for(Piece p:layout.pieces())if(p.box.intersects(clip))return true;return false;}
    }
    public PeVillageGenerator(long seed){this(seed,false);}
    public PeVillageGenerator(long seed,boolean old) {
        this(seed, old, new PeBiomes(seed, old), PeRelease.V0_11_1);
    }
    PeVillageGenerator(long seed, boolean old, PeBiomes biomes, PeRelease release) {
        this(seed,old,biomes,release,PeContent.defaults(release,old));
    }
    PeVillageGenerator(long seed,boolean old,PeBiomes biomes,PeRelease release,Set<PeContent> flags) {
        this.flags=flags;
        worldSeed=Integer.toUnsignedLong((int)seed);this.biomes=biomes;this.release=release;
        prefix="pe0111:village:v1:"+Long.toUnsignedString(worldSeed)+":"+(old?"old":"infinite")+":";
    }
    /** Caller supplies the native postProcess RNG; painting advances it. */
    public synchronized void generate(PePopulationWorld world,PeRandom random,int cx,int cz) {
        initialize(world);
        PeRandom layoutRandom=new PeRandom(0);
        for(int x=cx-8;x<=cx+8;x++)for(int z=cz-8;z<=cz+8;z++) {
            // A cell's final 12 coordinates can never equal its modulo-28 offset.
            // Preserve native wrapping/truncation, including negative coordinates.
            int adjustedX=x<0?x-39:x,adjustedZ=z<0?z-39:z;
            int offsetX=x-(adjustedX/40)*40,offsetZ=z-(adjustedZ/40)*40;
            if(offsetX<0||offsetX>=28||offsetZ<0||offsetZ>=28)continue;
            long key=key(x,z);
            if(discovery.contains(key))continue;
            if(!PeVillagePlacement.isFeatureChunk(worldSeed,x,z,1,layoutRandom))continue;
            int rawBiome=biomes.rawArea((x*16+8)>>2,(z*16+8)>>2,1,1)[0];
            if(!PeContent.villageBiome(rawBiome,flags,release))continue;
            Start start=makeStart(x,z,layoutRandom);
            String saved=world.loadVillageState(prefix+key);
            if(saved!=null)restore(start,saved);
            else world.saveVillageState(prefix+key,serialize(start));
            discovery.add(key);insert(start);
            world.saveVillageState(prefix+"index",discovery.toString());
        }
        Box clip=new Box(cx*16,1,cz*16,cx*16+15,512,cz*16+15);
        int processHash=hash(cx,cz);
        for(Start start:order) {
            if(start.processed.contains(processHash)||!start.intersects(clip))continue;
            List<Piece> pieces=start.layout.pieces();
            for(int i=0;i<pieces.size();i++) {
                Piece piece=pieces.get(i);
                if(!piece.box.intersects(clip))continue;
                PeVillagePainter.paint(piece,clip,new IdentifiedWorld(world,prefix+start.key+":piece:"+i, flags.contains(PeContent.ABANDONED) && new PeRandom(worldSeed ^ start.x*341873128712L ^ start.z*132897987541L).nextInt(50)==0),random);
                world.saveVillageState(prefix+start.key,serialize(start));
            }
            start.processed.add(processHash);
            world.saveVillageState(prefix+start.key,serialize(start));
        }
    }
    private Start makeStart(int x,int z,PeRandom r) {
        int biome=biomes.biomeAt(x*16+2,z*16+2);
        var style=PeVillageLayout.Style.fromBiome(biome,PeRelease.V0_15_10);
        return new Start(x,z,PeVillageLayout.create(r,x,z,0,style));
    }
    private void initialize(PePopulationWorld world) {
        if(initialized)return;
        String saved=world.loadVillageState(prefix+"index");
        if(saved!=null) {
            Matcher numbers=NUMBER.matcher(saved);
            while(numbers.find()) {
                long key=Long.parseLong(numbers.group());
                int x=(int)(key>>32),z=(int)key;
                PeRandom r=new PeRandom(0);
                PeVillagePlacement.isFeatureChunk(worldSeed,x,z,1,r);
                Start s=makeStart(x,z,r);
                String state=world.loadVillageState(prefix+key);
                if(state==null)throw new IllegalStateException("Missing persisted village state "+prefix+key);
                restore(s,state);discovery.add(key);insert(s);
            }
        }
        initialized=true;
    }
    private void insert(Start start) {
        int count=BUCKET_COUNTS[bucketIndex];
        if(order.size()+1>count) {
            if(bucketIndex+1>=BUCKET_COUNTS.length)throw new IllegalStateException("Village table capacity exhausted");
            bucketIndex++;
            ArrayList<Start> previous=new ArrayList<>(order);order.clear();
            for(Start item:previous)insertWithoutRehash(item);
        }
        insertWithoutRehash(start);
    }
    private void insertWithoutRehash(Start start) {
        int bucket=bucket(start);
        for(int i=0;i<order.size();i++)if(bucket(order.get(i))==bucket){order.add(i,start);return;}
        order.add(0,start);
    }
    private int bucket(Start s){return (int)(Integer.toUnsignedLong(hash(s.x,s.z))%BUCKET_COUNTS[bucketIndex]);}
    private static int hash(int x,int z){return x*0x1f1f1f1f^z;}
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    private static String serialize(Start s) {
        StringBuilder b=new StringBuilder("{\"version\":1,\"pieces\":[");boolean first=true;
        for(Piece p:s.layout.pieces()) {
            if(!first)b.append(',');first=false;
            b.append('[').append(p.groundY).append(',').append(p.box.minY()).append(',').append(p.box.maxY())
                .append(',').append(p.villagersSpawned).append(',').append(p.chestCreated?1:0).append(']');
        }
        b.append("],\"processed\":[");first=true;
        for(int hash:s.processed){if(!first)b.append(',');first=false;b.append(hash);}
        return b.append("]}").toString();
    }
    private static void restore(Start s,String state) {
        if(!state.startsWith("{\"version\":1,\"pieces\":["))throw new IllegalStateException("Unsupported village state");
        String marker="],\"processed\":[";
        int a=state.indexOf("\"pieces\":[")+10,b=state.indexOf(marker);
        if(b<a)throw new IllegalStateException("Malformed village state");
        Matcher m=NUMBER.matcher(state.substring(a,b));
        for(Piece p:s.layout.pieces()) {
            int[] fields=new int[5];
            for(int i=0;i<fields.length;i++){if(!m.find())throw new IllegalStateException("Truncated village state");fields[i]=Integer.parseInt(m.group());}
            p.groundY=fields[0];p.box=new Box(p.box.minX(),fields[1],p.box.minZ(),p.box.maxX(),fields[2],p.box.maxZ());
            p.villagersSpawned=fields[3];p.chestCreated=fields[4]!=0;
        }
        if(m.find())throw new IllegalStateException("Village piece count changed");
        m=NUMBER.matcher(state.substring(b+marker.length()));
        while(m.find())s.processed.add(Integer.parseInt(m.group()));
    }
    private record IdentifiedWorld(PePopulationWorld delegate,String identity,boolean abandoned) implements PePopulationWorld {
        public int getBlock(int x,int y,int z){return delegate.getBlock(x,y,z);}
        public int getData(int x,int y,int z){return delegate.getData(x,y,z);}
        public void setBlock(int x,int y,int z,int id,int data){
            if(abandoned) {
                if(id==64 || id==50) id=0;
                else if(id==4 && Math.floorMod(x*31L+y*17L+z*13L,5)<2) id=48;
                else if(id==102 && Math.floorMod(x+y+z,5)==0) id=30;
            }
            delegate.setBlock(x,y,z,id,data);
        }
        public void tickStructureLiquid(int x,int y,int z,int id){delegate.tickStructureLiquid(x,y,z,id);}
        public int surfaceY(int x,int z){return delegate.surfaceY(x,z);}
        public int biomeAt(int x,int z){return delegate.biomeAt(x,z);}
        public boolean isInside(int x,int y,int z){return delegate.isInside(x,y,z);}
        public void setChestLoot(int x,int y,int z,long seed,String table){delegate.setChestLoot(x,y,z,seed,table);}
        public void setChestSlot(int x,int y,int z,int slot,int item,int count){delegate.setChestSlot(x,y,z,slot,item,count);}
        public void setSpawner(int x,int y,int z,int id){delegate.setSpawner(x,y,z,id);}
        public void spawnVillager(int x,int y,int z,int profession){delegate.spawnVillager(x,y,z,abandoned ? -1 : profession,identity+":"+x+":"+y+":"+z);}
        public void spawnVillager(int x,int y,int z,int profession,String key){delegate.spawnVillager(x,y,z,profession,key);}
        public String loadVillageState(String key){return delegate.loadVillageState(key);}
        public void saveVillageState(String key,String state){delegate.saveVillageState(key,state);}
    }
}
