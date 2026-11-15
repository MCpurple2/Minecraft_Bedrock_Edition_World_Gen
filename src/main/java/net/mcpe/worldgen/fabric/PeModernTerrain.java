package net.mcpe.worldgen.fabric;

import java.util.EnumSet;
import java.util.Set;
import net.mcpe.worldgen.core.PeContent;
import net.mcpe.worldgen.core.PeWorldGenerator.TerrainChunk;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.densityfunction.*;
import net.minecraft.world.gen.noise.*;
import static net.mcpe.worldgen.core.PeContent.*;

/** Composes PE columns with individually enabled Java noise, fluid and vein samplers. */
final class PeModernTerrain {
    static final Set<PeContent> FEATURES = Set.copyOf(EnumSet.range(NOISE_CAVES,MANGROVE_SWAMPS));
    static final Set<String> MOUNTAINS = Set.of("meadow","grove","snowy_slopes","jagged_peaks","frozen_peaks","stony_peaks");
    private static final BlockState AIR=Blocks.AIR.getDefaultState(), STONE=Blocks.STONE.getDefaultState(),
            DEEP=Blocks.DEEPSLATE.getDefaultState(), BEDROCK=Blocks.BEDROCK.getDefaultState();
    private final PeContentSettings flags;
    private final PeModernInputs inputs;
    private final ChunkGeneratorSettings settings;
    private final NoiseConfig noise;
    private final AquiferSampler.FluidLevelSampler fluids;
    private final boolean below,caves,aquifers,copper,iron,mountains,mangrove,hillBiomes;
    PeModernTerrain(PeModernInputs inputs,long seed,PeContentSettings flags) {
        this.inputs=inputs;this.flags=flags;
        below=flags.has(DEEP_TERRAIN); caves=flags.has(NOISE_CAVES); aquifers=flags.has(AQUIFERS);
        copper=flags.has(COPPER_VEINS);iron=flags.has(IRON_VEINS);mountains=flags.has(MOUNTAIN_TERRAIN);
        hillBiomes=flags.has(MOUNTAIN_BIOMES);mangrove=flags.has(MANGROVE_SWAMPS);
        var base=inputs.overworld().value();
        var r=base.noiseRouter();
        var density=caves ? new DensityFunctionTypes.RegistryEntryHolder(inputs.caveMask()) : DensityFunctionTypes.constant(1);
        var router=new NoiseRouter(r.barrierNoise(),r.fluidLevelFloodednessNoise(),r.fluidLevelSpreadNoise(),r.lavaNoise(),
                r.temperature(),r.vegetation(),r.continents(),r.erosion(),r.depth(),r.ridges(),
                r.preliminarySurfaceLevel(),density,r.veinToggle(),r.veinRidged(),r.veinGap());
        settings=new ChunkGeneratorSettings(base.generationShapeConfig(),base.defaultBlock(),base.defaultFluid(),router,
                base.surfaceRule(),base.spawnTarget(),base.seaLevel(),false,aquifers,copper||iron,false);
        noise=NoiseConfig.create(settings,inputs.noises(),seed);
        var water=new AquiferSampler.FluidLevel(base.seaLevel(),base.defaultFluid());
        var lava=new AquiferSampler.FluidLevel(-54,Blocks.LAVA.getDefaultState());
        var dry=new AquiferSampler.FluidLevel(-1024,AIR);
        fluids=aquifers ? (x,y,z)->y < -54 ? lava : water : (x,y,z)->dry;
    }

    boolean changesBlocks() { return below||caves||aquifers||copper||iron||mountains||mangrove||hillBiomes; }
    private String vanillaBiome(int qx,int qy,int qz) {
        var climate=noise.getMultiNoiseSampler().sample(qx,qy,qz);
        return inputs.biomeParameters().value().getEntries().getValue(climate).getKey().orElseThrow().getValue().getPath();
    }
    private final java.util.Map<Long,String> surfaceCandidates = new java.util.LinkedHashMap<>(512,.75f,true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Long,String> e) { return size()>8192; }
    };
    private synchronized String surfaceCandidate(int qx,int qz) {
        long key=((long)qx<<32)|Integer.toUnsignedLong(qz);
        return surfaceCandidates.computeIfAbsent(key,k->vanillaBiome(qx,20,qz));
    }
    String surfaceBiome(int qx,int qz,boolean highland) {
        if (!mangrove && !hillBiomes) return null;
        String candidate=surfaceCandidate(qx,qz);
        if (mangrove && candidate.equals("mangrove_swamp")) return candidate;
        if (hillBiomes && (highland || mountains) && MOUNTAINS.contains(candidate)) return candidate;
        return null;
    }
    String caveBiome(int qx,int qy,int qz) {
        if (qy<0 && !below || qy>12 || !(flags.has(LUSH_CAVES)||flags.has(DRIPSTONE_CAVES)||flags.has(DEEP_DARK))) return null;
        String b=vanillaBiome(qx,qy,qz);
        return switch(b) {
            case "lush_caves" -> flags.has(LUSH_CAVES)?b:null;
            case "dripstone_caves" -> flags.has(DRIPSTONE_CAVES)?b:null;
            case "deep_dark" -> flags.has(DEEP_DARK)?b:null;
            default -> null;
        };
    }
    static int landHeight(TerrainChunk base,int column) {
        for (int y=127;y>=0;y--) {
            int id=Byte.toUnsignedInt(base.blocks()[column*128+y]);
            if (id!=0 && id!=8 && id!=9 && id!=10 && id!=11 && id!=79) return y;
        }
        return -1;
    }

    // Index is (localX * 16 + localZ) * 384 + y + 64. Null denotes air.
    BlockState[] generate(TerrainChunk base,int chunkX,int chunkZ) {
        BlockState[] result=new BlockState[256*384];
        int[] tops=new int[256]; int startX=chunkX<<4,startZ=chunkZ<<4;
        var sampler=new Sampler(startX,startZ);
        for(int lx=0;lx<16;lx++) for(int lz=0;lz<16;lz++) {
            int col=lx*16+lz,x=startX+lx,z=startZ+lz,off=col*384+64;
            int original=landHeight(base,col),top=original;
            if (mountains && original>=0) {
                int height=sampler.estimateSurfaceHeight(x,z);
                // Blend the native Java highland surface into PE columns. A PE ocean
                // under a Java peak can become an island when mountain terrain is enabled.
                double weight=Math.clamp((height-88)/32.0,0,1);
                top=Math.min(315,original+(int)Math.round(Math.max(0,height-original)*weight));
            }
            tops[col]=top;
            String biome=surfaceBiome(x>>2,z>>2,original>=78);
            for(int y=below?-64:0;y<=Math.max(127,top);y++) {
                BlockState block;
                if (y<0) {
                    boolean rock=y < -59 && base.blocks()[col*128+y+64]==7;
                    block=y==-64||rock?BEDROCK:DEEP;
                } else if(y<128) {
                    int i=col*128+y,id=Byte.toUnsignedInt(base.blocks()[i]);
                    block=id==0?AIR:LegacyBlocks.state(id,base.data()[i]);
                    if(below && block.isOf(Blocks.BEDROCK))block=DEEP;
                } else block=AIR;
                if(top>original && y>=original && y<=top) {
                    block=y==top?Blocks.GRASS_BLOCK.getDefaultState():y>=top-3?Blocks.DIRT.getDefaultState():STONE;
                }
                if(biome!=null && y<=top && y>top-4 && !block.isAir()) {
                    block=switch(biome) {
                        case "mangrove_swamp" -> Blocks.MUD.getDefaultState();
                        case "snowy_slopes","grove","jagged_peaks" -> y==top?Blocks.SNOW_BLOCK.getDefaultState():block;
                        case "frozen_peaks" -> y==top?Blocks.PACKED_ICE.getDefaultState():block;
                        case "stony_peaks" -> STONE;
                        default -> block;
                    };
                }
                if(!block.isAir())result[off+y]=block;
            }
        }
        if(!(aquifers||caves||copper||iron))return result;
        int minY=below?-64:0;
        sampler.sampleStartDensity();
        for(int cellX=0;cellX<4;cellX++) {
            sampler.sampleEndDensity(cellX);
            for(int cellZ=0;cellZ<4;cellZ++) {
                int max=0;
                for(int dx=0;dx<4;dx++)for(int dz=0;dz<4;dz++)max=Math.max(max,tops[(4*cellX+dx)*16+4*cellZ+dz]);
                for(int cy=Math.floorDiv(max,8);cy>=minY/8;cy--) {
                    sampler.onSampledCellCorners(cy+8,cellZ);
                    for(int dy=7;dy>=0;dy--) {
                        int y=cy*8+dy;
                        sampler.interpolateY(y,dy/8.0);
                        for(int dx=0;dx<4;dx++) {
                            int lx=cellX*4+dx;
                            sampler.interpolateX(startX+lx,dx/4.0);
                            for(int dz=0;dz<4;dz++) {
                                int lz=cellZ*4+dz,col=lx*16+lz,i=col*384+y+64;
                                if(y>tops[col] || result[i]==BEDROCK)continue;
                                sampler.interpolateZ(startZ+lz,dz/4.0);
                                BlockState was=result[i];
                                if(was==null) {
                                    if(aquifers) {
                                        var fluid=sampler.getAquiferSampler().apply(sampler,-1);
                                        if(fluid!=null&&!fluid.getFluidState().isEmpty())result[i]=fluid;
                                    }
                                    continue;
                                }
                                if(!was.getFluidState().isEmpty())continue;
                                BlockState material=sampler.material();
                                if(material==null)continue;
                                if(material.isAir() || !material.getFluidState().isEmpty()) {
                                    if(caves)result[i]=material.isAir()?null:material;
                                } else if ((y>=0?copper:iron) && (was==STONE||was==DEEP))result[i]=material;
                            }
                        }
                    }
                }
            }
            sampler.swapBuffers();
        }
        sampler.stopInterpolation();
        return result;
    }
    private final class Sampler extends ChunkNoiseSampler {
        Sampler(int x,int z) {
            super(4,noise,x,z,settings.generationShapeConfig(),net.minecraft.world.gen.StructureWeightSampler.field_61464,settings,fluids,Blender.getNoBlending());
        }
        BlockState material() { return sampleBlockState(); }
    }
}
