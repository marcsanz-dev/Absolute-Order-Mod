package io.github.marcsanzdev.chestseparators.util;

import net.minecraft.world.item.ItemStack;

/**
 * 1.16.5 shim for {@code ItemStack.isSameItemSameTags(a, b)} (added later). Combines the 1.16.5 primitives:
 * {@code isSame} (same item, ignores count/tags) and {@code tagMatches} (same NBT).
 */
public final class ItemMatch {
    private ItemMatch() {}

    public static boolean sameItemSameTags(ItemStack a, ItemStack b) {
        return ItemStack.isSame(a, b) && ItemStack.tagMatches(a, b);
    }
}
