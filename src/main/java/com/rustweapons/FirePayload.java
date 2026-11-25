package com.rustweapons;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the fire button is held/clicked. */
public record FirePayload(boolean aiming) implements CustomPacketPayload {
	public static final Type<FirePayload> TYPE = new Type<>(RustWeapons.id("fire"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FirePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, FirePayload::aiming, FirePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
