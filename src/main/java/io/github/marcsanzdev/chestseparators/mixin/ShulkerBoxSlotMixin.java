package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.ShulkerBoxSlot;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Intercepts the specific ShulkerBoxSlot class since it overrides canInsert without calling super.
 */
@Mixin(ShulkerBoxSlot.class)
public abstract class ShulkerBoxSlotMixin {

    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    public void onCanInsertShulker(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        // Cast 'this' to Slot to safely access the base properties without complex Shadowing
        Slot thisSlot = (Slot) (Object) this;

        if (thisSlot.inventory instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();

            if (whitelists != null && whitelists.containsKey(thisSlot.getIndex())) {
                SlotWhitelist wl = whitelists.get(thisSlot.getIndex());
                String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                boolean isAllowedItem = wl.allowedItems().contains(itemId);
                boolean isShift = ClickTracker.isShiftClick();

                if (isShift) {
                    if (wl.allowShift() && !isAllowedItem) {
                        cir.setReturnValue(false);
                    }
                } else {
                    if (wl.allowManual() && !isAllowedItem) {
                        cir.setReturnValue(false);
                    }
                }
            }
        }
    }
}