package com.rustweapons;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class GunItem extends Item {
	private static final SoundEvent CRACK = sound("entity.firework_rocket.blast");
	private static final SoundEvent THUMP = sound("entity.generic.explode");
	private static final SoundEvent EMPTY = sound("block.dispenser.fail");
	private static final SoundEvent RELOAD_START = sound("item.crossbow.loading_start");
	private static final SoundEvent RELOAD_END = sound("item.crossbow.loading_end");
	private static final SoundEvent HITMARKER = sound("entity.arrow.hit_player");

	private final GunStats stats;

	public GunItem(Properties properties, GunStats stats) {
		super(properties);
		this.stats = stats;
	}

	private static SoundEvent sound(String path) {
		return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path));
	}

	// ---------------------------------------------------------------- use / shoot

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		int ammo = stack.getOrDefault(RustItems.AMMO, 0);

		// Sneak + right click = manual reload
		if (player.isShiftKeyDown()) {
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
				reload(sl, sp, stack);
			}
			return InteractionResult.CONSUME;
		}

		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		// Empty magazine: try to reload automatically
		if (ammo <= 0) {
			player.getCooldowns().addCooldown(stack, 10);
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
				reload(sl, sp, stack);
			}
			return InteractionResult.FAIL;
		}

		if (level.isClientSide()) {
			// Recoil: kick the camera up (and a little sideways) on the client
			RandomSource rnd = level.getRandom();
			float kick = stats.recoil() * (0.8f + rnd.nextFloat() * 0.4f);
			player.setXRot(Mth.clamp(player.getXRot() - kick, -90.0f, 90.0f));
			player.setYRot(player.getYRot() + (rnd.nextFloat() - 0.5f) * kick * 0.6f);
			player.getCooldowns().addCooldown(stack, stats.cooldown());
			return InteractionResult.SUCCESS;
		}

		if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
			stack.set(RustItems.AMMO, ammo - 1);
			player.getCooldowns().addCooldown(stack, stats.cooldown());
			fire(sl, sp);
			sp.displayClientMessage(Component.literal((ammo - 1) + " / " + stats.magSize() + "   |   " + countAmmo(sp)), true);
		}
		return InteractionResult.SUCCESS;
	}

	private void fire(ServerLevel level, ServerPlayer player) {
		RandomSource rnd = level.getRandom();
		Vec3 eye = player.getEyePosition();

		level.playSound(null, player.getX(), player.getY(), player.getZ(), CRACK, SoundSource.PLAYERS, 1.6f, stats.pitch());
		level.playSound(null, player.getX(), player.getY(), player.getZ(), THUMP, SoundSource.PLAYERS, 0.45f, stats.thump());

		Vec3 look = player.getLookAngle();
		level.sendParticles(ParticleTypes.SMOKE, eye.x + look.x * 1.2, eye.y + look.y * 1.2 - 0.15, eye.z + look.z * 1.2,
				2, 0.02, 0.02, 0.02, 0.01);

		for (int i = 0; i < stats.pellets(); i++) {
			float yaw = player.getYRot() + (rnd.nextFloat() - 0.5f) * 2.0f * stats.spread();
			float pitch = player.getXRot() + (rnd.nextFloat() - 0.5f) * 2.0f * stats.spread();
			Vec3 dir = direction(yaw, pitch);
			ray(level, player, eye, dir);
		}
	}

	private static Vec3 direction(float yawDeg, float pitchDeg) {
		float yaw = yawDeg * Mth.DEG_TO_RAD;
		float pitch = pitchDeg * Mth.DEG_TO_RAD;
		double cp = Mth.cos(pitch);
		return new Vec3(-Mth.sin(yaw) * cp, -Mth.sin(pitch), Mth.cos(yaw) * cp);
	}

	/** Hitscan bullet: stops at the first block, hits the first living entity on the way. */
	private void ray(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 dir) {
		double range = stats.range();
		Vec3 end = eye.add(dir.scale(range));

		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
		double maxDist = range;
		if (hitBlock) {
			end = blockHit.getLocation();
			maxDist = eye.distanceTo(end);
		}

		AABB search = new AABB(eye, end).inflate(1.0);
		List<Entity> candidates = level.getEntities(player, search,
				e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());

		LivingEntity best = null;
		Vec3 bestPos = null;
		double bestDist = maxDist;
		for (Entity e : candidates) {
			AABB box = e.getBoundingBox().inflate(0.1);
			var hit = box.clip(eye, end);
			if (hit.isPresent()) {
				double d = eye.distanceTo(hit.get());
				if (d <= bestDist) {
					bestDist = d;
					best = (LivingEntity) e;
					bestPos = hit.get();
				}
			}
		}

		if (best != null) {
			float damage = stats.damage();
			boolean head = bestPos.y > best.getY() + best.getBbHeight() * 0.82;
			if (head) damage *= 1.5f;
			best.invulnerableTime = 0; // allow fast successive hits
			best.hurtServer(level, level.damageSources().playerAttack(player), damage);
			level.sendParticles(ParticleTypes.CRIT, bestPos.x, bestPos.y, bestPos.z, head ? 8 : 4, 0.1, 0.1, 0.1, 0.15);
			level.playSound(null, bestPos.x, bestPos.y, bestPos.z, HITMARKER, SoundSource.PLAYERS, 0.5f, head ? 1.6f : 1.2f);
		} else if (hitBlock) {
			Vec3 p = blockHit.getLocation();
			level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 3, 0.05, 0.05, 0.05, 0.02);
		}
	}

	// ---------------------------------------------------------------- reload

	private void reload(ServerLevel level, ServerPlayer player, ItemStack stack) {
		int mag = stack.getOrDefault(RustItems.AMMO, 0);
		int need = stats.magSize() - mag;
		if (need <= 0) return;

		int taken = takeAmmo(player, need);
		if (taken <= 0) {
			player.displayClientMessage(Component.translatable("message.rustweapons.no_ammo"), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), EMPTY, SoundSource.PLAYERS, 0.8f, 1.6f);
			return;
		}

		stack.set(RustItems.AMMO, mag + taken);
		player.getCooldowns().addCooldown(stack, stats.reloadTicks());
		player.displayClientMessage(Component.translatable("message.rustweapons.reloading"), true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), RELOAD_START, SoundSource.PLAYERS, 0.9f, 1.3f);
		Scheduler.later(Math.max(2, stats.reloadTicks() - 4), () ->
				level.playSound(null, player.getX(), player.getY(), player.getZ(), RELOAD_END, SoundSource.PLAYERS, 0.9f, 1.5f));
	}

	/** Takes up to {@code need} ammo items from the inventory; creative players reload for free. */
	private int takeAmmo(ServerPlayer player, int need) {
		if (player.getAbilities().instabuild) return need;
		int taken = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && taken < need; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(stats.ammo())) {
				int n = Math.min(s.getCount(), need - taken);
				s.shrink(n);
				taken += n;
			}
		}
		return taken;
	}

	private int countAmmo(ServerPlayer player) {
		if (player.getAbilities().instabuild) return 999;
		int total = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(stats.ammo())) total += s.getCount();
		}
		return total;
	}

	// ---------------------------------------------------------------- magazine bar in the hotbar

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		int ammo = stack.getOrDefault(RustItems.AMMO, 0);
		return Math.round(13.0f * ammo / stats.magSize());
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xE0A020;
	}
}
