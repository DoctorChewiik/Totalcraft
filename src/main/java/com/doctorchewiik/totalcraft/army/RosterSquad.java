package com.doctorchewiik.totalcraft.army;

import com.doctorchewiik.totalcraft.data.GameData;
import com.doctorchewiik.totalcraft.data.UnitType;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** A squad owned by a player between battles. */
public class RosterSquad {
    public static final int MAX_LEVEL = 3;

    public final UUID id;
    public final String typeId;
    public int alive;
    /** Average health of the living members, from 0 to 1. */
    public float health;
    public int level;

    public RosterSquad(UUID id, String typeId, int alive, float health, int level) {
        this.id = id;
        this.typeId = typeId;
        this.alive = alive;
        this.health = health;
        this.level = level;
    }

    public UnitType type() {
        return GameData.unit(typeId);
    }

    public static double levelMultiplier(int level) {
        return 1.0 + 0.15 * (level - 1);
    }

    /** Fraction of the squad's full strength that is missing (dead members and wounds). */
    public double missingStrength() {
        UnitType t = type();
        if (t == null) {
            return 0;
        }
        return 1.0 - (alive * (double) health) / t.squadSize();
    }

    public int healCost() {
        UnitType t = type();
        return t == null ? 0 : (int) Math.ceil(t.cost() * missingStrength() * 0.6);
    }

    public int upgradeCost() {
        UnitType t = type();
        return t == null || level >= MAX_LEVEL ? 0 : (int) Math.ceil(t.cost() * 0.5 * level);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("type", typeId);
        tag.putInt("alive", alive);
        tag.putFloat("health", health);
        tag.putInt("level", level);
        return tag;
    }

    public static RosterSquad load(CompoundTag tag) {
        return new RosterSquad(tag.getUUID("id"), tag.getString("type"), tag.getInt("alive"),
                tag.getFloat("health"), Math.max(1, tag.getInt("level")));
    }
}
