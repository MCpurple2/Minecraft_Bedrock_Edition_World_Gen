package net.mcpe.worldgen.fabric.mixin;

import net.mcpe.worldgen.fabric.PeAquaticCarvers;
import net.mcpe.worldgen.fabric.PeChunkGenerator;
import net.mcpe.worldgen.fabric.PeContentSettings.Aquatic;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunkGenerator.class)
abstract class NetherCanyonMixin {
    @Inject(method = "carve", at = @At("TAIL"))
    private void peWorldgen$netherCanyons(ChunkRegion region, long seed, NoiseConfig noise, BiomeAccess access,
            StructureAccessor structures, Chunk chunk, CallbackInfo ci) {
        var world = region.toServerWorld();
        if (world.getRegistryKey() == World.NETHER && world.getServer().getOverworld().getChunkManager().getChunkGenerator()
                instanceof PeChunkGenerator pe && pe.settings().enabled(Aquatic.CANYONS))
            PeAquaticCarvers.carve(seed, chunk, null, false, true);
    }
}
