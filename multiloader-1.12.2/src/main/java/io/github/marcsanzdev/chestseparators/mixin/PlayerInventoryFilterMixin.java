package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.FilterPriority;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces the player-inventory "Pick Up" rule: a filtered inventory slot only accepts its item when
 * items are auto-added (e.g. picked up from the ground). Non-matching items skip reserved slots and
 * matching items prefer their reserved slot. Filters are synced from the client into
 * {@link ChestSeparatorsState#INVENTORY_FILTERS}; with none present this is a no-op.
 *
 * <p>1.12.2 deltas: {@code add(ItemStack)}→{@code addItemStackToInventory}; {@code getFreeSlot}→
 * {@code getFirstEmptyStack}; {@code items}→{@code mainInventory} (the 36 main slots); {@code placeItemBack
 * InInventory(Level,ItemStack)}→{@code placeItemBackInInventory(World,ItemStack)}; {@code Player#getUUID}→
 * {@code Entity#getUniqueID}.
 */
@Mixin(InventoryPlayer.class)
public abstract class PlayerInventoryFilterMixin {

    // The stack currently being auto-inserted, so the empty-slot search can reserve filtered slots.
    @Unique
    private static ItemStack chestseparators$insertingStack = null;

    @Inject(method = "addItemStackToInventory(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    private void chestseparators$beginInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        chestseparators$insertingStack = stack;
    }

    @Inject(method = "addItemStackToInventory(Lnet/minecraft/item/ItemStack;)Z", at = @At("RETURN"))
    private void chestseparators$endInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        chestseparators$insertingStack = null;
    }

    // Also intercept placeItemBackInInventory: used when closing a screen with a cursor item. 1.12.2 takes a
    // leading World param, so the descriptor and handler carry it.
    @Inject(
            method = "placeItemBackInInventory(Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"))
    private void chestseparators$beginOfferOrDrop(
            net.minecraft.world.World world, ItemStack stack, CallbackInfo ci) {
        chestseparators$insertingStack = stack;
    }

    @Inject(
            method = "placeItemBackInInventory(Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)V",
            at = @At("RETURN"))
    private void chestseparators$endOfferOrDrop(
            net.minecraft.world.World world, ItemStack stack, CallbackInfo ci) {
        chestseparators$insertingStack = null;
    }

    @Inject(method = "getFirstEmptyStack", at = @At("HEAD"), cancellable = true)
    private void chestseparators$reserveFilteredSlots(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = chestseparators$insertingStack;
        if (stack == null || stack.isEmpty()) return;

        EntityPlayer player = ((InventoryPlayerAccessor) (Object) this).chestseparators$getPlayer();
        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUniqueID());
        if (filters == null || filters.isEmpty()) return;

        String itemId = stack.getItem().getRegistryName().toString();
        NonNullList<ItemStack> main = ((InventoryPlayerAccessor) (Object) this).chestseparators$getMainInventory();

        // Prefer an empty slot whose filter matches this item, ranked by the filter's own order (same
        // priority the shift/hopper/deposit paths use); ties keep the lowest index.
        int bestSlot = -1;
        int bestPreference = Integer.MAX_VALUE;
        for (int i = 0; i < main.size(); i++) {
            if (!main.get(i).isEmpty()) continue;
            SlotWhitelist wl = filters.get(i);
            if (wl == null || !isSlotActive(wl) || !wl.allowedItems().contains(itemId)) continue;
            int preference = FilterPriority.slotPreference(filters, i, itemId);
            if (preference < bestPreference) {
                bestPreference = preference;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0) {
            cir.setReturnValue(bestSlot);
            return;
        }
        // Otherwise the first empty slot not reserved by a non-matching filter.
        for (int i = 0; i < main.size(); i++) {
            if (!main.get(i).isEmpty()) continue;
            SlotWhitelist wl = filters.get(i);
            boolean reservedForOther = wl != null && isSlotActive(wl) && !wl.allowedItems().contains(itemId);
            if (!reservedForOther) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    @Unique
    private static boolean isSlotActive(SlotWhitelist wl) {
        return wl.allowHopper() || wl.allowManual() || wl.allowShift();
    }
}
