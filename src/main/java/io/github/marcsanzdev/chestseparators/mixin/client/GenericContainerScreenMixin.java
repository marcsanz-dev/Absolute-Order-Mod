package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HorseScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Injects custom rendering and input handling logic into the base HandledScreen class.
// This allows the mod to overlay the editor UI onto existing vanilla container screens.
@Mixin(HandledScreen.class)
public abstract class GenericContainerScreenMixin extends Screen {

    @Unique
    private ChestSeparatorsEditor editor;

    protected GenericContainerScreenMixin(Text title) {
        super(title);
    }

    // Intercepts the initialization phase of the screen to instantiate our custom editor.
    @Inject(method = "init", at = @At("TAIL"))
    protected void init(CallbackInfo ci) {
        // Validates that the current screen is a supported container type.
        // GenericContainerScreen handles standard Chests, Barrels, Chest Minecarts, and Chest Boats.
        // ShulkerBoxScreen is explicitly allowed for Shulker Box support.
        // HorseScreen is allowed for donkeys, mules, llamas, and alpacas (entities with cargo slots).
        boolean isValidScreen = (Object) this instanceof GenericContainerScreen
                || (Object) this instanceof ShulkerBoxScreen
                || (Object) this instanceof InventoryScreen
                || isHorseScreenWithCargo();

        if (!isValidScreen) return;

        this.editor = new ChestSeparatorsEditor((HandledScreen<?>) (Object) this);
        this.editor.init();
    }

    // Returns true only for horse-type screens that have cargo slots (donkeys, mules, llamas, alpacas).
    // Regular horses only have saddle + armor slots (≤ 2 non-player slots), so they are excluded.
    @Unique
    private boolean isHorseScreenWithCargo() {
        if (!((Object) this instanceof HorseScreen)) return false;
        ScreenHandler handler = ((HandledScreenAccessor) this).getHandler();
        // 36 = player inventory slots. More than 2 container slots means there is cargo.
        return handler.slots.size() - 36 > 2;
    }

    // Injects the editor's custom rendering logic or normal mode overlays to be drawn on top of the
    // vanilla GUI. Hooked at renderMain (not render) because InventoryScreen -> RecipeBookScreen calls
    // renderMain directly and never HandledScreen#render; renderMain is the common path for both chests
    // and the inventory, and runs inside the render flow so deferred tooltips still flush correctly.
    @Inject(method = "renderMain", at = @At("TAIL"))
    public void renderEditorOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.editor != null) {
            this.editor.render(context, mouseX, mouseY, delta);
            this.editor.renderNormalModeOverlay(context, mouseX, mouseY);
        }
    }

    // Renders the previously saved separator lines underneath the item slots, plus the player's own
    // inventory decorations on the player slots (visible in every screen, including chests).
    @Inject(method = "drawSlots", at = @At("HEAD"))
    public void renderSavedLinesLayer(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.editor != null) {
            this.editor.renderSavedLinesLayer(context);
            this.editor.renderInventoryDecorations(context);
        }
    }

    // Suppresses vanilla item tooltips while the editor mode is active
    // to prevent visual clutter while drawing lines.
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void onDrawMouseoverTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
        if (this.editor != null && this.editor.isEditMode()) {
            ci.cancel();
        }
    }

    // Suppresses the vanilla background slot highlight when editing.
    @Inject(method = "drawSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void onDrawSlotHighlightBack(DrawContext context, CallbackInfo ci) {
        if (this.editor != null && this.editor.isEditMode()) {
            ci.cancel();
        }
    }

    // Suppresses the vanilla foreground slot highlight when editing.
    @Inject(method = "drawSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void onDrawSlotHighlightFront(DrawContext context, CallbackInfo ci) {
        if (this.editor != null && this.editor.isEditMode()) {
            ci.cancel();
        }
    }

    @Unique
    private static int boundKeyCode(net.minecraft.client.option.KeyBinding binding) {
        return KeyBindingHelper.getBoundKeyOf(binding).getCode();
    }

    // Forwards keyboard input to the editor seamlessly and intercepts Deposit Hotkeys.
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (this.editor != null) {

            if (this.editor.keyPressed(input)) {
                cir.setReturnValue(true);
                return;
            }

            // The magnifier toggle works in any editor state, since it is used while painting. Feedback
            // goes through the editor's status overlay so it shows above the chest GUI.
            if (input.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN
                    && input.key() == boundKeyCode(ModKeyBindings.toggleMagnifierKey)) {
                this.editor.showStatus(
                        io.github.marcsanzdev.chestseparators.event.KeyInputHandler.toggleMagnifier(),
                        net.minecraft.util.Formatting.GRAY);
                this.editor.playClickSound(1.0f);
                cir.setReturnValue(true);
                return;
            }

            // The same UI toggles available with the chest closed (deposit button, edit buttons,
            // preview panel) also work with the chest open, applied instantly. Only when no editor
            // sub-menu is active, so they don't clash with typing in search boxes.
            if (!this.editor.isEditMode()) {
                int key = input.key();
                if (key != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
                    if (key == boundKeyCode(ModKeyBindings.toggleDepositButtonKey)) {
                        this.editor.showStatus(
                                io.github.marcsanzdev.chestseparators.event.KeyInputHandler.toggleDepositButton(),
                                net.minecraft.util.Formatting.GRAY);
                        this.editor.playClickSound(1.0f);
                        cir.setReturnValue(true);
                        return;
                    }
                    if (key == boundKeyCode(ModKeyBindings.toggleButtonKey)) {
                        this.editor.showStatus(
                                io.github.marcsanzdev.chestseparators.event.KeyInputHandler.toggleEditButtons(),
                                net.minecraft.util.Formatting.GRAY);
                        this.editor.playClickSound(1.0f);
                        cir.setReturnValue(true);
                        return;
                    }
                    if (key == boundKeyCode(ModKeyBindings.openEditorKey)) {
                        this.editor.showStatus(
                                io.github.marcsanzdev.chestseparators.event.KeyInputHandler.togglePreviewPanel(),
                                net.minecraft.util.Formatting.GRAY);
                        this.editor.playClickSound(1.0f);
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }

            // Deposit hotkeys only fire when no editor sub-menu is active.
            if (!this.editor.isEditMode() && GlobalChestConfig.instance.showDepositButton) {

                int currentKey = input.key();
                int depositFilterKey = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.depositFilterKey)
                        .getCode();
                int depositAllKey = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.depositAllKey)
                        .getCode();

                if (currentKey == depositFilterKey) {
                    this.editor.depositClickTime = System.currentTimeMillis();
                    this.editor.executeDeposit(false);
                    this.editor.playClickSound(1.2f);
                    cir.setReturnValue(true);
                    return;
                } else if (currentKey == depositAllKey) {
                    this.editor.depositClickTime = System.currentTimeMillis();
                    this.editor.executeDeposit(true);
                    this.editor.playClickSound(1.2f);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    // Forwards text input (typing) to the editor's search box (Using Override because HandledScreen doesn't have it).
    @Override
    public boolean charTyped(CharInput input) {
        if (this.editor != null && this.editor.charTyped(input)) {
            return true;
        }
        return super.charTyped(input);
    }

    // Ensures cleanup operations and server unlocks run when the GUI is destroyed for ANY reason
    // (Manual close, block broken by another player, server kick, etc).
    @Inject(method = "removed", at = @At("HEAD"))
    public void onRemoved(CallbackInfo ci) {
        if (this.editor != null) {
            this.editor.onClose();
        }

        // Clear cached context to prevent state leaking into the next container that is opened.
        ChestPosStorage.lastClickedPos = null;
        ChestPosStorage.lastClickedEntityUUID = null;
        ChestPosStorage.lastOpenedShulkerUUID = null;
        ChestPosStorage.isEntityOpened = false;
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> cir) {
        if (this.editor != null && this.editor.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }
}
