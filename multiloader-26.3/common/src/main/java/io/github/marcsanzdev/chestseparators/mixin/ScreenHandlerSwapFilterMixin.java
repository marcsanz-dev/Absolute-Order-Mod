package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.FilterPriority;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces inventory filters on {@code SWAP} clicks (the number/F keys), which bypass {@code mayPlace}
 * by swapping items directly. A disallowed destination still blocks the swap. When the ONLY
 * obstacle is an item displaced from the hotbar into a filtered MAIN inventory slot, relocate that
 * displaced stack to a suitable empty MAIN slot before completing the number-key action.
 * The swap target is identified by {@code button}: 0-8 = hotbar raw indices, 40 = offhand.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerSwapFilterMixin {

    @Shadow
    @Final
    public NonNullList<Slot> slots;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void enforceFilterOnSwap(
            int slotIndex, int button, ContainerInput actionType, Player player, CallbackInfo ci) {
        if (actionType != ContainerInput.SWAP) return;
        if (slotIndex < 0 || slotIndex >= slots.size()) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUUID());
        if (filters == null || filters.isEmpty()) return;

        Slot sourceSlot = slots.get(slotIndex);
        ItemStack sourceItem = sourceSlot.getItem(); // moves INTO the swap-target slot

        // button 0-8 = hotbar raw index; button 40 = offhand raw index.
        int targetRawIndex = (button >= 0 && button <= 8) ? button : 40;
        ItemStack targetItem = player.getInventory().getItem(targetRawIndex); // moves INTO sourceSlot

        // Can sourceItem enter the swap-target slot?
        SlotWhitelist wlTarget = filters.get(targetRawIndex);
        if (wlTarget != null && wlTarget.allowShift() && !sourceItem.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(sourceItem.getItem()).toString();
            if (!wlTarget.allowedItems().contains(id)) {
                ci.cancel();
                return;
            }
        }

        // Can targetItem enter the source slot (only relevant when it is a player-inventory slot)?
        if (sourceSlot.container instanceof Inventory) {
            SlotWhitelist wlSource = filters.get(sourceSlot.getContainerSlot());
            if (wlSource != null && wlSource.allowShift() && !targetItem.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(targetItem.getItem()).toString();
                if (!wlSource.allowedItems().contains(id)) {
                    // Only number-key swaps from a filtered MAIN slot get this fallback.
                    // Offhand/armor/hotbar swaps and swaps without free space keep their old behavior.
                    if (button >= 0 && button <= 8 && sourceSlot.getContainerSlot() >= 9
                            && sourceSlot.getContainerSlot() < Inventory.INVENTORY_SIZE
                            && !sourceItem.isEmpty()
                            && chestseparators$relocateDisplacedHotbarStack(
                                    player, sourceSlot, button, sourceItem, targetItem, filters)) {
                        ci.cancel(); // Our relocation and hotbar move already completed the click.
                        return;
                    }
                    ci.cancel();
                }
            }
        }
    }

    /**
     * A normal SWAP would put {@code displaced} into {@code sourceSlot}, violating its filter.
     * Instead, move the *entire* displaced stack into an empty, eligible MAIN inventory slot and
     * move the selected item into the hotbar. Called on BOTH sides to match client prediction.
     * Never consume the item from the cursor or displace items from a third occupied slot.
     */
    @Unique
    private boolean chestseparators$relocateDisplacedHotbarStack(
            Player player, Slot sourceSlot, int hotbarRawIndex,
            ItemStack selected, ItemStack displaced, Map<Integer, SlotWhitelist> filters) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (!menu.getCarried().isEmpty() || !sourceSlot.mayPickup(player)) return false;

        // Prefer an empty slot that is dedicated to the displaced item. If none exists,
        // use an unfiltered empty MAIN slot. Never pollute a slot reserved for another item.
        String displacedId = BuiltInRegistries.ITEM.getKey(displaced.getItem()).toString();
        int sourceRawIndex = sourceSlot.getContainerSlot();
        Slot destination = null;
        int bestPreference = Integer.MAX_VALUE;
        for (Slot candidate : this.slots) {
            if (!(candidate.container instanceof Inventory)) continue;
            int raw = candidate.getContainerSlot();
            if (raw < 9 || raw >= Inventory.INVENTORY_SIZE || raw == sourceRawIndex
                    || !candidate.getItem().isEmpty() || !candidate.isActive()
                    || !candidate.mayPlace(displaced)
                    || candidate.getMaxStackSize(displaced) < displaced.getCount()) continue;

            SlotWhitelist candidateFilter = filters.get(raw);
            if (candidateFilter == null) {
                if (destination == null) destination = candidate;
                continue;
            }
            if (!candidateFilter.allowedItems().contains(displacedId)) continue;
            int preference = FilterPriority.slotPreference(filters, raw, displacedId);
            if (bestPreference == Integer.MAX_VALUE || preference < bestPreference) {
                bestPreference = preference;
                destination = candidate;
            }
        }
        if (destination == null) return false;

        // All validation precedes mutation: without a valid empty slot the original protected
        // swap is simply cancelled and nothing moves. Keep this symmetric on client and server.
        sourceSlot.set(ItemStack.EMPTY);
        player.getInventory().setItem(hotbarRawIndex, selected.copy());
        destination.set(displaced.copy());
        sourceSlot.setChanged();
        destination.setChanged();
        player.getInventory().setChanged();
        if (!player.level().isClientSide()) menu.broadcastChanges();
        return true;
    }
}
