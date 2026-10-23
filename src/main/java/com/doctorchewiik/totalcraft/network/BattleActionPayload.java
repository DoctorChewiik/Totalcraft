package com.doctorchewiik.totalcraft.network;

import com.doctorchewiik.totalcraft.Totalcraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: battle-wide actions (ready, spells, surrender, leave). */
public record BattleActionPayload(int action, int squadId, int index) implements CustomPacketPayload {
    public static final int READY = 0;
    public static final int ABILITY = 1;
    public static final int SURRENDER = 2;
    public static final int LEAVE = 3;

    public static final Type<BattleActionPayload> TYPE = new Type<>(Totalcraft.id("battle_action"));
    public static final StreamCodec<FriendlyByteBuf, BattleActionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.action);
                buf.writeVarInt(p.squadId);
                buf.writeVarInt(p.index);
            },
            buf -> new BattleActionPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
