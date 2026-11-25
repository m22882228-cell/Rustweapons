package com.rustweapons;

import net.minecraft.world.item.Item;

/**
 * @param magSize     magazine capacity
 * @param damage      damage per bullet/pellet (20 = a full player health bar)
 * @param pellets     bullets per shot (shotgun > 1)
 * @param spread      random spread in degrees
 * @param recoil      camera kick in degrees per shot
 * @param range       max range in blocks
 * @param cooldown    ticks between shots
 * @param reloadTicks ticks a reload takes
 * @param ammo        ammo item used
 * @param pitch       pitch of the "crack" sound
 * @param thump       pitch of the low "thump" sound
 */
public record GunStats(int magSize, float damage, int pellets, float spread, float recoil, float range,
					   int cooldown, int reloadTicks, Item ammo, float pitch, float thump) {}
