package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.input.CharacterEvent;
import io.github.marcsanzdev.chestseparators.client.input.KeyEvent;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.ChatAllowedCharacters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the mod's editor is active over the creative inventory, the vanilla creative tab bar must not
 * react — clicking a tab would switch it out from under the editor. This blocks the single choke point
 * {@code setCurrentCreativeTab} (except the mod's own jump-to-inventory-tab, flagged via
 * {@link ChestSeparatorsEditor#allowCreativeTabSwitch}). The tab hover/tooltip are suppressed the same way.
 *
 * <p>1.12.2 (E1) deltas from the modern port: {@code CreativeModeInventoryScreen}→{@code GuiContainerCreative};
 * {@code selectTab}→{@code setCurrentCreativeTab}; {@code checkTabClicked}→{@code isMouseOverTab};
 * {@code checkTabHovering}→{@code renderCreativeInventoryHoveringText}. There is no split keyPressed/charTyped:
 * the creative search's own {@code keyTyped(char, int)} is intercepted and routed to the editor first.
 */
@Mixin(GuiContainerCreative.class)
public abstract class CreativeInventoryScreenMixin {

    private static boolean chestseparators$blocking() {
        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
        return editor != null && (editor.isEditMode() || editor.getSession().isPresetsMenuOpen);
    }

    @Inject(method = "setCurrentCreativeTab", at = @At("HEAD"), cancellable = true)
    private void chestseparators$blockTabSwitchWhileEditing(CreativeTabs group, CallbackInfo ci) {
        if (!ChestSeparatorsEditor.allowCreativeTabSwitch && chestseparators$blocking()) {
            ci.cancel();
        }
    }

    // A tab reads as "hovered" (it lifts) when the cursor is over it, which makes it look clickable even
    // though the switch is blocked. Reporting no tab as hovered while editing keeps the tab bar flat and inert.
    @Inject(method = "isMouseOverTab", at = @At("HEAD"), cancellable = true)
    private void chestseparators$noTabHoverWhileEditing(
            CreativeTabs group, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (chestseparators$blocking()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "renderCreativeInventoryHoveringText", at = @At("HEAD"), cancellable = true)
    private void chestseparators$blockTabTooltipsWhileEditing(
            CreativeTabs group, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (chestseparators$blocking()) {
            cir.setReturnValue(false);
        }
    }

    // GuiContainerCreative overrides keyTyped for its own search box, so the GuiContainer mixin that forwards
    // typing to the editor never runs here. Route input to the editor first; it only consumes keys it actually
    // needs (rename field, filter search, shortcuts), so normal creative typing (the item search) falls through.
    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void chestseparators$forwardKeyToEditor(char typedChar, int keyCode, CallbackInfo ci) {
        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
        if (editor == null) return;
        if (editor.keyPressed(new KeyEvent(keyCode, 0, 0))) {
            ci.cancel();
            return;
        }
        if (typedChar != 0
                && ChatAllowedCharacters.isAllowedCharacter(typedChar)
                && editor.charTyped(new CharacterEvent(typedChar, 0))) {
            ci.cancel();
        }
    }
}
