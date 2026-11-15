package net.mcpe.worldgen.core;

import java.util.ArrayList;
import java.util.List;
import net.mcpe.worldgen.core.PeVillageLayout.Box;

/** PE 0.11.1 StrongholdStart and its native, Random-by-value piece pipeline. */
public final class PeStrongholdLayout {
    public enum Type {START,STRAIGHT,PRISON_HALL,LEFT_TURN,RIGHT_TURN,ROOM_CROSSING,STRAIGHT_STAIRS_DOWN,STAIRS_DOWN,FIVE_CROSSING,CHEST_CORRIDOR,LIBRARY,PORTAL_ROOM,FILLER_CORRIDOR}
    public static final class Piece {
        public final Type type;public Box box;public final int orientation,depth,doorType,variant;
        public final boolean[] connections;public final boolean tallLibrary;public final boolean[] generated=new boolean[4];
        private Piece(Type t,Box b,int o,int d,int door,int variant,boolean[] connections){type=t;box=b;orientation=o;depth=d;doorType=door;this.variant=variant;this.connections=connections;tallLibrary=t==Type.LIBRARY&&b.spanY()>6;}
        public int worldX(int x,int z){return switch(orientation){case 0,2->box.minX()+x;case 1->box.maxX()-z;case 3->box.minX()+z;default->x;};}
        public int worldY(int y){return orientation==255?y:box.minY()+y;}
        public int worldZ(int x,int z){return switch(orientation){case 0->box.minZ()+z;case 1,3->box.minZ()+x;case 2->box.maxZ()-z;default->z;};}
    }
    private static final class Weight {final Type type;final int weight,limit,min;int used;Weight(Type t,int w,int l,int m){type=t;weight=w;limit=l;min=m;}}
    private final ArrayList<Piece> all=new ArrayList<>(),pending=new ArrayList<>();
    private final ArrayList<Weight> pool=new ArrayList<>();
    private final PeRandom random;private Piece start;private Type previous,forced;
    private PeStrongholdLayout(PeRandom r){random=r;}
    public List<Piece> pieces(){return List.copyOf(all);}
    public static PeStrongholdLayout create(PeRandom random,int chunkX,int chunkZ){var layout=new PeStrongholdLayout(random);layout.initialize(chunkX,chunkZ);return layout;}
    private void initialize(int cx,int cz){
        int x=cx*16+2,z=cz*16+2;
        start=new Piece(Type.START,new Box(x,64,z,x+4,74,z+4),random.nextInt(4),0,0,0,new boolean[4]);all.add(start);
        pool.add(new Weight(Type.STRAIGHT,40,0,0));pool.add(new Weight(Type.PRISON_HALL,5,5,0));
        pool.add(new Weight(Type.LEFT_TURN,20,0,0));pool.add(new Weight(Type.RIGHT_TURN,20,0,0));
        pool.add(new Weight(Type.ROOM_CROSSING,10,6,0));pool.add(new Weight(Type.STRAIGHT_STAIRS_DOWN,5,5,0));
        pool.add(new Weight(Type.STAIRS_DOWN,5,5,0));pool.add(new Weight(Type.FIVE_CROSSING,5,4,0));
        pool.add(new Weight(Type.CHEST_CORRIDOR,5,4,0));pool.add(new Weight(Type.LIBRARY,10,2,5));pool.add(new Weight(Type.PORTAL_ROOM,10,1,6));
        children(start);
        while(!pending.isEmpty())children(pending.remove(random.nextInt(pending.size())));
        int minY=Integer.MAX_VALUE,maxY=Integer.MIN_VALUE;
        for(Piece p:all){minY=Math.min(minY,p.box.minY());maxY=Math.max(maxY,p.box.maxY());}
        int top=maxY-minY+2;if(top<58)top+=random.nextInt(58-top);int shift=top-maxY;
        for(Piece p:all)p.box=p.box.move(0,shift,0);
    }
    private Piece collision(Box b){for(Piece p:all)if(p.box.intersects(b))return p;return null;}
    static Box oriented(int x,int y,int z,int ox,int oy,int width,int height,int length,int facing){
        return switch(facing){
            case 1->new Box(x-length+1,y+oy,z+ox,x,y+oy+height-1,z+ox+width-1);
            case 2->new Box(x+ox,y+oy,z-length+1,x+ox+width-1,y+oy+height-1,z);
            case 3->new Box(x,y+oy,z+ox,x+length-1,y+oy+height-1,z+ox+width-1);
            default->new Box(x+ox,y+oy,z,x+ox+width-1,y+oy+height-1,z+length-1);
        };
    }
    private Piece add(Piece parent,int x,int y,int z,int facing){
        if(parent.depth>=51||Math.abs(x-start.box.minX())>=113||Math.abs(z-start.box.minZ())>=113)return null;
        // This copy is intentional: the original C++ method takes all 0x9d0 bytes by value.
        Piece p=choose(random.copy(),x,y,z,facing,parent.depth+1);
        if(p!=null){all.add(p);pending.add(p);}return p;
    }
    private Piece choose(PeRandom r,int x,int y,int z,int facing,int depth){
        if(forced!=null){Type t=forced;forced=null;Piece p=create(t,r,x,y,z,facing,depth);if(p!=null)return p;}
        int total=0;boolean available=false;
        for(Weight w:pool){total+=w.weight;if(w.limit>0&&w.used<w.limit)available=true;}
        if(!available||total<=0)return null;
        for(int attempt=0;attempt<5;attempt++){
            int selected=r.nextInt(total);
            for(int i=0;i<pool.size();i++){
                Weight w=pool.get(i);selected-=w.weight;if(selected>=0)continue;
                if((w.limit!=0&&w.used>=w.limit)||depth<w.min||(w.type==previous&&pool.size()>1))break;
                Piece p=create(w.type,r,x,y,z,facing,depth);
                if(p!=null){w.used++;previous=w.type;if(w.limit!=0&&w.used>=w.limit)pool.remove(i);return p;}
            }
        }
        Box probe=oriented(x,y,z,-1,-1,5,5,4,facing);Piece hit=collision(probe);
        if(hit!=null&&hit.box.minY()==probe.minY()){
            for(int length=3;length>0;length--)if(!hit.box.intersects(oriented(x,y,z,-1,-1,5,5,length-1,facing))){
                Box box=oriented(x,y,z,-1,-1,5,5,length,facing);
                if(box.minY()>1)return new Piece(Type.FILLER_CORRIDOR,box,facing,depth,door(r),length,new boolean[4]);
            }
        }
        return null;
    }
    private static int door(PeRandom r){return switch(r.nextInt(5)){case 2->1;case 3->2;default->0;};}
    private Piece create(Type type,PeRandom r,int x,int y,int z,int facing,int depth){
        int ox=-1,oy=-1,width,height,length;
        switch(type){
            case STRAIGHT,CHEST_CORRIDOR->{width=5;height=5;length=7;}
            case LEFT_TURN,RIGHT_TURN->{width=5;height=5;length=5;}
            case PRISON_HALL->{width=9;height=5;length=11;}
            case ROOM_CROSSING->{ox=-4;width=11;height=7;length=11;}
            case STRAIGHT_STAIRS_DOWN->{oy=-7;width=5;height=11;length=8;}
            case STAIRS_DOWN->{oy=-7;width=5;height=11;length=5;}
            case FIVE_CROSSING->{ox=-4;oy=-3;width=10;height=9;length=11;}
            case LIBRARY->{ox=-4;width=14;height=11;length=15;}
            case PORTAL_ROOM->{ox=-4;width=11;height=8;length=16;}
            default->throw new IllegalArgumentException(type.toString());
        }
        Box b=oriented(x,y,z,ox,oy,width,height,length,facing);
        if(type==Type.LIBRARY&&(b.minY()<=10||collision(b)!=null))b=oriented(x,y,z,ox,oy,width,6,length,facing);
        if(b.minY()<=10||collision(b)!=null)return null;
        int door=type==Type.PORTAL_ROOM?0:door(r),variant=0;boolean[] c=new boolean[4];
        if(type==Type.STRAIGHT){c[0]=(r.nextRawInt()&1)==0;c[1]=(r.nextRawInt()&1)==0;}
        if(type==Type.FIVE_CROSSING){c[1]=((r.nextRawInt()>>>27)&1)!=0;c[0]=((r.nextRawInt()>>>27)&1)!=0;c[3]=((r.nextRawInt()>>>27)&1)!=0;c[2]=r.nextInt(3)!=0;}
        if(type==Type.ROOM_CROSSING)variant=r.nextInt(5);
        return new Piece(type,b,facing,depth,door,variant,c);
    }
    private void forward(Piece p,int x,int y){Box b=p.box;switch(p.orientation){
        case 0->add(p,b.minX()+x,b.minY()+y,b.maxZ()+1,0);
        case 1->add(p,b.minX()-1,b.minY()+y,b.minZ()+x,1);
        case 2->add(p,b.minX()+x,b.minY()+y,b.minZ()-1,2);
        case 3->add(p,b.maxX()+1,b.minY()+y,b.minZ()+x,3);
    }}
    private void left(Piece p,int y,int edge){Box b=p.box;if(p.orientation==0||p.orientation==2)add(p,b.minX()-1,b.minY()+y,b.minZ()+edge,1);else add(p,b.minX()+edge,b.minY()+y,b.minZ()-1,2);}
    private void right(Piece p,int y,int edge){Box b=p.box;if(p.orientation==0||p.orientation==2)add(p,b.maxX()+1,b.minY()+y,b.minZ()+edge,3);else add(p,b.minX()+edge,b.minY()+y,b.maxZ()+1,0);}
    private void children(Piece p){switch(p.type){
        case START->{forced=Type.FIVE_CROSSING;forward(p,1,1);}
        case STRAIGHT->{forward(p,1,1);if(p.connections[0])left(p,1,2);if(p.connections[1])right(p,1,2);}
        case PRISON_HALL,CHEST_CORRIDOR,STRAIGHT_STAIRS_DOWN,STAIRS_DOWN->forward(p,1,1);
        case ROOM_CROSSING->{forward(p,4,1);left(p,1,4);right(p,1,4);}
        case LEFT_TURN->{if(p.orientation>=2)left(p,1,1);else right(p,1,1);}
        case RIGHT_TURN->{if(p.orientation>=2)right(p,1,1);else left(p,1,1);}
        case FIVE_CROSSING->{int a=p.orientation==1||p.orientation==2?3:5,b=8-a;forward(p,5,1);if(p.connections[1])left(p,b,1);if(p.connections[0])left(p,a,7);if(p.connections[3])right(p,b,1);if(p.connections[2])right(p,a,7);}
        default->{}
    }}
}
