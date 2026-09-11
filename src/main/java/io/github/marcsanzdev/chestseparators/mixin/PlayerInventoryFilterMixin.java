package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsMain;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces the player-inventory "Pick Up" rule: a filtered inventory slot only accepts its item when
 * items are auto-added (e.g. picked up from the ground). Non-matching items skip reserved slots, and
 * matching items prefer their reserved slot. Filters are synced from the client into
 * {@link ChestSeparatorsMain#INVENTORY_FILTERS}; with none present this is a no-op (vanilla behaviour).
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryFilterMixin {

    @Shadow
    @Final
    public PlayerEntity player;

    @Shadow
    public abstract DefaultedList<ItemStack> getMainStacks();

    // The stack currently being auto-inserted, so the empty-slot search can reserve filtered slots.
    // Set/cleared around insertStack; the server tick is single-threaded so a static field is safe.
    @Unique
    private static ItemStack chestseparators$insertingStack = null;

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"))
    private void chestseparators$beginInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        chestseparators$insertingStack = stack;
    }

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("RETURN"))
    private void chestseparators$endInsert(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        chestseparators$insertingStack = null;
    }

    // Also intercept offerOrDrop: used when closing a screen with a cursor item (E key close).
    @Inject(method = "offerOrDrop", at = @At("HEAD"))
    private void chestseparators$beginOfferOrDrop(ItemStack stack, CallbackInfo ci) {
        chestseparators$insertingStack = stack;
    }

    @Inject(method = "offerOrDrop", at = @At("RETURN"))
    private void chestseparators$endOfferOrDrop(ItemStack stack, CallbackInfo ci) {
        chestseparators$insertingStack = null;
    }

    @Inject(method = "getEmptySlot", at = @At("HEAD"), cancellable = true)
    private void chestseparators$reserveFilteredSlots(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = chestseparators$insertingStack;
        if (stack == null || stack.isEmpty()) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsMain.INVENTORY_FILTERS.get(player.getUuid());
        if (filters == null || filters.isEmpty()) return;

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        DefaultedList<ItemStack> main = getMainStacks();

        // Prefer an empty slot whose filter matches this item, so picked-up items land in their slot.
        // A slot is "reserved" (or "preferred") when any of its active filter rules are on,
        // which covers both the Pick-Up rule and the Manual rule (cursor return on screen close).
        // Among the matching empty slots, pick the one the filter's own order ranks best (the item first
        // in the list heads for the group's first slot, and so on); ties keep the lowest index. This is
        // the same priority the shift, hopper and deposit paths use, so every route fills in one order.
        int bestSlot = -1;
        int bestPreference = Integer.MAX_VALUE;
        for (int i = 0; i < main.size(); i++) {
            if (!main.get(i).isEmpty()) continue;
            SlotWhitelist wl = filters.get(i);
            if (wl == null || !isSlotActive(wl) || !wl.allowedItems().contains(itemId)) continue;
            int preference =
                    io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(filters, i, itemId);
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
            boolean reservedForOther =
                    wl != null && isSlotActive(wl) && !wl.allowedItems().contains(itemId);
            if (!reservedForOther) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    // True when at least one insertion rule is enabled on this filter, meaning the slot is "owned"
    // by its whitelist — it should attract matching items and repel non-matching ones during any
    // automatic placement (pickup from ground, cursor return on screen close, etc.).
    @Unique
    private static boolean isSlotActive(SlotWhitelist wl) {
        return wl.allowHopper() || wl.allowManual() || wl.allowShift();
    }
}
