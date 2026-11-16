package net.mcpe.worldgen.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class PeWorldgenMod implements ModInitializer {
    private static final Identifier ROSE_ID = Identifier.of("pe_worldgen", "cyan_rose");
    private static final RegistryKey<Block> ROSE_KEY = RegistryKey.of(RegistryKeys.BLOCK, ROSE_ID);
    public static final FlowerBlock CYAN_ROSE = new FlowerBlock(((FlowerBlock) Blocks.POPPY).getStewEffects(),
            AbstractBlock.Settings.copy(Blocks.POPPY).registryKey(ROSE_KEY));

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, ROSE_KEY, CYAN_ROSE);
        var itemKey = RegistryKey.of(RegistryKeys.ITEM, ROSE_ID);
        Registry.register(Registries.ITEM, itemKey, new BlockItem(CYAN_ROSE,
                new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey()));
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

