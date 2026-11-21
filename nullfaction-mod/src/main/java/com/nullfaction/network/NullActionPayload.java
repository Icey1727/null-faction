package com.nullfaction.network;

import com.nullfaction.NullUtil;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** C2S: a GUI button press. {@code action} is a {@link NullAction} name, {@code arg} is free text (bounty name). */
public record NullActionPayload(String action, String arg) implements CustomPayload {
    public static final Id<NullActionPayload> ID = new Id<>(NullUtil.id("action"));
    public static final PacketCodec<RegistryByteBuf, NullActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(64), NullActionPayload::action,
            PacketCodecs.string(64), NullActionPayload::arg,
            NullActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
