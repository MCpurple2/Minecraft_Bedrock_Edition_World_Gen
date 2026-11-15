package net.mcpe.worldgen.fabric;

import net.mcpe.worldgen.fabric.PeContentSettings.Aquatic;

import net.mcpe.worldgen.core.PeContent;
import net.mcpe.worldgen.core.PeRandom;
import net.mcpe.worldgen.core.PeVillagePlacement;
import net.minecraft.block.Blocks;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.SpreadType;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import java.util.stream.Stream;

/** Narrow opt-ins: only the requested vanilla structure sets and placed features enter this generator. */
final class PeJavaContent {
    static GenerationSettings features(RegistryEntry<Biome> biome, PeContentSettings options) {
        if (!options.overworldContent()) return GenerationSettings.INSTANCE;
        var builder = new GenerationSettings.Builder();
        var steps = biome.value().getGenerationSettings().getFeatures();
        for (int i = 0; i < steps.size(); i++) {
            for (var feature : steps.get(i)) {
                var key = feature.getKey().orElse(null);
                if (key == null || !key.getValue().getNamespace().equals("minecraft")) continue;
                String id = key.getValue().getPath();
                boolean bamboo = id.equals("bamboo") || id.equals("bamboo_light");
                if (options.enabled(Aquatic.SEAGRASS) && id.startsWith("seagrass_")
                    || options.enabled(Aquatic.KELP) && id.startsWith("kelp_")
                    || options.enabled(Aquatic.SEA_PICKLES) && id.equals("sea_pickle")
                    || options.enabled(Aquatic.CORAL_REEF) && id.equals("warm_ocean_vegetation")
                    || options.enabled(Aquatic.ICEBERG) && (id.equals("iceberg_packed") || id.equals("iceberg_blue")) || options.has(PeContent.BAMBOO) && bamboo
                    || options.has(PeContent.FOSSILS) && id.equals("fossil_upper")) builder.addFeature(i, feature);
            }
        }
        return builder.build();
    }

    static Stream<RegistryEntry<StructureSet>> structures(RegistryWrapper<StructureSet> registry, PeContentSettings settings, net.mcpe.worldgen.core.PeWorldGenerator core) {
        return registry.streamEntries().<RegistryEntry<StructureSet>>mapMulti((entry, output) -> {
            var key = entry.registryKey().getValue();
            if (!key.getNamespace().equals("minecraft")) return;
            switch (key.getPath()) {
                case "shipwrecks" -> { if (settings.enabled(Aquatic.SHIPWRECK)) output.accept(entry); }
                case "ocean_ruins" -> { if (settings.enabled(Aquatic.OCEAN_RUINS)) output.accept(entry); }
                case "buried_treasures" -> { if (settings.enabled(Aquatic.BURIED_TREASURE)) output.accept(entry); }
                case "woodland_mansions" -> { if (settings.has(PeContent.MANSIONS)) output.accept(entry); }
                case "ruined_portals" -> { if (settings.has(PeContent.OVERWORLD_PORTALS)) output.accept(entry); }
                case "pillager_outposts" -> { if (settings.has(PeContent.OUTPOSTS)) output.accept(entry); }
                case "villages" -> {
                    if (settings.villages()) output.accept(RegistryEntry.of(new StructureSet(entry.value().structures(), new VillagePlacement(core,settings))));
                }
            }
        });
    }

    private static final class VillagePlacement extends RandomSpreadStructurePlacement {
        private final net.mcpe.worldgen.core.PeWorldGenerator core;
        private final PeContentSettings settings;
        VillagePlacement(net.mcpe.worldgen.core.PeWorldGenerator core, PeContentSettings settings) { super(40, 12, SpreadType.LINEAR, 10387312); this.core=core; this.settings=settings; }
        private boolean candidate(long seed,int x,int z,PeRandom random) {
            if(!core.isInsideChunk(x,z) || !PeVillagePlacement.isFeatureChunk(seed,x,z,1,random)) return false;
            int biome=core.structureBiomeAt(x,z);
            return PeContent.villageBiome(biome,settings.flags(),core.release());
        }
        @Override protected boolean isStartChunk(StructurePlacementCalculator calculator, int x, int z) {
            int ox = Math.floorMod(x, 40), oz = Math.floorMod(z, 40);
            return ox < 28 && oz < 28 && candidate(calculator.getStructureSeed(), x, z, new PeRandom(0));
        }
        // Vanilla's locate search needs an actual candidate in this cell. PE can have several or none.
        @Override public ChunkPos getStartChunk(long seed, int x, int z) {
            int baseX = Math.floorDiv(x,40)*40, baseZ = Math.floorDiv(z,40)*40;
            var random = new PeRandom(0);
            for (int dz=0; dz<28; dz++) for (int dx=0; dx<28; dx++)
                if (candidate(seed,baseX+dx,baseZ+dz,random)) return new ChunkPos(baseX+dx,baseZ+dz);
            return new ChunkPos(baseX+39,baseZ+39);
        }
    }

    static void beesAndBerries(StructureWorldAccess world, ChunkPos chunk, long seed, net.mcpe.worldgen.core.PeWorldGenerator core, PeContentSettings settings) {
        var random = new LocalRandom(seed ^ chunk.x * 341873128712L ^ chunk.z * 132897987541L);
        int[] biomes = core.biomeArea(chunk.getStartX(),chunk.getStartZ(),16,16);
        for (int x=chunk.getStartX(); x<=chunk.getEndX(); x++) for (int z=chunk.getStartZ(); z<=chunk.getEndZ(); z++) {
            int biome = biomes[(x & 15) + 16*(z & 15)];
            var base = new BlockPos.Mutable(x, 0, z);
            if ((biome==29 || biome==157) && settings.has(PeContent.DARK_BIRCH) && random.nextInt(110)==0) {
                int y=world.getTopY(net.minecraft.world.Heightmap.Type.OCEAN_FLOOR_WG,x,z);
                while(y>1 && !world.getBlockState(base.set(x,y-1,z)).isOf(Blocks.GRASS_BLOCK) && y>50) y--;
                net.mcpe.worldgen.core.PeTrees.Selected.BIRCH.place(new FabricPopulationWorld(world,core),
                    new PeRandom(random.nextLong()),x,y,z);
            }
            if (settings.has(PeContent.FLOWERS) && (biome==1 || biome==4 || biome==132) && random.nextInt(128)==0) {
                var flower=(biome==4 || biome==132 ? Blocks.LILY_OF_THE_VALLEY : Blocks.CORNFLOWER).getDefaultState();
                int y=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
                base.setY(y);
                if(y>0 && y<128 && world.isAir(base) && flower.canPlaceAt(world,base)) world.setBlockState(base,flower,2);
            }
            boolean giant=biome==32 || biome==33 || biome==160 || biome==161;
            if (giant && settings.has(PeContent.GIANT_BERRIES) || (biome==5 || biome==19 || biome==30 || biome==31) && settings.has(PeContent.BERRIES)) {
                if (random.nextInt(64) != 0) continue;
                int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
                base.setY(y);
                var berry = Blocks.SWEET_BERRY_BUSH.getDefaultState().with(net.minecraft.block.SweetBerryBushBlock.AGE, 2 + random.nextInt(2));
                if (y > 0 && y < 128 && world.isAir(base) && berry.canPlaceAt(world,base)) world.setBlockState(base,berry,2);
                continue;
            }
            int chance = switch(biome) { case 1,129,132 -> 20; case 4,18,27,28,155,156 -> 500; default -> 0; };
            if (!settings.has(PeContent.NESTS) || chance == 0) continue;
            int top=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,x,z);
            if(top<4 || !world.getBlockState(base.set(x,top-1,z)).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) continue;
            int trunk=top-2;
            while(trunk>=2 && world.getBlockState(base.setY(trunk)).isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) trunk--;
            if(trunk<2) continue;
            var log=world.getBlockState(base.setY(trunk));
            if((!log.isOf(Blocks.OAK_LOG) && !log.isOf(Blocks.BIRCH_LOG)) || random.nextInt(chance)!=0) continue;
            java.util.ArrayList<BlockPos> sites=new java.util.ArrayList<>();
            for(int y=trunk; y>=Math.max(2,trunk-4); y--) {
                if(!world.getBlockState(base.setY(y)).equals(log) || !world.getBlockState(base.down()).equals(log)) break;
                for(var side:Direction.Type.HORIZONTAL) {
                    var nest=base.offset(side);
                    if(world.isAir(nest) && world.isAir(nest.south()) && core.isInsideChunk(nest.getX()>>4,nest.getZ()>>4))
                        sites.add(nest);
                }
                if(!sites.isEmpty()) break;
            }
            if(sites.isEmpty()) continue;
            BlockPos nest=sites.get(random.nextInt(sites.size()));
            world.setBlockState(nest,Blocks.BEE_NEST.getDefaultState().with(BeehiveBlock.FACING,Direction.SOUTH),2);
            if(world.getBlockEntity(nest) instanceof BeehiveBlockEntity hive) {
                int count=2+random.nextInt(2);
                for(int i=0;i<count;i++) hive.addBee(BeehiveBlockEntity.BeeData.create(random.nextInt(599)));
            }
        }
    }
    private PeJavaContent() {}
}
