package com.rustweapons;

import net.minecraft.world.item.Item;

/**
 * One kind of ammo a gun can load.
 *
 * @param item           the ammo item
 * @param damageMult     damage multiplier
 * @param spreadMult     spread multiplier (lower = more accurate)
 * @param rangeMult      range multiplier
 * @param fireTicks      how long the target burns (0 = no fire)
 * @param pelletOverride if > 0, replaces the number of pellets per shot (slugs = 1)
 */
public record AmmoType(Item item, float damageMult, float spreadMult, float rangeMult, int fireTicks, int pelletOverride) {}
