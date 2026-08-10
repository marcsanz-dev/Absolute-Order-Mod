package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import io.github.marcsanzdev.chestseparators.util.SlotIndex;
import java.util.Map;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotShulkerBox;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mirrors {@link SlotWhitelistMixin} for {@link SlotShulkerBox}, which overrides {@code isItemValid} without
 * calling {@code super}, so the base-slot mixin never fires for Shulker Box slots.
 */
@Mixin(SlotShulkerBox.class)
public abstract class ShulkerBoxSlotMixin {

    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    public void onMayPlaceShulker(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot thisSlot = (Slot) (Object) this;

        if (thisSlot.inventory instanceof IWhitelistProvider) {
            IWhitelistProvider provider = (IWhitelistProvider) thisSlot.inventory;
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();
            int slotIndex = SlotIndex.of(thisSlot);

            if (whitelists != null && whitelists.containsKey(slotIndex)) {
                SlotWhitelist wl = whitelists.get(slotIndex);
                String itemId = stack.getItem().getRegistryName().toString();
                boolean isAllowedItem = wl.allowedItems().contains(itemId);
                boolean isShift = ClickTracker.IS_SHIFT_CLICK.get();

                if (isShift) {
                    if (wl.allowShift() && !isAllowedItem) cir.setReturnValue(false);
                } else {
                    if (wl.allowManual() && !isAllowedItem) cir.setReturnValue(false);
                }
            }
        }
    }
}
