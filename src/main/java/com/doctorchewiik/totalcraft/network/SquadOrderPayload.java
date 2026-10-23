package com.doctorchewiik.totalcraft.network;

import com.doctorchewiik.totalcraft.Totalcraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: orders for the selected squads. */
public record SquadOrderPayload(int[] squads, int order, double x, double y, double z, int targetSquad)
        implements CustomPacketPayload {
    public static final int MOVE = 0;
    public static final int ATTACK = 1;
    public static final int HOLD = 2;

    public static final Type<SquadOrderPayload> TYPE = new Type<>(Totalcraft.id("squad_order"));
    public static final StreamCodec<FriendlyByteBuf, SquadOrderPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarIntArray(p.squads);
                buf.writeVarInt(p.order);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeVarInt(p.targetSquad);
            },
            buf -> new SquadOrderPayload(buf.readVarIntArray(), buf.readVarInt(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
