package net.mcpe.worldgen.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.mcpe.worldgen.core.PeVillageLayout.Box;

/** PE 0.11.1 mineshaft starts, recursive connections and chunk-clipped piece placement. */
public final class PeMineshafts {
    private final int seed, xScale, zScale;
    private final Map<Long,List<Piece>> starts=new LinkedHashMap<>(512,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,List<Piece>> e) {return size()>2048;}
    };
    private final boolean badlands;
    public PeMineshafts(long seed) { this(seed,true); }
    public PeMineshafts(long seed,boolean badlands) {
        this.badlands=badlands;
        this.seed=(int)seed;
        PeRandom r=new PeRandom(seed);
        xScale=(r.nextRawInt()>>>2)*2+1; zScale=(r.nextRawInt()>>>2)*2+1;
    }

    public synchronized void populate(PePopulationWorld world,PeRandom random,int cx,int cz) {
        Box clip=new Box(cx*16,1,cz*16,cx*16+15,127,cz*16+15);
        for(int i=-8;i<=8;i++) for(int j=-8;j<=8;j++) {
            int sx=cx+i,sz=cz+j;
            long key=(long)sx<<32|Integer.toUnsignedLong(sz);
            var parts=starts.computeIfAbsent(key,k->start(world,sx,sz));
            boolean changed=false;
            for(Piece p:parts) if(!p.removed && p.box.intersects(clip)) {
                boolean hadSpawner=p.spawnerPlaced;
                p.removed=!new PeMinePainter(world,random,clip,p).paint();
                changed |= p.removed || hadSpawner!=p.spawnerPlaced;
            }
            if(changed)world.saveVillageState(stateKey(sx,sz),encode(parts));
        }
    }

    private List<Piece> start(PePopulationWorld w,int x,int z) {
        PeRandom r=new PeRandom(seed^(x*xScale+z*zScale));
        r.nextRawInt(); // StructureFeature::addFeature discards one draw before isFeatureChunk.
        if(r.nextFloat()>=0.004f || r.nextInt(80)>=Math.max(Math.abs(x),Math.abs(z))) return List.of();
        int id=w.biomeAt(x*16,z*16);
        boolean mesa=(id>=37&&id<=39)||(id>=165&&id<=167);
        if(mesa && !badlands) return List.of();
        List<Piece> pieces=new Layout().generate(r,x,z,mesa);
        String state=w.loadVillageState(stateKey(x,z));
        if(state!=null) {
            if(state.length()!=pieces.size())throw new IllegalStateException("Invalid saved PE mineshaft size");
            for(int i=0;i<state.length();i++) {
                Piece p=pieces.get(i);
                int flags=state.charAt(i)-'0';
                if(flags<0||flags>3)throw new IllegalStateException("Invalid saved PE mineshaft flags");
                p.removed=(flags&1)!=0;
                p.spawnerPlaced=(flags&2)!=0;
            }
        }
        return pieces;
    }

    private String stateKey(int x,int z){return "pe0111:mine:v1:"+seed+":"+x+":"+z;}
    private static String encode(List<Piece> parts) {
        StringBuilder out=new StringBuilder(parts.size());
        for(Piece p:parts)out.append((char)('0'+(p.removed?1:0)+(p.spawnerPlaced?2:0)));
        return out.toString();
    }

    enum Kind { ROOM, CORRIDOR, STAIRS, CROSSING }
    static final class Piece {
        Box box;
        final Kind kind;
        final int dir,depth;
        final boolean rails,spiders,surface;
        final List<Box> exits=new ArrayList<>();
        boolean spawnerPlaced,removed;
        Piece(Kind kind,Box b,int dir,int depth,boolean rails,boolean spiders,boolean surface){
            this.kind=kind;this.box=b;this.dir=dir;this.depth=depth;
            this.rails=rails;this.spiders=spiders;this.surface=surface;
        }
    }

    static final class Layout {
        private final List<Piece> pieces=new ArrayList<>();
        private boolean mesa;
        private PeRandom r;
        List<Piece> generate(PeRandom random,int cx,int cz,boolean surface) {
            r=random;mesa=surface;
            int x=cx*16+2,z=cz*16+2;
            Box b=new Box(x,50,z,x+7+r.nextInt(6),54+r.nextInt(6),z+7+r.nextInt(6));
            Piece room=new Piece(Kind.ROOM,b,255,0,false,false,mesa);pieces.add(room);
            if(r.nextFloat()<=(mesa?0.5f:1f))
                for(int dir:new int[]{2,0,1,3}) roomExits(room,dir);
            int y0=b.minY(),y1=b.maxY();
            for(Piece p:pieces){y0=Math.min(y0,p.box.minY());y1=Math.max(y1,p.box.maxY());}
            int top=y1-y0+2;
            if(mesa)top=68+(y1-y0+1)/2;
            else if(top<53)top+=r.nextInt(53-top);
            for(Piece p:pieces)p.box=p.box.move(0,top-y1,0);
            return pieces;
        }

        private void roomExits(Piece room,int dir) {
            Box b=room.box;boolean alongX=(dir&1)==0;
            int span=alongX?b.spanX():b.spanZ();
            for(int i=0;i<span;i+=4) {
                i+=r.nextInt(span);
                if(i+2>=span)break;
                int x=alongX?b.minX()+i:dir==1?b.minX()-1:b.maxX()+1;
                int z=!alongX?b.minZ()+i:dir==2?b.minZ()-1:b.maxZ()+1;
                int y=b.minY()+1+r.nextInt(Math.max(1,b.spanY()-4));
                Piece child=add(x,y,z,dir,0);
                if(child==null)continue;
                Box c=child.box;
                room.exits.add(switch(dir) {
                    case 2->new Box(c.minX(),c.minY(),b.minZ(),c.maxX(),c.maxY(),b.minZ()+1);
                    case 0->new Box(c.minX(),c.minY(),b.maxZ()-1,c.maxX(),c.maxY(),b.maxZ());
                    case 1->new Box(b.minX(),c.minY(),c.minZ(),b.minX()+1,c.maxY(),c.maxZ());
                    default->new Box(b.maxX()-1,c.minY(),c.minZ(),b.maxX(),c.maxY(),c.maxZ());
                });
            }
        }

        private Piece add(int x,int y,int z,int dir,int parentDepth) {
            Box root=pieces.getFirst().box;
            if(parentDepth>=9 || Math.abs(root.minX()-x)>80 || Math.abs(root.minZ()-z)>80)return null;
            int pick=r.nextInt(100);
            Kind kind=pick<70?Kind.CORRIDOR:pick<80?Kind.STAIRS:Kind.CROSSING;
            Box b;
            boolean rails=false, spiders=false;
            if(kind==Kind.CROSSING) {
                int top=y+(r.nextInt(4)==0?6:2);
                int xx=switch(dir){case 0,2->x-1;case 1->x-4;default->x;};
                int zz=switch(dir){case 1,3->z-1;case 2->z-4;default->z;};
                b=new Box(xx,y,zz,xx+4,top,zz+4);
                if(collides(b))return null;
            } else if(kind==Kind.STAIRS) {
                b=oriented(x,y-5,z,y+2,dir,9);
                if(collides(b))return null;
            } else {
                b=null;
                for(int sections=2+r.nextInt(3);sections>0;sections--) {
                    Box test=oriented(x,y,z,y+2,dir,sections*5);
                    if(!collides(test)){b=test;break;}
                }
                if(b==null)return null;
                rails=r.nextInt(3)==0;
                spiders=!rails && r.nextInt(23)==0 && b.maxY()<50;
            }
            Piece p=new Piece(kind,b,dir,parentDepth+1,rails,spiders,mesa);
            pieces.add(p);
            switch(kind){
                case STAIRS -> forward(p,b.minY());
                case CROSSING -> crossingChildren(p);
                case CORRIDOR -> corridorChildren(p);
                default -> throw new IllegalStateException();
            }
            return p;
        }

        private static Box oriented(int x,int bottom,int z,int top,int d,int length) {
            return switch(d){
                case 0->new Box(x,bottom,z,x+2,top,z+length-1);
                case 1->new Box(x-length+1,bottom,z,x,top,z+2);
                case 2->new Box(x,bottom,z-length+1,x+2,top,z);
                default->new Box(x,bottom,z,x+length-1,top,z+2);
            };
        }

        private boolean collides(Box b) {
            for(Piece p:pieces)if(p.box.intersects(b))return true;
            return false;
        }

        private void forward(Piece p,int y) {
            Box b=p.box;
            int x=p.dir==1?b.minX()-1:p.dir==3?b.maxX()+1:b.minX();
            int z=p.dir==0?b.maxZ()+1:p.dir==2?b.minZ()-1:b.minZ();
            add(x,y,z,p.dir,p.depth);
        }

        private void corridorChildren(Piece p) {
            Box b=p.box;int turn=r.nextInt(4),y=b.minY()-1+r.nextInt(3);
            if(turn<2)forward(p,y);
            else if((p.dir&1)==0)
                add(turn==2?b.minX()-1:b.maxX()+1,y,p.dir==0?b.maxZ()-3:b.minZ(),turn==2?1:3,p.depth);
            else add(p.dir==1?b.minX():b.maxX()-3,y,turn==2?b.minZ()-1:b.maxZ()+1,turn==2?2:0,p.depth);
            if(p.depth>=8)return;
            boolean ns=(p.dir&1)==0;
            for(int t=(ns?b.minZ():b.minX())+3; t+2<(ns?b.maxZ():b.maxX()); t+=5) {
                int choice=r.nextInt(5); if(choice>1)continue;
                if(ns)add(choice==0?b.minX()-1:b.maxX()+1,b.minY(),t,choice==0?1:3,p.depth+1);
                else add(t,b.minY(),choice==0?b.minZ()-1:b.maxZ()+1,choice==0?2:0,p.depth+1);
            }
        }

        private void crossingChildren(Piece p) {
            for(int dir:switch(p.dir){case 0->new int[]{0,1,3};case 1->new int[]{2,0,1};case 2->new int[]{2,1,3};default->new int[]{2,0,3};})
                crossExit(p,dir,p.box.minY());
            if(p.box.spanY()>3)
                for(int dir:new int[]{2,1,3,0})if((r.nextRawInt()&(1<<27))!=0)crossExit(p,dir,p.box.minY()+4);
        }

        private void crossExit(Piece p,int d,int y) {
            Box b=p.box;
            int x=d==1?b.minX()-1:d==3?b.maxX()+1:b.minX()+1;
            int z=d==2?b.minZ()-1:d==0?b.maxZ()+1:b.minZ()+1;
            add(x,y,z,d,p.depth);
        }
    }
}
