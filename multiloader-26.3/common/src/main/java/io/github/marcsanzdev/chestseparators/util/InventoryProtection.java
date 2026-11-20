package io.github.marcsanzdev.chestseparators.util;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Protects item counts assigned to player-inventory filters from automated outbound transfers.
 * Only matching items are protected; a wrong item in a filtered slot can still be deposited.
 * A positive target is shared by all slots of one filter group, per allowed item type.
 */
public final class InventoryProtection {
    private InventoryProtection() {}

    public static int movableCount(Inventory inventory, Map<Integer, SlotWhitelist> filters, int slot) {
        return movableCount(inventory.getContainerSize(), inventory::getItem, filters, slot);
    }

    /** Preview counterpart: uses the post-refill snapshot, not the current live inventory. */
    public static int movableCount(java.util.List<ItemStack> items, Map<Integer, SlotWhitelist> filters, int slot) {
        return movableCount(items.size(), items::get, filters, slot);
    }

    private static int movableCount(int size, java.util.function.IntFunction<ItemStack> get,
            Map<Integer, SlotWhitelist> filters, int slot) {
        if (slot < 0 || slot >= size) return 0;
        ItemStack source = get.apply(slot);
        if (source.isEmpty()) return 0;
        if (filters == null) return source.getCount();
        SlotWhitelist filter = filters.get(slot);
        if (filter == null) return source.getCount();

        String itemId = BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
        if (!filter.allowedItems().contains(itemId)) return source.getCount();
        if (filter.targetCount() <= 0) return 0;

        int earlier = 0;
        for (int i = 0; i < slot; i++) {
            SlotWhitelist previous = filters.get(i);
            if (previous == null || !filter.groupId().equals(previous.groupId())
                    || !previous.allowedItems().contains(itemId)) continue;
            ItemStack existing = get.apply(i);
            if (!existing.isEmpty() && BuiltInRegistries.ITEM.getKey(existing.getItem()).toString().equals(itemId)) {
                earlier += existing.getCount();
            }
        }
        return InventoryTransferPolicy.movableCount(source.getCount(), filter.targetCount(), earlier);
    }

}
