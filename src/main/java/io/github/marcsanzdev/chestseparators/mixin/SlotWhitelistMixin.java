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
 * Intercepts {@link Slot#canInsert} to enforce whitelist rules for direct (cursor) and
 * shift-click insertions. Uses {@link ClickTracker#IS_SHIFT_CLICK} to distinguish between
 * the two interaction types, since both ultimately flow through {@code canInsert}.
 *
 * <p>If the rule toggle is OFF, the slot behaves as a vanilla slot and accepts any item.
 * If the rule toggle is ON and the item is not whitelisted, insertion is blocked.
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
                boolean isShift = ClickTracker.IS_SHIFT_CLICK.get();

                if (isShift) {
                    // Shift rule ON + item not whitelisted → block the insertion.
                    // Shift rule OFF → fall through to vanilla behavior.
                    if (wl.allowShift() && !isAllowedItem) {
                        cir.setReturnValue(false);
                    }
                } else {
                    // Manual rule ON + item not whitelisted → block the insertion.
                    if (wl.allowManual() && !isAllowedItem) {
                        cir.setReturnValue(false);
                    }
                }
            }
        }
    }
}
