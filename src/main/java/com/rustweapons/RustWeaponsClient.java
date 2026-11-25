package com.rustweapons;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Rust-style controls:
 * left mouse = shoot (hold for automatic guns), right mouse = aim down sights, R = reload.
 */
public class RustWeaponsClient implements ClientModInitializer {
	private static KeyMapping reloadKey;
	private static boolean zoomed = false;
	private static int savedFov = 70;
	private static float pendingPitch = 0;
	private static float pendingYaw = 0;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(RustWeapons.id("main"));
		reloadKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.rustweapons.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));

		// Left mouse: shoot instead of attacking / breaking blocks while a gun is in the main hand
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			if (!(player.getMainHandItem().getItem() instanceof GunItem gun)) return false;
			if (gun.isAutomatic() || clickCount != 0) {
				ClientPlayNetworking.send(new FirePayload(client.options.keyUse.isDown()));
			}
			return true;
		});

		// Server tells us to kick the camera
		ClientPlayNetworking.registerGlobalReceiver(RecoilPayload.TYPE, (payload, context) -> {
			pendingPitch += payload.pitch();
			pendingYaw += payload.yaw();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			LocalPlayer player = client.player;
			GunItem gun = player != null && player.getMainHandItem().getItem() instanceof GunItem g ? g : null;
			boolean aiming = gun != null && client.screen == null && client.options.keyUse.isDown();
			updateZoom(client, aiming, gun == null ? 1.0f : gun.zoom());

			while (reloadKey.consumeClick()) {
				if (gun != null && client.screen == null) {
					ClientPlayNetworking.send(ReloadPayload.INSTANCE);
				}
			}

			// smooth recoil: apply half of the remaining kick every tick
			if (player != null && (pendingPitch != 0 || pendingYaw != 0)) {
				float dp = pendingPitch * 0.5f;
				float dy = pendingYaw * 0.5f;
				pendingPitch -= dp;
				pendingYaw -= dy;
				if (Math.abs(pendingPitch) < 0.02f) pendingPitch = 0;
				if (Math.abs(pendingYaw) < 0.02f) pendingYaw = 0;
				player.setXRot(Mth.clamp(player.getXRot() - dp, -90.0f, 90.0f));
				player.setYRot(player.getYRot() + dy);
			}
		});
	}

	/** Aim down sights = narrower field of view while the right mouse button is held. */
	private static void updateZoom(Minecraft client, boolean aiming, float zoom) {
		var fov = client.options.fov();
		if (aiming) {
			if (!zoomed) {
				savedFov = fov.get();
				zoomed = true;
			}
			int target = Mth.clamp(Math.round(savedFov * zoom), 30, 110);
			if (fov.get() != target) fov.set(target);
		} else if (zoomed) {
			fov.set(savedFov);
			zoomed = false;
		}
	}
}
