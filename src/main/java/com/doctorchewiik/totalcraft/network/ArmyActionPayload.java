package com.doctorchewiik.totalcraft.network;

import com.doctorchewiik.totalcraft.Totalcraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Client to server: recruitment screen actions. */
public record ArmyActionPayload(int action, String arg, UUID squad) implements CustomPacketPayload {
    public static final int OPEN = 0;
    public static final int BUY = 1;
    public static final int HEAL = 2;
    public static final int UPGRADE = 3;
    public static final int DISMISS = 4;
    public static final int START_BATTLE = 5;
    public static final int JOIN_BATTLE = 6;

    public static final UUID NONE = new UUID(0, 0);
    public static final Type<ArmyActionPayload> TYPE = new Type<>(Totalcraft.id("army_action"));
    public static final StreamCodec<FriendlyByteBuf, ArmyActionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.action);
                buf.writeUtf(p.arg);
                buf.writeUUID(p.squad);
            },
            buf -> new ArmyActionPayload(buf.readVarInt(), buf.readUtf(), buf.readUUID()));

    public static ArmyActionPayload of(int action) {
        return new ArmyActionPayload(action, "", NONE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
