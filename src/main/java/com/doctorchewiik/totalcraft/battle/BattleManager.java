package com.doctorchewiik.totalcraft.battle;

import com.doctorchewiik.totalcraft.Totalcraft;
import com.doctorchewiik.totalcraft.army.ArmyData;
import com.doctorchewiik.totalcraft.army.ArmyProfile;
import com.doctorchewiik.totalcraft.army.ArmyService;
import com.doctorchewiik.totalcraft.data.GameData;
import com.doctorchewiik.totalcraft.data.MapType;
import com.doctorchewiik.totalcraft.network.BattleActionPayload;
import com.doctorchewiik.totalcraft.network.BattleSyncPayload;
import com.doctorchewiik.totalcraft.network.SquadOrderPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owns every running battle and the lookup from unit entities to their squad. */
public final class BattleManager {
    public static final ResourceKey<Level> ARENA = ResourceKey.create(Registries.DIMENSION, Totalcraft.id("arena"));
    public static final String UNIT_TAG = "totalcraft_unit";
    public static final int MAX_PLAYERS = 3;
    private static final int SLOT_SPACING = 512;

    private static final Map<Integer, Battle> BATTLES = new LinkedHashMap<>();
    private static final Map<UUID, Battle> BY_PLAYER = new HashMap<>();
    private static final Map<Integer, Squad> BY_ENTITY = new HashMap<>();
    private static final Set<Integer> SLOTS = new HashSet<>();
    private static int nextBattleId = 1;
    private static int nextSquadId = 1;

    private BattleManager() {
    }

    static int nextSquadId() {
        return nextSquadId++;
    }

    static void register(Mob mob, Squad squad) {
        BY_ENTITY.put(mob.getId(), squad);
    }

    static void unregister(Mob mob) {
        BY_ENTITY.remove(mob.getId());
    }

    public static Squad squadOf(Entity entity) {
        return entity == null ? null : BY_ENTITY.get(entity.getId());
    }

    public static Battle battleOf(Player player) {
        return BY_PLAYER.get(player.getUUID());
    }

    public static List<Battle> joinable() {
        return BATTLES.values().stream()
                .filter(b -> b.phase == Battle.Phase.DEPLOY && b.participants.size() < MAX_PLAYERS)
                .toList();
    }

    public static Battle byId(int id) {
        return BATTLES.get(id);
    }

    // ------------------------------------------------------------------ lifecycle

    public static void start(ServerPlayer player, String mapId) {
        MinecraftServer server = player.server;
        if (BY_PLAYER.containsKey(player.getUUID())) {
            error(player, "Vous êtes déjà dans une bataille.");
            return;
        }
        if (server.overworld().getDifficulty() == Difficulty.PEACEFUL) {
            error(player, "Les batailles sont impossibles en difficulté Paisible : passez au moins en Facile.");
            return;
        }
        ServerLevel arena = server.getLevel(ARENA);
        if (arena == null) {
            error(player, "La dimension d'arène est introuvable.");
            return;
        }
        ArmyProfile profile = ArmyData.get(server).profile(player.getUUID());
        if (profile.roster.stream().noneMatch(r -> r.alive > 0 && r.type() != null)) {
            error(player, "Recrutez d'abord des unités (touche K ou /totalcraft armee).");
            return;
        }
        MapType map = resolveMap(server, mapId);
        if (map == null) {
            error(player, "Carte inconnue : " + mapId);
            return;
        }

        int slot = 0;
        while (SLOTS.contains(slot)) {
            slot++;
        }
        BlockPos center = new BlockPos(slot * SLOT_SPACING, ArenaBuilder.GROUND_Y, 0);
        ArenaBuilder.forceChunks(arena, center, true);
        clearEntities(arena, center);
        ArenaBuilder.build(arena, center, map.terrain(), arena.getRandom());

        Battle battle = new Battle(nextBattleId++, slot, arena, center, map, player);
        BATTLES.put(battle.id, battle);
        SLOTS.add(slot);
        BY_PLAYER.put(player.getUUID(), battle);
        battle.addPlayer(player, profile);
        battle.spawnEnemies(enemyBudget(profile), profile.wins);

        player.sendSystemMessage(Component.literal("Bataille : " + map.name()).withStyle(ChatFormatting.GOLD,
                ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal(
                "Déployez vos troupes (clic droit), puis cliquez sur PRÊT. Vos amis peuvent vous rejoindre avec "
                        + "/totalcraft rejoindre " + player.getGameProfile().getName()).withStyle(ChatFormatting.GRAY));
        battle.sync();
    }

    public static void join(ServerPlayer player, Battle battle) {
        if (BY_PLAYER.containsKey(player.getUUID())) {
            error(player, "Vous êtes déjà dans une bataille.");
            return;
        }
        if (battle == null || battle.phase != Battle.Phase.DEPLOY) {
            error(player, "Cette bataille n'accepte plus de renforts.");
            return;
        }
        if (battle.participants.size() >= MAX_PLAYERS) {
            error(player, "Cette bataille est complète (" + MAX_PLAYERS + " joueurs maximum).");
            return;
        }
        ArmyProfile profile = ArmyData.get(player.server).profile(player.getUUID());
        if (profile.roster.stream().noneMatch(r -> r.alive > 0 && r.type() != null)) {
            error(player, "Recrutez d'abord des unités (touche K ou /totalcraft armee).");
            return;
        }
        BY_PLAYER.put(player.getUUID(), battle);
        battle.addPlayer(player, profile);
        battle.spawnEnemies((int) (enemyBudget(profile) * 0.8), profile.wins);
        battle.broadcast(Component.literal(player.getGameProfile().getName()
                + " rejoint la bataille ! L'ennemi reçoit des renforts.").withStyle(ChatFormatting.AQUA));
        battle.sync();
    }

    private static int enemyBudget(ArmyProfile profile) {
        return 600 + 250 * Math.min(profile.wins, 12);
    }

    private static MapType resolveMap(MinecraftServer server, String mapId) {
        if (GameData.MAPS.isEmpty()) {
            return null;
        }
        if (mapId == null || mapId.isEmpty() || mapId.equals("aleatoire")) {
            List<MapType> maps = new ArrayList<>(GameData.MAPS.values());
            return maps.get(server.overworld().getRandom().nextInt(maps.size()));
        }
        return GameData.map(mapId);
    }

    public static void tick() {
        for (Battle battle : new ArrayList<>(BATTLES.values())) {
            try {
                battle.tick();
            } catch (Exception e) {
                Totalcraft.LOGGER.error("Erreur dans la bataille {}", battle.id, e);
                close(battle);
            }
        }
    }

    static void close(Battle battle) {
        if (BATTLES.remove(battle.id) == null) {
            return;
        }
        for (Squad s : battle.squads) {
            for (Mob m : s.members) {
                unregister(m);
                m.discard();
            }
            s.members.clear();
        }
        clearEntities(battle.level, battle.center);
        ArenaBuilder.forceChunks(battle.level, battle.center, false);
        SLOTS.remove(battle.slot);
        for (UUID uuid : new ArrayList<>(battle.participants.keySet())) {
            BY_PLAYER.remove(uuid);
            ServerPlayer p = battle.level.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) {
                restore(p);
            }
        }
        battle.participants.clear();
    }

    private static void clearEntities(ServerLevel level, BlockPos center) {
        AABB box = new AABB(center).inflate(ArenaBuilder.HALF + 4, 40, ArenaBuilder.HALF + 4);
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            if (e instanceof Mob m) {
                unregister(m);
            }
            e.discard();
        }
    }

    public static void shutdown() {
        for (Battle battle : new ArrayList<>(BATTLES.values())) {
            for (Squad s : battle.squads) {
                s.members.forEach(Entity::discard);
            }
            ArenaBuilder.forceChunks(battle.level, battle.center, false);
        }
        BATTLES.clear();
        BY_PLAYER.clear();
        BY_ENTITY.clear();
        SLOTS.clear();
    }

    /** Sends the player back where they were before the battle, if anything is pending. */
    public static void restore(ServerPlayer player) {
        MinecraftServer server = player.server;
        ArmyData data = ArmyData.get(server);
        ArmyProfile profile = data.profile(player.getUUID());
        CompoundTag ret = profile.pendingReturn;
        PacketDistributor.sendToPlayer(player, BattleSyncPayload.inactive());
        if (ret == null) {
            return;
        }
        ResourceLocation dim = ResourceLocation.tryParse(ret.getString("dim"));
        ServerLevel level = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (level == null || level.dimension() == ARENA) {
            level = server.overworld();
        }
        player.teleportTo(level, ret.getDouble("x"), ret.getDouble("y"), ret.getDouble("z"), ret.getFloat("yaw"),
                ret.getFloat("pitch"));
        player.setGameMode(GameType.byId(ret.getInt("gm")));
        profile.pendingReturn = null;
        data.setDirty();
        ArmyService.sendProfile(player, false);
    }

    public static void onLogout(ServerPlayer player) {
        Battle battle = BY_PLAYER.remove(player.getUUID());
        if (battle == null) {
            return;
        }
        battle.removePlayer(player.getUUID());
        if (battle.participants.isEmpty()) {
            if (battle.phase == Battle.Phase.FIGHT) {
                battle.end(false, "Tous les généraux ont quitté le champ de bataille.");
            }
            close(battle);
        }
    }

    public static void leave(ServerPlayer player) {
        Battle battle = BY_PLAYER.get(player.getUUID());
        if (battle == null) {
            restore(player);
            return;
        }
        if (battle.phase != Battle.Phase.ENDED && battle.participants.size() <= 1) {
            if (battle.phase == Battle.Phase.FIGHT) {
                battle.end(false, "Vous avez quitté le champ de bataille.");
            }
            close(battle);
            return;
        }
        BY_PLAYER.remove(player.getUUID());
        battle.removePlayer(player.getUUID());
        restore(player);
        if (battle.participants.isEmpty()) {
            close(battle);
        }
    }

    // ------------------------------------------------------------------ client requests

    public static void handleAction(ServerPlayer player, BattleActionPayload payload) {
        Battle battle = BY_PLAYER.get(player.getUUID());
        if (battle == null) {
            if (payload.action() == BattleActionPayload.LEAVE) {
                restore(player);
            }
            return;
        }
        switch (payload.action()) {
            case BattleActionPayload.READY -> {
                if (battle.phase == Battle.Phase.DEPLOY) {
                    battle.toggleReady(player.getUUID());
                    battle.sync();
                }
            }
            case BattleActionPayload.ABILITY -> {
                Squad s = battle.squad(payload.squadId());
                if (battle.phase == Battle.Phase.FIGHT && s != null && player.getUUID().equals(s.owner)) {
                    if (!Abilities.cast(battle, s, payload.index())) {
                        player.displayClientMessage(Component.literal("Sort indisponible : aucune cible à portée ou "
                                + "recharge en cours.").withStyle(ChatFormatting.GRAY), true);
                    }
                }
            }
            case BattleActionPayload.SURRENDER -> {
                if (battle.phase == Battle.Phase.FIGHT) {
                    battle.end(false, "Vous avez sonné la retraite.");
                } else if (battle.phase == Battle.Phase.DEPLOY) {
                    battle.broadcast(Component.literal("La bataille est annulée.").withStyle(ChatFormatting.GRAY));
                    close(battle);
                }
            }
            case BattleActionPayload.LEAVE -> leave(player);
            default -> {
            }
        }
    }

    public static void handleOrder(ServerPlayer player, SquadOrderPayload payload) {
        Battle battle = BY_PLAYER.get(player.getUUID());
        if (battle == null || battle.phase == Battle.Phase.ENDED) {
            return;
        }
        List<Squad> own = new ArrayList<>();
        for (int id : payload.squads()) {
            Squad s = battle.squad(id);
            if (s != null && s.alive() && !s.routing && player.getUUID().equals(s.owner)) {
                own.add(s);
            }
        }
        if (own.isEmpty()) {
            return;
        }
        switch (payload.order()) {
            case SquadOrderPayload.MOVE -> battle.orderMove(own, new Vec3(payload.x(), payload.y(), payload.z()));
            case SquadOrderPayload.ATTACK -> {
                Squad target = battle.squad(payload.targetSquad());
                if (battle.phase == Battle.Phase.FIGHT && target != null && target.team != Team.PLAYERS
                        && target.alive()) {
                    battle.orderAttack(own, target);
                }
            }
            case SquadOrderPayload.HOLD -> {
                if (battle.phase == Battle.Phase.FIGHT) {
                    battle.orderHold(own);
                }
            }
            default -> {
            }
        }
    }

    private static void error(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
