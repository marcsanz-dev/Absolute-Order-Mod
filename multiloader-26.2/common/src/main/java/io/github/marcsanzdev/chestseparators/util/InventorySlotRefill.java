package io.github.marcsanzdev.chestseparators.util;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Refills dedicated MAIN inventory slots from unfiltered MAIN slots, without touching hotbar,
 * armor, offhand or other filtered sources. The same algorithm runs on an inventory snapshot
 * for the Push preview, so it can accurately predict the action without mutating live items.
 */
public final class InventorySlotRefill {
    private InventorySlotRefill() {}

    public static boolean refill(Inventory inventory, Map<Integer, SlotWhitelist> filters) {
        if (filters == null || filters.isEmpty()) return false;
        List<ItemStack> projected = new ArrayList<>(Inventory.INVENTORY_SIZE);
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            projected.add(inventory.getItem(i).copy());
        }
        if (!refillStacks(projected, filters, inventory::getMaxStackSize)) return false;
        for (int i = 9; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack before = inventory.getItem(i);
            ItemStack after = projected.get(i);
            if (before.getCount() != after.getCount()
                    || !ItemStack.isSameItemSameComponents(before, after)) {
                inventory.setItem(i, after);
            }
        }
        inventory.setChanged();
        return true;
    }

    /** Mutates ONLY the supplied copied stacks. Raw player inventory indices are used throughout. */
    public static boolean refillStacks(
            List<ItemStack> stacks,
            Map<Integer, SlotWhitelist> filters,
            ToIntFunction<ItemStack> inventoryMax) {
        if (filters == null || filters.isEmpty() || stacks.size() < Inventory.INVENTORY_SIZE) return false;
        boolean changed = false;
        // First finish matching, partly-filled filter slots, then occupy empty filter slots.
        for (int target = 9; target < Inventory.INVENTORY_SIZE; target++) {
            SlotWhitelist filter = filters.get(target);
            if (filter == null || filter.allowedItems().isEmpty()) continue;
            ItemStack home = stacks.get(target);
            if (home.isEmpty() || !filter.allowedItems().contains(itemId(home))) continue;
            changed |= topUp(stacks, filters, target, home, inventoryMax);
        }
        for (int target = 9; target < Inventory.INVENTORY_SIZE; target++) {
            SlotWhitelist filter = filters.get(target);
            if (filter == null || filter.allowedItems().isEmpty() || !stacks.get(target).isEmpty()) continue;
            for (String wanted : filter.allowedItems()) {
                int source = findSource(stacks, filters, target, wanted, null);
                if (source < 0) continue;
                stacks.set(target, stacks.get(source).copy());
                stacks.set(source, ItemStack.EMPTY);
                changed = true;
                changed |= topUp(stacks, filters, target, stacks.get(target), inventoryMax);
                break;
            }
        }
        return changed;
    }

    private static boolean topUp(List<ItemStack> stacks, Map<Integer, SlotWhitelist> filters,
            int target, ItemStack home, ToIntFunction<ItemStack> inventoryMax) {
        boolean changed = false;
        String id = itemId(home);
        int limit = Math.min(home.getMaxStackSize(), inventoryMax.applyAsInt(home));
        while (home.getCount() < limit) {
            int source = findSource(stacks, filters, target, id, home);
            if (source < 0) break;
            ItemStack extra = stacks.get(source);
            int take = Math.min(limit - home.getCount(), extra.getCount());
            if (take <= 0) break;
            home.grow(take);
            extra.shrink(take);
            stacks.set(source, extra.isEmpty() ? ItemStack.EMPTY : extra);
            stacks.set(target, home);
            changed = true;
        }
        return changed;
    }

    private static int findSource(List<ItemStack> stacks, Map<Integer, SlotWhitelist> filters,
            int target, String itemId, ItemStack matching) {
        for (int raw = 9; raw < Inventory.INVENTORY_SIZE; raw++) {
            if (raw == target || filters.containsKey(raw)) continue;
            ItemStack candidate = stacks.get(raw);
            if (candidate.isEmpty() || !itemId(candidate).equals(itemId)) continue;
            if (matching != null && !ItemStack.isSameItemSameComponents(matching, candidate)) continue;
            return raw;
        }
        return -1;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
