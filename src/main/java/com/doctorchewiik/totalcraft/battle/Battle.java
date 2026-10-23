package com.doctorchewiik.totalcraft.battle;

import com.doctorchewiik.totalcraft.army.ArmyData;
import com.doctorchewiik.totalcraft.army.ArmyProfile;
import com.doctorchewiik.totalcraft.army.ArmyService;
import com.doctorchewiik.totalcraft.army.RosterSquad;
import com.doctorchewiik.totalcraft.data.Ability;
import com.doctorchewiik.totalcraft.data.GameData;
import com.doctorchewiik.totalcraft.data.MapType;
import com.doctorchewiik.totalcraft.data.Modifier;
import com.doctorchewiik.totalcraft.data.UnitType;
import com.doctorchewiik.totalcraft.network.BattleSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One battle on one arena: deployment, fight, then results. */
public class Battle {
    public enum Phase { DEPLOY, FIGHT, ENDED }

    public static final int DEPLOY_SECONDS = 120;
    public static final int END_SECONDS = 20;
    public static final int MAX_FIGHT_SECONDS = 900;
    private static final double SQUAD_SPACING = 8.5;
    private static final double CAMERA_HEIGHT = 28;

    public final int id;
    public final int slot;
    public final ServerLevel level;
    public final BlockPos center;
    public final MapType map;
    public final UUID host;
    public final String hostName;
    public Phase phase = Phase.DEPLOY;
    public long tick;
    private int phaseTicks;

    public final List<Squad> squads = new ArrayList<>();
    /** Players currently in the battle, with their ready flag. */
    final Map<UUID, Boolean> participants = new LinkedHashMap<>();
    /** Every player who brought troops, rewarded even if they left. */
    private final Set<UUID> contributors = new LinkedHashSet<>();
    private final Map<UUID, Integer> playerRows = new HashMap<>();
    private int enemyRows;
    private String endTitle = "";
    private String endText = "";

    Battle(int id, int slot, ServerLevel level, BlockPos center, MapType map, ServerPlayer host) {
        this.id = id;
        this.slot = slot;
        this.level = level;
        this.center = center;
        this.map = map;
        this.host = host.getUUID();
        this.hostName = host.getGameProfile().getName();
    }

    // ------------------------------------------------------------------ players

    void addPlayer(ServerPlayer player, ArmyProfile profile) {
        CompoundTag ret = new CompoundTag();
        ret.putString("dim", player.level().dimension().location().toString());
        ret.putDouble("x", player.getX());
        ret.putDouble("y", player.getY());
        ret.putDouble("z", player.getZ());
        ret.putFloat("yaw", player.getYRot());
        ret.putFloat("pitch", player.getXRot());
        ret.putInt("gm", player.gameMode.getGameModeForPlayer().getId());
        profile.pendingReturn = ret;
        ArmyData.get(level.getServer()).setDirty();

        UUID uuid = player.getUUID();
        participants.put(uuid, false);
        contributors.add(uuid);
        int row = playerRows.size();
        playerRows.put(uuid, row);

        player.teleportTo(level, center.getX() + 0.5, ArenaBuilder.GROUND_Y + CAMERA_HEIGHT,
                center.getZ() - ArenaBuilder.HALF - 6, 0f, 55f);
        player.setGameMode(GameType.SPECTATOR);

        List<RosterSquad> deployable = profile.roster.stream()
                .filter(r -> r.alive > 0 && r.type() != null)
                .limit(ArmyProfile.MAX_ROSTER)
                .toList();
        for (int i = 0; i < deployable.size(); i++) {
            RosterSquad r = deployable.get(i);
            Vec3 anchor = rowPosition(Team.PLAYERS, row, i, deployable.size());
            spawnSquad(Team.PLAYERS, uuid, r.id, r.type(), r.level, r.alive, r.health, anchor);
        }
    }

    void removePlayer(UUID uuid) {
        participants.remove(uuid);
    }

    public boolean isReady(UUID uuid) {
        return participants.getOrDefault(uuid, false);
    }

    void toggleReady(UUID uuid) {
        if (participants.containsKey(uuid)) {
            participants.put(uuid, !participants.get(uuid));
        }
    }

    // ------------------------------------------------------------------ spawning

    private Vec3 rowPosition(Team team, int row, int index, int count) {
        int[] rows = {36, 29, 43};
        double z = center.getZ() + 0.5 + team.side() * rows[Math.min(row, rows.length - 1)];
        double x = center.getX() + 0.5 + (index - (count - 1) / 2.0) * SQUAD_SPACING;
        return new Vec3(x, ArenaBuilder.GROUND_Y + 1, z);
    }

    private Squad spawnSquad(Team team, UUID owner, UUID rosterId, UnitType type, int lvl, int alive, float health,
                             Vec3 anchor) {
        Vec3 facing = new Vec3(0, 0, -team.side());
        Squad squad = new Squad(BattleManager.nextSquadId(), this, team, owner, rosterId, type, lvl, anchor, facing);
        squads.add(squad);
        for (int i = 0; i < alive; i++) {
            Mob mob = spawnUnit(squad, Formation.slot(squad, i, alive, anchor, facing), Math.max(0.05f, health));
            if (mob != null) {
                squad.members.add(mob);
            }
        }
        if (!squad.alive()) {
            squads.remove(squad);
        }
        return squad;
    }

    private Mob spawnUnit(Squad squad, Vec3 pos, float health) {
        UnitType type = squad.type;
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(type.entity()).orElse(null);
        if (entityType == null) {
            return null;
        }
        Entity entity = entityType.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        float yaw = Formation.yaw(squad.facing);
        mob.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        for (Map.Entry<EquipmentSlot, net.minecraft.resources.ResourceLocation> e : type.equipment().entrySet()) {
            BuiltInRegistries.ITEM.getOptional(e.getValue()).ifPresent(item -> {
                mob.setItemSlot(e.getKey(), new ItemStack(item));
                mob.setDropChance(e.getKey(), 0f);
            });
        }
        mob.setPersistenceRequired();
        mob.setCanPickUpLoot(false);
        mob.addTag(BattleManager.UNIT_TAG);

        double levelMult = squad.levelMultiplier();
        double maxHealth = type.health() * levelMult * map.multiplier(type, Modifier.Stat.HEALTH);
        setAttribute(mob, Attributes.MAX_HEALTH, maxHealth);
        setAttribute(mob, Attributes.ARMOR, type.armor());
        setAttribute(mob, Attributes.MOVEMENT_SPEED, squad.effectiveSpeed(tick));
        setAttribute(mob, Attributes.FOLLOW_RANGE, 128);
        setAttribute(mob, Attributes.SCALE, type.scale());
        if (type.scale() > 1.2 || type.tags().contains("large")) {
            setAttribute(mob, Attributes.KNOCKBACK_RESISTANCE, 0.8);
        }
        mob.setHealth((float) (maxHealth * health));
        if (type.champion()) {
            mob.setCustomName(Component.literal(type.name()).withStyle(
                    squad.team == Team.PLAYERS ? ChatFormatting.AQUA : ChatFormatting.RED));
            mob.setCustomNameVisible(true);
        }

        BattleManager.register(mob, squad);
        level.addFreshEntity(mob);
        // Vanilla brains would fight our orders: units only do what their squad tells them.
        mob.goalSelector.removeAllGoals(goal -> true);
        mob.targetSelector.removeAllGoals(goal -> true);
        return mob;
    }

    private static void setAttribute(Mob mob, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Builds the AI army for a budget, preferring counters to the players' troops. */
    void spawnEnemies(int budget, int wins) {
        List<UnitType> pool = new ArrayList<>(GameData.UNITS.values());
        if (pool.isEmpty()) {
            return;
        }
        List<UnitType> playerTypes = squads.stream().filter(s -> s.team == Team.PLAYERS).map(s -> s.type).toList();
        boolean hasChampion = squads.stream().anyMatch(s -> s.team == Team.ENEMY && s.type.champion());
        int lvl = 1 + (wins >= 4 ? 1 : 0) + (wins >= 8 ? 1 : 0);
        List<UnitType> picks = new ArrayList<>();
        int remaining = budget;
        while (picks.size() < ArmyProfile.MAX_ROSTER) {
            final int left = remaining;
            final boolean allowChampion = !hasChampion && wins >= 1;
            List<UnitType> affordable = pool.stream()
                    .filter(u -> u.cost() <= left && (!u.champion() || allowChampion))
                    .toList();
            if (affordable.isEmpty()) {
                break;
            }
            UnitType pick;
            if (!playerTypes.isEmpty() && level.random.nextFloat() < 0.45f) {
                pick = affordable.stream()
                        .max(Comparator.comparingDouble(u -> playerTypes.stream()
                                .mapToDouble(u::bonusAgainst).sum() + level.random.nextDouble() * 0.3))
                        .orElseThrow();
            } else {
                pick = affordable.get(level.random.nextInt(affordable.size()));
            }
            picks.add(pick);
            remaining -= pick.cost();
            hasChampion |= pick.champion();
        }
        int row = enemyRows++;
        for (int i = 0; i < picks.size(); i++) {
            UnitType t = picks.get(i);
            spawnSquad(Team.ENEMY, null, null, t, lvl, t.squadSize(), 1f, rowPosition(Team.ENEMY, row, i, picks.size()));
        }
    }

    // ------------------------------------------------------------------ ticking

    void tick() {
        tick++;
        phaseTicks++;
        for (Squad s : squads) {
            prune(s);
            s.buffs.removeIf(b -> b.until() <= tick);
        }
        switch (phase) {
            case DEPLOY -> {
                for (Squad s : squads) {
                    holdDeployed(s);
                }
                boolean allReady = !participants.isEmpty() && participants.values().stream().allMatch(b -> b);
                if (allReady || phaseTicks >= DEPLOY_SECONDS * 20) {
                    startFight();
                }
            }
            case FIGHT -> {
                for (Squad s : squads) {
                    for (int i = 0; i < s.cooldowns.length; i++) {
                        if (s.cooldowns[i] > 0) {
                            s.cooldowns[i]--;
                        }
                    }
                }
                if (tick % 20 == 0) {
                    AiCommander.tick(this);
                    updateSpeeds();
                }
                for (Squad s : squads) {
                    tickSquad(s);
                    tickMorale(s);
                }
                checkEnd();
                if (phase == Phase.FIGHT && phaseTicks >= MAX_FIGHT_SECONDS * 20) {
                    end(false, "Le temps est écoulé.");
                }
            }
            case ENDED -> {
                if (phaseTicks >= END_SECONDS * 20) {
                    BattleManager.close(this);
                    return;
                }
            }
        }
        if (tick % 10 == 0) {
            sync();
        }
    }

    private void startFight() {
        phase = Phase.FIGHT;
        phaseTicks = 0;
        for (Squad s : squads) {
            s.order = Squad.Order.HOLD;
            s.anchor = s.center();
        }
        broadcast(Component.literal("La bataille commence !").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        level.playSound(null, center, SoundEvents.RAID_HORN.value(), net.minecraft.sounds.SoundSource.HOSTILE, 64f, 1f);
    }

    private void prune(Squad s) {
        int before = s.members.size();
        s.members.removeIf(m -> {
            if (m.isAlive() && !m.isRemoved()) {
                return false;
            }
            BattleManager.unregister(m);
            s.nextAttack.remove(m.getId());
            s.lastNavTarget.remove(m.getId());
            s.memberTargets.remove(m.getId());
            return true;
        });
        int dead = before - s.members.size();
        if (dead > 0 && phase == Phase.FIGHT) {
            s.deaths += dead;
            s.morale -= dead * (90.0 / s.maxCount);
            if (!s.alive()) {
                if (s.type.champion()) {
                    broadcast(Component.literal(s.type.name() + " est tombé !").withStyle(
                            s.team == Team.PLAYERS ? ChatFormatting.RED : ChatFormatting.GREEN));
                    for (Squad ally : squads) {
                        if (ally.team == s.team) {
                            ally.morale -= 25;
                        }
                    }
                }
            }
        }
    }

    private void holdDeployed(Squad s) {
        for (int i = 0; i < s.members.size(); i++) {
            Mob m = s.members.get(i);
            Vec3 slot = Formation.slot(s, i, s.members.size(), s.anchor, s.facing);
            m.getNavigation().stop();
            if (m.position().distanceToSqr(slot) > 2.25) {
                m.teleportTo(slot.x, slot.y, slot.z);
            }
            Formation.face(m, s.facing);
        }
    }

    void updateSpeeds() {
        for (Squad s : squads) {
            double speed = s.effectiveSpeed(tick);
            for (Mob m : s.members) {
                setAttribute(m, Attributes.MOVEMENT_SPEED, speed);
            }
        }
    }

    private void tickSquad(Squad s) {
        if (!s.alive()) {
            return;
        }
        keepInside(s);
        if (s.routing) {
            Vec3 c = s.center();
            Vec3 flee = new Vec3(c.x, ArenaBuilder.GROUND_Y + 1,
                    center.getZ() + 0.5 + s.team.side() * (ArenaBuilder.HALF - 3));
            for (int i = 0; i < s.members.size(); i++) {
                Mob m = s.members.get(i);
                m.setAggressive(false);
                navigate(s, m, flee.add((i % 3 - 1) * 2, 0, 0), 1.3);
            }
            return;
        }

        Squad fightTarget = null;
        if (s.order == Squad.Order.ATTACK) {
            if (s.target == null || !s.target.alive()) {
                s.order = Squad.Order.HOLD;
                s.anchor = s.center();
                s.target = null;
            } else {
                fightTarget = s.target;
            }
        }
        if (s.order == Squad.Order.MOVE && s.center().distanceTo(s.anchor) < 2.5) {
            s.order = Squad.Order.HOLD;
        }
        if (s.order == Squad.Order.HOLD) {
            double radius = s.type.ranged() ? s.type.range() : 10;
            if (s.engaged != null && (!s.engaged.alive() || s.engaged.center().distanceTo(s.anchor) > radius + 8)) {
                s.engaged = null;
            }
            if (s.engaged == null && tick % 10 == s.id % 10) {
                s.engaged = nearestEnemy(s, s.center(), radius);
            }
            fightTarget = s.engaged;
        }

        for (int i = 0; i < s.members.size(); i++) {
            Mob m = s.members.get(i);
            if (fightTarget != null) {
                fight(s, m, fightTarget);
            } else {
                m.setAggressive(false);
                Vec3 slot = Formation.slot(s, i, s.members.size(), s.anchor, s.facing);
                if (m.position().distanceToSqr(slot) > 1.0) {
                    navigate(s, m, slot, s.order == Squad.Order.MOVE ? 1.0 : 0.9);
                } else if (m.getNavigation().isDone()) {
                    Formation.face(m, s.facing);
                }
            }
        }
    }

    private void fight(Squad s, Mob m, Squad enemy) {
        Mob target = s.memberTargets.get(m.getId());
        if (target == null || !target.isAlive() || !enemy.members.contains(target) || (tick + m.getId()) % 20 == 0) {
            target = nearest(enemy, m.position());
            if (target == null) {
                return;
            }
            s.memberTargets.put(m.getId(), target);
        }
        m.setAggressive(true);
        m.getLookControl().setLookAt(target, 30f, 30f);
        double dist = m.distanceTo(target);
        boolean ready = tick >= s.nextAttack.getOrDefault(m.getId(), 0L);
        if (s.type.ranged()) {
            if (dist <= s.type.range() && m.hasLineOfSight(target)) {
                m.getNavigation().stop();
                if (ready) {
                    shoot(s, m, target);
                    s.nextAttack.put(m.getId(), tick + s.type.attackCooldown() + m.getRandom().nextInt(6));
                }
            } else {
                navigate(s, m, target.position(), 1.0);
            }
        } else {
            double reach = (m.getBbWidth() + target.getBbWidth()) * 0.5 + 1.2;
            if (dist <= reach) {
                m.getNavigation().stop();
                if (ready) {
                    melee(s, m, target);
                    s.nextAttack.put(m.getId(), tick + s.type.attackCooldown() + m.getRandom().nextInt(4));
                }
            } else {
                navigate(s, m, target.position(), 1.15);
            }
        }
    }

    private void melee(Squad s, Mob m, LivingEntity target) {
        m.swing(InteractionHand.MAIN_HAND);
        if (m instanceof IronGolem || m instanceof Ravager) {
            level.broadcastEntityEvent(m, (byte) 4);
        }
        target.invulnerableTime = 0;
        // The incoming-damage hook replaces this amount with the unit's own damage and modifiers.
        target.hurt(m.damageSources().mobAttack(m), (float) s.type.damage());
    }

    private void shoot(Squad s, Mob m, LivingEntity target) {
        m.swing(InteractionHand.MAIN_HAND);
        if (s.type.projectile().equals("vanilla") && m instanceof RangedAttackMob ranged) {
            ranged.performRangedAttack(target, 1.0f);
            return;
        }
        Arrow arrow = new Arrow(level, m, new ItemStack(Items.ARROW), null);
        double dx = target.getX() - m.getX();
        double dy = target.getY(0.3333) - arrow.getY();
        double dz = target.getZ() - m.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.6f, 4f);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        if (s.type.projectile().equals("frost_arrow")) {
            arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60));
        }
        level.addFreshEntity(arrow);
        m.playSound(m instanceof Pillager ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT, 1.0f,
                1.0f / (m.getRandom().nextFloat() * 0.4f + 0.8f));
    }

    private void navigate(Squad s, Mob m, Vec3 to, double speed) {
        Vec3 last = s.lastNavTarget.get(m.getId());
        boolean stale = last == null || last.distanceToSqr(to) > 1.0 || m.getNavigation().isDone();
        if (stale && (last == null || (tick + m.getId()) % 5 == 0)) {
            m.getNavigation().moveTo(to.x, to.y, to.z, speed);
            s.lastNavTarget.put(m.getId(), to);
        }
    }

    private void keepInside(Squad s) {
        double limit = ArenaBuilder.HALF - 0.5;
        for (Mob m : s.members) {
            double dx = m.getX() - (center.getX() + 0.5);
            double dz = m.getZ() - (center.getZ() + 0.5);
            boolean out = Math.abs(dx) > limit || Math.abs(dz) > limit
                    || m.getY() < ArenaBuilder.GROUND_Y - 2 || m.getY() > ArenaBuilder.GROUND_Y + 14;
            if (out) {
                double x = center.getX() + 0.5 + Math.max(-limit + 1, Math.min(limit - 1, dx));
                double z = center.getZ() + 0.5 + Math.max(-limit + 1, Math.min(limit - 1, dz));
                m.teleportTo(x, ArenaBuilder.GROUND_Y + 1, z);
                m.getNavigation().stop();
            }
        }
    }

    private void tickMorale(Squad s) {
        if (!s.alive()) {
            return;
        }
        boolean threatened = nearestEnemy(s, s.center(), s.routing ? 14 : 10) != null;
        if (threatened && !s.routing && s.healthFraction() < 0.5f) {
            s.morale -= 0.03;
        }
        if (!threatened) {
            s.morale = Math.min(s.type.morale(), s.morale + (s.routing ? 0.25 : 0.05));
        }
        if (!s.routing && s.morale <= 0) {
            s.routing = true;
            s.engaged = null;
            s.target = null;
            broadcast(Component.literal(squadLabel(s) + " est en déroute !").withStyle(
                    s.team == Team.PLAYERS ? ChatFormatting.RED : ChatFormatting.GREEN));
        } else if (s.routing && s.morale >= s.type.morale() * 0.4 && !threatened) {
            s.routing = false;
            s.order = Squad.Order.HOLD;
            s.anchor = s.center();
            broadcast(Component.literal(squadLabel(s) + " se rallie.").withStyle(ChatFormatting.YELLOW));
        }
    }

    private String squadLabel(Squad s) {
        return (s.team == Team.PLAYERS ? "Votre escouade " : "L'ennemi ") + s.type.name();
    }

    private void checkEnd() {
        boolean players = squads.stream().anyMatch(s -> s.team == Team.PLAYERS && s.active());
        boolean enemies = squads.stream().anyMatch(s -> s.team == Team.ENEMY && s.active());
        if (!enemies) {
            end(true, "L'armée ennemie est détruite ou en fuite.");
        } else if (!players) {
            end(false, "Votre armée est détruite ou en fuite.");
        }
    }

    // ------------------------------------------------------------------ queries

    public Squad nearestEnemy(Squad from, Vec3 pos, double radius) {
        Squad best = null;
        double bestDist = radius;
        for (Squad s : squads) {
            if (s.team == from.team || !s.alive()) {
                continue;
            }
            Mob m = nearest(s, pos);
            if (m != null) {
                double d = m.position().distanceTo(pos);
                if (d <= bestDist) {
                    bestDist = d;
                    best = s;
                }
            }
        }
        return best;
    }

    private static Mob nearest(Squad squad, Vec3 pos) {
        Mob best = null;
        double bestDist = Double.MAX_VALUE;
        for (Mob m : squad.members) {
            double d = m.position().distanceToSqr(pos);
            if (d < bestDist) {
                bestDist = d;
                best = m;
            }
        }
        return best;
    }

    public Squad squad(int id) {
        for (Squad s : squads) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ orders

    void orderMove(List<Squad> group, Vec3 point) {
        if (group.isEmpty()) {
            return;
        }
        Vec3 groupCenter = Vec3.ZERO;
        for (Squad s : group) {
            groupCenter = groupCenter.add(s.center());
        }
        groupCenter = groupCenter.scale(1.0 / group.size());
        Vec3 dir = point.subtract(groupCenter).multiply(1, 0, 1);
        Vec3 facing = dir.lengthSqr() > 4 ? dir.normalize() : group.get(0).facing;
        Vec3 right = new Vec3(-facing.z, 0, facing.x);
        List<Squad> sorted = new ArrayList<>(group);
        sorted.sort(Comparator.comparingDouble(s -> s.center().dot(right)));
        for (int i = 0; i < sorted.size(); i++) {
            Squad s = sorted.get(i);
            double offset = (i - (sorted.size() - 1) / 2.0) * SQUAD_SPACING;
            Vec3 anchor = clampToArena(point.add(right.scale(offset)), 3);
            if (phase == Phase.DEPLOY) {
                anchor = clampToDeployZone(anchor);
                s.anchor = anchor;
                s.facing = new Vec3(0, 0, -s.team.side());
            } else {
                s.order = Squad.Order.MOVE;
                s.anchor = anchor;
                s.facing = facing;
                s.target = null;
                s.engaged = null;
                s.lastNavTarget.clear();
            }
        }
    }

    void orderAttack(List<Squad> group, Squad target) {
        for (Squad s : group) {
            s.order = Squad.Order.ATTACK;
            s.target = target;
            s.engaged = null;
            s.lastNavTarget.clear();
        }
    }

    void orderHold(List<Squad> group) {
        for (Squad s : group) {
            s.order = Squad.Order.HOLD;
            s.anchor = s.center();
            s.target = null;
            s.engaged = null;
            s.members.forEach(m -> m.getNavigation().stop());
        }
    }

    private Vec3 clampToArena(Vec3 p, double margin) {
        double limit = ArenaBuilder.HALF - margin;
        double x = Math.max(center.getX() + 0.5 - limit, Math.min(center.getX() + 0.5 + limit, p.x));
        double z = Math.max(center.getZ() + 0.5 - limit, Math.min(center.getZ() + 0.5 + limit, p.z));
        return new Vec3(x, ArenaBuilder.GROUND_Y + 1, z);
    }

    private Vec3 clampToDeployZone(Vec3 p) {
        double near = ArenaBuilder.HALF - ArenaBuilder.DEPLOY_DEPTH;
        double far = ArenaBuilder.HALF - 4;
        double rel = center.getZ() + 0.5 - p.z;
        rel = Math.max(near, Math.min(far, rel));
        return new Vec3(p.x, p.y, center.getZ() + 0.5 - rel);
    }

    // ------------------------------------------------------------------ end of battle

    void end(boolean victory, String reason) {
        if (phase == Phase.ENDED) {
            return;
        }
        phase = Phase.ENDED;
        phaseTicks = 0;
        for (Squad s : squads) {
            for (Mob m : s.members) {
                m.getNavigation().stop();
                m.setAggressive(false);
            }
        }

        int killedValue = 0;
        int enemySquads = 0;
        int enemyDestroyed = 0;
        int losses = 0;
        for (Squad s : squads) {
            if (s.team == Team.ENEMY) {
                enemySquads++;
                if (!s.alive()) {
                    enemyDestroyed++;
                }
                killedValue += (int) (s.type.cost() * (double) s.deaths / s.maxCount);
            } else {
                losses += s.deaths;
            }
        }
        int reward = (int) Math.round((victory ? 400 + 0.35 * killedValue : 150 + 0.15 * killedValue)
                * map.goldMultiplier());

        ArmyData data = ArmyData.get(level.getServer());
        for (UUID uuid : contributors) {
            ArmyProfile profile = data.profile(uuid);
            profile.money += reward;
            if (victory) {
                profile.wins++;
            } else {
                profile.losses++;
            }
        }
        for (Squad s : squads) {
            if (s.team != Team.PLAYERS || s.owner == null) {
                continue;
            }
            ArmyProfile profile = data.profile(s.owner);
            RosterSquad r = profile.squad(s.rosterId);
            if (r == null) {
                continue;
            }
            r.alive = s.members.size();
            r.health = r.alive > 0 ? s.healthFraction() : 0f;
            if (r.alive == 0 && !s.type.champion()) {
                profile.roster.remove(r);
            }
        }
        data.setDirty();

        endTitle = victory ? "VICTOIRE !" : "DÉFAITE";
        endText = reason + "\nButin : +" + reward + " pièces d'or"
                + "\nEscouades ennemies détruites : " + enemyDestroyed + " / " + enemySquads
                + "\nVos pertes : " + losses + " unités";
        broadcast(Component.literal(endTitle + " " + reason + " (+" + reward + " or)")
                .withStyle(victory ? ChatFormatting.GOLD : ChatFormatting.RED, ChatFormatting.BOLD));
        for (UUID uuid : contributors) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) {
                ArmyService.sendProfile(p, false);
            }
        }
        sync();
    }

    // ------------------------------------------------------------------ sync

    List<ServerPlayer> onlinePlayers() {
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID uuid : participants.keySet()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    void broadcast(Component message) {
        onlinePlayers().forEach(p -> p.sendSystemMessage(message));
    }

    void sync() {
        List<BattleSyncPayload.SquadView> views = new ArrayList<>();
        for (Squad s : squads) {
            if (!s.alive()) {
                continue;
            }
            int[] ids = s.members.stream().mapToInt(Entity::getId).toArray();
            int[] cooldowns = new int[s.cooldowns.length];
            for (int i = 0; i < cooldowns.length; i++) {
                cooldowns[i] = (s.cooldowns[i] + 19) / 20;
            }
            views.add(new BattleSyncPayload.SquadView(s.id, s.team.ordinal(),
                    s.owner == null ? new UUID(0, 0) : s.owner, s.type.name(), s.type.category(), s.level, ids,
                    s.members.size(), s.maxCount, s.healthFraction(), (float) (s.morale / s.type.morale()),
                    s.routing, s.type.abilities().stream().map(Ability::displayName).toList(), cooldowns));
        }
        int seconds = switch (phase) {
            case DEPLOY -> Math.max(0, DEPLOY_SECONDS - phaseTicks / 20);
            case FIGHT -> phaseTicks / 20;
            case ENDED -> Math.max(0, END_SECONDS - phaseTicks / 20);
        };
        for (ServerPlayer p : onlinePlayers()) {
            PacketDistributor.sendToPlayer(p, new BattleSyncPayload(true, id, phase.ordinal(), map.name(),
                    map.fullDescription(), seconds, isReady(p.getUUID()), center.getX(), ArenaBuilder.GROUND_Y,
                    center.getZ(), endTitle, endText, views));
        }
    }
}
