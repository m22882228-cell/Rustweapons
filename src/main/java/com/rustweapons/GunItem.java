package com.rustweapons;

import com.google.common.base.Suppliers;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import java.util.function.Consumer;
import java.util.function.Supplier;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
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

public class GunItem extends Item implements GeoItem {
	private static final SoundEvent CRACK = sound("entity.firework_rocket.blast");
	private static final SoundEvent THUMP = sound("entity.generic.explode");
	private static final SoundEvent EMPTY = sound("block.dispenser.fail");
	private static final SoundEvent RELOAD_START = sound("item.crossbow.loading_start");
	private static final SoundEvent RELOAD_END = sound("item.crossbow.loading_end");
	private static final SoundEvent HITMARKER = sound("entity.arrow.hit_player");

	private final GunStats stats;
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

	public GunItem(Properties properties, GunStats stats) {
		super(properties);
		this.stats = stats;
		GeoItem.registerSyncedAnimatable(this);
	}

	// ---------------------------------------------------------------- GeckoLib (3D model + animations)

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		consumer.accept(new GeoRenderProvider() {
			private final Supplier<GeoItemRenderer<GunItem>> renderer = Suppliers.memoize(() -> new GeoItemRenderer<>(GunItem.this));

			@Override
			public GeoItemRenderer<GunItem> getGeoItemRenderer() {
				return this.renderer.get();
			}
		});
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<GunItem>("actions", state -> PlayState.STOP)
				.triggerableAnim("shoot", RawAnimation.begin().thenPlay("gun.shoot"))
				.triggerableAnim("reload", RawAnimation.begin().thenPlay("gun.reload")));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.geoCache;
	}

	private static SoundEvent sound(String path) {
		return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(path));
	}

	// ---------------------------------------------------------------- use / shoot

	/** Right click is "aim down sights", which the client handles. Nothing to do here. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return InteractionResult.CONSUME;
	}

	/** Full-auto guns keep shooting while the fire button is held. */
	public boolean isAutomatic() {
		return stats.cooldown() <= 3;
	}

	/** Field-of-view factor while aiming (smaller = more zoom). */
	public float zoom() {
		if (stats.range() >= 200) return 0.3f;
		if (stats.range() >= 160) return 0.45f;
		return 0.7f;
	}

	/** Server side: the client is holding/clicking the fire button. */
	public void handleFire(ServerPlayer player, boolean aiming) {
		ItemStack stack = player.getMainHandItem();
		if (stack.getItem() != this || !(player.level() instanceof ServerLevel level)) return;
		if (player.getCooldowns().isOnCooldown(stack)) return;

		int ammo = stack.getOrDefault(RustItems.AMMO, 0);
		if (ammo <= 0) {
			// empty magazine: try to reload automatically
			player.getCooldowns().addCooldown(stack, 10);
			reload(level, player, stack);
			return;
		}

		int kind = Mth.clamp(stack.getOrDefault(RustItems.AMMO_KIND, 0), 0, stats.ammo().length - 1);
		AmmoType type = stats.ammo()[kind];
		stack.set(RustItems.AMMO, ammo - 1);
		player.getCooldowns().addCooldown(stack, stats.cooldown());
		fire(level, player, type, aiming);
		triggerAnim(player, GeoItem.getOrAssignId(stack, level), "actions", "shoot");
		player.displayClientMessage(Component.literal((ammo - 1) + " / " + stats.magSize() + "   |   " + countAmmo(player, type.item()) + "   ")
				.append(Component.translatable(type.item().getDescriptionId())), true);

		RandomSource rnd = level.getRandom();
		float kick = stats.recoil() * (aiming ? 0.7f : 1.0f) * (0.8f + rnd.nextFloat() * 0.4f);
		ServerPlayNetworking.send(player, new RecoilPayload(kick, (rnd.nextFloat() - 0.5f) * kick * 0.6f));
	}

	/** Server side: the reload key was pressed. */
	public void handleReload(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (stack.getItem() != this || !(player.level() instanceof ServerLevel level)) return;
		if (player.getCooldowns().isOnCooldown(stack)) return;
		reload(level, player, stack);
	}

	private void fire(ServerLevel level, ServerPlayer player, AmmoType type, boolean aiming) {
		RandomSource rnd = level.getRandom();
		float spreadDeg = stats.spread() * type.spreadMult() * (aiming ? 0.4f : 1.0f);
		float damage = stats.damage() * type.damageMult();
		double range = stats.range() * type.rangeMult();
		int pellets = type.pelletOverride() > 0 ? type.pelletOverride() : stats.pellets();
		Vec3 eye = player.getEyePosition();

		level.playSound(null, player.getX(), player.getY(), player.getZ(), CRACK, SoundSource.PLAYERS, 1.6f, stats.pitch());
		level.playSound(null, player.getX(), player.getY(), player.getZ(), THUMP, SoundSource.PLAYERS, 0.45f, stats.thump());

		Vec3 look = player.getLookAngle();
		level.sendParticles(ParticleTypes.SMOKE, eye.x + look.x * 1.2, eye.y + look.y * 1.2 - 0.15, eye.z + look.z * 1.2,
				2, 0.02, 0.02, 0.02, 0.01);

		for (int i = 0; i < pellets; i++) {
			float yaw = player.getYRot() + (rnd.nextFloat() - 0.5f) * 2.0f * spreadDeg;
			float pitch = player.getXRot() + (rnd.nextFloat() - 0.5f) * 2.0f * spreadDeg;
			Vec3 dir = direction(yaw, pitch);
			ray(level, player, eye, dir, range, damage, type.fireTicks());
		}
	}

	private static Vec3 direction(float yawDeg, float pitchDeg) {
		float yaw = yawDeg * Mth.DEG_TO_RAD;
		float pitch = pitchDeg * Mth.DEG_TO_RAD;
		double cp = Mth.cos(pitch);
		return new Vec3(-Mth.sin(yaw) * cp, -Mth.sin(pitch), Mth.cos(yaw) * cp);
	}

	/** Hitscan bullet: stops at the first block, hits the first living entity on the way. */
	private void ray(ServerLevel level, ServerPlayer player, Vec3 eye, Vec3 dir, double range, float baseDamage, int fireTicks) {
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
			float damage = baseDamage;
			boolean head = bestPos.y > best.getY() + best.getBbHeight() * 0.82;
			if (head) damage *= 1.5f;
			best.invulnerableTime = 0; // allow fast successive hits
			best.hurtServer(level, level.damageSources().playerAttack(player), damage);
			if (fireTicks > 0) best.setRemainingFireTicks(fireTicks);
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

		// a non-empty magazine keeps its ammo kind; an empty one takes the first kind found in the inventory
		int kind = mag > 0 ? Mth.clamp(stack.getOrDefault(RustItems.AMMO_KIND, 0), 0, stats.ammo().length - 1) : pickKind(player);
		int taken = kind < 0 ? 0 : takeAmmo(player, need, stats.ammo()[kind].item());
		if (taken <= 0) {
			player.displayClientMessage(Component.translatable("message.rustweapons.no_ammo"), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), EMPTY, SoundSource.PLAYERS, 0.8f, 1.6f);
			return;
		}

		stack.set(RustItems.AMMO_KIND, kind);
		stack.set(RustItems.AMMO, mag + taken);
		triggerAnim(player, GeoItem.getOrAssignId(stack, level), "actions", "reload");
		player.getCooldowns().addCooldown(stack, stats.reloadTicks());
		player.displayClientMessage(Component.translatable("message.rustweapons.reloading"), true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), RELOAD_START, SoundSource.PLAYERS, 0.9f, 1.3f);
		Scheduler.later(Math.max(2, stats.reloadTicks() - 4), () ->
				level.playSound(null, player.getX(), player.getY(), player.getZ(), RELOAD_END, SoundSource.PLAYERS, 0.9f, 1.5f));
	}

	private int pickKind(ServerPlayer player) {
		for (int k = 0; k < stats.ammo().length; k++) {
			if (countAmmo(player, stats.ammo()[k].item()) > 0) return k;
		}
		return player.getAbilities().instabuild ? 0 : -1;
	}

	/** Takes up to {@code need} ammo items from the inventory; creative players reload for free. */
	private int takeAmmo(ServerPlayer player, int need, Item ammoItem) {
		if (player.getAbilities().instabuild) return need;
		int taken = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && taken < need; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ammoItem)) {
				int n = Math.min(s.getCount(), need - taken);
				s.shrink(n);
				taken += n;
			}
		}
		return taken;
	}

	private int countAmmo(ServerPlayer player, Item ammoItem) {
		if (player.getAbilities().instabuild) return 999;
		int total = 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ammoItem)) total += s.getCount();
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
