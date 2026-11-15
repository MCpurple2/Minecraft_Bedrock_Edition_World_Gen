package net.mcpe.worldgen.core;

/** PE 0.11.1 tree selection and feature ports. Native pseudocode anchors are
 * recorded per method. Fifteen feature types match original ARM execution for
 * three flat-ground sample seeds, including block metadata and following RNG.
 * Material/solidity predicates use the supported legacy block-ID set. */
public final class PeTrees {
    private PeTrees() {}
    public enum Selected {
        OAK, BIRCH, TALL_BIRCH, SPRUCE, PINE, JUNGLE, GROUND_BUSH,
        FANCY, ROOF, MEGA_PINE, MEGA_SPRUCE, MEGA_JUNGLE, SWAMP, ACACIA, HUGE_MUSHROOM;
        public boolean place(PePopulationWorld w, PeRandom r,int x,int y,int z){return PeTrees.place(this,w,r,x,y,z);}
        public boolean implemented(){return true;}
    }
    /** Species draws occur before decorator position draws. Derived from all
     * native Biome::*getTreeFeature exports and decorator feature slots. */
    public static Selected select(PeRandom r,int id){
        if(id==155||id==156)return (r.nextRawInt()&0x08000000)!=0?Selected.TALL_BIRCH:Selected.BIRCH;
        int base=id>=128?id-128:id;
        if(base==4||base==18||base==27||base==28||base==29){
            int mode=base==27||base==28?2:base==29?3:id==132?1:0;
            if(mode==3&&r.nextInt(3)==0)return Selected.ROOF;
            if(mode==2||r.nextInt(5)==0)return Selected.BIRCH;
            return generic(r);
        }
        if(base==5||base==19||base==30||base==31||base==32||base==33){
            int mode=id==160?2:base==32||base==33?1:0;
            if(mode>0&&r.nextInt(3)==0){if(mode==2||r.nextInt(13)==0)return Selected.MEGA_SPRUCE;return Selected.MEGA_PINE;}
            return r.nextInt(3)==0?Selected.PINE:Selected.SPRUCE;
        }
        if(base==21||base==22||base==23){
            if(r.nextInt(10)==0)return Selected.FANCY;
            if((r.nextRawInt()&1)==0)return Selected.GROUND_BUSH;
            if(base!=23&&r.nextInt(10)==0)return Selected.MEGA_JUNGLE;
            return Selected.JUNGLE;
        }
        if(base==1)return r.nextInt(3)==0?Selected.FANCY:Selected.OAK;
        if(base==3||base==20||base==34)return r.nextInt(3)==0?generic(r):Selected.SPRUCE;
        if(base==6)return r.nextInt(20)==0?Selected.HUGE_MUSHROOM:Selected.SWAMP;
        if(base==35||base==36)return r.nextInt(5)==0?Selected.OAK:Selected.ACACIA;
        if(base==12||base==13)return Selected.SPRUCE;
        if(base==37||base==38||base==39)return Selected.OAK;
        return generic(r);
    }
    private static Selected generic(PeRandom r){return r.nextInt(10)==0?Selected.FANCY:Selected.OAK;}
    public static boolean selectAndPlace(PePopulationWorld w,PeRandom r,int id,int x,int y,int z){return select(r,id).place(w,r,x,y,z);}
    /** Native BiomeDecorator+0xcc is float, not integer. Constructor evidence in
     * decompiled-tree-config.json; special biome decorate prepasses remain separate. */
    public static float treeDensity(int id){
        if(id==132)return 1; if(id==162)return 0; if(id==163||id==164)return 2;
        int b=id>=128?id-128:id;
        return switch(b){case 1->.05f;case 4,18,27,28->10;case 29->-999;case 5,19,30,31,32,33->10;case 21,22->25;case 23->2;case 6->2;case 20,34->3;case 35,36->1;case 38->5;case 2,16,17,25,26,37,39->-999;case 14,15->-100;default->0;};
    }
    public static int treesPerChunk(int id){return (int)treeDensity(id);}
    public static void beforeDecoration(PePopulationWorld w,PeRandom r,int id,int x,int z,boolean old){
        PeBiomePopulation.preDecorate(w,r,id,x,z,old,PeDecorationSettings.fromBiome(id));
    }
    private static boolean place(Selected s,PePopulationWorld w,PeRandom r,int x,int y,int z){
        return switch(s){
            case OAK->common(w,r,x,y,z,r.nextInt(3)+4,0,false);
            case BIRCH->common(w,r,x,y,z,r.nextInt(3)+5,2,false);
            case TALL_BIRCH->{int h=r.nextInt(3)+5+r.nextInt(7);yield common(w,r,x,y,z,h,2,false);}
            case JUNGLE->common(w,r,x,y,z,r.nextInt(7)+4,3,true);
            case SPRUCE->spruce(w,r,x,y,z);
            case PINE->pine(w,r,x,y,z);
            case GROUND_BUSH->bush(w,r,x,y,z);
            case SWAMP->swamp(w,r,x,y,z);
            case ACACIA->acacia(w,r,x,y,z);
            case FANCY->fancy(w,r,x,y,z);
            case ROOF->roof(w,r,x,y,z);
            case MEGA_PINE->mega(w,r,x,y,z,false,false);
            case MEGA_SPRUCE->mega(w,r,x,y,z,true,false);
            case MEGA_JUNGLE->mega(w,r,x,y,z,false,true);
            case HUGE_MUSHROOM->hugeMushroom(w,r,x,y,z,-1);
        };
    }
    private static boolean airLeaves(int id){return id==0||id==18||id==161;}
    private static boolean leafFree(int id){return airLeaves(id)||id==8||id==9||id==31||id==32||id==37||id==38||id==39||id==40||id==78||id==106||id==175;}
    // Tile::solid as read by TreeFeature::placeLeaf in 0.11.1.
    private static boolean canopyCanReplace011(int id) {
        return switch (id) {
            case 0,6,8,9,10,11,18,20,26,27,30,31,32,37,38,39,40,44,50,51,52,53,54,59,60,
                 63,64,65,66,67,68,71,78,79,81,83,85,92,95,96,101,102,104,105,106,107,108,
                 109,111,114,120,126,127,128,134,135,136,139,141,142,156,158,161,163,164,
                 171,174,175,183,184,185,186,187,198,244 -> true;
            default -> false;
        };
    }
    private static boolean free(int id){return airLeaves(id)||id==2||id==3;}
    private static void leaf(PePopulationWorld w,int x,int y,int z,int meta){if(y>=0&&y<128&&leafFree(w.getBlock(x,y,z)))w.setBlock(x,y,z,18,meta);}
    private static boolean prepare(PePopulationWorld w,int x,int y,int z,int h){
        if(y<1||y+h>127)return false;int ground=w.getBlock(x,y-1,z);
        boolean saplingSoil = w.release().compareTo(PeRelease.V0_14_3) >= 0;
        if(ground!=2 && ground!=3 && !(saplingSoil && (ground==60 || ground==243)))return false;
        for(int yy=y;yy<=y+h+1;yy++){int rad=yy==y?0:yy>=y+h-1?2:1;for(int xx=x-rad;xx<=x+rad;xx++)for(int zz=z-rad;zz<=z+rad;zz++)if(yy>=128||!free(w.getBlock(xx,yy,zz)))return false;}
        if (saplingSoil) w.setBlock(x,y-1,z,3,0);
        return true;
    }
    /** TreeFeature::place 00252ad4. Crown corner draws are consumed even for
     * the omitted top corners; trunk is placed first, as in the native code. */
    private static boolean common(PePopulationWorld w,PeRandom r,int x,int y,int z,int h,int data,boolean vines){
        if(!prepare(w,x,y,z,h))return false;
        boolean standing=trunk(w,r,x,y,z,h,data,vines);
        if(!standing)return true;
        for(int yy=y+h-3;yy<=y+h;yy++){int dy=yy-(y+h),radius=1-dy/2;
            for(int xx=x-radius;xx<=x+radius;xx++)for(int zz=z-radius;zz<=z+radius;zz++){
                boolean corner=Math.abs(xx-x)==radius&&Math.abs(zz-z)==radius;
                if (corner && ((r.nextRawInt() & 1)==0 || dy==0)) continue;
                int block = w.getBlock(xx,yy,zz);
                if (w.release().compareTo(PeRelease.V0_12_3) < 0 ? canopyCanReplace011(block) : leafFree(block)) {
                    w.setBlock(xx,yy,zz,18,data);
                }
            }
        }
        if(vines){
            for(int yy=y+h-3;yy<=y+h;yy++){int radius=2-(yy-y-h)/2;for(int xx=x-radius;xx<=x+radius;xx++)for(int zz=z-radius;zz<=z+radius;zz++)if(w.getBlock(xx,yy,zz)==18){
                if((r.nextRawInt()&3)==0&&w.getBlock(xx-1,yy,zz)==0)vine(w,xx-1,yy,zz,8,5);
                if((r.nextRawInt()&3)==0&&w.getBlock(xx+1,yy,zz)==0)vine(w,xx+1,yy,zz,2,5);
                if((r.nextRawInt()&3)==0&&w.getBlock(xx,yy,zz-1)==0)vine(w,xx,yy,zz-1,1,5);
                if((r.nextRawInt()&3)==0&&w.getBlock(xx,yy,zz+1)==0)vine(w,xx,yy,zz+1,4,5);
            }}
            if(r.nextInt(5)==0&&h>5)for(int bound=4;bound>=3;bound--)for(int d=0;d<4;d++)if(r.nextInt(bound)==0){int age=r.nextInt(3);w.setBlock(x-DX[d],y+h-1-bound,z-DZ[d],127,d|(age<<2));}
        }
        w.setBlock(x,y-1,z,3,0);return true;
    }
    private static final int[] DX={0,-1,0,1},DZ={1,0,-1,0};
    /** _placeTrunk 00252410, including PE fallen logs and trunk vines. */
    private static boolean trunk(PePopulationWorld w,PeRandom r,int x,int y,int z,int h,int data,boolean jungle){
        boolean standing=true;float chance;
        if(r.nextInt(80)==0){fallen(w,r,x,y,z,h,data);h=1;standing=false;chance=data==2?0:.75f;}
        else if(data==2)chance=0;else if(jungle)chance=1f/3f;else chance=r.nextInt(12)==0?1:0;
        for(int yy=y;yy<y+h;yy++)if(airLeaves(w.getBlock(x,yy,z))){w.setBlock(x,yy,z,17,data);if(chance!=0){
            if(r.nextFloat()<chance&&w.getBlock(x-1,yy,z)==0)w.setBlock(x-1,yy,z,106,8);
            if(r.nextFloat()<chance&&w.getBlock(x+1,yy,z)==0)w.setBlock(x+1,yy,z,106,2);
            if(r.nextFloat()<chance&&w.getBlock(x,yy,z-1)==0)w.setBlock(x,yy,z-1,106,1);
            if(r.nextFloat()<chance&&w.getBlock(x,yy,z+1)==0)w.setBlock(x,yy,z+1,106,4);
        }}return standing;
    }
    private static void fallen(PePopulationWorld w,PeRandom r,int x,int y,int z,int h,int data){
        int face=(r.nextRawInt()&3)+2,dist=(r.nextRawInt()&1)+2;
        int dx=face==4?-1:face==5?1:0,dz=face==2?-1:face==3?1:0;
        x+=dx*dist;z+=dz*dist;int yy=w.surfaceY(x,z);if(yy>y+1||h<=2)return;
        int gap=0;for(int i=0;i<h-2;i++){int xx=x+dx*i,zz=z+dz*i;if(w.getBlock(xx,yy,zz)!=0)return;if(!solid(w.getBlock(xx,yy-1,zz))){if(++gap>2)return;}else gap=0;}
        for(int i=0;i<h-2;i++){int xx=x+dx*i,zz=z+dz*i;w.setBlock(xx,yy,zz,17,data|(dx==0?8:4));if(r.nextInt(10)==0&&w.getBlock(xx,yy+1,zz)==0)w.setBlock(xx,yy+1,zz,r.nextFloat()<.5f?39:40,0);}
    }
    private static boolean solid(int id){return !leafFree(id)&&id!=10&&id!=11;}
    private static void vine(PePopulationWorld w,int x,int y,int z,int data,int max){for(int i=0;i<max&&y-i>=0&&w.getBlock(x,y-i,z)==0;i++)w.setBlock(x,y-i,z,106,data);}
    /** SpruceFeature::place 001833a4: top-down crown with expanding/reset radius. */
    private static boolean spruce(PePopulationWorld w,PeRandom r,int x,int y,int z){
        int h=(r.nextRawInt()&3)+6;if(!prepare(w,x,y,z,h))return false;
        int bare=(r.nextRawInt()&1)+1,max=(r.nextRawInt()&1)+2,shorten=r.nextInt(3);
        if(!trunk(w,r,x,y,z,h-shorten,1,false))return true;
        int rad=r.nextRawInt()&1,limit=1,reset=0;
        for(int dy=0;dy<=h-bare;dy++){int yy=y+h-dy;for(int xx=x-rad;xx<=x+rad;xx++)for(int zz=z-rad;zz<=z+rad;zz++)if(rad==0||Math.abs(xx-x)!=rad||Math.abs(zz-z)!=rad)leaf(w,xx,yy,zz,1);
            if(rad<limit)rad++;else{rad=reset;reset=1;limit=Math.min(limit+1,max);}
        }return true;
    }
    /** PineFeature::place 001862a0: independent validation and needle cone. */
    private static boolean pine(PePopulationWorld w,PeRandom r,int x,int y,int z){
        int h=r.nextInt(5)+7,bare=h-(r.nextRawInt()&1)-3,foliage=h-bare,max=r.nextInt(foliage+1)+1;
        if(y<1||y+h>=128)return false;
        for(int yy=y;yy<=y+h+1;yy++){int rad=yy-y<bare?0:max;for(int xx=x-rad;xx<=x+rad;xx++)for(int zz=z-rad;zz<=z+rad;zz++)if(yy>=128||!airLeaves(w.getBlock(xx,yy,zz)))return false;}
        int ground=w.getBlock(x,y-1,z);if(ground!=2&&ground!=3||y>=128-h-1)return false;w.setBlock(x,y-1,z,3,0);
        int rad=0;for(int yy=y+h;yy>=y+bare;yy--){for(int xx=x-rad;xx<=x+rad;xx++)for(int zz=z-rad;zz<=z+rad;zz++)if(rad==0||Math.abs(xx-x)!=rad||Math.abs(zz-z)!=rad)leaf(w,xx,yy,zz,1);if(rad>0&&yy==y+bare+1)rad--;else if(rad<max)rad++;}
        for(int yy=y;yy<y+h-1;yy++)if(airLeaves(w.getBlock(x,yy,z)))w.setBlock(x,yy,z,17,1);return true;
    }
    /** GroundBushFeature::place 0026fce8. */
    private static boolean bush(PePopulationWorld w,PeRandom r,int x,int y,int z){
        while(y>0&&airLeaves(w.getBlock(x,y,z)))y--;int ground=w.getBlock(x,y,z);if(ground!=2&&ground!=3)return true;w.setBlock(x,y,z,17,3);
        for(int yy=y;yy<=y+2;yy++){int rad=2-(yy-y);for(int xx=x-rad;xx<=x+rad;xx++)for(int zz=z-rad;zz<=z+rad;zz++){boolean corner=Math.abs(xx-x)==rad&&Math.abs(zz-z)==rad;if(!corner||(r.nextRawInt()&1)!=0)leaf(w,xx,yy,zz,3);}}return true;
    }
    /** SwampTreeFeature::place 00194458, with its larger crown and water descent. */
    private static boolean swamp(PePopulationWorld w,PeRandom r,int x,int y,int z){
        int h=(r.nextRawInt()&3)+5;
        while(y>0){int b=w.getBlock(x,y-1,z);if(b!=8&&b!=9&&b!=17)break;y--;}
        if(y<1||y+h>127)return false;
        for(int yy=y;yy<=y+h+1;yy++){int rad=yy==y?0:yy>=y+h-1?3:1;for(int xx=x-rad;xx<=x+rad;xx++)for(int zz=z-rad;zz<=z+rad;zz++){int b=w.getBlock(xx,yy,zz);if(yy>=128||!(airLeaves(b)||b==17||yy==y&&(b==8||b==9)))return false;}}
        int ground=w.getBlock(x,y-1,z);if((ground!=2&&ground!=3)||y+h>=127)return false;w.setBlock(x,y-1,z,3,0);
        for(int yy=y+h-3;yy<=y+h;yy++){int dy=yy-y-h,rad=2-dy/2;for(int xx=x-rad;xx<=x+rad;xx++)for(int zz=z-rad;zz<=z+rad;zz++){boolean corner=Math.abs(xx-x)==rad&&Math.abs(zz-z)==rad;if(!corner||(r.nextRawInt()&1)!=0&&dy!=0)leaf(w,xx,yy,zz,0);}}
        for(int yy=y;yy<y+h;yy++){int b=w.getBlock(x,yy,z);if(airLeaves(b)||b==8||b==9)w.setBlock(x,yy,z,17,0);}
        for(int yy=y+h-3;yy<=y+h;yy++){int rad=2-(yy-y-h)/2;for(int xx=x-rad;xx<=x+rad;xx++)for(int zz=z-rad;zz<=z+rad;zz++)if(w.getBlock(xx,yy,zz)==18){
            if((r.nextRawInt()&3)==0&&w.getBlock(xx-1,yy,zz)==0)vine(w,xx-1,yy,zz,8,5);
            if((r.nextRawInt()&3)==0&&w.getBlock(xx+1,yy,zz)==0)vine(w,xx+1,yy,zz,2,5);
            if((r.nextRawInt()&3)==0&&w.getBlock(xx,yy,zz-1)==0)vine(w,xx,yy,zz-1,1,5);
            if((r.nextRawInt()&3)==0&&w.getBlock(xx,yy,zz+1)==0)vine(w,xx,yy,zz+1,4,5);
        }}return true;
    }
    private static void acaciaLeaf(PePopulationWorld w,int x,int y,int z){if(y>=0&&y<128&&airLeaves(w.getBlock(x,y,z)))w.setBlock(x,y,z,161,0);}
    private static void acaciaCrown(PePopulationWorld w,int x,int y,int z,int radius,boolean points){
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)acaciaLeaf(w,x+dx,y+1,z+dz);
        if(points){acaciaLeaf(w,x+2,y+1,z);acaciaLeaf(w,x-2,y+1,z);acaciaLeaf(w,x,y+1,z+2);acaciaLeaf(w,x,y+1,z-2);}
        for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(Math.abs(dx)!=radius||Math.abs(dz)!=radius)acaciaLeaf(w,x+dx,y,z+dz);
    }
    /** SavannaTreeFeature::place 00194c38: bent primary and optional secondary trunk. */
    private static boolean acacia(PePopulationWorld w,PeRandom r,int x,int y,int z){
        int h=r.nextInt(3)+r.nextInt(3)+5;if(!prepare(w,x,y,z,h)||y+h>=127)return false;w.setBlock(x,y-1,z,3,0);
        int dir=r.nextRawInt()&3,bend=h-(r.nextRawInt()&3)-1,remaining=3-r.nextInt(3),tx=x,tz=z,top=0;
        for(int i=0;i<h;i++){if(i>=bend&&remaining>0){remaining--;tx+=DX[dir];tz+=DZ[dir];}if(airLeaves(w.getBlock(tx,y+i,tz))){w.setBlock(tx,y+i,tz,162,0);top=y+i;}}
        acaciaCrown(w,tx,top,tz,3,true);
        int dir2=r.nextRawInt()&3;if(dir2!=dir){int start=bend-(r.nextRawInt()&1)-1,len=r.nextInt(3)+1;tx=x;tz=z;top=0;
            for(int i=start;i<h&&len>0;i++,len--)if(i>0){tx+=DX[dir2];tz+=DZ[dir2];if(airLeaves(w.getBlock(tx,y+i,tz))){w.setBlock(tx,y+i,tz,162,0);top=y+i;}}
            if(top>0)acaciaCrown(w,tx,top,tz,2,false);
        }return true;
    }
    private record Foliage(int x,int y,int z,int branchY) {}
    /** FancyTreeFeature::place 00205990 and helpers 001fb5a0..001fbc80.
     * All interpolation deliberately uses float and truncation toward zero. */
    private static boolean fancy(PePopulationWorld w,PeRandom r,int x,int y,int z){
        int h=r.nextInt(12)+5,ground=w.getBlock(x,y-1,z);
        if(ground!=2&&ground!=3&&ground!=60)return false;
        int obstruction=fancyLine(w,x,y,z,x,y+h-1,z,false);
        if(obstruction!=-1){if(obstruction<=5)return false;h=obstruction;}
        int trunkH=Math.min((int)((float)h*Float.intBitsToFloat(0x3f1e353f)),h-1),top=y+trunkH;
        float factor=.07692308f*(float)h;
        int count=Math.max(1,(int)(factor*factor+1.382f));
        java.util.ArrayList<Foliage> nodes=new java.util.ArrayList<>();
        nodes.add(new Foliage(x,y+h-4,z,top));
        for(int layer=h-4;layer>=0;layer--){
            float shape=fancyShape(h,layer);if(shape<0)continue;
            for(int j=0;j<count;j++){
                float radius=shape*(r.nextFloat()+.328f),angle=r.nextFloat()*65536f;
                int nx=x+(int)(radius*PeMth.indexed((int)angle)+.5f);
                int nz=z+(int)(radius*PeMth.indexed((int)(angle+16384f))+.5f),ny=y+layer-1;
                if(fancyLine(w,nx,ny,nz,nx,ny+4,nz,false)!=-1)continue;
                int dx=x-nx,dz=z-nz;
                float by=(float)ny-(float)StrictMath.sqrt((float)(dx*dx+dz*dz))*Float.intBitsToFloat(0x3ec3126f);
                int branch=by<(float)top?(int)by:top;
                if(fancyLine(w,x,branch,z,nx,ny,nz,false)==-1)nodes.add(new Foliage(nx,ny,nz,branch));
            }
        }
        for(Foliage n:nodes)for(int i=0;i<4;i++){
            float radius=i==0||i==3?2:3;int rad=(int)(radius+.618f);
            for(int dx=-rad;dx<=rad;dx++)for(int dz=-rad;dz<=rad;dz++){
                float ax=Math.abs(dx)+.5f,az=Math.abs(dz)+.5f;
                if(ax*ax+az*az<=radius*radius&&airLeaves(w.getBlock(n.x+dx,n.y+i,n.z+dz)))w.setBlock(n.x+dx,n.y+i,n.z+dz,18,0);
            }
        }
        fancyLine(w,x,y,z,x,top,z,true);
        for(Foliage n:nodes)if((float)h*.2f<=(float)(n.branchY-y))fancyLine(w,x,n.branchY,z,n.x,n.y,n.z,true);
        return true;
    }
    private static float fancyShape(int h,int layer){
        if((float)layer<(float)h*.3f)return -1;
        float half=(float)h*.5f,d=half-(float)layer;
        if(d==0)return half*.5f;
        if(half<=Math.abs(d))return 0;
        return (float)StrictMath.sqrt(half*half-d*d)*.5f;
    }
    private static int fancyLine(PePopulationWorld w,int x,int y,int z,int tx,int ty,int tz,boolean place){
        int dx=tx-x,dy=ty-y,dz=tz-z,steps=Math.max(Math.abs(dx),Math.max(Math.abs(dy),Math.abs(dz)));
        float inv=1f/(float)steps;
        for(int i=0;i<=steps;i++){
            float progress=(float)i*inv;
            int px=x+(int)(.5f+progress*(float)dx),py=y+(int)(.5f+progress*(float)dy),pz=z+(int)(.5f+progress*(float)dz);
            if(place){int ax=Math.abs(px-x),az=Math.abs(pz-z),data=ax<az?8:ax==0?0:4;w.setBlock(px,py,pz,17,data);}
            else if(!free(w.getBlock(px,py,pz)))return i;
        }return -1;
    }
    private static void speciesLeaf(PePopulationWorld w,int x,int y,int z,int data){if(y>=0&&y<128&&airLeaves(w.getBlock(x,y,z)))w.setBlock(x,y,z,18,data);}
    private static void roofLeaf(PePopulationWorld w,int x,int y,int z){int b=w.getBlock(x,y,z);if(y>=0&&y<128&&(b==0||b==31||b==32||b>=37&&b<=40||b==106||b==175))w.setBlock(x,y,z,161,1);}
    private static void chanceVine(PePopulationWorld w,PeRandom r,int x,int y,int z,int data,int bound){if(r.nextInt(bound)!=0&&w.getBlock(x,y,z)==0)w.setBlock(x,y,z,106,data);}
    private static boolean cornerLog(PePopulationWorld w,PeRandom r,int x,int y,int z,int block,int data,boolean vines,int bound,int corner){
        if(!airLeaves(w.getBlock(x,y,z)))return false;w.setBlock(x,y,z,block,data);
        if(vines){chanceVine(w,r,x+(corner==0||corner==3?-1:1),y,z,corner==0||corner==3?8:2,bound);chanceVine(w,r,x,y,z+(corner<2?-1:1),corner<2?1:4,bound);}return true;
    }
    /** RoofTreeFeature::place 00192738. Native checks its bent center but places
     * the four trunk columns at the original coordinates; preserve that behavior. */
    private static boolean roof(PePopulationWorld w,PeRandom r,int x,int y,int z){
        int h=r.nextInt(3)+(r.nextRawInt()&1)+6;
        if(!prepare(w,x,y,z,h)||y+h>=127)return false;
        for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++)w.setBlock(x+dx,y-1,z+dz,3,0);
        int dir=r.nextRawInt()&3,bend=h-(r.nextRawInt()&3),remaining=2-r.nextInt(3);
        boolean vines=r.nextInt(6)==0;int tx=x,tz=z,top=0;
        for(int i=0;i<h;i++){
            if(i>=bend&&remaining>0){remaining--;tx+=DX[dir];tz+=DZ[dir];}
            if(airLeaves(w.getBlock(tx,y+i,tz))){
                cornerLog(w,r,x,y+i,z,162,1,vines,7,0);cornerLog(w,r,x+1,y+i,z,162,1,vines,7,1);
                cornerLog(w,r,x+1,y+i,z+1,162,1,vines,7,2);cornerLog(w,r,x,y+i,z+1,162,1,vines,7,3);top=y+i;
            }
        }
        for(int dx=-2;dx<=0;dx++)for(int dz=-2;dz<=0;dz++){
            roofFour(w,tx,tz,top-1,dx,dz);
            if(!(dx< -1&&dz<0)&&!(dx==-1&&dz==-2))roofFour(w,tx,tz,top+1,dx,dz);
        }
        if((r.nextRawInt()&0x08000000)!=0)for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++)roofLeaf(w,tx+dx,top+2,tz+dz);
        for(int dx=-3;dx<=4;dx++)for(int dz=-3;dz<=4;dz++)if((Math.abs(dx)<3||Math.abs(dz)<3)&&!((dx==-3||dx==4)&&(dz==-3||dz==4)))roofLeaf(w,tx+dx,top,tz+dz);
        for(int dx=-1;dx<=2;dx++)for(int dz=-1;dz<=2;dz++)if((dx<0||dx>1||dz<0||dz>1)&&r.nextInt(3)==0){
            int len=r.nextInt(3)+2;for(int i=0;i<len;i++)w.setBlock(x+dx,top-1-i,z+dz,162,1);
            for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++)roofLeaf(w,tx+dx+a,top,tz+dz+b);
            for(int a=-2;a<=2;a++)for(int b=-2;b<=2;b++)if(Math.abs(a)!=2||Math.abs(b)!=2)roofLeaf(w,tx+dx+a,top-1,tz+dz+b);
        }return true;
    }
    private static void roofFour(PePopulationWorld w,int x,int z,int y,int dx,int dz){roofLeaf(w,x+dx,y,z+dz);roofLeaf(w,x+1-dx,y,z+dz);roofLeaf(w,x+dx,y,z+1-dz);roofLeaf(w,x+1-dx,y,z+1-dz);}
    /** MegaTreeFeature helpers 001934xx and species place 00193718/00193b10. */
    private static boolean megaPrepare(PePopulationWorld w,int x,int y,int z,int h){
        if(y<1||y+h>127)return false;
        for(int i=0;i<=h+1;i++){int rad=i==0?1:2;for(int dx=-rad;dx<=rad;dx++)for(int dz=-rad;dz<=rad;dz++)if(y+i>=128||!free(w.getBlock(x+dx,y+i,z+dz)))return false;}
        int b=w.getBlock(x,y-1,z);if(y<=1||b!=2&&b!=3&&b!=243)return false;
        for(int dx=0;dx<2;dx++)for(int dz=0;dz<2;dz++)w.setBlock(x+dx,y-1,z+dz,3,0);return true;
    }
    private static void doubleLeaves(PePopulationWorld w,int x,int y,int z,int radius,int data){
        int rr=radius*radius;
        for(int dx=-radius;dx<=radius+1;dx++)for(int dz=-radius;dz<=radius+1;dz++)if(dx*dx+dz*dz<=rr||(dx-1)*(dx-1)+dz*dz<=rr||dx*dx+(dz-1)*(dz-1)<=rr||(dx-1)*(dx-1)+(dz-1)*(dz-1)<=rr)speciesLeaf(w,x+dx,y,z+dz,data);
    }
    private static boolean mega(PePopulationWorld w,PeRandom r,int x,int y,int z,boolean spruce,boolean jungle){
        int h=(jungle?10:13)+r.nextInt(3)+r.nextInt(jungle?20:15);
        if(!megaPrepare(w,x,y,z,h))return false;int top=y+h;
        if(jungle){
            for(int radius=5;radius>=3;radius--)doubleLeaves(w,x,top+3-radius,z,radius,3);
            for(int by=top-2-(r.nextRawInt()&3);by>y+h/2;){
                float angle=r.nextFloat()*65536f;float cos=PeMth.indexed((int)(angle+16384f)),sin=PeMth.indexed((int)angle);int bx=x,bz=z;
                for(int i=0;i<5;i++){bx=x+(int)((float)i*cos+1.5f);bz=z+(int)((float)i*sin+1.5f);w.setBlock(bx,by-3+(i>>1),bz,17,3);}
                int depth=(r.nextRawInt()&1)+1;
                for(int yy=by-depth,radius=depth+1;yy<=by;yy++,radius--)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(dx*dx+dz*dz<=radius*radius)speciesLeaf(w,bx+dx,yy,bz+dz,3);
                by-=(r.nextRawInt()&3)+2;
            }
        }else{
            int crown=r.nextInt(5)+(spruce?13:3),prev=0;float coefficient=3.5f/(float)crown;
            // ARM 00193854..0019388c: divide first, multiply remaining second.
            for(int yy=top-crown,remaining=crown;yy<=top;yy++,remaining--){int radius=(int)StrictMath.floor(coefficient*(float)remaining),extra=radius==prev&&remaining>0?((yy&1)^1):0;doubleLeaves(w,x,yy,z,radius+extra,1);prev=radius;}
        }
        int data=jungle?3:1;
        for(int i=0;i<h;i++){
            boolean vines=jungle&&i!=0;
            cornerLog(w,r,x,y+i,z,17,data,vines,3,0);
            if(i<h-1){cornerLog(w,r,x+1,y+i,z,17,data,vines,3,1);cornerLog(w,r,x+1,y+i,z+1,17,data,vines,3,2);cornerLog(w,r,x,y+i,z+1,17,data,vines,3,3);}
        }return true;
    }
    /** HugeMushroomFeature::place 001847ac; type -1 chooses red/brown.
     * The initial species RNG draw is consumed even when a fixed type is given. */
    public static boolean hugeMushroom(PePopulationWorld w,PeRandom r,int x,int y,int z,int type){
        int draw=r.nextRawInt();if(type<0)type=draw&1;
        int h=r.nextInt(3)+4;if(r.nextInt(12)==0)h*=2;
        int top=y+h;if(y<1||top>126)return false;
        for(int yy=y;yy<=top+1;yy++){int radius=yy>y+3?3:0;for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)if(yy>=128||!airLeaves(w.getBlock(x+dx,yy,z+dz)))return false;}
        int ground=w.getBlock(x,y-1,z);
        if(ground!=2&&ground!=3&&ground!=110){
            int under=w.getBlock(x,y-2,z);if(ground!=9||under!=2&&under!=3&&under!=110)return false;
            y--;top--;
        }
        for(int yy=type==1?top-3:top;yy<=top;yy++){
            int radius=type==0?3:yy<top?2:1;
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                int data=5;if(dx==-radius)data--;else if(dx==radius)data++;
                if(dz==-radius)data-=3;else if(dz==radius)data+=3;
                if(type==0||yy<top){
                    if((dx==-radius||dx==radius)&&(dz==-radius||dz==radius))continue;
                    if((dx==1-radius&&dz==-radius)||(dx==-radius&&dz==1-radius))data=1;
                    if((dx==radius-1&&dz==-radius)||(dx==radius&&dz==1-radius))data=3;
                    if((dx==1-radius&&dz==radius)||(dx==-radius&&dz==radius-1))data=7;
                    if((dx==radius-1&&dz==radius)||(dx==radius&&dz==radius-1))data=9;
                }
                if(data==5&&yy<top){if(y<top-1)continue;data=0;}
                if(!solidBlocking(w.getBlock(x+dx,yy,z+dz)))w.setBlock(x+dx,yy,z+dz,99+type,data);
            }
        }
        for(int i=0;i<h;i++)if(!solidBlocking(w.getBlock(x,y+i,z)))w.setBlock(x,y+i,z,99+type,10);
        return true;
    }
    private static boolean solidBlocking(int id){return !leafFree(id)&&id!=10&&id!=11;}
}

