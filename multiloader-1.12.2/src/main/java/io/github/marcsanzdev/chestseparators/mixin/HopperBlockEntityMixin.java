package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.FilterPriority;
import java.util.Map;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces the Hopper insertion rule on all containers that implement {@link IWhitelistProvider}. When
 * the Hopper rule is enabled for a slot, only whitelisted items may be deposited by automation; when
 * disabled, the slot accepts any item as if unfiltered.
 *
 * <p>1.12.2 delta: the modern {@code HopperBlockEntity#canPlaceItemInContainer(Container,ItemStack,int,
 * Direction)} is the private-static {@code TileEntityHopper#canInsertItemInSlot(IInventory,ItemStack,int,
 * EnumFacing)} here — the same per-slot gate vanilla's insert path consults.
 */
@Mixin(TileEntityHopper.class)
public abstract class HopperBlockEntityMixin {

    @Inject(method = "canInsertItemInSlot", at = @At("HEAD"), cancellable = true)
    private static void onCanInsert(
            IInventory inventory, ItemStack stack, int slot, EnumFacing side, CallbackInfoReturnable<Boolean> cir) {
        if (inventory instanceof IWhitelistProvider) {
            IWhitelistProvider provider = (IWhitelistProvider) inventory;
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();

            if (whitelists != null && whitelists.containsKey(slot)) {
                SlotWhitelist wl = whitelists.get(slot);

                // Hopper rule ON: enforce the whitelist. Rule OFF: let vanilla decide.
                if (wl.allowHopper()) {
                    if (!io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack)) {
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }
        }

        // Same ordering rule as the shift-click quick-move: an item that has a dedicated filter slot
        // fills that slot first, so ordinary slots decline it while the dedicated one still has room.
        if (FilterPriority.shouldDefer(inventory, slot, stack)) {
            cir.setReturnValue(false);
        }
    }
}
