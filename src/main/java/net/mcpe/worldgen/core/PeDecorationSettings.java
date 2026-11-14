package net.mcpe.worldgen.core;

/** Mutable native BiomeDecorator constructor fields, including per-chunk
 * Plains overrides. Offsets: flowers d0,grass d4,dead d8,mushrooms dc,
 * reeds e0,cacti e4,gravel e8,sand ec,clay f0,bigMushrooms f4,lilies c8. */
public final class PeDecorationSettings {
    public float treeDensity;
    public int grass=1,flowers=2,bigMushrooms,deadBush,lilies,mushrooms,reeds,cacti,sand=3,clay=1,gravel=1;
    public static PeDecorationSettings fromBiome(int id){
        PeDecorationSettings s=new PeDecorationSettings();s.treeDensity=PeTrees.treeDensity(id);
        int b=id>=128?id-128:id;
        switch(b){
            case 1->{s.flowers=4;s.grass=10;}
            case 2,17->{s.deadBush=2;s.reeds=50;s.cacti=10;}
            case 4,18,27,28,29->{s.grass=0;if(id==132){s.flowers=100;s.grass=1;}}
            case 5,19,30,31->{s.grass=1;s.mushrooms=1;}
            case 32,33->{s.grass=7;s.mushrooms=3;s.deadBush=1;}
            case 6->{s.mushrooms=8;s.reeds=10;s.lilies=4;s.grass=5;s.flowers=1;s.deadBush=1;s.sand=0;s.gravel=0;}
            case 14,15->{s.flowers=-100;s.grass=-100;s.mushrooms=1;s.bigMushrooms=1;}
            case 16,25,26->{s.deadBush=0;s.reeds=0;s.cacti=0;}
            case 21,22,23->{s.flowers=4;s.grass=25;}
            case 35,36->{s.flowers=4;s.grass=20;if(id>=128){s.flowers=2;s.grass=5;}}
            case 37,38,39->{s.flowers=0;s.deadBush=20;s.reeds=3;s.cacti=5;}
            default->{}
        }
        return s;
    }
}
