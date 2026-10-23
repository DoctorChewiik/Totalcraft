package com.doctorchewiik.totalcraft.client;

import com.doctorchewiik.totalcraft.network.ArmyActionPayload;
import com.doctorchewiik.totalcraft.network.ClientState;
import com.doctorchewiik.totalcraft.network.ProfileSyncPayload;
import com.doctorchewiik.totalcraft.network.ProfileSyncPayload.MapView;
import com.doctorchewiik.totalcraft.network.ProfileSyncPayload.RosterView;
import com.doctorchewiik.totalcraft.network.ProfileSyncPayload.UnitView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/** Camp screen between battles: recruit, heal, upgrade, then pick a battlefield. */
public class ArmyScreen extends Screen {
    private static final int ROW = 20;
    private static final int TOP = 40;

    private static int mapIndex = -1;
    private int unitScroll;
    private int rosterScroll;
    private int builtVersion = -1;

    public ArmyScreen() {
        super(Component.literal("Armée"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int rowsVisible() {
        return Math.max(1, (height - TOP - 64) / ROW);
    }

    @Override
    protected void init() {
        builtVersion = ClientState.profileVersion;
        ProfileSyncPayload p = ClientState.profile;
        if (p == null) {
            return;
        }
        int half = width / 2;
        int leftX = 10, leftW = half - 15;
        int rightX = half + 5, rightW = half - 15;
        int rows = rowsVisible();

        List<UnitView> units = p.units();
        unitScroll = Math.max(0, Math.min(unitScroll, units.size() - rows));
        for (int i = 0; i < rows && i + unitScroll < units.size(); i++) {
            UnitView u = units.get(i + unitScroll);
            boolean affordable = p.money() >= u.cost();
            Button b = Button.builder(Component.literal(u.name() + " ×" + u.size() + "  —  " + u.cost() + " or"),
                            btn -> send(ArmyActionPayload.BUY, u.id(), ArmyActionPayload.NONE))
                    .bounds(leftX, TOP + i * ROW, leftW, ROW - 2)
                    .tooltip(Tooltip.create(Component.literal(u.description())))
                    .build();
            b.active = affordable;
            addRenderableWidget(b);
        }

        List<RosterView> roster = p.roster();
        rosterScroll = Math.max(0, Math.min(rosterScroll, roster.size() - rows));
        int buttonsW = 60 + 60 + 18 + 6;
        for (int i = 0; i < rows && i + rosterScroll < roster.size(); i++) {
            RosterView r = roster.get(i + rosterScroll);
            int y = TOP + i * ROW;
            int bx = rightX + rightW - buttonsW;
            Button heal = Button.builder(Component.literal(r.healCost() > 0 ? "Soigner " + r.healCost() : "Complet"),
                            btn -> send(ArmyActionPayload.HEAL, "", r.id()))
                    .bounds(bx, y, 60, ROW - 2)
                    .tooltip(Tooltip.create(Component.literal("Remet l'escouade à pleine santé et la complète.")))
                    .build();
            heal.active = r.healCost() > 0 && p.money() >= r.healCost();
            addRenderableWidget(heal);
            Button upgrade = Button.builder(Component.literal(r.upgradeCost() > 0 ? "Niv+ " + r.upgradeCost() : "Max"),
                            btn -> send(ArmyActionPayload.UPGRADE, "", r.id()))
                    .bounds(bx + 62, y, 60, ROW - 2)
                    .tooltip(Tooltip.create(Component.literal("+15% de PV et de dégâts par niveau (max 3).")))
                    .build();
            upgrade.active = r.upgradeCost() > 0 && p.money() >= r.upgradeCost();
            addRenderableWidget(upgrade);
            addRenderableWidget(Button.builder(Component.literal("✖"), btn -> send(ArmyActionPayload.DISMISS, "", r.id()))
                    .bounds(bx + 124, y, 18, ROW - 2)
                    .tooltip(Tooltip.create(Component.literal("Renvoyer l'escouade (rembourse 25%).")))
                    .build());
        }

        // Battlefield selection and launch.
        List<MapView> maps = p.maps();
        int by = height - 50;
        if (!maps.isEmpty()) {
            if (mapIndex < -1 || mapIndex >= maps.size()) {
                mapIndex = -1;
            }
            String mapLabel = mapIndex < 0 ? "Carte : aléatoire" : "Carte : " + maps.get(mapIndex).name();
            Tooltip mapTip = Tooltip.create(Component.literal(mapIndex < 0
                    ? "Une carte tirée au hasard parmi " + maps.size() + "." : maps.get(mapIndex).description()));
            addRenderableWidget(Button.builder(Component.literal("◀"), btn -> cycleMap(-1, maps.size()))
                    .bounds(leftX, by, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(mapLabel), btn -> cycleMap(1, maps.size()))
                    .bounds(leftX + 22, by, 160, 20).tooltip(mapTip).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), btn -> cycleMap(1, maps.size()))
                    .bounds(leftX + 184, by, 20, 20).build());
            Button launch = Button.builder(Component.literal("⚔ Lancer la bataille"), btn -> {
                        String id = mapIndex < 0 ? "" : maps.get(mapIndex).id();
                        send(ArmyActionPayload.START_BATTLE, id, ArmyActionPayload.NONE);
                        onClose();
                    })
                    .bounds(leftX + 210, by, Math.min(160, width - leftX - 220), 20)
                    .tooltip(Tooltip.create(Component.literal("Affrontez l'IA avec toutes vos escouades en état "
                            + "de combattre.")))
                    .build();
            launch.active = roster.stream().anyMatch(r -> r.alive() > 0);
            addRenderableWidget(launch);
        }

        int jx = leftX;
        for (ProfileSyncPayload.JoinView j : p.joinable()) {
            if (jx + 150 > width - 70) {
                break;
            }
            addRenderableWidget(Button.builder(Component.literal("Rejoindre " + j.host()), btn -> {
                        send(ArmyActionPayload.JOIN_BATTLE, String.valueOf(j.battleId()), ArmyActionPayload.NONE);
                        onClose();
                    })
                    .bounds(jx, height - 26, 150, 20)
                    .tooltip(Tooltip.create(Component.literal("Coop : rejoindre la bataille de " + j.host() + " ("
                            + j.map() + ")")))
                    .build());
            jx += 154;
        }
        addRenderableWidget(Button.builder(Component.literal("Fermer"), btn -> onClose())
                .bounds(width - 70, height - 26, 60, 20).build());
    }

    private void cycleMap(int delta, int count) {
        // -1 means random; cycle through -1, 0 .. count-1.
        mapIndex = Math.floorMod(mapIndex + 1 + delta, count + 1) - 1;
        rebuildWidgets();
    }

    private void send(int action, String arg, UUID squad) {
        PacketDistributor.sendToServer(new ArmyActionPayload(action, arg, squad));
    }

    @Override
    public void tick() {
        if (builtVersion != ClientState.profileVersion) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : 1;
        if (mx < width / 2.0) {
            unitScroll += delta;
        } else {
            rosterScroll += delta;
        }
        rebuildWidgets();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        ProfileSyncPayload p = ClientState.profile;
        if (p == null) {
            g.drawCenteredString(font, "Chargement de l'armée…", width / 2, height / 2, 0xFFFFFFFF);
            return;
        }
        g.drawCenteredString(font, "Trésor : " + p.money() + " pièces d'or   |   Victoires : " + p.wins()
                + "   Défaites : " + p.losses(), width / 2, 8, 0xFFFFD27F);
        int half = width / 2;
        g.drawString(font, "Recruter (molette pour défiler)", 10, TOP - 12, 0xFFFFFFFF);
        g.drawString(font, "Votre armée (" + p.roster().size() + "/10)", half + 5, TOP - 12, 0xFFFFFFFF);

        int rows = rowsVisible();
        int rightW = half - 15;
        int labelW = rightW - (60 + 60 + 18 + 10);
        for (int i = 0; i < rows && i + rosterScroll < p.roster().size(); i++) {
            RosterView r = p.roster().get(i + rosterScroll);
            int y = TOP + i * ROW;
            int color = r.alive() == 0 ? 0xFFFF6060 : (r.alive() < r.max() || r.health() < 0.99f ? 0xFFFFE080 : 0xFFFFFFFF);
            String status = r.alive() == 0 ? "blessé" : r.alive() + "/" + r.max() + " " + Math.round(r.health() * 100) + "%";
            g.drawString(font, font.plainSubstrByWidth(r.name(), labelW), half + 5, y + 1, color);
            g.drawString(font, status + (r.level() > 1 ? "  niv. " + r.level() : ""), half + 5, y + 10, 0xFFAAAAAA);
            if (mx >= half + 5 && mx < half + 5 + labelW && my >= y && my < y + ROW) {
                g.renderComponentTooltip(font, java.util.Arrays.stream(r.description().split("\n"))
                        .map(line -> (Component) Component.literal(line)).toList(), mx, my);
            }
        }
        if (p.roster().isEmpty()) {
            g.drawString(font, "Aucune escouade : recrutez à gauche.", half + 5, TOP + 4, 0xFFAAAAAA);
        }
    }
}
