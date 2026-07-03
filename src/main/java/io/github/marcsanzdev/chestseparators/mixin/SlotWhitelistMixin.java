package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Intercepts the base Slot class to prevent items from being inserted natively.
 * Respects the individual UI toggles to allow bypassing the filter (Vanilla fallback).
 */
@Mixin(Slot.class)
public abstract class SlotWhitelistMixin {

    @Shadow @Final public Inventory inventory;
    @Shadow public abstract int getIndex();

    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    public void onCanInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (this.inventory instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();

            if (whitelists != null && whitelists.containsKey(this.getIndex())) {
                SlotWhitelist wl = whitelists.get(this.getIndex());
                String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                boolean isAllowedItem = wl.allowedItems().contains(itemId);
                boolean isShift = ClickTracker.isShiftClick();

                if (isShift) {
                    // If it is a Shift-Click, the toggle is ON, and the item is NOT allowed -> BLOCK
                    if (wl.allowShift() && !isAllowedItem) {
                        cir.setReturnValue(false);
                    }
                    // If the toggle is OFF, do nothing.
                    // Minecraft's native code will continue and act as a normal slot.
                } else {
                    // If it is Manual, the toggle is ON, and the item is NOT allowed -> BLOCK
                    if (wl.allowManual() && !isAllowedItem) {
                        cir.setReturnValue(false);
                    }
                }
            }
        }
    }
}