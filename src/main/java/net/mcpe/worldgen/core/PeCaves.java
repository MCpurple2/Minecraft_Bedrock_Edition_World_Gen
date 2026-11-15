package net.mcpe.worldgen.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Infinite-world LargeCaveFeature, operating directly on PE column-major chunk bytes. */
final class PeCaves {
    private static final int RANGE = 8;
    private static final float PI = (float)Math.PI;
    private record Tunnel(int seed, float x, float y, float z, float width, float yaw, float pitch, boolean room) {}
    private final int seed, xScale, zScale;
    private final Map<Long,List<Tunnel>> starts = new LinkedHashMap<>(512,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long,List<Tunnel>> entry) { return size()>1024; }
    };

    private final boolean nether;
    private final boolean zFirst;

    PeCaves(long seed) { this(seed,false); }

    PeCaves(long seed,boolean nether) {
        this(seed, nether, false);
    }

    PeCaves(long seed, boolean nether, boolean zFirst) {
        this.nether=nether;
        this.zFirst=zFirst;
        this.seed=(int)seed;
        PeRandom random=new PeRandom(seed);
        xScale=(random.nextRawInt()>>>2)*2+1;
        zScale=(random.nextRawInt()>>>2)*2+1;
    }

    void carve(byte[] blocks, int cx, int cz) {
        for (int dx=-RANGE; dx<=RANGE; dx++) for (int dz=-RANGE; dz<=RANGE; dz++) {
            int sx=cx+dx, sz=cz+dz;
            long key=((long)sx<<32) | Integer.toUnsignedLong(sz);
            for (Tunnel t: starts.computeIfAbsent(key, unused -> origins(sx,sz))) {
                tunnel(blocks,cx,cz,t.seed,t.x,t.y,t.z,t.width,t.yaw,t.pitch,t.room ? -1 : 0,0,t.room || nether ? 0.5f : 1f);
            }
        }
    }

    private List<Tunnel> origins(int sx, int sz) {
        PeRandom r=new PeRandom((sx*xScale+sz*zScale)^seed);
        return origins(sx,sz,r);
    }

    PeRandom lastSourceRandom(int cx,int cz) {
        int x=cx+RANGE,z=cz+RANGE;
        PeRandom r=new PeRandom((x*xScale+z*zScale)^seed);
        origins(x,z,r);
        return r;
    }

    private List<Tunnel> origins(int sx,int sz,PeRandom r) {
        int n=r.nextInt(r.nextInt(r.nextInt(nether ? 10 : 40)+1)+1);
        if (r.nextInt(nether ? 5 : 15)!=0 || n==0) return List.of();
        List<Tunnel> result=new ArrayList<>();
        for (int i=0; i<n; i++) {
            int first=r.nextInt(16);
            int y=nether ? r.nextInt(128) : r.nextInt(r.nextInt(120)+8);
            int last=r.nextInt(16);
            int x=sx*16+(zFirst ? last : first);
            int z=sz*16+(zFirst ? first : last);
            int branches=1;
            if (r.nextInt(4)==0) {
                float width=r.nextFloat()*6+1;
                result.add(new Tunnel(r.nextInt(),x,y,z,width,0,0,true));
                branches=1+r.nextInt(4);
            }
            for (int j=0;j<branches;j++) {
                float yaw=r.nextFloat()*(PI*2), pitch=(r.nextFloat()-0.5f)*0.25f;
                float width=r.nextFloat()*2+r.nextFloat();
                if(nether)width*=2;
                result.add(new Tunnel(r.nextInt(),x,y,z,width,yaw,pitch,false));
            }
        }
        return result;
    }

    private void tunnel(byte[] blocks,int cx,int cz,int seed,float x,float y,float z,
                               float width,float yaw,float pitch,int step,int end,float stretch) {
        PeRandom r=new PeRandom(seed);
        if (end==0) end=112-r.nextInt(28);
        boolean room=step==-1;
        if (room) step=end/2;
        int branchAt=r.nextInt(end/2)+end/4;
        boolean steep=r.nextInt(6)==0;
        float dyaw=0, dpitch=0, scale=PI/end, centerX=cx*16+8, centerZ=cz*16+8;
        for (;step<end;step++) {
            float radius;
            if(nether){
                float horizontal=PeMth.cos(pitch);
                radius=1.5f+PeMth.indexed((int)((scale*10430.378f)*step))*width;
                x+=horizontal*PeMth.cos(yaw);
                y+=PeMth.sin(pitch);
                z+=horizontal*PeMth.sin(yaw);
            }else{
                radius=(float)(1.5+Math.sin(scale*step)*width);
                float horizontal=(float)Math.cos(pitch);
                x=(float)(x+horizontal*Math.cos(yaw));
                y+=(float)Math.sin(pitch);
                z=(float)(z+horizontal*Math.sin(yaw));
            }
            pitch*=steep ? 0.92f : 0.7f;
            yaw+=dyaw*0.1f;
            pitch+=dpitch*0.1f;
            float diff=r.nextFloat()-r.nextFloat();
            dpitch=dpitch*0.9f+r.nextFloat()*2*diff;
            diff=r.nextFloat()-r.nextFloat();
            dyaw=dyaw*0.75f+r.nextFloat()*4*diff;
            if (!room && step==branchAt && width>1) {
                float left=r.nextFloat()*0.5f+0.5f;
                tunnel(blocks,cx,cz,r.nextInt(),x,y,z,left,yaw-PI/2,pitch*(1f/3),step,end,1);
                float right=r.nextFloat()*0.5f+0.5f;
                tunnel(blocks,cx,cz,r.nextInt(),x,y,z,right,yaw+PI/2,pitch*(1f/3),step,end,1);
                return;
            }
            if (!room && r.nextInt(4)==0) continue;
            float dx=x-centerX, dz=z-centerZ, remaining=end-step, reach=width+18;
            if (dz*dz+dx*dx-remaining*remaining>reach*reach) return;
            if (x<centerX-radius*2-16 || x>centerX+16+radius*2 ||
                z<centerZ-radius*2-16 || z>centerZ+16+radius*2) continue;
            if (carveSection(blocks,cx,cz,x,y,z,radius,radius*stretch) && room) return;
        }
    }

    private boolean carveSection(byte[] blocks,int cx,int cz,float x,float y,float z,float radius,float vertical) {
        int x0=Math.max(0,(int)Math.floor(x-radius)-cx*16-1), x1=Math.min(16,(int)Math.floor(x+radius)-cx*16+1);
        int z0=Math.max(0,(int)Math.floor(z-radius)-cz*16-1), z1=Math.min(16,(int)Math.floor(z+radius)-cz*16+1);
        int y0=Math.max(1,(int)Math.floor(y-vertical)-1), y1=Math.min(120,(int)Math.floor(y+vertical)+1);
        for (int px=x0;px<x1;px++) for (int pz=z0;pz<z1;pz++) {
            boolean edge=px==x0 || px==x1-1 || pz==z0 || pz==z1-1;
            int base=(px*16+pz)*128;
            for (int py=y1+1;py>=y0-1;py--) {
                if (nether ? blocks[base+py]==10 || blocks[base+py]==11 : blocks[base+py]==8 || blocks[base+py]==9) return false;
                if (!edge && py>y0) py=y0;
            }
        }
        float ir=1/radius, iv=1/vertical;
        for (int px=x0;px<x1;px++) for (int pz=z0;pz<z1;pz++) {
            float dx=(px+cx*16+(0.5f-x))*ir, dz=(pz+cz*16+(0.5f-z))*ir;
            float horizontal=dx*dx+dz*dz;
            boolean grass=false;
            int base=(px*16+pz)*128;
            for (int py=y1-1;py>=y0;py--) {
                float dy=(py+(0.5f-y))*iv;
                if (dy<=-0.7f || horizontal+dy*dy>=1) continue;
                // Native uses y1 as the initial byte index for sample coordinate y1-1.
                int index=base+py+1;
                int id=Byte.toUnsignedInt(blocks[index]), above=Byte.toUnsignedInt(blocks[index+1]);
                if(nether) {
                    if(id==87 || id==2 || id==3) blocks[index]=0;
                    continue;
                }
                grass|=id==2;
                if (!(id==1 || id==2 || id==3 || id==24 || id==110 || id==159 || id==172 || id==243 || (id==12 && above!=9))) continue;
                if (py<10) blocks[index]=11;
                else {
                    if (above==12 && blocks[index+2]==12 && blocks[index+3]==12) blocks[index+1]=24;
                    blocks[index]=0;
                    if (grass && blocks[index-1]==3) blocks[index-1]=2;
                }
            }
        }
        return true;
    }
}
