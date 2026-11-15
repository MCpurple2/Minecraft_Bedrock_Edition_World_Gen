package net.mcpe.worldgen.core;

/** MCPE 0.11.1 biome layer pipeline, translated from the ARM library's Layer::*
 * and *Layer::fillArea exports. Array layout is x + z * width.
 * Native parity has not yet been checked against a running PE client. */
public final class PeBiomes {
    private static final long A=6364136223846793005L, B=1442695040888963407L;
    private static final float[] DEPTH=new float[256], SCALE=new float[256];
    static {
        // Raw IEEE-754 constants are from _INIT_1 at Ghidra 00163550;
        // assignments below follow Biome::initBiomes at 001e0130.
        java.util.Arrays.fill(DEPTH,Float.intBitsToFloat(0x3dcccccd));
        java.util.Arrays.fill(SCALE,Float.intBitsToFloat(0x3e4ccccd));
        height(0,-1f,.1f);height(10,-1f,.1f);height(24,Float.intBitsToFloat(0xbfe66666),.1f);
        // PlainsBiome constructor itself applies HEIGHTS_LOWLANDS.
        height(1,.125f,.05f);height(2,.125f,.05f);height(12,.125f,.05f);height(35,.125f,.05f);
        height(3,1f,.5f);height(34,1f,.5f);height(20,1f*.8f,.5f*.8f);
        height(6,-.2f,.1f);height(7,-.5f,0f);height(11,-.5f,0f);
        for(int id:new int[]{13,17,18,19,22,28,31,33})height(id,.45f,.3f);
        height(14,.2f,.3f);
        for(int id:new int[]{15,16,26})height(id,0f,.025f);
        height(25,.1f,.8f);height(30,.2f,.2f);height(32,.2f,.2f);
        for(int id:new int[]{36,38,39})height(id,1.5f,.025f);
        // MutatedBiome::MutatedBiome at 00258ddc: default height increments.
        for(int id=128;id<256;id++)height(id,DEPTH[id-128]+.1f,SCALE[id-128]+.2f);
        // Per-class createMutatedCopy overrides (native exports in analysis).
        height(129,DEPTH[1],SCALE[1]); // PlainsBiome creates a fresh PlainsBiome.
        // ExtremeHillsBiome::setMutated at 00215eb4 copies both source fields.
        height(131,DEPTH[3],SCALE[3]);height(162,DEPTH[34],SCALE[34]);
        height(132,DEPTH[4],SCALE[4]+.2f); // Flower Forest keeps base depth.
        height(140,DEPTH[12]+.3f,SCALE[12]+.4f); // Ice Spikes final stores.
        height(160,DEPTH[32],SCALE[32]); // Mega Spruce Taiga copies source height.
        for(int id:new int[]{163,164})height(id,DEPTH[id-128]*.5f+.3f,SCALE[id-128]*.5f+1.2f);
        height(165,.1f,.2f); // Mesa Bryce creates a fresh MesaBiome.
        height(166,.45f,.3f);height(167,.45f,.3f); // Mesa mutation uses MOUNTAINS.
        // MutatedBirchForest ctor 00272668 only invokes MutatedBiome, so
        // the generic +.1/+ .2 increments for IDs 155 and 156 remain intact.
    }
    private static void height(int id,float depth,float scale){DEPTH[id]=depth;SCALE[id]=scale;}
    public static float depth(int id){return DEPTH[exists(id)?id:1];}
    public static float scale(int id){return SCALE[exists(id)?id:1];}
    private final L raw, zoomed;
    private final long seed;
    private final boolean old;
    public PeBiomes(long seed, boolean old) {
        this(seed, old, true);
    }

    PeBiomes(long seed, boolean old, boolean nativeHillsSeeds) {
        this.seed=Integer.toUnsignedLong((int)seed); this.old=old;
        L land=layer(1,"island",null);
        land=zoom(2000,land,true); land=layer(1,"add",land); land=zoom(2001,land,false);
        land=layer(2,"add",land); land=layer(50,"add",land); land=layer(70,"add",land);
        land=layer(2,"ocean",land); land=layer(2,"snow",land); land=layer(3,"add",land);
        land=layer(2,"warm",land); land=layer(2,"ice",land); land=layer(3,"special",land);
        land=zoom(2002,land,false); land=zoom(2003,land,false); land=layer(4,"add",land);
        land=layer(5,"mushroom",land); land=layer(4,"deep",land);
        L riverInit=layer(100,"riverInit",land);
        L biome=layer(200,"biome",land); biome=magnify(1000,biome,2);
        biome=layer(1000,"edge",biome);
        // RegionHillsLayer inherits Layer::init, which only visits its primary
        // parent. Both private zooms of its second parent keep world seed zero.
        L hillsRiver = new L(1001, "zoom", riverInit, null, nativeHillsSeeds);
        hillsRiver = new L(1001, "zoom", hillsRiver, null, nativeHillsSeeds);
        biome=new L(1000,"hills",biome,hillsRiver);
        int size=old?2:4;
        L river=magnify(1000,riverInit,2); river=magnify(1000,river,size);
        river=layer(1,"river",river); river=layer(1000,"smooth",river);
        biome=layer(1001,"rare",biome);
        for(int i=0;i<size;i++) { biome=zoom(1000+i,biome,false); if(i==0)biome=layer(3,"add",biome); if(i==1)biome=layer(1000,"shore",biome); }
        biome=layer(1000,"smooth",biome);
        raw=new L(100,"mix",biome,river); zoomed=layer(10,"voronoi",raw);
    }
    public synchronized int[] rawArea(int x,int z,int width,int height) { return raw.area(x,z,width,height); }
    /** BiomeSource::findValidSpawnPosition reads a 10x10 result as a linear buffer.
     * The ten entries after it still belong to SmoothLayer's 12x12 input. */
    synchronized PeWorldGenerator.Spawn initialSpawn() {
        for (int origin = 0; ; origin += 10) {
            int[] cells = raw.area(origin, 0, 10, 10);
            int[] smoothInput = null;
            // The north neighbor in row zero reads the caller's stack. In the
            // ServerLevel -> Level -> LevelData chain only column 3 can match:
            // its word is saved r8, the generator type (1 for infinite).
            if (!old && spawnBiome(cells[3]) && spawnBiome(cells[2])
                    && spawnBiome(cells[4]) && spawnBiome(cells[13])) {
                return new PeWorldGenerator.Spawn((origin + 3) * 4, 128, 0);
            }
            for (int i = 10; i < cells.length; i++) {
                if (!spawnBiome(cells[i]) || !spawnBiome(cells[i - 1]) || !spawnBiome(cells[i - 10])) continue;
                if (i >= 90 && smoothInput == null) smoothInput = raw.p.p.area(origin - 1, -1, 12, 12);
                int east = i == 99 ? smoothInput[100] : cells[i + 1];
                int south = i >= 90 ? smoothInput[i + 10] : cells[i + 10];
                if (spawnBiome(east) && spawnBiome(south)) {
                    return new PeWorldGenerator.Spawn((origin + i % 10) * 4, 128, i / 10 * 4);
                }
            }
        }
    }

    private static boolean spawnBiome(int id) {
        return switch (id) {
            case 1, 4, 5, 18, 19, 21, 22 -> true;
            default -> false;
        };
    }
    public synchronized int[] zoomedArea(int x,int z,int width,int height) { return zoomed.area(x,z,width,height); }
    public int biomeAt(int x,int z) { return zoomedArea(x,z,1,1)[0]; }
    private L layer(long salt,String kind,L p) { return new L(salt,kind,p,null); }
    private L zoom(long salt,L p,boolean fuzzy) {return layer(salt,fuzzy?"fuzzy":"zoom",p);}
    // Unlike Java's GenLayer magnify, this PE export repeatedly uses salt + 1.
    private L magnify(long salt,L p,int n) {for(int i=0;i<n;i++)p=zoom(salt+1,p,false);return p;}
    private static long mix(long value,long salt) {return value*(value*A+B)+salt;}
    private static boolean ocean(int id){return id==0||id==10||id==24;}
    // Legacy-ID classifications used by native getBiome()/virtual predicates.
    // These tables still require a running-client parity check, especially mutations.
    private static boolean exists(int id){return id>=0&&id<=39 || switch(id){case 129,130,131,132,133,134,140,149,151,155,156,157,158,160,161,162,163,164,165,166,167->true;default->false;};}
    private static boolean snow(int id){return switch(id){case 10,11,12,13,26,30,31,140,158->true;default->false;};}
    private static int type(int id){int b=id>=128?id-128:id;return switch(b){case 0,10,24->0;case 1->1;case 2,17->2;case 3,20,34->3;case 4,18,27,28,29->4;case 5,19,30,31,32,33->5;case 6->6;case 21,22,23->7;case 7,11->8;case 12,13->9;case 14,15->10;case 16,26->11;case 25->12;case 35,36->13;case 37,38,39->14;default->b+100;};}
    private static boolean same(int a,int b){if(a==b)return true;if(a==38||a==39)return b==38||b==39;return exists(a)&&exists(b)&&type(a)==type(b);}
    private static int temperature(int id){int b=id>=128?id-128:id; if(snow(id))return 1;return b==2||b==17||b==35||b==36||b==37||b==38||b==39?3:2;}
    private static boolean tempEdge(int a,int b){int x=temperature(a),y=temperature(b);return same(a,b)||x==y||x==2||y==2;}
    private static boolean jungleCompatible(int id){return type(id)==7||id==23||id==21||id==22||id==4||id==5||ocean(id);}
    private static boolean mesa(int id){return id==37||id==38||id==39||id==165||id==166||id==167;}
    private final class R {
        long world,state;
        R(long w,int x,int z){world=w;state=mix(mix(mix(mix(w,x),z),x),z);}
        int next(int bound){int n=(int)((state>>24)%bound);if(n<0)n+=bound;state=mix(state,world);return n;}
        int choose(int...v){return v[next(v.length)];}
    }
    private final class L {
        final String kind; final L p,q; final long world;
        L(long salt,String kind,L p,L q){this(salt,kind,p,q,false);}
        L(long salt,String kind,L p,L q,boolean unseeded){this.kind=kind;this.p=p;this.q=q;long b=salt;for(int i=0;i<3;i++)b=mix(b,salt);long w=seed;for(int i=0;i<3;i++)w=mix(w,b);world=unseeded?0:w;}
        R random(int x,int z){return new R(world,x,z);}
        int[] area(int x,int z,int w,int h){
            if(w<1||h<1)return new int[0];
            if(kind.equals("zoom")||kind.equals("fuzzy"))return zoomArea(x,z,w,h);
            if(kind.equals("voronoi"))return voronoi(x,z,w,h);
            int[] out=new int[w*h];
            if(kind.equals("island")){
                for(int dz=0;dz<h;dz++)for(int dx=0;dx<w;dx++)out[dx+dz*w]=random(x+dx,z+dz).next(10)==0?1:0;
                if(x<=0&&x+w>0&&z<=0&&z+h>0)out[-x-z*w]=1; return out;
            }
            boolean neighborhood=switch(kind){case "add","ocean","warm","ice","mushroom","deep","edge","hills","river","smooth","rare","shore"->true;default->false;};
            int b=neighborhood?1:0,pw=w+2*b;int[] in=p.area(x-b,z-b,pw,h+2*b);
            int[] second=q==null?null:q.area(x-b,z-b,pw,h+2*b);
            for(int dz=0;dz<h;dz++)for(int dx=0;dx<w;dx++){
                int k=dx+b+(dz+b)*pw,c=in[k],v=c; R r=random(x+dx,z+dz);
                int n=b==0?c:in[k-pw],s=b==0?c:in[k+pw],west=b==0?c:in[k-1],e=b==0?c:in[k+1];
                switch(kind){
                    case "add"->{int nw=in[k-pw-1],ne=in[k-pw+1],sw=in[k+pw-1],se=in[k+pw+1];
                        if(c==0&&(nw!=0||ne!=0||sw!=0||se!=0)){int count=1,chosen=1;if(nw!=0&&r.next(count++)==0)chosen=nw;if(ne!=0&&r.next(count++)==0)chosen=ne;if(sw!=0&&r.next(count++)==0)chosen=sw;if(se!=0&&r.next(count++)==0)chosen=se;v=r.next(3)==0?chosen:chosen==4?4:0;}
                        else if(c>0&&(nw==0||ne==0||sw==0||se==0)&&r.next(5)==0)v=c==4?4:0;
                    }
                    case "ocean"->{if(c==0&&n==0&&s==0&&west==0&&e==0&&r.next(2)==0)v=1;}
                    case "snow"->{if(c!=0){int i=r.next(6);v=i==0?4:i==1?3:1;}}
                    case "warm"->{if(c==1&&(n==3||n==4||s==3||s==4||west==3||west==4||e==3||e==4))v=2;}
                    case "ice"->{if(c==4&&(n==1||n==2||s==1||s==2||west==1||west==2||e==1||e==2))v=3;}
                    case "special"->{if(c!=0&&r.next(13)==0)v=c|((1+r.next(15))<<8);}
                    case "mushroom"->{if(c==0&&in[k-pw-1]==0&&in[k-pw+1]==0&&in[k+pw-1]==0&&in[k+pw+1]==0&&r.next(100)==0)v=14;}
                    case "deep"->{if(c==0&&n==0&&s==0&&west==0&&e==0)v=24;}
                    case "riverInit"->{v=c>0?r.next(299999)+2:0;}
                    case "biome"->{int special=(c&0xf00)>>8;c&=~0xf00;v=c;
                        if(!ocean(c)&&c!=14){v=switch(c){case 1->special!=0?(r.next(3)==0?39:38):r.choose(2,2,2,35,35,1);case 2->special!=0?21:(old?r.choose(4,3,1,1,1,27,6):r.choose(4,29,3,1,1,1,27,6));case 3->special!=0?32:r.choose(4,3,5,1);case 4->r.choose(12,12,12,30);default->14;};}
                    }
                    case "edge"->{
                        if(same(c,3)){if(!tempEdge(n,c)||!tempEdge(s,c)||!tempEdge(west,c)||!tempEdge(e,c))v=20;}
                        else if(c==38||c==39||c==32){if(!same(n,c)||!same(s,c)||!same(west,c)||!same(e,c))v=c==32?5:37;}
                        else if(c==2){if(n==12||s==12||west==12||e==12)v=34;}
                        else if(!old&&c==6){if(n==2||s==2||west==2||e==2||n==30||s==30||west==30||e==30||n==12||s==12||west==12||e==12)v=1;else if(n==21||s==21||west==21||e==21)v=23;}
                    }
                    case "hills"->{int noise=second[k];boolean mutate=(noise-2)%29==0;
                        if(c!=0&&noise>=2&&(noise-2)%29==1&&c<128){v=exists(c+128)?c+128:c;}
                        else if(r.next(3)==0||mutate){int hill=switch(c){case 2->17;case 4->18;case 27->28;case 29->1;case 5->19;case 32->33;case 30->31;case 1->r.next(3)==0?18:4;case 12->13;case 21->22;case 0->24;case 3->34;case 35->36;default->c;};
                            if(same(c,38))hill=37;else if(c==24&&r.next(3)==0)hill=r.next(2)==0?1:4;
                            if(mutate&&hill!=c)hill=exists(hill+128)?hill+128:c;
                            if(hill!=c&&((same(n,c)?1:0)+(same(s,c)?1:0)+(same(west,c)?1:0)+(same(e,c)?1:0))>=3)v=hill;
                        }
                    }
                    case "river"->{int t=riverValue(c);v=t==riverValue(n)&&t==riverValue(s)&&t==riverValue(west)&&t==riverValue(e)?-1:7;}
                    case "smooth"->{if(west==e&&n==s)v=r.next(2)==0?west:n;else if(west==e)v=west;else if(n==s)v=n;}
                    case "rare"->{if(r.next(57)==0&&c==1)v=129;}
                    case "shore"->{boolean nextOcean=ocean(n)||ocean(s)||ocean(west)||ocean(e);
                        if(c==14){if(n==0||s==0||west==0||e==0)v=15;}
                        else if(type(c)==7){if(!jungleCompatible(n)||!jungleCompatible(s)||!jungleCompatible(west)||!jungleCompatible(e))v=23;else if(nextOcean)v=16;}
                        else if(c==3||c==34||c==20){if(!ocean(c)&&nextOcean)v=25;}
                        else if(snow(c)){if(!ocean(c)&&nextOcean)v=26;}
                        else if(c==37||c==38){if(!nextOcean&&(!mesa(n)||!mesa(s)||!mesa(west)||!mesa(e)))v=2;}
                        else if(c!=0&&c!=24&&c!=7&&c!=6&&nextOcean)v=16;
                    }
                    case "mix"->{int river=second[k];if(c!=0&&c!=24&&river==7)v=c==12?11:c==14||c==15?15:river&255;}
                    default->throw new IllegalStateException(kind);
                }
                out[dx+dz*w]=v;
            }return out;
        }
        int[] zoomArea(int x,int z,int w,int h){
            int px=x>>1,pz=z>>1,pw=(w>>1)+2,ph=(h>>1)+2;int[] in=p.area(px,pz,pw,ph);int tw=(pw-1)*2;int[] tmp=new int[tw*(ph-1)*2];
            for(int dz=0;dz<ph-1;dz++)for(int dx=0;dx<pw-1;dx++){
                R r=random((px+dx)*2,(pz+dz)*2);int a=in[dx+dz*pw],b=in[dx+1+dz*pw],c=in[dx+(dz+1)*pw],d=in[dx+1+(dz+1)*pw];int k=dx*2+dz*2*tw;
                tmp[k]=a;tmp[k+tw]=r.choose(a,c);tmp[k+1]=r.choose(a,b);tmp[k+tw+1]=kind.equals("fuzzy")?r.choose(a,b,c,d):mode(r,a,b,c,d);
            }int[] out=new int[w*h];for(int dz=0;dz<h;dz++)System.arraycopy(tmp,(dz+(z&1))*tw+(x&1),out,dz*w,w);return out;
        }
        int mode(R r,int a,int b,int c,int d){if(b==c&&c==d)return b;if(a==b&&a==c)return a;if(a==b&&a==d)return a;if(a==c&&a==d)return a;if(a==b&&c!=d)return a;if(a==c&&b!=d)return a;if(a==d&&b!=c)return a;if(b==c&&a!=d)return b;if(b==d&&a!=c)return b;if(c==d&&a!=b)return c;return r.choose(a,b,c,d);}
        int[] voronoi(int x,int z,int w,int h){
            int ax=x-2,az=z-2,px=ax>>2,pz=az>>2,pw=((w+(ax&3)+3)>>2)+1,ph=((h+(az&3)+3)>>2)+1,tw=(pw-1)*4;int[] in=p.area(px,pz,pw,ph),tmp=new int[tw*(ph-1)*4];
            for(int dz=0;dz<ph-1;dz++)for(int dx=0;dx<pw-1;dx++){
                R a=random((px+dx)*4,(pz+dz)*4),b=random((px+dx+1)*4,(pz+dz)*4),c=random((px+dx)*4,(pz+dz+1)*4),d=random((px+dx+1)*4,(pz+dz+1)*4);
                float xa=(a.next(1024)*0.0009765625f-0.5f)*3.6f,za=(a.next(1024)*0.0009765625f-0.5f)*3.6f;
                float xb=(b.next(1024)*0.0009765625f-0.5f)*3.6f+4,zb=(b.next(1024)*0.0009765625f-0.5f)*3.6f;
                float xc=(c.next(1024)*0.0009765625f-0.5f)*3.6f,zc=(c.next(1024)*0.0009765625f-0.5f)*3.6f+4;
                float xd=(d.next(1024)*0.0009765625f-0.5f)*3.6f+4,zd=(d.next(1024)*0.0009765625f-0.5f)*3.6f+4;
                for(int zz=0;zz<4;zz++)for(int xx=0;xx<4;xx++){
                    float da=(zz-za)*(zz-za)+(xx-xa)*(xx-xa),db=(zz-zb)*(zz-zb)+(xx-xb)*(xx-xb),dc=(zz-zc)*(zz-zc)+(xx-xc)*(xx-xc),dd=(zz-zd)*(zz-zd)+(xx-xd)*(xx-xd);
                    tmp[dx*4+xx+(dz*4+zz)*tw]=da<db&&da<dc&&da<dd?in[dx+dz*pw]:db<da&&db<dc&&db<dd?in[dx+1+dz*pw]&255:dc<da&&dc<db&&dc<dd?in[dx+(dz+1)*pw]:in[dx+1+(dz+1)*pw]&255;
                }
            }int[] out=new int[w*h];for(int dz=0;dz<h;dz++)System.arraycopy(tmp,(dz+(az&3))*tw+(ax&3),out,dz*w,w);return out;
        }
    }
    private static int riverValue(int v){return v<2?v:2+(v&1);}
}
