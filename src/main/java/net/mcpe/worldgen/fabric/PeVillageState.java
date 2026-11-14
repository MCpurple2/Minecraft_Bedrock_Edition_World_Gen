package net.mcpe.worldgen.fabric;

import com.mojang.serialization.Codec;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.datafixer.DataFixTypes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Native village piece progress survives server restart independently of JE structures. */
final class PeVillageState extends PersistentState {
    private static final Codec<PeVillageState> CODEC=Codec.unboundedMap(Codec.STRING,Codec.STRING)
        .fieldOf("villages").codec().xmap(PeVillageState::new,s->Map.copyOf(s.values));
    static final PersistentStateType<PeVillageState> TYPE=new PersistentStateType<>(
        "pe_worldgen_villages",PeVillageState::new,CODEC,DataFixTypes.LEVEL);
    final Map<String,String> values;
    PeVillageState(){values=new ConcurrentHashMap<>();}
    PeVillageState(Map<String,String> values){this.values=new ConcurrentHashMap<>(values);}
    String get(String key){return values.get(key);}
    void put(String key,String value){values.put(key,value);markDirty();}
}
