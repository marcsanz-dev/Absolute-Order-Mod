package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsMain;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import java.util.Map;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends inventory-filter enforcement to ArmorSlot, which overrides {@code canInsert}
 * without delegating to its superclass, so {@link SlotWhitelistMixin} on {@code Slot} cannot
 * intercept armor-slot clicks. Applies the same Manual/Shift rule logic.
 *
 * <p>ArmorSlot is package-private; its members ({@code inventory}, {@code getIndex}) are inherited
 * from {@link Slot} and cannot be resolved by {@code @Shadow} on a string-target mixin with no
 * refMap. We cast through {@code Slot} instead — safe because ArmorSlot extends Slot.
 */
@Mixin(targets = "net.minecraft.screen.slot.ArmorSlot")
public abstract class ArmorSlotFilterMixin {

    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    public void onCanInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        if (!(self.inventory instanceof PlayerInventory pinv)) return;

        Map<Integer, SlotWhitelist> whitelists = ChestSeparatorsMain.INVENTORY_FILTERS.get(pinv.player.getUuid());
        if (whitelists == null) return;

        int slotIndex = self.getIndex();
        SlotWhitelist wl = whitelists.get(slotIndex);
        if (wl == null) return;

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        boolean isAllowedItem = wl.allowedItems().contains(itemId);
        boolean isShift = ClickTracker.IS_SHIFT_CLICK.get();

        if (isShift) {
            if (wl.allowShift() && !isAllowedItem) cir.setReturnValue(false);
        } else {
            if (wl.allowManual() && !isAllowedItem) cir.setReturnValue(false);
        }
    }
}
