package com.doctorchewiik.totalcraft.army;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-level storage of every player's army. */
public class ArmyData extends SavedData {
    private final Map<UUID, ArmyProfile> profiles = new HashMap<>();

    public static ArmyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ArmyData::new, ArmyData::load, null), "totalcraft_armies");
    }

    public ArmyProfile profile(UUID player) {
        ArmyProfile p = profiles.get(player);
        if (p == null) {
            p = new ArmyProfile(player);
            profiles.put(player, p);
            setDirty();
        }
        return p;
    }

    public static ArmyData load(CompoundTag tag, HolderLookup.Provider registries) {
        ArmyData data = new ArmyData();
        ListTag list = tag.getList("profiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ArmyProfile p = ArmyProfile.load(list.getCompound(i));
            data.profiles.put(p.player, p);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        profiles.values().forEach(p -> list.add(p.save()));
        tag.put("profiles", list);
        return tag;
    }
}
