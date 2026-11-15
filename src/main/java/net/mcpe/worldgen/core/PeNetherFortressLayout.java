package net.mcpe.worldgen.core;

import java.util.ArrayList;
import java.util.List;
import net.mcpe.worldgen.core.PeVillageLayout.Box;

/** PE 0.12.3 NetherBridgeFeature placement and NetherBridgeStart assembly. */
final class PeNetherFortressLayout {
    enum Type {
        BridgeStraight(-1,-3,5,10,19,30,0,true), BridgeCrossing(-8,-3,19,10,19,10,4,false),
        RoomCrossing(-2,0,7,9,7,10,4,false), StairsRoom(-2,0,7,11,7,10,3,false),
        MonsterThrone(-2,0,7,8,9,5,2,false), CastleEntrance(-5,-3,13,14,13,5,1,false),
        CastleSmallCorridorPiece(-1,0,5,7,5,25,0,true), CastleSmallCorridorCrossingPiece(-1,0,5,7,5,15,5,false),
        CastleSmallCorridorRightTurnPiece(-1,0,5,7,5,5,10,false),
        CastleSmallCorridorLeftTurnPiece(-1,0,5,7,5,5,10,false),
        CastleCorridorStairsPiece(-1,-7,5,14,10,10,3,true), CastleCorridorTBalconyPiece(-3,0,9,7,9,7,2,false),
        CastleStalkRoom(-5,-3,13,14,13,5,2,false), BridgeEndFiller(-1,-3,5,10,8,0,0,false);
        final int offsetX,offsetY,width,height,length,weight,limit;
        final boolean repeat;
        Type(int ox,int oy,int w,int h,int l,int weight,int limit,boolean repeat) {
            this.offsetX=ox;this.offsetY=oy;this.width=w;this.height=h;this.length=l;
            this.weight=weight;this.limit=limit;this.repeat=repeat;
        }
    }
    record Piece(Type type, Box box, int facing, int depth, int variant) {}
    final List<Piece> pieces=new ArrayList<>();
    private final List<Piece> pending=new ArrayList<>();
    private final int[] counts=new int[Type.values().length];
    private final PeRandom random;
    private Type previous;

    /** The successful isFeatureChunk call leaves this same MT advanced by four draws. */
    static PeNetherFortressLayout region(long seed,int regionX,int regionZ) {
        PeRandom random=new PeRandom((int)seed ^ regionX ^ (regionZ<<4));
        random.nextRawInt();
        if(random.nextInt(3)!=0) return null;
        int cx=regionX*16+4+random.nextInt(8), cz=regionZ*16+4+random.nextInt(8);
        return new PeNetherFortressLayout(random,cx,cz);
    }
    private PeNetherFortressLayout(PeRandom random,int cx,int cz) {
        this.random=random;
        int x=cx*16+2,z=cz*16+2;
        Piece start=new Piece(Type.BridgeCrossing,new Box(x,64,z,x+18,73,z+18),random.nextInt(4),0,0);
        pieces.add(start);
        expand(start);
        while(!pending.isEmpty()) expand(pending.remove(random.nextInt(pending.size())));
        int min=pieces.stream().mapToInt(p->p.box.minY()).min().orElseThrow();
        int max=pieces.stream().mapToInt(p->p.box.maxY()).max().orElseThrow();
        int range=22-(max-min), shift=48-min+(range>1?random.nextInt(range):0);
        pieces.replaceAll(p->new Piece(p.type,p.box.move(0,shift,0),p.facing,p.depth,p.variant));
    }
    private void expand(Piece p) {
        switch(p.type) {
            case BridgeCrossing -> { forward(p,8,3,false); side(p,false,3,8,false); side(p,true,3,8,false); }
            case BridgeStraight -> forward(p,1,3,false);
            case RoomCrossing -> { forward(p,2,0,false); side(p,false,0,2,false); side(p,true,0,2,false); }
            case StairsRoom -> side(p,true,6,2,false);
            case CastleEntrance -> forward(p,5,3,true);
            case CastleStalkRoom -> {forward(p,5,3,true); forward(p,5,11,true);}
            case CastleSmallCorridorPiece, CastleCorridorStairsPiece -> forward(p,1,0,true);
            case CastleSmallCorridorCrossingPiece -> {forward(p,1,0,true);side(p,false,0,1,true);side(p,true,0,1,true);}
            case CastleSmallCorridorRightTurnPiece -> side(p,true,0,1,true);
            case CastleSmallCorridorLeftTurnPiece -> side(p,false,0,1,true);
            case CastleCorridorTBalconyPiece -> {
                int offset=(p.facing==1 || p.facing==2)?5:1;
                side(p,false,0,offset,random.nextInt(8)!=0);
                side(p,true,0,offset,random.nextInt(8)!=0);
            }
            default -> {}
        }
    }
    private void forward(Piece p,int across,int up,boolean castle) {
        Box b=p.box;
        int x=switch(p.facing){case 1->b.minX()-1;case 3->b.maxX()+1;default->b.minX()+across;};
        int z=switch(p.facing){case 0->b.maxZ()+1;case 2->b.minZ()-1;default->b.minZ()+across;};
        add(p,x,b.minY()+up,z,p.facing,castle);
    }
    private void side(Piece p,boolean right,int up,int along,boolean castle) {
        Box b=p.box;
        if((p.facing&1)==0) add(p,right?b.maxX()+1:b.minX()-1,b.minY()+up,b.minZ()+along,right?3:1,castle);
        else add(p,b.minX()+along,b.minY()+up,right?b.maxZ()+1:b.minZ()-1,right?0:2,castle);
    }
    private void add(Piece parent,int x,int y,int z,int facing,boolean castle) {
        Box start=pieces.get(0).box;
        if(Math.abs(start.minX()-x)>112 || Math.abs(start.minZ()-z)>112) {
            create(Type.BridgeEndFiller,x,y,z,facing,parent.depth); // This branch discards the created piece.
            return;
        }
        int depth=parent.depth+1;
        Type[] types=Type.values();
        int from=castle?6:0, to=castle?13:6, total=0;
        boolean expandable=false;
        for(int i=from;i<to;i++) {
            Type t=types[i];
            if(t.limit!=0 && counts[i]>=t.limit)continue;
            total+=t.weight;
            expandable|=t.limit!=0;
        }
        Piece piece=null;
        for(int attempt=0;attempt<5 && expandable && depth<=30 && piece==null;attempt++) {
            int ticket=random.nextInt(total);
            for(int i=from;i<to;i++) {
                Type t=types[i];
                if(t.limit!=0 && counts[i]>=t.limit) continue;
                ticket-=t.weight;
                if(ticket>=0)continue;
                if(t==previous && !t.repeat)break;
                piece=create(t,x,y,z,facing,depth);
                if(piece!=null){counts[i]++;previous=t;break;}
            }
        }
        if(piece==null) piece=create(Type.BridgeEndFiller,x,y,z,facing,depth);
        if(piece!=null){pieces.add(piece);pending.add(piece);}
    }
    private Piece create(Type t,int x,int y,int z,int face,int depth) {
        Box box=PeStrongholdLayout.oriented(x,y,z,t.offsetX,t.offsetY,t.width,t.height,t.length,face);
        if(box.minY()<=10)return null;
        for(Piece piece:pieces)if(box.intersects(piece.box))return null;
        int variant=switch(t) {
            case BridgeEndFiller->random.nextInt();
            case CastleSmallCorridorLeftTurnPiece,CastleSmallCorridorRightTurnPiece->random.nextInt(3)==0?1:0;
            default->0;
        };
        return new Piece(t,box,face,depth,variant);
    }
}
