package com.doctorchewiik.totalcraft.army;

import com.doctorchewiik.totalcraft.battle.Battle;
import com.doctorchewiik.totalcraft.battle.BattleManager;
import com.doctorchewiik.totalcraft.data.GameData;
import com.doctorchewiik.totalcraft.data.MapType;
import com.doctorchewiik.totalcraft.data.UnitType;
import com.doctorchewiik.totalcraft.network.ArmyActionPayload;
import com.doctorchewiik.totalcraft.network.ProfileSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Recruitment, healing and upgrades between battles. */
public final class ArmyService {
    public static final int MAX_CHAMPIONS = 2;

    private ArmyService() {
    }

    public static void handle(ServerPlayer player, ArmyActionPayload payload) {
        switch (payload.action()) {
            case ArmyActionPayload.OPEN -> sendProfile(player, true);
            case ArmyActionPayload.BUY -> buy(player, payload.arg());
            case ArmyActionPayload.HEAL -> heal(player, payload.squad());
            case ArmyActionPayload.UPGRADE -> upgrade(player, payload.squad());
            case ArmyActionPayload.DISMISS -> dismiss(player, payload.squad());
            case ArmyActionPayload.START_BATTLE -> BattleManager.start(player, payload.arg());
            case ArmyActionPayload.JOIN_BATTLE -> {
                try {
                    BattleManager.join(player, BattleManager.byId(Integer.parseInt(payload.arg())));
                } catch (NumberFormatException ignored) {
                    // Malformed request: nothing to join.
                }
            }
            default -> {
            }
        }
    }

    private static ArmyData data(ServerPlayer player) {
        return ArmyData.get(player.server);
    }

    private static boolean busy(ServerPlayer player) {
        if (BattleManager.battleOf(player) != null) {
            error(player, "Impossible de gérer votre armée pendant une bataille.");
            return true;
        }
        return false;
    }

    public static void buy(ServerPlayer player, String typeId) {
        if (busy(player)) {
            return;
        }
        UnitType type = GameData.unit(typeId);
        ArmyData data = data(player);
        ArmyProfile profile = data.profile(player.getUUID());
        if (type == null) {
            error(player, "Unité inconnue : " + typeId);
        } else if (profile.roster.size() >= ArmyProfile.MAX_ROSTER) {
            error(player, "Votre armée est complète (" + ArmyProfile.MAX_ROSTER + " escouades).");
        } else if (type.champion() && profile.roster.stream()
                .filter(r -> r.type() != null && r.type().champion()).count() >= MAX_CHAMPIONS) {
            error(player, "Vous ne pouvez avoir que " + MAX_CHAMPIONS + " champions.");
        } else if (profile.money < type.cost()) {
            error(player, "Pas assez d'or (" + type.cost() + " requis).");
        } else {
            profile.money -= type.cost();
            profile.roster.add(new RosterSquad(UUID.randomUUID(), type.id(), type.squadSize(), 1f, 1));
            data.setDirty();
            info(player, type.name() + " rejoint votre armée.");
        }
        sendProfile(player, false);
    }

    public static void heal(ServerPlayer player, UUID id) {
        if (busy(player)) {
            return;
        }
        ArmyData data = data(player);
        ArmyProfile profile = data.profile(player.getUUID());
        RosterSquad squad = profile.squad(id);
        if (squad != null && squad.type() != null) {
            int cost = squad.healCost();
            if (cost <= 0) {
                info(player, "Cette escouade est déjà au complet.");
            } else if (profile.money < cost) {
                error(player, "Pas assez d'or (" + cost + " requis).");
            } else {
                profile.money -= cost;
                squad.alive = squad.type().squadSize();
                squad.health = 1f;
                data.setDirty();
                info(player, squad.type().name() + " est soignée et renforcée.");
            }
        }
        sendProfile(player, false);
    }

    public static void upgrade(ServerPlayer player, UUID id) {
        if (busy(player)) {
            return;
        }
        ArmyData data = data(player);
        ArmyProfile profile = data.profile(player.getUUID());
        RosterSquad squad = profile.squad(id);
        if (squad != null && squad.type() != null) {
            int cost = squad.upgradeCost();
            if (squad.level >= RosterSquad.MAX_LEVEL) {
                info(player, "Niveau maximum atteint.");
            } else if (profile.money < cost) {
                error(player, "Pas assez d'or (" + cost + " requis).");
            } else {
                profile.money -= cost;
                squad.level++;
                data.setDirty();
                info(player, squad.type().name() + " passe au niveau " + squad.level + " (+15% PV et dégâts).");
            }
        }
        sendProfile(player, false);
    }

    public static void dismiss(ServerPlayer player, UUID id) {
        if (busy(player)) {
            return;
        }
        ArmyData data = data(player);
        ArmyProfile profile = data.profile(player.getUUID());
        RosterSquad squad = profile.squad(id);
        if (squad != null) {
            UnitType type = squad.type();
            int refund = type == null ? 0 : (int) (type.cost() * 0.25 * squad.alive / type.squadSize());
            profile.money += refund;
            profile.roster.remove(squad);
            data.setDirty();
            info(player, "Escouade renvoyée (+" + refund + " or).");
        }
        sendProfile(player, false);
    }

    public static void sendProfile(ServerPlayer player, boolean open) {
        ArmyProfile profile = data(player).profile(player.getUUID());
        List<ProfileSyncPayload.RosterView> roster = new ArrayList<>();
        for (RosterSquad r : profile.roster) {
            UnitType t = r.type();
            if (t == null) {
                continue;
            }
            roster.add(new ProfileSyncPayload.RosterView(r.id, t.name(), t.categoryLabel(), r.alive, t.squadSize(),
                    r.health, r.level, r.healCost(), r.upgradeCost(), t.fullDescription()));
        }
        List<ProfileSyncPayload.UnitView> units = GameData.UNITS.values().stream()
                .map(u -> new ProfileSyncPayload.UnitView(u.id(), u.name(), u.categoryLabel(), u.squadSize(), u.cost(),
                        u.fullDescription()))
                .toList();
        List<ProfileSyncPayload.MapView> maps = new ArrayList<>();
        for (MapType m : GameData.MAPS.values()) {
            maps.add(new ProfileSyncPayload.MapView(m.id(), m.name(), m.fullDescription()));
        }
        List<ProfileSyncPayload.JoinView> joinable = new ArrayList<>();
        for (Battle b : BattleManager.joinable()) {
            if (!b.host.equals(player.getUUID())) {
                joinable.add(new ProfileSyncPayload.JoinView(b.id, b.hostName, b.map.name()));
            }
        }
        PacketDistributor.sendToPlayer(player, new ProfileSyncPayload(open, profile.money, profile.wins,
                profile.losses, roster, units, maps, joinable));
    }

    private static void info(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GREEN), true);
    }

    private static void error(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }
}
