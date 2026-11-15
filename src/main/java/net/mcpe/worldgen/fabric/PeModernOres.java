package net.mcpe.worldgen.fabric;

import java.util.Optional;
import java.util.stream.Stream;
import net.mcpe.worldgen.core.PeContent;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.feature.PlacedFeature;

/** 1.18.2 placement counts and height providers, evaluated against the actual PE/JE biome at each deposit. */
final class PeModernOres {
    private static final String[] TYPES = {"coal_upper","coal_lower","iron_upper","iron_middle","iron_small",
        "gold","gold_lower","redstone","redstone_lower","diamond","diamond_large","diamond_buried",
        "lapis","lapis_buried","copper","gold_extra","emerald"};
    private static RegistryKey<PlacedFeature> key(String ore) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE,Identifier.of("pe_worldgen","je118_ore_"+ore));
    }
    static void generate(StructureWorldAccess world, PeChunkGenerator generator, ChunkPos chunk,long seed,PeContentSettings options) {
        boolean modern=options.has(PeContent.MODERN_ORES);
        boolean dripCopper=options.has(PeContent.DRIPSTONE_CAVES) && options.has(PeContent.COPPER_ORE);
        if(!modern && !dripCopper)return;
        var features=world.getRegistryManager().getOrThrow(RegistryKeys.PLACED_FEATURE);
        var largeCopper=features.getOrThrow(key("copper_large")).value().feature();
        var random=new ChunkRandom(new LocalRandom(0));
        long populationSeed=random.setPopulationSeed(seed,chunk.getStartX(),chunk.getStartZ());
        var context=new FeaturePlacementContext(world,generator,Optional.empty());
        for(int i=0;i<TYPES.length;i++) {
            String type=TYPES[i];
            boolean copper=type.equals("copper");
            if(copper && !options.has(PeContent.COPPER_ORE) || !modern && !copper)continue;
            random.setDecoratorSeed(populationSeed,i,6);
            var placed=features.getOrThrow(key(type)).value();
            if(!copper && !type.equals("gold_extra") && !type.equals("emerald")) {
                placed.generateUnregistered(world,generator,random,chunk.getStartPos());
                continue;
            }
            Stream<BlockPos> positions=Stream.of(chunk.getStartPos());
            for(var modifier:placed.placementModifiers())
                positions=positions.flatMap(p->modifier.getPositions(context,random,p));
            positions.forEach(p->{
                var biome=world.getBiome(p);
                boolean drip=dripCopper && biome.matchesKey(net.minecraft.world.biome.BiomeKeys.DRIPSTONE_CAVES);
                if(copper && !modern && !drip || type.equals("gold_extra") && !biome.isIn(BiomeTags.IS_BADLANDS)
                        || type.equals("emerald") && !(biome.isIn(BiomeTags.IS_MOUNTAIN) || biome.isIn(BiomeTags.IS_HILL)))return;
                var configured=copper && drip ? largeCopper : placed.feature();
                configured.value().generate(world,generator,random,p);
            });
        }
    }
    private PeModernOres() {}
}
