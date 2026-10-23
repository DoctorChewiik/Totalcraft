package com.doctorchewiik.totalcraft.client;

import com.doctorchewiik.totalcraft.network.ArmyActionPayload;
import com.doctorchewiik.totalcraft.network.ClientState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    /** The battle camera assumes this vertical field of view to turn clicks into world rays. */
    public static final double BATTLE_FOV = 70.0;

    public static final KeyMapping ARMY_KEY = new KeyMapping("key.totalcraft.army", GLFW.GLFW_KEY_K,
            "key.categories.totalcraft");

    private static boolean wasInBattle;
    private static boolean previousHideGui;

    private ClientEvents() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(ARMY_KEY);
    }

    public static boolean inBattle() {
        return ClientState.battle != null && ClientState.battle.active();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        while (ARMY_KEY.consumeClick()) {
            if (!inBattle()) {
                PacketDistributor.sendToServer(ArmyActionPayload.of(ArmyActionPayload.OPEN));
            }
        }
        boolean battle = inBattle();
        if (ClientState.openArmyScreen) {
            ClientState.openArmyScreen = false;
            if (!battle) {
                mc.setScreen(new ArmyScreen());
            }
        }
        if (battle) {
            if (!wasInBattle) {
                previousHideGui = mc.options.hideGui;
                mc.options.hideGui = true;
                BattleScreen.resetCamera();
            }
            if (mc.screen == null || mc.screen instanceof ArmyScreen) {
                mc.setScreen(new BattleScreen());
            }
        } else if (wasInBattle) {
            if (mc.screen instanceof BattleScreen) {
                mc.setScreen(null);
            }
            mc.options.hideGui = previousHideGui;
        }
        wasInBattle = battle;
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (inBattle() && event.usedConfiguredFov()) {
            event.setFOV(BATTLE_FOV);
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (wasInBattle) {
            Minecraft.getInstance().options.hideGui = previousHideGui;
        }
        wasInBattle = false;
        ClientState.reset();
    }
}
