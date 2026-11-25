package com.rustweapons;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RustWeapons implements ModInitializer {
	public static final String MOD_ID = "rustweapons";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RustItems.init();
		Scheduler.init();

		PayloadTypeRegistry.playC2S().register(FirePayload.TYPE, FirePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ReloadPayload.TYPE, ReloadPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(RecoilPayload.TYPE, RecoilPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(FirePayload.TYPE, (payload, context) -> {
			if (context.player().getMainHandItem().getItem() instanceof GunItem gun) {
				gun.handleFire(context.player(), payload.aiming());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ReloadPayload.TYPE, (payload, context) -> {
			if (context.player().getMainHandItem().getItem() instanceof GunItem gun) {
				gun.handleReload(context.player());
			}
		});

		LOGGER.info("Rust Weapons loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
