package com.doctorchewiik.totalcraft.battle;

import com.doctorchewiik.totalcraft.army.RosterSquad;
import com.doctorchewiik.totalcraft.data.Modifier;
import com.doctorchewiik.totalcraft.data.UnitType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A group of mobs fighting as one unit. */
public class Squad {
    public enum Order { HOLD, MOVE, ATTACK }

    public record Buff(double damage, double defense, double speed, long until) {
    }

    public final int id;
    public final Battle battle;
    public final Team team;
    /** Owning player, or null for the AI. */
    public final UUID owner;
    /** Matching roster entry, or null for the AI. */
    public final UUID rosterId;
    public final UnitType type;
    public final int level;
    public final int maxCount;
    public final List<Mob> members = new ArrayList<>();

    public Order order = Order.HOLD;
    public Vec3 anchor;
    public Vec3 facing;
    public Squad target;
    /** Enemy squad engaged on its own initiative while holding. */
    public Squad engaged;
    public double morale;
    public boolean routing;
    public long lastHurtTick = -1000;
    public final int[] cooldowns;
    public final List<Buff> buffs = new ArrayList<>();
    public int deaths;

    final Map<Integer, Long> nextAttack = new HashMap<>();
    final Map<Integer, Vec3> lastNavTarget = new HashMap<>();
    final Map<Integer, Mob> memberTargets = new HashMap<>();

    public Squad(int id, Battle battle, Team team, UUID owner, UUID rosterId, UnitType type, int level, Vec3 anchor,
                 Vec3 facing) {
        this.id = id;
        this.battle = battle;
        this.team = team;
        this.owner = owner;
        this.rosterId = rosterId;
        this.type = type;
        this.level = level;
        this.maxCount = type.squadSize();
        this.anchor = anchor;
        this.facing = facing;
        this.morale = type.morale();
        this.cooldowns = new int[type.abilities().size()];
    }

    public boolean alive() {
        return !members.isEmpty();
    }

    /** Can still fight: alive and not fleeing. */
    public boolean active() {
        return alive() && !routing;
    }

    public Vec3 center() {
        if (members.isEmpty()) {
            return anchor;
        }
        double x = 0, y = 0, z = 0;
        for (Mob m : members) {
            x += m.getX();
            y += m.getY();
            z += m.getZ();
        }
        int n = members.size();
        return new Vec3(x / n, y / n, z / n);
    }

    public float healthFraction() {
        if (members.isEmpty()) {
            return 0;
        }
        float sum = 0;
        for (Mob m : members) {
            sum += m.getHealth() / m.getMaxHealth();
        }
        return sum / members.size();
    }

    public double levelMultiplier() {
        return RosterSquad.levelMultiplier(level);
    }

    public void addBuff(Buff buff) {
        buffs.add(buff);
    }

    public double buffDamage(long tick) {
        double m = 1;
        for (Buff b : buffs) {
            if (b.until > tick) {
                m *= b.damage;
            }
        }
        return m;
    }

    public double buffDefense(long tick) {
        double m = 1;
        for (Buff b : buffs) {
            if (b.until > tick) {
                m *= b.defense;
            }
        }
        return m;
    }

    public double buffSpeed(long tick) {
        double m = 1;
        for (Buff b : buffs) {
            if (b.until > tick) {
                m *= b.speed;
            }
        }
        return m;
    }

    /** Multiplier on outgoing damage against {@code victim} (may be null). */
    public double attackMultiplier(Squad victim, long tick) {
        double m = levelMultiplier() * battle.map.multiplier(type, Modifier.Stat.DAMAGE) * buffDamage(tick);
        if (victim != null) {
            m *= type.bonusAgainst(victim.type);
        }
        return m;
    }

    /** Multiplier on incoming damage. */
    public double damageTakenMultiplier(long tick) {
        return buffDefense(tick) / battle.map.multiplier(type, Modifier.Stat.DEFENSE);
    }

    public double effectiveSpeed(long tick) {
        double s = type.speed() * battle.map.multiplier(type, Modifier.Stat.SPEED) * buffSpeed(tick);
        return routing ? s * 1.15 : s;
    }
}
