package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import io.github.marcsanzdev.chestseparators.util.SlotIndex;
import java.util.Map;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@link Slot#isItemValid} (Mojmap {@code mayPlace}) to enforce whitelist rules for direct
 * (cursor) and shift-click insertions. Uses {@link ClickTracker#IS_SHIFT_CLICK} to distinguish the two,
 * since both flow through {@code isItemValid}. If the rule toggle is OFF the slot behaves as vanilla; if
 * ON and the item is not whitelisted, insertion is blocked.
 *
 * <p>1.12.2 deltas: {@code Container}(field)→{@code IInventory inventory}; the modern private container-slot
 * field is exposed by the vanilla {@code Slot#getSlotIndex()} here (reached via {@link SlotIndex}, a normal
 * remappable call rather than a {@code @Shadow} method, whose SRG mapping the AP could not locate);
 * {@code Registry.ITEM.getKey}→{@code Item#getRegistryName}; {@code Player#getUUID}→{@code Entity#getUniqueID}.
 */
@Mixin(Slot.class)
public abstract class SlotWhitelistMixin {

    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    public void onMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        // The editor probes isItemValid to detect vanilla slot restrictions; don't let our own filter
        // enforcement pollute that probe (it would hide every unfiltered item when editing a filter).
        if (ClickTracker.BYPASS_ENFORCEMENT.get()) return;

        Slot self = (Slot) (Object) this;
        IInventory inventory = self.inventory;
        Map<Integer, SlotWhitelist> whitelists = null;
        int slotIndex = SlotIndex.of(self);

        if (inventory instanceof IWhitelistProvider) {
            // Block containers and entities carry their whitelist on the inventory itself.
            whitelists = ((IWhitelistProvider) inventory).getWhitelists();
        } else if (inventory instanceof InventoryPlayer) {
            InventoryPlayer pinv = (InventoryPlayer) inventory;
            // The player's own inventory filters live server-side, keyed by raw inventory index
            // (which matches Slot#getSlotIndex). Enforced here for manual and shift-click placement.
            whitelists = ChestSeparatorsState.INVENTORY_FILTERS.get(pinv.player.getUniqueID());
        }

        if (whitelists != null && whitelists.containsKey(slotIndex)) {
            SlotWhitelist wl = whitelists.get(slotIndex);
            boolean isAllowedItem =
                    io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack);
            boolean isShift = ClickTracker.IS_SHIFT_CLICK.get();

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
