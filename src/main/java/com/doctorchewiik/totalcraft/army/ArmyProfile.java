package com.doctorchewiik.totalcraft.army;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A player's persistent army: gold, record and roster. */
public class ArmyProfile {
    public static final int START_MONEY = 1500;
    public static final int MAX_ROSTER = 10;

    public final UUID player;
    public int money = START_MONEY;
    public int wins;
    public int losses;
    public final List<RosterSquad> roster = new ArrayList<>();
    /** Where to send the player back after a battle; kept on disk in case of a crash or logout. */
    public CompoundTag pendingReturn;

    public ArmyProfile(UUID player) {
        this.player = player;
    }

    public RosterSquad squad(UUID id) {
        for (RosterSquad s : roster) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", player);
        tag.putInt("money", money);
        tag.putInt("wins", wins);
        tag.putInt("losses", losses);
        ListTag list = new ListTag();
        roster.forEach(s -> list.add(s.save()));
        tag.put("roster", list);
        if (pendingReturn != null) {
            tag.put("return", pendingReturn);
        }
        return tag;
    }

    public static ArmyProfile load(CompoundTag tag) {
        ArmyProfile p = new ArmyProfile(tag.getUUID("player"));
        p.money = tag.getInt("money");
        p.wins = tag.getInt("wins");
        p.losses = tag.getInt("losses");
        ListTag list = tag.getList("roster", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            p.roster.add(RosterSquad.load(list.getCompound(i)));
        }
        if (tag.contains("return", Tag.TAG_COMPOUND)) {
            p.pendingReturn = tag.getCompound("return");
        }
        return p;
    }
}
