package com.doctorchewiik.totalcraft.network;

import com.doctorchewiik.totalcraft.Totalcraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Server to client: the player's army, the recruitable units, the maps and the battles open to join. */
public record ProfileSyncPayload(boolean open, int money, int wins, int losses, List<RosterView> roster,
                                 List<UnitView> units, List<MapView> maps, List<JoinView> joinable)
        implements CustomPacketPayload {

    public record RosterView(UUID id, String name, String category, int alive, int max, float health, int level,
                             int healCost, int upgradeCost, String description) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(id);
            buf.writeUtf(name);
            buf.writeUtf(category);
            buf.writeVarInt(alive);
            buf.writeVarInt(max);
            buf.writeFloat(health);
            buf.writeVarInt(level);
            buf.writeVarInt(healCost);
            buf.writeVarInt(upgradeCost);
            buf.writeUtf(description);
        }

        static RosterView read(FriendlyByteBuf buf) {
            return new RosterView(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                    buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf());
        }
    }

    public record UnitView(String id, String name, String category, int size, int cost, String description) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(name);
            buf.writeUtf(category);
            buf.writeVarInt(size);
            buf.writeVarInt(cost);
            buf.writeUtf(description);
        }

        static UnitView read(FriendlyByteBuf buf) {
            return new UnitView(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUtf());
        }
    }

    public record MapView(String id, String name, String description) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeUtf(name);
            buf.writeUtf(description);
        }

        static MapView read(FriendlyByteBuf buf) {
            return new MapView(buf.readUtf(), buf.readUtf(), buf.readUtf());
        }
    }

    public record JoinView(int battleId, String host, String map) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(battleId);
            buf.writeUtf(host);
            buf.writeUtf(map);
        }

        static JoinView read(FriendlyByteBuf buf) {
            return new JoinView(buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }
    }

    public static final Type<ProfileSyncPayload> TYPE = new Type<>(Totalcraft.id("profile_sync"));
    public static final StreamCodec<FriendlyByteBuf, ProfileSyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.open);
                buf.writeVarInt(p.money);
                buf.writeVarInt(p.wins);
                buf.writeVarInt(p.losses);
                buf.writeVarInt(p.roster.size());
                p.roster.forEach(r -> r.write(buf));
                buf.writeVarInt(p.units.size());
                p.units.forEach(u -> u.write(buf));
                buf.writeVarInt(p.maps.size());
                p.maps.forEach(m -> m.write(buf));
                buf.writeVarInt(p.joinable.size());
                p.joinable.forEach(j -> j.write(buf));
            },
            buf -> new ProfileSyncPayload(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    readList(buf, RosterView::read), readList(buf, UnitView::read), readList(buf, MapView::read),
                    readList(buf, JoinView::read)));

    private static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        int n = buf.readVarInt();
        List<T> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
