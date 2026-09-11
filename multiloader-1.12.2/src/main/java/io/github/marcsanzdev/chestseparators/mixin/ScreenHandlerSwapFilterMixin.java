package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.SlotIndex;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces inventory filters on {@code SWAP} clicks (the number/F keys), which bypass {@code isItemValid}
 * by swapping items directly. Treats the swap as a shift-click: if either the source slot or the swap
 * target has the Shift rule enabled and the incoming item is not whitelisted, the swap is cancelled.
 * The swap target is identified by {@code button}: 0-8 = hotbar raw indices, 40 = offhand.
 *
 * <p>1.12.2 deltas: {@code clicked}→{@code slotClick(int,int,ClickType,EntityPlayer)} (returns the affected
 * ItemStack, cancelling returns {@code ItemStack.EMPTY}); {@code slots}→{@code inventorySlots};
 * {@code Slot#getItem}→{@code getStack}; {@code Inventory#getItem}→{@code getStackInSlot};
 * {@code Slot#container}→{@code inventory}.
 */
@Mixin(Container.class)
public abstract class ScreenHandlerSwapFilterMixin {

    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true)
    private void enforceFilterOnSwap(
            int slotIndex, int button, ClickType actionType, EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        if (actionType != ClickType.SWAP) return;
        List<Slot> inventorySlots = ((ContainerInventorySlotsAccessor) (Object) this).chestseparators$getInventorySlots();
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUniqueID());
        if (filters == null || filters.isEmpty()) return;

        Slot sourceSlot = inventorySlots.get(slotIndex);
        ItemStack sourceItem = sourceSlot.getStack(); // moves INTO the swap-target slot

        // button 0-8 = hotbar raw index; button 40 = offhand raw index.
        int targetRawIndex = (button >= 0 && button <= 8) ? button : 40;
        ItemStack targetItem = player.inventory.getStackInSlot(targetRawIndex); // moves INTO sourceSlot

        // Can sourceItem enter the swap-target slot?
        SlotWhitelist wlTarget = filters.get(targetRawIndex);
        if (wlTarget != null && wlTarget.allowShift() && !sourceItem.isEmpty()) {
            if (!io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wlTarget.allowedItems(), sourceItem)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
        }

        // Can targetItem enter the source slot (only relevant when it is a player-inventory slot)?
        if (sourceSlot.inventory instanceof InventoryPlayer) {
            SlotWhitelist wlSource = filters.get(SlotIndex.of(sourceSlot));
            if (wlSource != null && wlSource.allowShift() && !targetItem.isEmpty()) {
                if (!io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wlSource.allowedItems(), targetItem)) {
                    cir.setReturnValue(ItemStack.EMPTY);
                }
            }
        }
    }
}
