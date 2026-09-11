package io.github.marcsanzdev.chestseparators.forge.mixin;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Forge-only counterpart to the common GenericContainerScreenMixin slot-highlight suppression.
 *
 * <p>Forge patches {@link AbstractContainerScreen#render} to call the 5-arg
 * {@code renderSlotHighlight(GuiGraphics, x, y, blitOffset, color)} overload (the colour comes from
 * {@code getSlotColor}); the common redirect targets the vanilla 4-arg overload, which Forge's render no longer
 * invokes, so under Forge the hovered-slot highlight is never suppressed. This 5-arg overload only exists on the
 * Forge classpath, so the redirect has to live in the Forge module. Gated on the active editor so the highlight
 * is hidden while editing (matching Fabric) — most visibly when the color picker / eyedropper panels fully cover
 * the slots. require = 1: the Forge version is pinned, so the 5-arg call is always present. (1.20.1 is render era
 * E4, so the highlight takes a GuiGraphics rather than the PoseStack used by the 1.18.2/1.19.2 modules.)
 */
@Mixin(AbstractContainerScreen.class)
public class ContainerScreenHighlightForgeMixin {

    @Redirect(
            method = "render",
            require = 1,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;IIII)V"))
    private void chestseparators$suppressSlotHighlight(GuiGraphics guiGraphics, int x, int y, int blitOffset, int color) {
        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
        if (editor == null || !(editor.isEditMode() || editor.getSession().isPresetsMenuOpen)) {
            AbstractContainerScreen.renderSlotHighlight(guiGraphics, x, y, blitOffset, color);
        }
    }
}
