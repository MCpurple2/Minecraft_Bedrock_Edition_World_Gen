package net.mcpe.worldgen.core;

/** BiomeDecorator::decorateOres 001975b0 and OreFeature::place 00183da0.
 * Preserves unsigned MT draws, original float operations, loop inclusivity and
 * native truncating integer conversions, including the unusual gravel burst. */
public final class PeOres {
    private PeOres() {}
    public static void decorate(PePopulationWorld w, PeRandom r, int baseX, int baseZ) {
        span(w,r,baseX,baseZ,10,3,0,32,0,128);
        span(w,r,baseX,baseZ,8,13,0,32,0,128);
        if ((r.nextRawInt() & 15) == 0) span(w,r,baseX,baseZ,80,13,0,32,0,50);
        span(w,r,baseX,baseZ,10,1,3,32,0,80);
        span(w,r,baseX,baseZ,10,1,1,32,0,80);
        span(w,r,baseX,baseZ,10,1,5,32,0,80);
        span(w,r,baseX,baseZ,20,16,0,16,0,128);
        span(w,r,baseX,baseZ,20,15,0,8,0,64);
        span(w,r,baseX,baseZ,2,14,0,8,0,32);
        span(w,r,baseX,baseZ,8,73,0,7,0,16);
        span(w,r,baseX,baseZ,1,56,0,7,0,16);
        int x=baseX+(r.nextRawInt()&15), y=r.nextInt(16)+r.nextInt(16), z=baseZ+(r.nextRawInt()&15);
        place(w,r,x,y,z,21,0,6);
    }
    private static void span(PePopulationWorld w,PeRandom r,int bx,int bz,int count,int id,int data,int size,int min,int max) {
        for(int i=0;i<count;i++) {
            int x=bx+(r.nextRawInt()&15), y=min+r.nextInt(max-min), z=bz+(r.nextRawInt()&15);
            place(w,r,x,y,z,id,data,size);
        }
    }
    public static void place(PePopulationWorld w,PeRandom r,int x,int y,int z,int id,int data,int size) {
        float angle=r.nextFloat()*32768.0f;
        float sin=PeMth.indexed((int)angle), cos=PeMth.indexed((int)(angle+16384.0f));
        float extent=(float)size*0.125f;
        float x1=extent*sin+(float)(x+8), x2=(float)(x+8)-extent*sin;
        float z1=extent*cos+(float)(z+8), z2=(float)(z+8)-extent*cos;
        float y1=(float)(y+r.nextInt(3)+2), y2=(float)(y+r.nextInt(3)+2);
        for(int step=0;step<=size;step++) {
            float inverse=1.0f/(float)size, t=(float)step;
            float px=x1+t*(x2-x1)*inverse, py=y1+t*(y2-y1)*inverse, pz=z1+t*(z2-z1)*inverse;
            float thickness=(float)size*0.0625f*r.nextFloat();
            float radius=(thickness+PeMth.sin((t*3.1415927f)/(float)size)*thickness)*0.5f+0.5f;
            float reciprocal=1.0f/radius;
            for(int xx=(int)(px-radius);xx<=(int)(px+radius);xx++) {
                float dx=reciprocal*((float)xx+(0.5f-px)); dx*=dx;
                if(dx>=1.0f)continue;
                for(int yy=(int)(py-radius);yy<=(int)(py+radius);yy++) {
                    float dy=reciprocal*((float)yy+(0.5f-py)); float xy=dx+dy*dy;
                    if(xy>=1.0f)continue;
                    for(int zz=(int)(pz-radius);zz<=(int)(pz+radius);zz++) {
                        float dz=reciprocal*((float)zz+(0.5f-pz));
                        if(xy+dz*dz<1.0f && w.isInside(xx,yy,zz) && w.getBlock(xx,yy,zz)==1)
                            w.setBlock(xx,yy,zz,id,data);
                    }
                }
            }
        }
    }
}
