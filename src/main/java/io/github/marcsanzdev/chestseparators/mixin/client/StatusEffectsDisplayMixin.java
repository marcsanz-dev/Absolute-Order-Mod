package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.StatusEffectsDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While a mod sub-screen (layout / filters / presets) is open, the vanilla potion-effect panel drawn beside
// the inventory sits behind the editor's own panels and menus, making them unreadable. Suppress the effect
// panel entirely while editing; it comes back the moment the editor closes.
@Mixin(StatusEffectsDisplay.class)
public class StatusEffectsDisplayMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void chestseparators$hideWhileEditing(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
        if (editor != null && (editor.isEditMode() || editor.getSession().isPresetsMenuOpen)) {
            ci.cancel();
        }
    }
}
