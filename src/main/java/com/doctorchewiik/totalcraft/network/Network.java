package com.doctorchewiik.totalcraft.network;

import com.doctorchewiik.totalcraft.army.ArmyService;
import com.doctorchewiik.totalcraft.battle.BattleManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
    private Network() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(BattleSyncPayload.TYPE, BattleSyncPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientState.battle = payload));
        registrar.playToClient(ProfileSyncPayload.TYPE, ProfileSyncPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientState.onProfile(payload)));

        registrar.playToServer(ArmyActionPayload.TYPE, ArmyActionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        ArmyService.handle(player, payload);
                    }
                }));
        registrar.playToServer(BattleActionPayload.TYPE, BattleActionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        BattleManager.handleAction(player, payload);
                    }
                }));
        registrar.playToServer(SquadOrderPayload.TYPE, SquadOrderPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        BattleManager.handleOrder(player, payload);
                    }
                }));
    }
}
