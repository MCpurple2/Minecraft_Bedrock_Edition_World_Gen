package net.mcpe.worldgen.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.mcpe.worldgen.core.PeWorldBorderLocation;

public record PeFiniteBounds(int centerX, int centerZ) implements CustomPayload {
    public static final Id<PeFiniteBounds> ID = new Id<>(Identifier.of("pe_worldgen", "finite_bounds"));
    public static final PacketCodec<RegistryByteBuf, PeFiniteBounds> CODEC = PacketCodec.tuple(
        PacketCodecs.INTEGER, PeFiniteBounds::centerX, PacketCodecs.INTEGER, PeFiniteBounds::centerZ, PeFiniteBounds::new);

    static PeFiniteBounds from(ServerWorld world) {
        if (!(world.getChunkManager().getChunkGenerator() instanceof PeChunkGenerator generator) || !generator.isOld())
            return null;
        var border = generator.finiteBorder();
        return new PeFiniteBounds(border.centerX(), border.centerZ());
    }

    public PeWorldBorderLocation location() { return new PeWorldBorderLocation(centerX, centerZ); }

    @Override public Id<PeFiniteBounds> getId() { return ID; }
}
