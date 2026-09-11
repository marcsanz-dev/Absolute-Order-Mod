package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a mod sub-screen (layout / filters / presets) is open, the vanilla potion-effect panel drawn beside
 * the inventory sits behind the editor's own panels, making them unreadable. Suppress the effect panel while
 * editing; it returns the moment the editor closes.
 *
 * <p>1.12.2 (E1): the effect panel is drawn by {@code InventoryEffectRenderer#drawActivePotionEffects}
 * (the super of {@code GuiInventory}). {@code require = 0} keeps the build green even if the private method
 * name ever drifts.
 */
@Mixin(InventoryEffectRenderer.class)
public abstract class StatusEffectsDisplayMixin {

    @Inject(method = "drawActivePotionEffects", at = @At("HEAD"), cancellable = true, require = 0)
    private void chestseparators$hideWhileEditing(CallbackInfo ci) {
        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
        if (editor != null && (editor.isEditMode() || editor.getSession().isPresetsMenuOpen)) {
            ci.cancel();
        }
    }
}
