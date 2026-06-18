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

/**
 * Intercepts the {@code Slot.canInsert} check inside the {@code ScreenHandler.insertItem} loop,
 * which drives the shift-click quick-move flow. This redirect enforces the Shift rule on each
 * target slot independently, complementing the HEAD/RETURN guards in {@link ScreenHandlerClickMixin}.
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerWhitelistMixin {

    @Shadow @Final public DefaultedList<Slot> slots;

    @Redirect(method = "insertItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/slot/Slot;canInsert(Lnet/minecraft/item/ItemStack;)Z"))
    private boolean enforceWhitelistOnShiftClick(Slot slot, ItemStack stack) {
        // Respect vanilla constraints first (also triggers SlotWhitelistMixin).
        if (!slot.canInsert(stack)) {
            return false;
        }

        if (slot.inventory instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();
            int slotIndex = slot.getIndex();

            if (whitelists != null && whitelists.containsKey(slotIndex)) {
                SlotWhitelist whitelist = whitelists.get(slotIndex);

                // Shift rule ON: enforce the item filter.
                // Shift rule OFF: treat the slot as vanilla (no restriction).
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
