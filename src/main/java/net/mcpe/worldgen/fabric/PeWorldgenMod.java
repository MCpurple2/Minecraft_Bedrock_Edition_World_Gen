package net.mcpe.worldgen.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class PeWorldgenMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> PeOldWorldBorder.install(server.getOverworld()));
        Registry.register(Registries.CHUNK_GENERATOR, Identifier.of("pe_worldgen", "pe_0111"), PeChunkGenerator.CODEC);
        Registry.register(Registries.BIOME_SOURCE, Identifier.of("pe_worldgen", "pe_0111"), PeBiomeSource.CODEC);
    }
}

