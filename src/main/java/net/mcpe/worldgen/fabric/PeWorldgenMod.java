package net.mcpe.worldgen.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class PeWorldgenMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(PeFiniteBounds.ID, PeFiniteBounds.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var packet = PeFiniteBounds.from(server.getOverworld());
            for (var world : server.getWorlds()) {
                PeOldWorldBorder.install(world);
                if (world.getBiomeAccess() instanceof PeBorderBiomeAccess access)
                    access.setBounds(PeOldWorldBorder.bounds(world) == null ? null : packet);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var bounds = PeFiniteBounds.from(server.getOverworld());
            if (bounds != null && ServerPlayNetworking.canSend(handler, PeFiniteBounds.ID)) sender.sendPacket(bounds);
        });
        Registry.register(Registries.CHUNK_GENERATOR, Identifier.of("pe_worldgen", "pe_0111"), PeChunkGenerator.CODEC);
        Registry.register(Registries.CHUNK_GENERATOR, Identifier.of("pe_worldgen", "pe_012_nether"), PeNetherChunkGenerator.CODEC);
        Registry.register(Registries.BIOME_SOURCE, Identifier.of("pe_worldgen", "pe_0111"), PeBiomeSource.CODEC);
    }
}

