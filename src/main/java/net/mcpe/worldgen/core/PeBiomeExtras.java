package net.mcpe.worldgen.core;

/** Biome-specific population callbacks from the 0.11.1 ARM generator. */
final class PeBiomeExtras {
    static void pre(PePopulationWorld w,PeRandom r,int id,int bx,int bz,boolean old){
        if(id==35||id==36){
            if(!old){int x=bx+r.nextInt(16),z=bz+r.nextInt(16);PeBiomePopulation.doublePlant(w,r,x,w.surfaceY(x,z),z,2);}
        }else if(id==5||id==19||id==30||id==31||id==32||id==33||id==160){
            if(id==32||id==33||id==160){
                int count=r.nextInt(3);
                for(int i=0;i<count;i++){int x=bx+r.nextInt(16),z=bz+r.nextInt(16);boulder(w,r,x,w.surfaceY(x,z),z);}
            }
            // This particular native call passes the two local offsets as world coordinates.
            // A ChunkViewSource only supplies the chunks in its population window.
            if(!old)for(int i=0;i<7;i++){
                int x=r.nextInt(16),z=r.nextInt(16);
                if(Math.abs(bx)<=16&&Math.abs(bz)<=16)
                    PeBiomePopulation.doublePlant(w,r,x,r.nextInt(w.surfaceY(x,z)+32),z,3);
                else {r.nextInt(32);for(int draw=0;draw<384;draw++)r.nextRawInt();}
            }
        }else if(id==140){
            for(int i=0;i<5;i++){
                int x=bx+r.nextInt(16),z=bz+r.nextInt(16),y=w.surfaceY(x,z);
                while(y>2&&w.getBlock(x,y,z)==0)y--;
                if(w.getBlock(x,y,z)!=80)continue;
                if(i<3)iceSpike(w,r,x,y,z);
                else {
                    int radius=r.nextInt(2)+2;
                    for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)
                        if(dx*dx+dz*dz<=radius*radius)for(int dy=-1;dy<=1;dy++){
                            int block=w.getBlock(x+dx,y+dy,z+dz);
                            if(block==3||block==79||block==80)w.setBlock(x+dx,y+dy,z+dz,174,0);
                        }
                }
            }
        }
    }
    private static void boulder(PePopulationWorld w,PeRandom r,int x,int y,int z){
        for(;y>3;y--){
            int floor=w.getBlock(x,y-1,z);
            if(floor==1||floor==2||floor==3||floor==243)break;
        }
        if(y<=3)return;
        for(int n=0;n<3;n++){
            int rx=r.nextInt(2),ry=r.nextInt(2),rz=r.nextInt(2);
            float radius=(rx+ry+rz)*0.333f+0.5f;
            for(int dx=-rx;dx<rx;dx++)for(int dy=-ry;dy<ry;dy++)for(int dz=-rz;dz<rz;dz++)
                if(dx*dx+dy*dy+dz*dz<=radius*radius)w.setBlock(x+dx,y+dy,z+dz,48,0);
            x+=r.nextInt(2)-1;y-=r.nextInt(2);z+=r.nextInt(2)-1;
        }
    }
    private static boolean iceTarget(int id){return id==0||id==3||id==80||id==79;}
    private static void iceSpike(PePopulationWorld w,PeRandom r,int x,int y,int z){
        y+=r.nextInt(4);
        int height=r.nextInt(4)+7,radius=r.nextInt(2)+height/4;
        if(radius!=1&&r.nextInt(60)==0)y+=10+r.nextInt(30);
        for(int h=0;h<height;h++){
            float width=(1f-h*(1f/height))*radius;
            int extent=(int)Math.ceil(width);
            for(int dx=-extent;dx<=extent;dx++)for(int dz=-extent;dz<=extent;dz++){
                float a=Math.abs(dx)-0.25f,b=Math.abs(dz)-0.25f;
                if((dx!=0||dz!=0)&&a*a+b*b>width*width)continue;
                if((Math.abs(dx)==extent||Math.abs(dz)==extent)&&r.nextFloat()>0.75f)continue;
                if(iceTarget(w.getBlock(x+dx,y+h,z+dz)))w.setBlock(x+dx,y+h,z+dz,174,0);
                if(h!=0&&extent>1&&iceTarget(w.getBlock(x+dx,y-h,z+dz)))w.setBlock(x+dx,y-h,z+dz,174,0);
            }
        }
        int root=Math.min(1,radius-1);
        for(int dx=-root;dx<=root;dx++)for(int dz=-root;dz<=root;dz++){
            int run=Math.abs(dx)==1&&Math.abs(dz)==1?r.nextInt(5):50;
            for(int py=y-1;py>50;){
                int block=w.getBlock(x+dx,py,z+dz);
                if(!iceTarget(block)&&block!=174)break;
                w.setBlock(x+dx,py--,z+dz,174,0);
                if(--run<=0){py-=1+r.nextInt(5);run=r.nextInt(5);}
            }
        }
    }

    static void mesaOres(PePopulationWorld w,PeRandom r,int id,int bx,int bz){
        if((id>=37&&id<=39)||(id>=165&&id<=167))
            for(int i=0;i<20;i++){
                int x=bx+r.nextInt(16),y=32+r.nextInt(48),z=bz+r.nextInt(16);
                PeOres.place(w,r,x,y,z,14,0,8);
            }
    }
    static void post(PePopulationWorld w,PeRandom r,int id,int bx,int bz){
        if((id==2||id==17)&&r.nextInt(500)==0)desertWell(w,bx+r.nextInt(16),bz+r.nextInt(16));
        if(id==3||id==20||id==34||id==131||id==162){
            int count=3+r.nextInt(6);
            for(int i=0;i<count;i++){
                int x=bx+r.nextInt(16),y=4+r.nextInt(28),z=bz+r.nextInt(16);
                if(w.getBlock(x,y,z)==1)w.setBlock(x,y,z,129,0);
            }
            for(int i=0;i<7;i++){
                int x=bx+r.nextInt(16),y=r.nextInt(32),z=bz+r.nextInt(16);
                PeOres.place(w,r,x,y,z,97,0,8);
            }
        }
        if(id==21||id==22||id==23)jungle(w,r,bx,bz);
    }
    private static void desertWell(PePopulationWorld w,int x,int z){
        int y=128;
        while(y>2&&w.getBlock(x,y,z)==0)y--;
        if(w.getBlock(x,y,z)!=12)return;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
            if(w.getBlock(x+dx,y-1,z+dz)==0&&w.getBlock(x+dx,y-2,z+dz)==0)return;
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
            w.setBlock(x+dx,y-1,z+dz,24,0);
            w.setBlock(x+dx,y,z+dz,Math.abs(dx)+Math.abs(dz)<=1?9:24,0);
            if(Math.abs(dx)==2||Math.abs(dz)==2){
                boolean slab=dx==0||dz==0;
                w.setBlock(x+dx,y+1,z+dz,slab?44:24,slab?1:0);
            }
        }
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            boolean center=dx==0&&dz==0;
            w.setBlock(x+dx,y+4,z+dz,center?24:44,center?0:1);
            if(dx!=0&&dz!=0)for(int h=1;h<=3;h++)w.setBlock(x+dx,y+h,z+dz,24,0);
        }
    }
    private static void jungle(PePopulationWorld w,PeRandom r,int bx,int bz){
        int x=bx+r.nextInt(16),z=bz+r.nextInt(16),bound=2*w.surfaceY(x,z),y=bound==0?0:r.nextInt(bound);
        for(int i=0;i<64;i++){
            int xx=x+r.nextInt(8)-r.nextInt(8),yy=y+r.nextInt(4)-r.nextInt(4),zz=z+r.nextInt(8)-r.nextInt(8);
            if(w.getBlock(xx,yy,zz)==0&&w.getBlock(xx,yy-1,zz)==2)w.setBlock(xx,yy,zz,103,0);
        }
        for(int i=0;i<50;i++){
            int originX=bx+8+r.nextInt(16),originZ=bz+8+r.nextInt(16),xx=originX,zz=originZ;
            for(int yy=64;yy<128;yy++){
                if(w.getBlock(xx,yy,zz)!=0){
                    xx=originX+r.nextInt(4)-r.nextInt(4);zz=originZ+r.nextInt(4)-r.nextInt(4);
                }else for(int side=0;side<4;side++){
                    int sx=xx+(side==2?1:side==3?-1:0),sz=zz+(side==0?1:side==1?-1:0);
                    int id=w.getBlock(sx,yy,sz);
                    if(id!=95&&w.solidRender(sx,yy,sz)&&PeSmallStructures.solid(id)){
                        w.setBlock(xx,yy,zz,106,new int[]{1,4,8,2}[side]);break;
                    }
                }
            }
        }
    }
}
