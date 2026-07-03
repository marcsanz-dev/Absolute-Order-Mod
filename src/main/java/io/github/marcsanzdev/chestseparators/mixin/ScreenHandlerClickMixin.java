package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.util.ClickTracker;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts item insertion operations within the screen handler to track shift-click states.
 */
@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerClickMixin {

    @Inject(method = "insertItem", at = @At("HEAD"))
    protected void onInsertItemBegin(ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        ClickTracker.setShiftClick(true);
    }

    @Inject(method = "insertItem", at = @At("RETURN"))
    protected void onInsertItemEnd(ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir) {
        ClickTracker.clear();
    }
}