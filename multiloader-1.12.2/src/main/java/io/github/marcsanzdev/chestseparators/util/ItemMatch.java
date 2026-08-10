package io.github.marcsanzdev.chestseparators.util;

import net.minecraft.item.ItemStack;

/**
 * "Same item, same NBT" comparison, matching the modern ports' {@code isSameItemSameTags}. On 1.12.2 this
 * combines the vanilla primitives {@link ItemStack#areItemsEqual} (same item + metadata, ignores count) and
 * {@link ItemStack#areItemStackTagsEqual} (same NBT).
 */
public final class ItemMatch {
    private ItemMatch() {}

    public static boolean sameItemSameTags(ItemStack a, ItemStack b) {
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }
}
