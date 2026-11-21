package com.nullfaction.network;

import com.nullfaction.NullUtil;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** S2C: open the Null command screen. */
public record OpenNullGuiPayload() implements CustomPayload {
    public static final Id<OpenNullGuiPayload> ID = new Id<>(NullUtil.id("open_gui"));
    public static final PacketCodec<RegistryByteBuf, OpenNullGuiPayload> CODEC = PacketCodec.unit(new OpenNullGuiPayload());

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
