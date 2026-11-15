package net.mcpe.worldgen.fabric;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.mcpe.worldgen.core.PeWorldBorderLocation;

public record PeFiniteBounds(int centerX, int centerZ, int width, boolean syncNether) implements CustomPayload {
    public static final Id<PeFiniteBounds> ID = new Id<>(Identifier.of("pe_worldgen", "finite_bounds_v2"));
    public static final PacketCodec<RegistryByteBuf, PeFiniteBounds> CODEC = PacketCodec.tuple(
        PacketCodecs.INTEGER, PeFiniteBounds::centerX, PacketCodecs.INTEGER, PeFiniteBounds::centerZ,
        PacketCodecs.INTEGER, PeFiniteBounds::width, PacketCodecs.BOOLEAN, PeFiniteBounds::syncNether, PeFiniteBounds::new);

    static PeFiniteBounds from(ServerWorld world) {
        if (!(world.getChunkManager().getChunkGenerator() instanceof PeChunkGenerator generator))
            return null;
        var border = generator.finiteBorder();
        return border == null ? null : new PeFiniteBounds(border.centerX(), border.centerZ(), border.width(),
            generator.isOld() && generator.bedrock().syncNether());
    }

    public PeWorldBorderLocation location() { return new PeWorldBorderLocation(centerX, centerZ, width); }

    @Override public Id<PeFiniteBounds> getId() { return ID; }
}
