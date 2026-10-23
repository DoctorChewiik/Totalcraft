package com.doctorchewiik.totalcraft;

import com.doctorchewiik.totalcraft.army.ArmyData;
import com.doctorchewiik.totalcraft.army.ArmyProfile;
import com.doctorchewiik.totalcraft.army.ArmyService;
import com.doctorchewiik.totalcraft.battle.Battle;
import com.doctorchewiik.totalcraft.battle.BattleManager;
import com.doctorchewiik.totalcraft.battle.Squad;
import com.doctorchewiik.totalcraft.data.GameData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus hooks: ticking battles, damage rules, cleanup and player sessions. */
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new GameData.UnitLoader());
        event.addListener(new GameData.MapLoader());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        TotalcraftCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        BattleManager.tick();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BattleManager.shutdown();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ArmyProfile profile = ArmyData.get(player.server).profile(player.getUUID());
            if (profile.pendingReturn != null && BattleManager.battleOf(player) == null) {
                BattleManager.restore(player);
            } else {
                ArmyService.sendProfile(player, false);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BattleManager.onLogout(player);
        }
    }

    /** Only units spawned by a battle may live in the arena dimension. */
    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.getLevel().dimension() != BattleManager.ARENA) {
            return;
        }
        if (event.getEntity() instanceof Mob mob && BattleManager.squadOf(mob) == null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Squad victim = BattleManager.squadOf(event.getEntity());
        DamageSource source = event.getSource();
        Squad attacker = BattleManager.squadOf(source.getEntity());
        if (victim == null && attacker == null) {
            return;
        }
        if (victim != null && attacker != null && victim.team == attacker.team) {
            event.setCanceled(true);
            return;
        }
        Battle battle = victim != null ? victim.battle : attacker.battle;
        if (battle.phase != Battle.Phase.FIGHT) {
            event.setCanceled(true);
            return;
        }
        long tick = battle.tick;
        double amount = event.getAmount();
        if (attacker != null) {
            boolean basicAttack = source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.ARROW)
                    || source.getDirectEntity() instanceof ThrownPotion;
            if (basicAttack) {
                amount = attacker.type.damage() * attacker.attackMultiplier(victim, tick);
            } else {
                amount *= attacker.buffDamage(tick);
            }
        }
        if (victim != null) {
            amount *= victim.damageTakenMultiplier(tick);
            victim.lastHurtTick = tick;
        }
        event.setAmount((float) amount);
    }

    /** Lets a whole rank hit the same target in one tick. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (BattleManager.squadOf(event.getEntity()) != null) {
            event.getEntity().invulnerableTime = 0;
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity().getTags().contains(BattleManager.UNIT_TAG)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains(BattleManager.UNIT_TAG)) {
            event.setCanceled(true);
        }
    }
}
