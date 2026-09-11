package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import io.github.marcsanzdev.chestseparators.util.FilterPriority;
import io.github.marcsanzdev.chestseparators.util.SlotIndex;
import java.util.List;
import java.util.Map;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts the {@code Slot.isItemValid} check inside the {@code Container.mergeItemStack} loop (the
 * shift-click quick-move flow). This redirect enforces the Shift rule on each target slot independently,
 * complementing {@link SlotWhitelistMixin}, and applies the filter-priority ordering.
 *
 * <p>1.12.2 deltas: {@code moveItemStackTo}→{@code mergeItemStack}; {@code Slot#mayPlace}→
 * {@code Slot#isItemValid}; {@code slots}→{@code inventorySlots}; {@code Slot#container}→{@code inventory}.
 */
@Mixin(Container.class)
public abstract class ScreenHandlerWhitelistMixin {

    @Redirect(
            method = "mergeItemStack",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/inventory/Slot;isItemValid(Lnet/minecraft/item/ItemStack;)Z"))
    private boolean enforceWhitelistOnShiftClick(Slot slot, ItemStack stack) {
        // Respect vanilla constraints first (also triggers SlotWhitelistMixin).
        if (!slot.isItemValid(stack)) {
            return false;
        }

        if (slot.inventory instanceof IWhitelistProvider) {
            IWhitelistProvider provider = (IWhitelistProvider) slot.inventory;
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();
            int slotIndex = SlotIndex.of(slot);

            if (whitelists != null && whitelists.containsKey(slotIndex)) {
                SlotWhitelist whitelist = whitelists.get(slotIndex);

                // Shift rule ON: enforce the item filter. Shift rule OFF: treat as vanilla.
                if (whitelist.allowShift()) {
                    if (!io.github.marcsanzdev.chestseparators.util.ItemKey.matches(whitelist.allowedItems(), stack)) {
                        return false;
                    }
                }
            }
        }

        // Ordering rule: while a slot dedicated to this item still has room, ordinary slots decline it
        // so vanilla keeps scanning and drops the item into its dedicated slot.
        int[] range = ClickTracker.INSERT_RANGE.get();
        List<Slot> inventorySlots =
                ((ContainerInventorySlotsAccessor) (Object) this).chestseparators$getInventorySlots();
        if (range != null && FilterPriority.shouldDefer(inventorySlots, range[0], range[1], slot, stack)) {
            return false;
        }

        return true;
    }
}
