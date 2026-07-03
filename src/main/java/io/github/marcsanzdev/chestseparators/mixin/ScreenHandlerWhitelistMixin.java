package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

// Intercepts the Shift-Click (Quick Move) logic to respect the whitelists.
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerWhitelistMixin {

    @Shadow @Final public DefaultedList<Slot> slots;

    // Redirects the 'canInsert' check specifically during the 'insertItem' loop used by Shift-Click.
    @Redirect(method = "insertItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;canInsert(Lnet/minecraft/item/ItemStack;)Z"))
    private boolean enforceWhitelistOnShiftClick(Slot slot, ItemStack stack) {
        // First, check the vanilla constraints (this also triggers our SlotWhitelistMixin).
        if (!slot.canInsert(stack)) {
            return false;
        }

        // Then, enforce our custom whitelist logic for Shift-Clicks.
        if (slot.inventory instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();
            int slotIndex = slot.getIndex();

            if (whitelists != null && whitelists.containsKey(slotIndex)) {
                SlotWhitelist whitelist = whitelists.get(slotIndex);

                // Option A: If the toggle is ON, we enforce the item filter.
                // If it is OFF, we bypass it completely and act as a normal vanilla slot.
                if (whitelist.allowShift()) {
                    String incomingItemId = Registries.ITEM.getId(stack.getItem()).toString();

                    if (!whitelist.allowedItems().contains(incomingItemId)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}