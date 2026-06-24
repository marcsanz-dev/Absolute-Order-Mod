package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsMain;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces inventory filters on {@code SWAP} clicks (the F key), which bypass {@code canInsert}
 * entirely by swapping items directly. Treats the swap as a shift-click for filter purposes:
 * if either the source slot or the swap-target slot has the Shift rule enabled and the item
 * moving into it is not whitelisted, the swap is cancelled.
 *
 * <p>The swap-target is identified by {@code button}: 0-8 maps to the hotbar raw indices in
 * {@link PlayerInventory}, and 40 maps to the offhand slot.
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerSwapFilterMixin {

    @Shadow
    @Final
    public DefaultedList<Slot> slots;

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void enforceFilterOnSwap(
            int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (actionType != SlotActionType.SWAP) return;
        if (slotIndex < 0 || slotIndex >= slots.size()) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsMain.INVENTORY_FILTERS.get(player.getUuid());
        if (filters == null || filters.isEmpty()) return;

        Slot sourceSlot = slots.get(slotIndex);
        ItemStack sourceItem = sourceSlot.getStack(); // moves INTO the swap-target slot

        // button 0-8 = hotbar raw PlayerInventory index; button 40 = offhand raw index.
        int targetRawIndex = (button >= 0 && button <= 8) ? button : 40;
        // PlayerInventory.getStack handles 0-35 (main), 36-39 (armor), 40 (offhand).
        ItemStack targetItem = player.getInventory().getStack(targetRawIndex); // moves INTO sourceSlot

        // Check: can sourceItem enter the swap-target slot?
        SlotWhitelist wlTarget = filters.get(targetRawIndex);
        if (wlTarget != null && wlTarget.allowShift() && !sourceItem.isEmpty()) {
            String id = Registries.ITEM.getId(sourceItem.getItem()).toString();
            if (!wlTarget.allowedItems().contains(id)) {
                ci.cancel();
                return;
            }
        }

        // Check: can targetItem enter the source slot (only relevant when it is a PlayerInventory slot)?
        if (sourceSlot.inventory instanceof PlayerInventory) {
            SlotWhitelist wlSource = filters.get(sourceSlot.getIndex());
            if (wlSource != null && wlSource.allowShift() && !targetItem.isEmpty()) {
                String id = Registries.ITEM.getId(targetItem.getItem()).toString();
                if (!wlSource.allowedItems().contains(id)) {
                    ci.cancel();
                }
            }
        }
    }
}
