package net.mcpe.worldgen.core;

import java.util.ArrayList;
import java.util.List;

/** PE 0.11.1 VillageStart / VillagePiece / StraightRoad layout pipeline.
 * Constructor decisions and random-consumption order are kept separate from
 * terrain-dependent block painting. Coordinates/bounds are inclusive. */
public final class PeVillageLayout {
    public enum Type {
        START, ROAD, SIMPLE_HOUSE, SMALL_TEMPLE, BOOK_HOUSE, SMALL_HUT, PIG_HOUSE,
        DOUBLE_FARMLAND, FARMLAND, SMITHY, TWO_ROOM_HOUSE, LIGHT_POST
    }

    public record Box(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        public boolean intersects(Box b) {
            return maxX>=b.minX&&minX<=b.maxX&&maxY>=b.minY&&minY<=b.maxY&&maxZ>=b.minZ&&minZ<=b.maxZ;
        }
        public boolean contains(int x,int y,int z) {
            return x>=minX&&x<=maxX&&y>=minY&&y<=maxY&&z>=minZ&&z<=maxZ;
        }
        public Box move(int x,int y,int z) {return new Box(minX+x,minY+y,minZ+z,maxX+x,maxY+y,maxZ+z);}
        public int spanX(){return maxX-minX+1;}
        public int spanY(){return maxY-minY+1;}
        public int spanZ(){return maxZ-minZ+1;}
    }

    public static final class Piece {
        public final Type type;
        public Box box;
        public final int orientation,depth;
        public final boolean desert;
        public final int variant,hutStyle;
        public final int[] crops;
        public int groundY=-1,villagersSpawned=0;
        public boolean chestCreated=false;

        private Piece(Type type,Box box,int orientation,int depth,boolean desert,int variant,int hutStyle,int[] crops) {
            this.type=type;this.box=box;this.orientation=orientation;this.depth=depth;this.desert=desert;
            this.variant=variant;this.hutStyle=hutStyle;this.crops=crops;
        }
        public int worldX(int x,int z) {
            return switch(orientation){case 0,2->box.minX+x;case 1->box.maxX-z;case 3->box.minX+z;default->x;};
        }
        public int worldY(int y){return orientation==255?y:box.minY+y;}
        public int worldZ(int x,int z) {
            return switch(orientation){case 0->box.minZ+z;case 1,3->box.minZ+x;case 2->box.maxZ-z;default->z;};
        }
    }

    private static final class Weight {
        final Type type;final int weight,limit;int used;
        Weight(Type type,int weight,int limit){this.type=type;this.weight=weight;this.limit=limit;}
    }

    private final ArrayList<Piece> all=new ArrayList<>(),roads=new ArrayList<>(),houses=new ArrayList<>();
    private final ArrayList<Weight> weights=new ArrayList<>();
    private final PeRandom random;
    private final int size;
    private final boolean desert;
    private Piece start;
    private Type previous;

    private PeVillageLayout(PeRandom random,int size,boolean desert) {this.random=random;this.size=size;this.desert=desert;}
    public List<Piece> pieces(){return List.copyOf(all);}

    public static PeVillageLayout create(PeRandom random,int chunkX,int chunkZ,int size,boolean desert) {
        PeVillageLayout v=new PeVillageLayout(random,size,desert);
        v.initialize(chunkX,chunkZ);
        return v;
    }

    private int between(int lo,int hi){return lo+random.nextInt(hi-lo+1);}
    private void weight(Type type,int value,int lo,int hi) {
        int limit=between(lo,hi);
        if(limit!=0)weights.add(new Weight(type,value,limit));
    }
    private void initialize(int chunkX,int chunkZ) {
        weight(Type.SIMPLE_HOUSE,4,size+2,(size+2)*2);
        weight(Type.SMALL_TEMPLE,20,size,size+1);
        weight(Type.BOOK_HOUSE,20,size,size+2);
        weight(Type.SMALL_HUT,3,size+2,size*3+5);
        weight(Type.PIG_HOUSE,15,size,size+2);
        weight(Type.DOUBLE_FARMLAND,3,size+1,size+4);
        weight(Type.FARMLAND,3,size+2,(size+2)*2);
        weight(Type.SMITHY,15,0,size+1);
        weight(Type.TWO_ROOM_HOUSE,8,size,(size+2)*2-1);
        int x=chunkX*16+2,z=chunkZ*16+2;
        start=new Piece(Type.START,new Box(x,64,z,x+5,78,z+5),random.nextInt(4),0,desert,0,0,new int[0]);
        all.add(start);
        addRoad(x-1,74,z+1,1,0);
        addRoad(x+6,74,z+1,3,0);
        addRoad(x+1,74,z-1,2,0);
        addRoad(x+1,74,z+6,0,0);
        while(!roads.isEmpty()||!houses.isEmpty()) {
            if(!roads.isEmpty())roadChildren(roads.remove(random.nextInt(roads.size())));
            else houses.remove(random.nextInt(houses.size())); // native no-op addChildren still consumes selection RNG
        }
    }

    private boolean collision(Box box) {
        for(Piece p:all)if(p.box.intersects(box))return true;
        return false;
    }
    private static Box oriented(int x,int y,int z,int width,int height,int depth,int orientation) {
        return switch(orientation) {
            case 1->new Box(x-depth+1,y,z,x,y+height-1,z+width-1);
            case 2->new Box(x,y,z-depth+1,x+width-1,y+height-1,z);
            case 3->new Box(x,y,z,x+depth-1,y+height-1,z+width-1);
            default->new Box(x,y,z,x+width-1,y+height-1,z+depth-1);
        };
    }

    private Piece addRoad(int x,int y,int z,int orientation,int depth) {
        if(depth>size+3||Math.abs(x-start.box.minX)>=113||Math.abs(z-start.box.minZ)>=113)return null;
        int length=between(3,5)*7;
        for(;length>6;length-=7) {
            Box box=oriented(x,y,z,3,3,length,orientation);
            if(collision(box))continue;
            if(box.minY<=10)return null;
            Piece p=new Piece(Type.ROAD,box,orientation,depth,desert,0,0,new int[0]);
            all.add(p);roads.add(p);return p;
        }
        return null;
    }

    private Piece addHouse(int x,int y,int z,int orientation,int depth) {
        if(depth>=51||Math.abs(x-start.box.minX)>=113||Math.abs(z-start.box.minZ)>=113)return null;
        Piece p=chooseHouse(x,y,z,orientation,depth+1);
        if(p!=null){all.add(p);houses.add(p);}
        return p;
    }

    private Piece chooseHouse(int x,int y,int z,int orientation,int depth) {
        int total=0;boolean available=false;
        for(Weight w:weights){total+=w.weight;if(w.limit>0&&w.used<w.limit)available=true;}
        if(!available||total<=0)return null;
        for(int attempt=0;attempt<5;attempt++) {
            int selected=random.nextInt(total);
            for(int index=0;index<weights.size();index++) {
                Weight w=weights.get(index);selected-=w.weight;
                if(selected>=0)continue;
                if((w.limit!=0&&w.used>=w.limit)||(w.type==previous&&weights.size()>1))break;
                Piece p=create(w.type,x,y,z,orientation,depth);
                if(p!=null){w.used++;previous=w.type;if(w.limit!=0&&w.used>=w.limit)weights.remove(index);return p;}
            }
        }
        return create(Type.LIGHT_POST,x,y,z,orientation,depth);
    }

    private Piece create(Type type,int x,int y,int z,int orientation,int depth) {
        int width,height,length;
        switch(type) {
            case SIMPLE_HOUSE->{width=5;height=6;length=5;}
            case SMALL_TEMPLE->{width=5;height=12;length=9;}
            case BOOK_HOUSE->{width=9;height=9;length=6;}
            case SMALL_HUT->{width=4;height=6;length=5;}
            case PIG_HOUSE->{width=9;height=7;length=11;}
            case DOUBLE_FARMLAND->{width=13;height=4;length=9;}
            case FARMLAND->{width=7;height=4;length=9;}
            case SMITHY->{width=10;height=6;length=7;}
            case TWO_ROOM_HOUSE->{width=9;height=7;length=12;}
            case LIGHT_POST->{width=3;height=4;length=2;}
            default->throw new IllegalArgumentException("Not a house: "+type);
        }
        Box box=oriented(x,y,z,width,height,length,orientation);
        if(collision(box))return null;
        int variant=0,hutStyle=0;int[] crops=new int[0];
        if(type==Type.SIMPLE_HOUSE||type==Type.SMALL_HUT)variant=(random.nextRawInt()>>>27)&1;
        if(type==Type.SMALL_HUT)hutStyle=random.nextInt(3);
        if(type==Type.FARMLAND||type==Type.DOUBLE_FARMLAND) {
            crops=new int[type==Type.FARMLAND?2:4];
            for(int i=0;i<crops.length;i++)crops[i]=switch(random.nextInt(5)){case 0->141;case 1->142;default->59;};
        }
        return new Piece(type,box,orientation,depth,desert,variant,hutStyle,crops);
    }

    private Piece sideHouse(Piece road,int offset,boolean right) {
        Box b=road.box;
        if(road.orientation==0||road.orientation==2)
            return addHouse(right?b.maxX+1:b.minX-1,b.minY,b.minZ+offset,right?3:1,road.depth);
        return addHouse(b.minX+offset,b.minY,right?b.maxZ+1:b.minZ-1,right?0:2,road.depth);
    }

    private void roadChildren(Piece road) {
        int length=Math.max(road.box.spanX(),road.box.spanZ());
        boolean attached=false;
        for(int side=0;side<2;side++) {
            for(int pos=random.nextInt(5);pos<length-8;pos+=random.nextInt(5)+2) {
                Piece p=sideHouse(road,pos,side==1);
                if(p!=null){attached=true;pos+=Math.max(p.box.spanX(),p.box.spanZ());}
            }
        }
        if(!attached)return;
        Box b=road.box;
        if(random.nextInt(3)!=0) {
            switch(road.orientation) {
                case 0->addRoad(b.minX-1,b.minY,b.maxZ-2,1,road.depth);
                case 1->addRoad(b.minX,b.minY,b.minZ-1,2,road.depth);
                case 2->addRoad(b.minX-1,b.minY,b.minZ,1,road.depth);
                case 3->addRoad(b.maxX-2,b.minY,b.minZ-1,2,road.depth);
            }
        }
        if(random.nextInt(3)!=0) {
            switch(road.orientation) {
                case 0->addRoad(b.maxX+1,b.minY,b.maxZ-2,3,road.depth);
                case 1->addRoad(b.minX,b.minY,b.maxZ+1,0,road.depth);
                case 2->addRoad(b.maxX+1,b.minY,b.minZ,3,road.depth);
                case 3->addRoad(b.maxX-2,b.minY,b.maxZ+1,0,road.depth);
            }
        }
    }
}
