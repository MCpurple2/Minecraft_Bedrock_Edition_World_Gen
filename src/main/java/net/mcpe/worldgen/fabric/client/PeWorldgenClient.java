package net.mcpe.worldgen.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mcpe.worldgen.fabric.PeBorderBiomeAccess;
import net.mcpe.worldgen.fabric.PeFiniteBounds;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

public final class PeWorldgenClient implements ClientModInitializer {
    private PeFiniteBounds bounds;

    @Override public void onInitializeClient() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> bounds = null);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> bounds = null);
        ClientPlayNetworking.registerGlobalReceiver(PeFiniteBounds.ID, (packet, context) -> {
            bounds = packet;
            apply(context.client().world);
        });
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> apply(world));
    }

    private void apply(ClientWorld world) {
        if (world != null && world.getBiomeAccess() instanceof PeBorderBiomeAccess access)
            access.setBounds(world.getRegistryKey().equals(World.OVERWORLD) || bounds != null && bounds.syncNether()
                && world.getRegistryKey().equals(World.NETHER) ? bounds : null);
    }
}
