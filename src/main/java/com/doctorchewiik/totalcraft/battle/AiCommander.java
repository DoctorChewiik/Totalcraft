package com.doctorchewiik.totalcraft.battle;

import com.doctorchewiik.totalcraft.data.Ability;
import net.minecraft.world.phys.Vec3;

/** Gives orders to the enemy squads once per second. */
final class AiCommander {
    private AiCommander() {
    }

    static void tick(Battle battle) {
        for (Squad s : battle.squads) {
            if (s.team != Team.ENEMY || !s.active()) {
                continue;
            }
            if (s.type.champion()) {
                castSpells(battle, s);
            }
            Squad best = pickTarget(battle, s);
            if (best == null) {
                continue;
            }
            if (s.type.ranged()) {
                Vec3 from = s.center();
                Vec3 to = best.center();
                double dist = from.distanceTo(to);
                if (dist <= s.type.range() * 0.9) {
                    if (s.order != Squad.Order.HOLD) {
                        s.order = Squad.Order.HOLD;
                        s.anchor = from;
                    }
                    s.engaged = best;
                } else {
                    Vec3 away = from.subtract(to).multiply(1, 0, 1).normalize();
                    s.order = Squad.Order.MOVE;
                    s.anchor = to.add(away.scale(s.type.range() * 0.75));
                    s.facing = away.scale(-1);
                }
            } else if (s.order != Squad.Order.ATTACK || s.target == null || !s.target.alive()
                    || battle.tick % 100 == 0) {
                s.order = Squad.Order.ATTACK;
                s.target = best;
            }
        }
    }

    private static Squad pickTarget(Battle battle, Squad s) {
        Squad best = null;
        double bestScore = -Double.MAX_VALUE;
        Vec3 from = s.center();
        for (Squad t : battle.squads) {
            if (t.team == s.team || !t.alive()) {
                continue;
            }
            double score = s.type.bonusAgainst(t.type) * 10 - from.distanceTo(t.center()) / 8;
            if (t.routing) {
                score -= 6;
            }
            if (t.type.ranged() && !s.type.ranged()) {
                score += 2;
            }
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    private static void castSpells(Battle battle, Squad s) {
        Vec3 pos = s.center();
        boolean enemyNear = battle.nearestEnemy(s, pos, 14) != null;
        for (int i = 0; i < s.type.abilities().size(); i++) {
            if (s.cooldowns[i] > 0) {
                continue;
            }
            Ability a = s.type.abilities().get(i);
            boolean want = switch (a) {
                case WAR_CRY, CHARGE, SHIELD_WALL -> enemyNear;
                case FIRE_NOVA -> battle.nearestEnemy(s, pos, 6) != null;
                case HEAL_AURA -> battle.squads.stream().anyMatch(o -> o.team == s.team && o.alive()
                        && o.healthFraction() < 0.6f && o.center().distanceTo(pos) <= 10);
                case FANGS, LIGHTNING -> true;
            };
            if (want && Abilities.cast(battle, s, i)) {
                return;
            }
        }
    }
}
