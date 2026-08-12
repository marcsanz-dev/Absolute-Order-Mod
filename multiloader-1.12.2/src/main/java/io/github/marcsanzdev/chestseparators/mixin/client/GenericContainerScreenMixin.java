package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.compat.GuiGraphics;
import io.github.marcsanzdev.chestseparators.client.input.CharacterEvent;
import io.github.marcsanzdev.chestseparators.client.input.KeyEvent;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.event.KeyInputHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.text.TextFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Boundary E1: constructs and drives the editor over ANY GuiContainer (chests, shulker, creative, inventory).
@Mixin(GuiContainer.class)
public abstract class GenericContainerScreenMixin extends GuiScreen {

    @Unique
    private ChestSeparatorsEditor chestseparators$editor;

    @Unique
    private int chestseparators$lastDragX;

    @Unique
    private int chestseparators$lastDragY;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void chestseparators$init(CallbackInfo ci) {
        this.chestseparators$editor = new ChestSeparatorsEditor((GuiContainer) (Object) this);
        this.chestseparators$editor.init();
    }

    @Inject(method = "onGuiClosed", at = @At("HEAD"))
    private void chestseparators$onClosed(CallbackInfo ci) {
        if (this.chestseparators$editor != null) this.chestseparators$editor.onClose();
    }

    // Overlay principal + normal-mode + tooltip diferido, encima de todo.
    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void chestseparators$renderOverlay(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.chestseparators$editor == null) return;
        // Reset to a clean 2D-GUI GL state before the mod draws, undoing what vanilla left after item/tooltip
        // rendering (the mod cancels renderHoveredToolTip, one place vanilla would restore it):
        //  - GL_LIGHTING on darkens the mod's flat drawRect fills (whole cristal reads too dark).
        //  - GL_ALPHA_TEST at 0.1 DISCARDS any fill/border below ~10% alpha, so the cristal's thin hairline
        //    borders, top highlights and disabled-button boxes (alpha 0x0A-0x14) vanish entirely.
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GuiGraphics ctx = new GuiGraphics((GuiScreen) (Object) this);
        this.chestseparators$editor.render(ctx, mouseX, mouseY, delta);
        this.chestseparators$editor.renderNormalModeOverlay(ctx, mouseX, mouseY);
        ctx.flush();
        ctx.flushTooltip();
        // Restore the state we (and the fill() primitive) turned off, so vanilla's alpha-tested/lit draws next
        // frame — the creative player model and the armour/offhand slot icons — are not left rendering black.
        GlStateManager.enableAlpha();
        GlStateManager.enableLighting();
    }

    // Capa de líneas guardadas: justo tras el fondo del contenedor (matriz en origen en 1.12.2),
    // trasladando a (guiLeft, guiTop) para alinear con los slots (que se dibujan después, encima).
    @Inject(
            method = "drawScreen",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerBackgroundLayer(FII)V",
                    shift = At.Shift.AFTER))
    private void chestseparators$renderSavedLines(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.chestseparators$editor == null) return;
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GuiGraphics ctx = new GuiGraphics();
        HandledScreenAccessor acc = (HandledScreenAccessor) this;
        ctx.pose().pushPose();
        ctx.pose().translate(acc.getX(), acc.getY(), 0.0);
        this.chestseparators$editor.renderSavedLinesLayer(ctx);
        ctx.pose().popPose();
        // This hook runs BEFORE the slots and the creative player model are drawn, so restore the alpha test
        // and lighting our fills disabled — otherwise those vanilla, alpha-tested draws render black.
        GlStateManager.enableAlpha();
        GlStateManager.enableLighting();
    }

    // Suprime los tooltips de item de vanilla mientras se edita.
    @Inject(method = "renderHoveredToolTip", at = @At("HEAD"), cancellable = true)
    private void chestseparators$suppressTooltip(int x, int y, CallbackInfo ci) {
        if (this.chestseparators$editor != null
                && (this.chestseparators$editor.isEditMode()
                        || this.chestseparators$editor.getSession().isPresetsMenuOpen)) {
            ci.cancel();
        }
    }

    // Oculta el item real de los slots que participan en el preview push/pull.
    @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
    private void chestseparators$onDrawSlot(Slot slot, CallbackInfo ci) {
        if (this.chestseparators$editor != null && this.chestseparators$editor.isPreviewSlot(slot)) ci.cancel();
    }

    // Suprime el resaltado del slot bajo el cursor mientras se edita (drawGradientRect 0x80FFFFFF).
    // require = 0: opcional; si el sitio no existe, no rompe el build.
    @Redirect(
            method = "drawScreen",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGradientRect(IIIIII)V"))
    private void chestseparators$suppressHighlight(
            GuiContainer self, int x1, int y1, int x2, int y2, int cFrom, int cTo) {
        if (this.chestseparators$editor != null
                && cFrom == 0x80FFFFFF
                && cTo == 0x80FFFFFF
                && (this.chestseparators$editor.isEditMode()
                        || this.chestseparators$editor.getSession().isPresetsMenuOpen)) {
            return;
        }
        this.drawGradientRect(x1, y1, x2, y2, cFrom, cTo);
    }

    // --- Mouse (coords int; handleClick/etc devuelven true=allow, false=consumido) ---
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void chestseparators$onMouseClicked(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (this.chestseparators$editor == null) return;
        this.chestseparators$lastDragX = mouseX;
        this.chestseparators$lastDragY = mouseY;
        if (!this.chestseparators$editor.inputHandler.handleClick(mouseX, mouseY, button)) ci.cancel();
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void chestseparators$onMouseReleased(int mouseX, int mouseY, int state, CallbackInfo ci) {
        if (this.chestseparators$editor != null
                && !this.chestseparators$editor.inputHandler.onMouseReleased(mouseX, mouseY, state)) ci.cancel();
    }

    @Inject(method = "mouseClickMove", at = @At("HEAD"), cancellable = true)
    private void chestseparators$onMouseDragged(int mouseX, int mouseY, int button, long time, CallbackInfo ci) {
        if (this.chestseparators$editor == null) return;
        double dx = mouseX - this.chestseparators$lastDragX;
        double dy = mouseY - this.chestseparators$lastDragY;
        this.chestseparators$lastDragX = mouseX;
        this.chestseparators$lastDragY = mouseY;
        if (!this.chestseparators$editor.inputHandler.onMouseDragged(mouseX, mouseY, dx, dy)) ci.cancel();
    }

    // Scroll: 1.12.2 no tiene mouseScrolled y GuiContainer NO declara handleMouseInput (lo hereda de
    // GuiScreen), así que no se puede inyectar aquí — el rueda se enruta desde GuiScreenScrollMixin.

    // keyTyped combina tecla + carácter (LWJGL2). keyPressed primero; luego hotkeys; luego charTyped.
    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void chestseparators$onKeyTyped(char typedChar, int keyCode, CallbackInfo ci) {
        if (this.chestseparators$editor == null) return;
        ChestSeparatorsEditor editor = this.chestseparators$editor;

        if (editor.keyPressed(new KeyEvent(keyCode, 0, 0))) {
            ci.cancel();
            return;
        }

        if (ModKeyBindings.matches(ModKeyBindings.toggleMagnifierKey, keyCode)) {
            editor.showStatus(KeyInputHandler.toggleMagnifier(), TextFormatting.GRAY);
            editor.playClickSound(1.0f);
            ci.cancel();
            return;
        }
        if (!editor.isEditMode() && ModKeyBindings.matches(ModKeyBindings.openEditorKey, keyCode)) {
            editor.showStatus(KeyInputHandler.togglePreviewPanel(), TextFormatting.GRAY);
            editor.playClickSound(1.0f);
            ci.cancel();
            return;
        }
        if (!editor.isEditMode() && GlobalChestConfig.instance.showDepositButton) {
            boolean shift = GuiScreen.isShiftKeyDown();
            if (ModKeyBindings.matches(ModKeyBindings.pushKey, keyCode)) {
                editor.depositClickTime = System.currentTimeMillis();
                editor.executeDeposit(shift);
                editor.playClickSound(1.2f);
                ci.cancel();
                return;
            }
            if (ModKeyBindings.matches(ModKeyBindings.pullKey, keyCode)) {
                editor.fillClickTime = System.currentTimeMillis();
                editor.requestFillFromOpenChest(shift);
                ci.cancel();
                return;
            }
        }

        if (typedChar != 0
                && ChatAllowedCharacters.isAllowedCharacter(typedChar)
                && editor.charTyped(new CharacterEvent(typedChar, 0))) {
            ci.cancel();
        }
    }
}
