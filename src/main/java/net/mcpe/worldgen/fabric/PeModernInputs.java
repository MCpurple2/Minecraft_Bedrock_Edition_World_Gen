package net.mcpe.worldgen.fabric;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler.NoiseParameters;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/** World-owned registries, also available before world creation in the preview. */
public record PeModernInputs(RegistryEntryLookup<NoiseParameters> noises,
        RegistryEntry<ChunkGeneratorSettings> overworld, RegistryEntry<DensityFunction> caveMask,
        RegistryEntry<MultiNoiseBiomeSourceParameterList> biomeParameters) {
    private static final RegistryKey<DensityFunction> CAVES = RegistryKey.of(RegistryKeys.DENSITY_FUNCTION,
            Identifier.of("pe_worldgen","pe_cave_mask"));
    static final MapCodec<PeModernInputs> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        RegistryOps.<NoiseParameters,PeModernInputs>getEntryLookupCodec(RegistryKeys.NOISE_PARAMETERS),
        RegistryOps.<ChunkGeneratorSettings,PeModernInputs>getEntryCodec(ChunkGeneratorSettings.OVERWORLD),
        RegistryOps.<DensityFunction,PeModernInputs>getEntryCodec(CAVES),
        RegistryOps.<MultiNoiseBiomeSourceParameterList,PeModernInputs>getEntryCodec(MultiNoiseBiomeSourceParameterLists.OVERWORLD)
    ).apply(i,PeModernInputs::new));

    public static PeModernInputs from(RegistryEntryLookup.RegistryLookup registries) {
        return new PeModernInputs(registries.getOrThrow(RegistryKeys.NOISE_PARAMETERS),
                registries.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.OVERWORLD),
                registries.getOrThrow(RegistryKeys.DENSITY_FUNCTION).getOrThrow(CAVES),
                registries.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
    }
}
