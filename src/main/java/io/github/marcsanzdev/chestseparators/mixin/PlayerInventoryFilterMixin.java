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

    @Inject(method = "getEmptySlot", at = @At("HEAD"), cancellable = true)
    private void chestseparators$reserveFilteredSlots(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = chestseparators$insertingStack;
        if (stack == null || stack.isEmpty()) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsMain.INVENTORY_FILTERS.get(player.getUuid());
        if (filters == null || filters.isEmpty()) return;

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        DefaultedList<ItemStack> main = getMainStacks();

        // Prefer an empty slot whose filter matches this item, so picked-up items land in their slot.
        for (int i = 0; i < main.size(); i++) {
            if (!main.get(i).isEmpty()) continue;
            SlotWhitelist wl = filters.get(i);
            if (wl != null && wl.allowHopper() && wl.allowedItems().contains(itemId)) {
                cir.setReturnValue(i);
                return;
            }
        }
        // Otherwise the first empty slot not reserved by a non-matching filter.
        for (int i = 0; i < main.size(); i++) {
            if (!main.get(i).isEmpty()) continue;
            SlotWhitelist wl = filters.get(i);
            boolean reservedForOther =
                    wl != null && wl.allowHopper() && !wl.allowedItems().contains(itemId);
            if (!reservedForOther) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }
}
