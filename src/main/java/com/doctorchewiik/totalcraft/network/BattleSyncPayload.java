package com.doctorchewiik.totalcraft.network;

import com.doctorchewiik.totalcraft.Totalcraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server to client: the state of the battle the player is in (or {@link #inactive()}). */
public record BattleSyncPayload(boolean active, int battleId, int phase, String mapName, String mapInfo,
                                int seconds, boolean ready, int centerX, int centerY, int centerZ,
                                String endTitle, String endText, List<SquadView> squads)
        implements CustomPacketPayload {
    public static final int DEPLOY = 0;
    public static final int FIGHT = 1;
    public static final int ENDED = 2;

    public record SquadView(int id, int team, UUID owner, String name, String category, int level,
                            int[] entities, int alive, int max, float health, float morale, boolean routing,
                            List<String> abilities, int[] cooldowns) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeVarInt(team);
            buf.writeUUID(owner);
            buf.writeUtf(name);
            buf.writeUtf(category);
            buf.writeVarInt(level);
            buf.writeVarIntArray(entities);
            buf.writeVarInt(alive);
            buf.writeVarInt(max);
            buf.writeFloat(health);
            buf.writeFloat(morale);
            buf.writeBoolean(routing);
            buf.writeVarInt(abilities.size());
            abilities.forEach(buf::writeUtf);
            buf.writeVarIntArray(cooldowns);
        }

        static SquadView read(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            int team = buf.readVarInt();
            UUID owner = buf.readUUID();
            String name = buf.readUtf();
            String category = buf.readUtf();
            int level = buf.readVarInt();
            int[] entities = buf.readVarIntArray();
            int alive = buf.readVarInt();
            int max = buf.readVarInt();
            float health = buf.readFloat();
            float morale = buf.readFloat();
            boolean routing = buf.readBoolean();
            int n = buf.readVarInt();
            List<String> abilities = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                abilities.add(buf.readUtf());
            }
            int[] cooldowns = buf.readVarIntArray();
            return new SquadView(id, team, owner, name, category, level, entities, alive, max, health, morale,
                    routing, abilities, cooldowns);
        }
    }

    public static final Type<BattleSyncPayload> TYPE = new Type<>(Totalcraft.id("battle_sync"));
    public static final StreamCodec<FriendlyByteBuf, BattleSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.active);
                buf.writeVarInt(p.battleId);
                buf.writeVarInt(p.phase);
                buf.writeUtf(p.mapName);
                buf.writeUtf(p.mapInfo);
                buf.writeVarInt(p.seconds);
                buf.writeBoolean(p.ready);
                buf.writeInt(p.centerX);
                buf.writeInt(p.centerY);
                buf.writeInt(p.centerZ);
                buf.writeUtf(p.endTitle);
                buf.writeUtf(p.endText);
                buf.writeVarInt(p.squads.size());
                p.squads.forEach(s -> s.write(buf));
            },
            buf -> {
                boolean active = buf.readBoolean();
                int battleId = buf.readVarInt();
                int phase = buf.readVarInt();
                String mapName = buf.readUtf();
                String mapInfo = buf.readUtf();
                int seconds = buf.readVarInt();
                boolean ready = buf.readBoolean();
                int cx = buf.readInt();
                int cy = buf.readInt();
                int cz = buf.readInt();
                String endTitle = buf.readUtf();
                String endText = buf.readUtf();
                int n = buf.readVarInt();
                List<SquadView> squads = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    squads.add(SquadView.read(buf));
                }
                return new BattleSyncPayload(active, battleId, phase, mapName, mapInfo, seconds, ready, cx, cy, cz,
                        endTitle, endText, squads);
            });

    public static BattleSyncPayload inactive() {
        return new BattleSyncPayload(false, 0, 0, "", "", 0, false, 0, 0, 0, "", "", List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
