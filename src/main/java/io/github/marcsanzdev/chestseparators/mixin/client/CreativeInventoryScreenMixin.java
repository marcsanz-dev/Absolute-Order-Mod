package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the mod's editor is active over the creative inventory, the vanilla creative tab bar must not
 * react — clicking a tab would switch it out from under the editor. Rather than fight the several mouse
 * events the tab bar can respond to, this blocks the single choke point {@code setSelectedTab} (except
 * the mod's own jump-to-inventory-tab, flagged via {@link ChestSeparatorsEditor#allowCreativeTabSwitch}).
 * The tab tooltips are suppressed the same way.
 */
@Mixin(CreativeInventoryScreen.class)
public abstract class CreativeInventoryScreenMixin {

    private static boolean chestseparators$blocking() {
        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
        return editor != null && (editor.isEditMode() || editor.getSession().isPresetsMenuOpen);
    }

    @Inject(method = "setSelectedTab", at = @At("HEAD"), cancellable = true)
    private void chestseparators$blockTabSwitchWhileEditing(ItemGroup group, CallbackInfo ci) {
        if (!ChestSeparatorsEditor.allowCreativeTabSwitch && chestseparators$blocking()) {
            ci.cancel();
        }
    }

    // A tab reads as "hovered" (it lifts) when the cursor is over it, which makes it look clickable even
    // though the switch is blocked. Reporting no tab as hovered while editing keeps the tab bar flat and
    // inert, so nothing about it invites a click.
    @Inject(method = "isClickInTab", at = @At("HEAD"), cancellable = true)
    private void chestseparators$noTabHoverWhileEditing(
            ItemGroup group, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (chestseparators$blocking()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "renderTabTooltipIfHovered", at = @At("HEAD"), cancellable = true)
    private void chestseparators$blockTabTooltipsWhileEditing(
            DrawContext context, ItemGroup group, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (chestseparators$blocking()) {
            cir.setReturnValue(false);
        }
    }

    // CreativeInventoryScreen overrides keyPressed/charTyped for its own search box, so the HandledScreen
    // mixin that forwards typing to the editor never runs here. Without this, editing a filter's search or
    // renaming a preset from the creative inventory swallowed every keystroke. Route input to the editor
    // first; it only consumes keys it actually needs (rename field, filter search, shortcuts), so normal
    // creative typing (the item search) still falls through untouched.
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void chestseparators$forwardKeyToEditor(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
        if (editor != null && editor.keyPressed(input)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void chestseparators$forwardCharToEditor(CharInput input, CallbackInfoReturnable<Boolean> cir) {
        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
        if (editor != null && editor.charTyped(input)) {
            cir.setReturnValue(true);
        }
    }
}
