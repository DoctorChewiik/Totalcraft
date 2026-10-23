package com.doctorchewiik.totalcraft.battle;

import com.doctorchewiik.totalcraft.data.Ability;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.phys.Vec3;

/** Champion spells. Each returns false when it had no valid target, so no cooldown is spent. */
public final class Abilities {
    private Abilities() {
    }

    public static boolean cast(Battle battle, Squad caster, int index) {
        if (index < 0 || index >= caster.type.abilities().size() || caster.cooldowns[index] > 0 || !caster.active()) {
            return false;
        }
        Ability ability = caster.type.abilities().get(index);
        Mob champion = caster.members.get(0);
        ServerLevel level = battle.level;
        long tick = battle.tick;
        Vec3 origin = champion.position();
        boolean done = switch (ability) {
            case WAR_CRY -> buffAllies(battle, caster, 12, new Squad.Buff(1.3, 1, 1, tick + 200),
                    ParticleTypes.ANGRY_VILLAGER, SoundEvents.RAVAGER_ROAR);
            case CHARGE -> buffAllies(battle, caster, 12, new Squad.Buff(1.15, 1, 1.4, tick + 120),
                    ParticleTypes.CLOUD, SoundEvents.EVOKER_PREPARE_ATTACK);
            case SHIELD_WALL -> buffAllies(battle, caster, 10, new Squad.Buff(1, 0.6, 1, tick + 200),
                    ParticleTypes.ENCHANTED_HIT, SoundEvents.ANVIL_PLACE);
            case HEAL_AURA -> {
                for (Squad s : battle.squads) {
                    if (s.team == caster.team && s.alive() && s.center().distanceTo(origin) <= 10) {
                        for (Mob m : s.members) {
                            m.heal(m.getMaxHealth() * 0.35f);
                            level.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + m.getBbHeight() + 0.3,
                                    m.getZ(), 3, 0.3, 0.2, 0.3, 0);
                        }
                    }
                }
                sound(level, origin, SoundEvents.ILLUSIONER_CAST_SPELL);
                yield true;
            }
            case FIRE_NOVA -> {
                boolean hit = false;
                for (Squad s : battle.squads) {
                    if (s.team == caster.team) {
                        continue;
                    }
                    for (Mob m : s.members) {
                        if (m.distanceTo(champion) <= 7) {
                            m.igniteForSeconds(5);
                            m.hurt(champion.damageSources().indirectMagic(champion, champion),
                                    (float) (8 * caster.levelMultiplier()));
                            hit = true;
                        }
                    }
                }
                level.sendParticles(ParticleTypes.FLAME, origin.x, origin.y + 1, origin.z, 120, 3.5, 0.5, 3.5, 0.05);
                sound(level, origin, SoundEvents.FIRECHARGE_USE);
                yield hit;
            }
            case FANGS -> {
                Squad target = battle.nearestEnemy(caster, origin, 18);
                if (target == null) {
                    yield false;
                }
                Vec3 to = target.center();
                Vec3 dir = to.subtract(origin).multiply(1, 0, 1).normalize();
                float yaw = (float) Mth.atan2(dir.z, dir.x);
                double length = Math.min(18, origin.distanceTo(to) + 3);
                for (int i = 1; i * 1.25 <= length; i++) {
                    Vec3 at = origin.add(dir.scale(i * 1.25));
                    level.addFreshEntity(new EvokerFangs(level, at.x, ArenaBuilder.GROUND_Y + 1, at.z, yaw, i, champion));
                }
                sound(level, origin, SoundEvents.EVOKER_PREPARE_ATTACK);
                yield true;
            }
            case LIGHTNING -> {
                Squad target = battle.nearestEnemy(caster, origin, 24);
                if (target == null) {
                    yield false;
                }
                Vec3 at = target.center();
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(at.x, at.y, at.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                for (Mob m : target.members) {
                    if (m.position().distanceTo(at) <= 4.5) {
                        m.hurt(champion.damageSources().indirectMagic(champion, champion),
                                (float) (12 * caster.levelMultiplier()));
                    }
                }
                sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER);
                yield true;
            }
        };
        if (done) {
            caster.cooldowns[index] = ability.cooldownTicks();
            champion.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        return done;
    }

    private static boolean buffAllies(Battle battle, Squad caster, double radius, Squad.Buff buff,
                                      ParticleOptions particle, SoundEvent sound) {
        Vec3 origin = caster.center();
        for (Squad s : battle.squads) {
            if (s.team == caster.team && s.alive() && s.center().distanceTo(origin) <= radius) {
                s.addBuff(buff);
                for (Mob m : s.members) {
                    battle.level.sendParticles(particle, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 4,
                            0.3, 0.3, 0.3, 0.02);
                }
            }
        }
        battle.updateSpeeds();
        sound(battle.level, origin, sound);
        return true;
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, 2.0f, 1.0f);
    }
}
