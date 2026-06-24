package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsMain;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import java.util.Map;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives filter slots first-priority during {@link ScreenHandler#insertItem} on shift-click.
 *
 * <p>Vanilla sweeps slots left-to-right, so an item always goes to the earliest available slot
 * regardless of whether a later slot has a dedicated filter for it. This mixin adds two pre-passes
 * that run before vanilla's own passes:
 *
 * <ol>
 *   <li>Stack onto existing stacks in matching filter slots.
 *   <li>Fill empty matching filter slots.
 * </ol>
 *
 * <p>If either pre-pass fully consumes the item the return value is forced to {@code true};
 * otherwise vanilla's passes handle any remainder using the normal non-priority order.
 * The priority only applies during shift-click (guarded by {@link ClickTracker#IS_SHIFT_CLICK}).
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerInsertPriorityMixin {

    @Shadow
    @Final
    public DefaultedList<Slot> slots;

    // Tracks whether our pre-passes moved any items this call so the RETURN inject can fix the result.
    @Unique
    private static final ThreadLocal<Boolean> chestseparators$prePassInserted = ThreadLocal.withInitial(() -> false);

    @Inject(method = "insertItem", at = @At("HEAD"))
    private void chestseparators$filterPriorityPrePass(
            ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        chestseparators$prePassInserted.set(false);
        // Only apply during shift-click; other contexts (hoppers, direct pickup) use their own rules.
        if (!ClickTracker.IS_SHIFT_CLICK.get() || stack.isEmpty()) return;

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();

        // Pre-pass 1: stack onto existing stacks in slots with a matching filter.
        if (stack.isStackable()) {
            int i = fromLast ? endIndex - 1 : startIndex;
            while (!stack.isEmpty()) {
                if (fromLast ? i < startIndex : i >= endIndex) break;
                Slot slot = slots.get(i);
                if (chestseparators$isFilterMatch(slot, itemId)) {
                    ItemStack existing = slot.getStack();
                    if (!existing.isEmpty()
                            && ItemStack.areItemsAndComponentsEqual(stack, existing)
                            && slot.canInsert(stack)) {
                        int available = slot.getMaxItemCount(stack) - existing.getCount();
                        if (available > 0) {
                            int transfer = Math.min(available, stack.getCount());
                            existing.increment(transfer);
                            stack.decrement(transfer);
                            slot.markDirty();
                            chestseparators$prePassInserted.set(true);
                        }
                    }
                }
                if (fromLast) i--;
                else i++;
            }
        }

        // Pre-pass 2: fill empty slots with a matching filter.
        if (!stack.isEmpty()) {
            int i = fromLast ? endIndex - 1 : startIndex;
            while (!stack.isEmpty()) {
                if (fromLast ? i < startIndex : i >= endIndex) break;
                Slot slot = slots.get(i);
                if (chestseparators$isFilterMatch(slot, itemId)
                        && slot.getStack().isEmpty()
                        && slot.canInsert(stack)) {
                    int count = Math.min(stack.getCount(), slot.getMaxItemCount(stack));
                    if (count > 0) {
                        slot.setStack(stack.split(count));
                        chestseparators$prePassInserted.set(true);
                    }
                }
                if (fromLast) i--;
                else i++;
            }
        }
    }

    @Inject(method = "insertItem", at = @At("RETURN"))
    private void chestseparators$filterPriorityPostReturn(
            ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        // If our pre-pass moved items but vanilla's passes found nothing left to do (returning false),
        // override to true so callers know an insertion did occur.
        if (chestseparators$prePassInserted.get() && !cir.getReturnValueZ()) {
            cir.setReturnValue(true);
        }
        chestseparators$prePassInserted.set(false);
    }

    // True when this slot has a filter that explicitly lists the given item.
    @Unique
    private boolean chestseparators$isFilterMatch(Slot slot, String itemId) {
        if (slot.inventory instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> wl = provider.getWhitelists();
            if (wl != null) {
                SlotWhitelist filter = wl.get(slot.getIndex());
                return filter != null && filter.allowedItems().contains(itemId);
            }
        }
        if (slot.inventory instanceof PlayerInventory pinv) {
            Map<Integer, SlotWhitelist> wl = ChestSeparatorsMain.INVENTORY_FILTERS.get(pinv.player.getUuid());
            if (wl != null) {
                SlotWhitelist filter = wl.get(slot.getIndex());
                return filter != null && filter.allowedItems().contains(itemId);
            }
        }
        return false;
    }
}
