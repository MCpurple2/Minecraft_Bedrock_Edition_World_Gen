package net.mcpe.worldgen.fabric.mixin;

import java.util.List;
import net.minecraft.structure.OceanMonumentGenerator;
import net.minecraft.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OceanMonumentGenerator.Base.class)
public interface MonumentRoomsAccessor {
    @Accessor("children")
    List<? extends StructurePiece> peWorldgen$getRooms();
}
