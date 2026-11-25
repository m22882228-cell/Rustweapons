package com.rustweapons;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class RustItems {
	private static final List<Item> ALL = new ArrayList<>();

	/** Bullets currently loaded in the gun's magazine. */
	public static final DataComponentType<Integer> AMMO = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE, RustWeapons.id("ammo"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	// ---- ammo ----
	public static final Item RIFLE_AMMO = register("rifle_ammo", Item::new, new Item.Properties());
	public static final Item PISTOL_AMMO = register("pistol_ammo", Item::new, new Item.Properties());
	public static final Item SHOTGUN_SHELL = register("shotgun_shell", Item::new, new Item.Properties());

	// ---- guns ----           mag  dmg  pel  spr   rec   rng  cd  reload  ammo           pitch thump
	public static final Item AK47 = gun("ak47", new GunStats(30, 6.0f, 1, 1.2f, 2.4f, 120, 3, 50, RIFLE_AMMO, 1.5f, 1.4f));
	public static final Item LR300 = gun("lr300", new GunStats(30, 5.5f, 1, 0.8f, 1.6f, 120, 3, 45, RIFLE_AMMO, 1.7f, 1.5f));
	public static final Item MP5A4 = gun("mp5a4", new GunStats(30, 4.0f, 1, 1.4f, 1.0f, 70, 2, 40, PISTOL_AMMO, 2.0f, 2.0f));
	public static final Item THOMPSON = gun("thompson", new GunStats(20, 4.5f, 1, 1.8f, 1.4f, 70, 3, 45, PISTOL_AMMO, 1.3f, 1.2f));
	public static final Item SEMI_PISTOL = gun("semi_pistol", new GunStats(10, 4.5f, 1, 1.0f, 1.6f, 80, 4, 30, PISTOL_AMMO, 1.8f, 1.7f));
	public static final Item REVOLVER = gun("revolver", new GunStats(8, 4.0f, 1, 1.0f, 1.5f, 80, 6, 55, PISTOL_AMMO, 1.4f, 1.3f));
	public static final Item PYTHON = gun("python", new GunStats(6, 9.0f, 1, 0.6f, 3.5f, 90, 10, 60, PISTOL_AMMO, 1.0f, 0.9f));
	public static final Item M39 = gun("m39", new GunStats(20, 9.0f, 1, 0.25f, 2.0f, 160, 5, 55, RIFLE_AMMO, 1.1f, 1.0f));
	public static final Item BOLT_ACTION = gun("bolt_action", new GunStats(4, 16.0f, 1, 0.1f, 4.5f, 200, 25, 65, RIFLE_AMMO, 0.9f, 0.8f));
	public static final Item PUMP_SHOTGUN = gun("pump_shotgun", new GunStats(6, 2.6f, 8, 5.5f, 5.0f, 35, 22, 70, SHOTGUN_SHELL, 0.9f, 0.7f));

	private RustItems() {}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, RustWeapons.id(name));
		T item = factory.apply(props.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		ALL.add(item);
		return item;
	}

	private static Item gun(String name, GunStats stats) {
		return register(name, props -> new GunItem(props, stats), new Item.Properties().stacksTo(1));
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
			for (Item item : ALL) {
				entries.accept(item);
			}
		});
	}
}
