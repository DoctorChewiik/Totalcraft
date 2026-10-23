package com.doctorchewiik.totalcraft.client;

import com.doctorchewiik.totalcraft.network.BattleActionPayload;
import com.doctorchewiik.totalcraft.network.BattleSyncPayload;
import com.doctorchewiik.totalcraft.network.BattleSyncPayload.SquadView;
import com.doctorchewiik.totalcraft.network.ClientState;
import com.doctorchewiik.totalcraft.network.SquadOrderPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Top-down command screen shown for the whole battle. The player is a spectator whose position
 * and rotation this screen drives directly, so the world renders from the RTS camera.
 */
public class BattleScreen extends Screen {
    private static final int ARENA_HALF = 48;
    private static final float MIN_PITCH = 35f;
    private static final float MAX_PITCH = 80f;

    private static final int COLOR_OWN = 0xCC1E5AA8;
    private static final int COLOR_ALLY = 0xCC1E8A5A;
    private static final int COLOR_ENEMY = 0xCCB02828;
    private static final int COLOR_SELECTED = 0xFFFFD700;

    private static float yaw;
    private static float pitch = 55f;
    private static final Set<Integer> SELECTED = new LinkedHashSet<>();

    private long lastFrame;
    private boolean boxSelecting;
    private boolean rotating;
    private double dragStartX;
    private double dragStartY;
    private double mouseX;
    private double mouseY;
    private Vec3 orderMarker;
    private long orderMarkerTime;
    private int orderMarkerColor;
    private long surrenderArmedUntil;

    // Camera basis for the current frame.
    private Vec3 camPos = Vec3.ZERO;
    private Vec3 forward = new Vec3(0, 0, 1);
    private Vec3 right = new Vec3(-1, 0, 0);
    private Vec3 up = new Vec3(0, 1, 0);
    private double tanHalfFov = Math.tan(Math.toRadians(ClientEvents.BATTLE_FOV / 2));

    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action, List<Component> tooltip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public BattleScreen() {
        super(Component.literal("Bataille"));
    }

    static void resetCamera() {
        yaw = 0f;
        pitch = 55f;
        SELECTED.clear();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(new PauseScreen(true));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The battlefield itself is the background.
    }

    // ------------------------------------------------------------------ state helpers

    private static BattleSyncPayload state() {
        return ClientState.battle;
    }

    private UUID me() {
        return minecraft.player.getUUID();
    }

    private boolean isOwn(SquadView s) {
        return s.team() == 0 && s.owner().equals(me());
    }

    private int colorOf(SquadView s) {
        if (s.team() != 0) {
            return COLOR_ENEMY;
        }
        return isOwn(s) ? COLOR_OWN : COLOR_ALLY;
    }

    private List<SquadView> ownSquads() {
        List<SquadView> list = new ArrayList<>();
        for (SquadView s : state().squads()) {
            if (isOwn(s)) {
                list.add(s);
            }
        }
        return list;
    }

    private SquadView squad(int id) {
        for (SquadView s : state().squads()) {
            if (s.id() == id) {
                return s;
            }
        }
        return null;
    }

    private List<Entity> entities(SquadView s) {
        List<Entity> list = new ArrayList<>();
        for (int id : s.entities()) {
            Entity e = minecraft.level.getEntity(id);
            if (e != null && e.isAlive()) {
                list.add(e);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ camera

    private boolean keyDown(KeyMapping mapping, int fallback) {
        long window = minecraft.getWindow().getWindow();
        InputConstants.Key key = mapping.getKey();
        boolean mapped = key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
        return mapped || InputConstants.isKeyDown(window, fallback);
    }

    private void updateCamera() {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        LocalPlayer player = minecraft.player;
        BattleSyncPayload st = state();

        double speed = 26 * dt * (hasShiftDown() ? 2.5 : 1);
        double fx = 0, fz = 0;
        if (keyDown(minecraft.options.keyUp, GLFW.GLFW_KEY_UP) || mouseY <= 2) fz += 1;
        if (keyDown(minecraft.options.keyDown, GLFW.GLFW_KEY_DOWN) || mouseY >= height - 3) fz -= 1;
        if (keyDown(minecraft.options.keyLeft, GLFW.GLFW_KEY_LEFT) || mouseX <= 2) fx -= 1;
        if (keyDown(minecraft.options.keyRight, GLFW.GLFW_KEY_RIGHT) || mouseX >= width - 3) fx += 1;
        double rad = Math.toRadians(yaw);
        double forwardX = -Math.sin(rad), forwardZ = Math.cos(rad);
        double rightX = -Math.cos(rad), rightZ = -Math.sin(rad);
        double x = player.getX() + (forwardX * fz + rightX * fx) * speed;
        double z = player.getZ() + (forwardZ * fz + rightZ * fx) * speed;
        double y = player.getY();

        double cx = st.centerX() + 0.5, cz = st.centerZ() + 0.5;
        x = Mth.clamp(x, cx - ARENA_HALF - 12, cx + ARENA_HALF + 12);
        z = Mth.clamp(z, cz - ARENA_HALF - 30, cz + ARENA_HALF + 30);
        y = Mth.clamp(y, st.centerY() + 6, st.centerY() + 70);

        player.setPos(x, y, z);
        player.xo = x;
        player.yo = y;
        player.zo = z;
        player.xOld = x;
        player.yOld = y;
        player.zOld = z;
        player.setDeltaMovement(Vec3.ZERO);
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(pitch);
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;

        camPos = minecraft.gameRenderer.getMainCamera().getPosition();
        forward = Vec3.directionFromRotation(pitch, yaw);
        right = forward.cross(new Vec3(0, 1, 0)).normalize();
        up = right.cross(forward).normalize();
        tanHalfFov = Math.tan(Math.toRadians(ClientEvents.BATTLE_FOV / 2));
    }

    private Vec3 rayDirection(double mx, double my) {
        double aspect = (double) width / height;
        double nx = (2 * mx / width - 1) * tanHalfFov * aspect;
        double ny = (1 - 2 * my / height) * tanHalfFov;
        return forward.add(right.scale(nx)).add(up.scale(ny)).normalize();
    }

    /** Screen coordinates of a world point, or null when it is behind the camera. */
    private double[] project(Vec3 p) {
        Vec3 rel = p.subtract(camPos);
        double depth = rel.dot(forward);
        if (depth < 0.2) {
            return null;
        }
        double aspect = (double) width / height;
        double sx = rel.dot(right) / (depth * tanHalfFov * aspect);
        double sy = rel.dot(up) / (depth * tanHalfFov);
        return new double[]{(sx + 1) / 2 * width, (1 - sy) / 2 * height};
    }

    // ------------------------------------------------------------------ picking

    private SquadView squadUnderCursor(double mx, double my) {
        for (Hit h : hits) {
            if (h.contains(mx, my) && h.tooltip == null && h.action instanceof SelectAction sa) {
                return squad(sa.squadId);
            }
        }
        Vec3 dir = rayDirection(mx, my);
        Vec3 end = camPos.add(dir.scale(250));
        SquadView best = null;
        double bestDist = Double.MAX_VALUE;
        for (SquadView s : state().squads()) {
            for (Entity e : entities(s)) {
                AABB box = e.getBoundingBox().inflate(0.3);
                Optional<Vec3> hit = box.clip(camPos, end);
                if (hit.isPresent()) {
                    double d = hit.get().distanceToSqr(camPos);
                    if (d < bestDist) {
                        bestDist = d;
                        best = s;
                    }
                }
            }
        }
        return best;
    }

    private Vec3 groundUnderCursor(double mx, double my) {
        Vec3 dir = rayDirection(mx, my);
        BlockHitResult hit = minecraft.level.clip(new ClipContext(camPos, camPos.add(dir.scale(250)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        if (hit.getType() == HitResult.Type.MISS) {
            // Fall back to the ground plane.
            double groundY = state().centerY() + 1;
            if (dir.y >= -1e-3) {
                return null;
            }
            double t = (groundY - camPos.y) / dir.y;
            return camPos.add(dir.scale(t));
        }
        return hit.getLocation();
    }

    /** Marker for banner clicks, so the cursor picking can recognise them. */
    private final class SelectAction implements Runnable {
        final int squadId;

        SelectAction(int squadId) {
            this.squadId = squadId;
        }

        @Override
        public void run() {
            clickSelect(squad(squadId));
        }
    }

    private void clickSelect(SquadView s) {
        if (s == null || !isOwn(s)) {
            if (!hasShiftDown() && s == null) {
                SELECTED.clear();
            }
            return;
        }
        if (hasShiftDown()) {
            if (!SELECTED.remove(s.id())) {
                SELECTED.add(s.id());
            }
        } else {
            SELECTED.clear();
            SELECTED.add(s.id());
        }
    }

    private int[] selectedOwnIds() {
        return SELECTED.stream().filter(id -> {
            SquadView s = squad(id);
            return s != null && isOwn(s) && !s.routing();
        }).mapToInt(Integer::intValue).toArray();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Hit h : hits) {
                if (h.contains(mx, my)) {
                    h.action.run();
                    return true;
                }
            }
            boxSelecting = true;
            dragStartX = mx;
            dragStartY = my;
            return true;
        }
        if (button == 1) {
            issueOrder(mx, my);
            return true;
        }
        if (button == 2) {
            rotating = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && boxSelecting) {
            boxSelecting = false;
            if (Math.abs(mx - dragStartX) < 4 && Math.abs(my - dragStartY) < 4) {
                clickSelect(squadUnderCursor(mx, my));
            } else {
                boxSelect(Math.min(mx, dragStartX), Math.min(my, dragStartY), Math.max(mx, dragStartX),
                        Math.max(my, dragStartY));
            }
            return true;
        }
        if (button == 2) {
            rotating = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 2 && rotating) {
            yaw = Mth.wrapDegrees(yaw + (float) dx * 0.5f);
            pitch = Mth.clamp(pitch + (float) dy * 0.3f, MIN_PITCH, MAX_PITCH);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        LocalPlayer player = minecraft.player;
        double y = player.getY() - scrollY * 3;
        player.setPos(player.getX(), y, player.getZ());
        return true;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        this.mouseX = mx;
        this.mouseY = my;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        BattleSyncPayload st = state();
        if (minecraft.options.keyChat.matches(keyCode, scanCode)) {
            minecraft.setScreen(new ChatScreen(""));
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_3) {
            castAbility(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> {
                SELECTED.clear();
                ownSquads().forEach(s -> SELECTED.add(s.id()));
                return true;
            }
            case GLFW.GLFW_KEY_H -> {
                int[] ids = selectedOwnIds();
                if (ids.length > 0 && st.phase() == BattleSyncPayload.FIGHT) {
                    PacketDistributor.sendToServer(new SquadOrderPayload(ids, SquadOrderPayload.HOLD, 0, 0, 0, -1));
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (st.phase() == BattleSyncPayload.DEPLOY) {
                    PacketDistributor.sendToServer(new BattleActionPayload(BattleActionPayload.READY, 0, 0));
                } else if (st.phase() == BattleSyncPayload.ENDED) {
                    PacketDistributor.sendToServer(new BattleActionPayload(BattleActionPayload.LEAVE, 0, 0));
                }
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void castAbility(int index) {
        SquadView champion = selectedChampion();
        if (champion != null && index < champion.abilities().size()) {
            PacketDistributor.sendToServer(new BattleActionPayload(BattleActionPayload.ABILITY, champion.id(), index));
        }
    }

    private SquadView selectedChampion() {
        for (int id : SELECTED) {
            SquadView s = squad(id);
            if (s != null && isOwn(s) && !s.abilities().isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private void boxSelect(double x0, double y0, double x1, double y1) {
        if (!hasShiftDown()) {
            SELECTED.clear();
        }
        for (SquadView s : ownSquads()) {
            for (Entity e : entities(s)) {
                double[] p = project(e.position().add(0, e.getBbHeight() / 2, 0));
                if (p != null && p[0] >= x0 && p[0] <= x1 && p[1] >= y0 && p[1] <= y1) {
                    SELECTED.add(s.id());
                    break;
                }
            }
        }
    }

    private void issueOrder(double mx, double my) {
        BattleSyncPayload st = state();
        int[] ids = selectedOwnIds();
        if (ids.length == 0 || st.phase() == BattleSyncPayload.ENDED) {
            return;
        }
        SquadView target = squadUnderCursor(mx, my);
        if (target != null && target.team() != 0 && st.phase() == BattleSyncPayload.FIGHT) {
            PacketDistributor.sendToServer(new SquadOrderPayload(ids, SquadOrderPayload.ATTACK, 0, 0, 0, target.id()));
            List<Entity> es = entities(target);
            orderMarker = es.isEmpty() ? null : es.get(0).position();
            orderMarkerColor = 0xFFFF4040;
        } else {
            Vec3 ground = groundUnderCursor(mx, my);
            if (ground == null) {
                return;
            }
            PacketDistributor.sendToServer(new SquadOrderPayload(ids, SquadOrderPayload.MOVE, ground.x, ground.y,
                    ground.z, -1));
            orderMarker = ground;
            orderMarkerColor = 0xFF40FF40;
        }
        orderMarkerTime = Util.getMillis();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        mouseX = mx;
        mouseY = my;
        if (!ClientEvents.inBattle() || minecraft.player == null || minecraft.level == null) {
            return;
        }
        updateCamera();
        hits.clear();
        BattleSyncPayload st = state();
        SELECTED.removeIf(id -> squad(id) == null);

        List<Component> tooltip = null;
        for (SquadView s : st.squads()) {
            List<Component> t = renderSquadMarker(g, s, partialTick, mx, my);
            if (t != null) {
                tooltip = t;
            }
        }
        renderOrderMarker(g);
        if (boxSelecting && (Math.abs(mx - dragStartX) >= 4 || Math.abs(my - dragStartY) >= 4)) {
            int x0 = (int) Math.min(mx, dragStartX), y0 = (int) Math.min(my, dragStartY);
            int x1 = (int) Math.max(mx, dragStartX), y1 = (int) Math.max(my, dragStartY);
            g.fill(x0, y0, x1, y1, 0x3340FF40);
            g.renderOutline(x0, y0, x1 - x0, y1 - y0, 0xFF40FF40);
        }

        List<Component> hudTip = renderHud(g, st, mx, my);
        if (hudTip != null) {
            tooltip = hudTip;
        }
        if (st.phase() == BattleSyncPayload.ENDED) {
            renderEnd(g, st);
        }
        for (Hit h : hits) {
            if (h.tooltip != null && h.contains(mx, my)) {
                tooltip = h.tooltip;
            }
        }
        if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mx, my);
        }
    }

    private List<Component> renderSquadMarker(GuiGraphics g, SquadView s, float partialTick, int mx, int my) {
        List<Entity> es = entities(s);
        if (es.isEmpty()) {
            return null;
        }
        boolean selected = SELECTED.contains(s.id());
        double x = 0, z = 0, top = -Double.MAX_VALUE;
        for (Entity e : es) {
            Vec3 p = e.getPosition(partialTick);
            x += p.x;
            z += p.z;
            top = Math.max(top, p.y + e.getBbHeight());
            if (selected) {
                double[] head = project(p.add(0, e.getBbHeight() + 0.25, 0));
                if (head != null) {
                    g.fill((int) head[0] - 1, (int) head[1] - 1, (int) head[0] + 2, (int) head[1] + 2, COLOR_SELECTED);
                }
            }
        }
        double[] p = project(new Vec3(x / es.size(), top + 1.2, z / es.size()));
        if (p == null) {
            return null;
        }
        int w = 54, h = 13;
        int bx = (int) p[0] - w / 2, by = (int) p[1] - h;
        g.fill(bx, by, bx + w, by + h, colorOf(s));
        g.fill(bx + 1, by + h - 3, bx + 1 + (int) ((w - 2) * s.health()), by + h - 1, 0xFF40E040);
        String label = font.plainSubstrByWidth(s.name(), w - 4);
        if (s.routing() && (Util.getMillis() / 400) % 2 == 0) {
            label = "DÉROUTE";
        }
        g.drawString(font, label, bx + 2, by + 1, 0xFFFFFFFF, false);
        if (selected) {
            g.renderOutline(bx - 1, by - 1, w + 2, h + 2, COLOR_SELECTED);
        }
        hits.add(new Hit(bx, by, w, h, new SelectAction(s.id()), null));
        if (mx >= bx && mx < bx + w && my >= by && my < by + h) {
            return squadTooltip(s);
        }
        return null;
    }

    private List<Component> squadTooltip(SquadView s) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(s.name() + (s.level() > 1 ? " (niv. " + s.level() + ")" : "")));
        lines.add(Component.literal("Unités : " + s.alive() + " / " + s.max()));
        lines.add(Component.literal("Santé : " + Math.round(s.health() * 100) + "%"));
        lines.add(Component.literal("Moral : " + Math.round(Math.max(0, s.morale()) * 100) + "%"
                + (s.routing() ? " (en déroute)" : "")));
        if (s.team() != 0) {
            lines.add(Component.literal("Clic droit avec vos troupes pour attaquer"));
        }
        return lines;
    }

    private void renderOrderMarker(GuiGraphics g) {
        if (orderMarker == null || Util.getMillis() - orderMarkerTime > 1000) {
            return;
        }
        double[] p = project(orderMarker.add(0, 0.2, 0));
        if (p == null) {
            return;
        }
        int x = (int) p[0], y = (int) p[1];
        int r = 5 + (int) ((Util.getMillis() - orderMarkerTime) / 120);
        g.renderOutline(x - r, y - r / 2, r * 2, r, orderMarkerColor);
        g.fill(x - 1, y - 1, x + 2, y + 2, orderMarkerColor);
    }

    private List<Component> renderHud(GuiGraphics g, BattleSyncPayload st, int mx, int my) {
        List<Component> tooltip = null;
        // Top bar.
        String phase = switch (st.phase()) {
            case BattleSyncPayload.DEPLOY -> "Déploiement : " + st.seconds() + " s";
            case BattleSyncPayload.FIGHT -> "Bataille : " + formatTime(st.seconds());
            default -> "Fin de la bataille";
        };
        String top = st.mapName() + "  |  " + phase;
        int tw = font.width(top) + 12;
        int tx = width / 2 - tw / 2;
        g.fill(tx, 0, tx + tw, 14, 0xAA000000);
        g.drawCenteredString(font, top, width / 2, 3, 0xFFFFD27F);
        if (mx >= tx && mx < tx + tw && my < 14) {
            tooltip = lines(st.mapInfo());
        }

        // Army strength.
        int own = 0, ownMax = 0, foes = 0, foesMax = 0;
        for (SquadView s : st.squads()) {
            if (s.team() == 0) {
                own += s.alive();
                ownMax += s.max();
            } else {
                foes += s.alive();
                foesMax += s.max();
            }
        }
        g.drawString(font, "Alliés : " + own, 6, 4, 0xFF7FB8FF);
        g.drawString(font, "Ennemis : " + foes, 6, 14, 0xFFFF8080);

        if (st.phase() == BattleSyncPayload.DEPLOY) {
            String label = st.ready() ? "PRÊT ✔ (en attente)" : "PRÊT (Entrée)";
            int bw = 120, bx = width / 2 - bw / 2, by = 18;
            button(g, bx, by, bw, 16, label, st.ready() ? 0xCC2E7D32 : 0xCC444444, mx, my,
                    () -> PacketDistributor.sendToServer(new BattleActionPayload(BattleActionPayload.READY, 0, 0)),
                    lines("Lance la bataille quand tous les joueurs sont prêts."));
            g.drawCenteredString(font, "Clic droit sur le terrain pour placer les escouades sélectionnées",
                    width / 2, 38, 0xFFDDDDDD);
        }
        if (st.phase() != BattleSyncPayload.ENDED) {
            boolean armed = Util.getMillis() < surrenderArmedUntil;
            String label = st.phase() == BattleSyncPayload.DEPLOY ? "Annuler" : (armed ? "Confirmer ?" : "Retraite");
            button(g, width - 76, 2, 72, 14, label, armed ? 0xCCB02828 : 0xAA333333, mx, my, () -> {
                if (Util.getMillis() < surrenderArmedUntil) {
                    PacketDistributor.sendToServer(new BattleActionPayload(BattleActionPayload.SURRENDER, 0, 0));
                } else {
                    surrenderArmedUntil = Util.getMillis() + 3000;
                }
            }, lines("Cliquez deux fois pour abandonner la bataille."));
        }

        renderCards(g, st, mx, my);
        renderAbilities(g, mx, my);

        g.drawString(font, "ZQSD/flèches : caméra • molette : zoom • clic molette : rotation", 6, height - 70,
                0x99FFFFFF, false);
        g.drawString(font, "Clic gauche : sélection • clic droit : ordre • H : halte • Espace : tout", 6,
                height - 60, 0x99FFFFFF, false);
        return tooltip;
    }

    private void renderCards(GuiGraphics g, BattleSyncPayload st, int mx, int my) {
        List<SquadView> own = ownSquads();
        if (own.isEmpty()) {
            return;
        }
        int cw = 58, ch = 30, gap = 3;
        int total = own.size() * (cw + gap) - gap;
        int x = Math.max(4, width / 2 - total / 2);
        int y = height - ch - 4;
        for (SquadView s : own) {
            boolean selected = SELECTED.contains(s.id());
            g.fill(x, y, x + cw, y + ch, s.routing() ? 0xCC7A1F1F : 0xCC1B2A3F);
            if (selected) {
                g.renderOutline(x - 1, y - 1, cw + 2, ch + 2, COLOR_SELECTED);
            }
            g.drawString(font, font.plainSubstrByWidth(s.name(), cw - 4), x + 2, y + 2, 0xFFFFFFFF, false);
            g.drawString(font, s.alive() + "/" + s.max() + (s.level() > 1 ? "  N" + s.level() : ""), x + 2, y + 12,
                    0xFFCCCCCC, false);
            g.fill(x + 2, y + ch - 7, x + 2 + (int) ((cw - 4) * s.health()), y + ch - 5, 0xFF40E040);
            g.fill(x + 2, y + ch - 4, x + 2 + (int) ((cw - 4) * Mth.clamp(s.morale(), 0, 1)), y + ch - 2,
                    0xFF4FA3FF);
            final int id = s.id();
            hits.add(new Hit(x, y, cw, ch, () -> clickSelect(squad(id)), squadTooltip(s)));
            x += cw + gap;
        }
    }

    private void renderAbilities(GuiGraphics g, int mx, int my) {
        SquadView champion = selectedChampion();
        if (champion == null) {
            return;
        }
        int bw = 110, bh = 16, gap = 4;
        int total = champion.abilities().size() * (bw + gap) - gap;
        int x = width / 2 - total / 2;
        int y = height - 34 - bh - 6;
        for (int i = 0; i < champion.abilities().size(); i++) {
            int cd = i < champion.cooldowns().length ? champion.cooldowns()[i] : 0;
            String label = "[" + (i + 1) + "] " + champion.abilities().get(i) + (cd > 0 ? " (" + cd + "s)" : "");
            final int index = i;
            button(g, x, y, bw, bh, label, cd > 0 ? 0xCC333333 : 0xCC6A1B9A, mx, my,
                    () -> castAbility(index), lines(champion.abilities().get(i)));
            x += bw + gap;
        }
    }

    private void renderEnd(GuiGraphics g, BattleSyncPayload st) {
        int w = 260, h = 120;
        int x = width / 2 - w / 2, y = height / 2 - h / 2;
        g.fill(x, y, x + w, y + h, 0xDD000000);
        boolean win = st.endTitle().startsWith("VICTOIRE");
        g.pose().pushPose();
        g.pose().scale(2f, 2f, 1f);
        g.drawCenteredString(font, st.endTitle(), width / 4, (y + 8) / 2, win ? 0xFFFFD700 : 0xFFFF5555);
        g.pose().popPose();
        int line = y + 34;
        for (String s : st.endText().split("\n")) {
            g.drawCenteredString(font, s, width / 2, line, 0xFFFFFFFF);
            line += 11;
        }
        button(g, width / 2 - 60, y + h - 22, 120, 16, "Retour au camp (" + st.seconds() + ")", 0xCC2E7D32,
                (int) mouseX, (int) mouseY,
                () -> PacketDistributor.sendToServer(new BattleActionPayload(BattleActionPayload.LEAVE, 0, 0)), null);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int color, int mx, int my,
                        Runnable action, List<Component> tooltip) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, color);
        g.renderOutline(x, y, w, h, hover ? 0xFFFFFFFF : 0xFF888888);
        g.drawCenteredString(font, font.plainSubstrByWidth(label, w - 4), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, h, action, tooltip));
    }

    private static List<Component> lines(String text) {
        List<Component> list = new ArrayList<>();
        for (String s : text.split("\n")) {
            list.add(Component.literal(s));
        }
        return list;
    }

    private static String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
