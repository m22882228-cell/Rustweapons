package com.rustweapons;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: kick the camera after a shot (degrees). */
public record RecoilPayload(float pitch, float yaw) implements CustomPacketPayload {
	public static final Type<RecoilPayload> TYPE = new Type<>(RustWeapons.id("recoil"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RecoilPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.FLOAT, RecoilPayload::pitch, ByteBufCodecs.FLOAT, RecoilPayload::yaw, RecoilPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
