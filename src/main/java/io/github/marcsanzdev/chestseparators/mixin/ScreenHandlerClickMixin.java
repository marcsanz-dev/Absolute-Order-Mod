package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sets the {@link ClickTracker#IS_SHIFT_CLICK} thread-local flag for the duration of
 * {@code insertItem}, which is the internal method vanilla calls during shift-click quick-move.
 *
 * <p>This flag allows {@link SlotWhitelistMixin} to distinguish shift-clicks from direct
 * cursor insertions, since both ultimately arrive at {@code Slot#canInsert}.
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerClickMixin {

    @Inject(method = "insertItem", at = @At("HEAD"))
    protected void onInsertItemBegin(
            ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        ClickTracker.IS_SHIFT_CLICK.set(true);
        // Publish the destination range so the filter-priority rule only defers to slots this
        // insertion can actually reach.
        ClickTracker.INSERT_RANGE.set(new int[] {startIndex, endIndex});
    }

    @Inject(method = "insertItem", at = @At("RETURN"))
    protected void onInsertItemEnd(
            ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        ClickTracker.IS_SHIFT_CLICK.set(false);
        ClickTracker.INSERT_RANGE.remove();
    }
}
