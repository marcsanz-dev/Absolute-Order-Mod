package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
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
                || (Object) this instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
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

            // Creative's drawForeground sets a pointer cursor over its tabs/scrollbar, making them look
            // clickable even while the editor has them blocked. This overlay renders last, so forcing the
            // default cursor here wins the frame and keeps the tab bar looking inert while editing.
            if (this.editor.isEditMode() || this.editor.getSession().isPresetsMenuOpen) {
                context.setCursor(net.minecraft.client.gui.cursor.Cursor.DEFAULT);
            }
        }
    }

    // While a mod sub-screen (layout / filters / presets) is open on a recipe-book screen, keep the book
    // closed AND recenter the container. Opening the book shifts the whole GUI sideways; if we only closed
    // the book the container would stay shifted and everything the mod draws (panels, slot decorations)
    // would sit out of place. Doing it at renderMain HEAD — before the GUI is drawn — makes the container
    // render centered this very frame, as if the book had never been open.
    // Remembers that we force-closed a recipe book the player had open, so it can be restored when the mod
    // sub-screen is dismissed and the normal inventory returns.
    @Unique
    private boolean chestseparators$bookWasOpen = false;

    // The horizontal shift we applied to recenter the container while editing. Kept so it can be undone
    // exactly on exit, returning the container (and its GUI-anchored widgets) to the book-open position.
    @Unique
    private int chestseparators$appliedShift = 0;

    @Inject(method = "renderMain", at = @At("HEAD"))
    public void normalizeRecipeBookWhileEditing(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.editor == null) return;
        if (!((Object) this instanceof net.minecraft.client.gui.screen.ingame.RecipeBookScreen<?> rbs)) return;

        net.minecraft.client.gui.screen.recipebook.RecipeBookWidget<?> book =
                ((RecipeBookScreenAccessor) rbs).chestseparators$getRecipeBook();
        if (book == null) return;

        boolean editing = this.editor.isEditMode() || this.editor.getSession().isPresetsMenuOpen;
        if (!editing) {
            // Back in the normal inventory: reopen the book the player had left open, and UNDO our recentering
            // shift so the container, its widgets and the effect panel all slide back to the book-open layout.
            if (chestseparators$bookWasOpen && !book.isOpen()) {
                ((RecipeBookWidgetInvoker) book).chestseparators$setOpen(true);
            }
            if (chestseparators$appliedShift != 0) {
                chestseparators$shiftGui((HandledScreenAccessor) this, -chestseparators$appliedShift);
                chestseparators$appliedShift = 0;
            }
            chestseparators$bookWasOpen = false;
            return;
        }

        // A mod sub-screen is open: force the book closed (remembering it was open) so it stops covering the
        // editor, then recenter the container as if the book were closed.
        if (book.isOpen()) {
            chestseparators$bookWasOpen = true;
            ((RecipeBookWidgetInvoker) book).chestseparators$setOpen(false);
        }

        HandledScreenAccessor acc = (HandledScreenAccessor) this;
        int centeredX = (this.width - acc.getBackgroundWidth()) / 2;
        int dx = centeredX - acc.getX();
        if (dx != 0) {
            chestseparators$shiftGui(acc, dx);
            chestseparators$appliedShift += dx;
        }
    }

    // Slides the container's x and every GUI-anchored vanilla widget (the recipe book toggle button) by dx.
    // Those widgets are positioned once at init and do not follow a live x change on their own, so shifting
    // them by the same delta keeps the whole GUI moving as one piece.
    @Unique
    private void chestseparators$shiftGui(HandledScreenAccessor acc, int dx) {
        acc.setX(acc.getX() + dx);
        for (net.minecraft.client.gui.Element el : this.children()) {
            if (el instanceof net.minecraft.client.gui.widget.ClickableWidget widget) {
                widget.setX(widget.getX() + dx);
            }
        }
    }

    // Renders the previously saved separator lines underneath the item slots, plus the player's own
    // inventory decorations on the player slots (visible in every screen, including chests).
    @Inject(method = "drawSlots", at = @At("HEAD"))
    public void renderSavedLinesLayer(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.editor != null) {
            this.editor.renderSavedLinesLayer(context);
        }
    }

    // Suppresses vanilla item tooltips while the editor mode is active
    // to prevent visual clutter while drawing lines.
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void onDrawMouseoverTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
        if (this.editor != null && (this.editor.isEditMode() || this.editor.getSession().isPresetsMenuOpen)) {
            ci.cancel();
        }
    }

    // Suppresses the vanilla background slot highlight when editing.
    @Inject(method = "drawSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void onDrawSlotHighlightBack(DrawContext context, CallbackInfo ci) {
        if (this.editor != null && (this.editor.isEditMode() || this.editor.getSession().isPresetsMenuOpen)) {
            ci.cancel();
        }
    }

    // Suppresses the vanilla foreground slot highlight when editing.
    @Inject(method = "drawSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void onDrawSlotHighlightFront(DrawContext context, CallbackInfo ci) {
        if (this.editor != null && (this.editor.isEditMode() || this.editor.getSession().isPresetsMenuOpen)) {
            ci.cancel();
        }
    }

    // Skips the vanilla item render for slots taking part in the push/pull preview, so the editor can
    // draw a clean ghost with no real item (or its count) showing through underneath.
    @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
    private void onDrawSlot(
            DrawContext context, net.minecraft.screen.slot.Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.editor != null && this.editor.isPreviewSlot(slot)) {
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

            // Push/Pull hotkeys act on the open container and only fire when no editor sub-menu is active.
            // Holding Shift runs the "+ empty slots" variant of each — push also into empty slots, pull also
            // unfiltered items — matching the Shift behaviour of the deposit and fill buttons.
            if (!this.editor.isEditMode() && GlobalChestConfig.instance.showDepositButton) {
                int currentKey = input.key();
                if (currentKey != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
                    long window = net.minecraft.client.MinecraftClient.getInstance()
                            .getWindow()
                            .getHandle();
                    boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                                    == org.lwjgl.glfw.GLFW.GLFW_PRESS
                            || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT)
                                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;

                    if (currentKey == boundKeyCode(ModKeyBindings.pushKey)) {
                        this.editor.depositClickTime = System.currentTimeMillis();
                        this.editor.executeDeposit(shift);
                        this.editor.playClickSound(1.2f);
                        cir.setReturnValue(true);
                        return;
                    }
                    if (currentKey == boundKeyCode(ModKeyBindings.pullKey)) {
                        this.editor.fillClickTime = System.currentTimeMillis();
                        // requestFillFromOpenChest already plays the click sound; don't play a second one.
                        this.editor.requestFillFromOpenChest(shift);
                        cir.setReturnValue(true);
                        return;
                    }
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

        // NOTE: the ChestPosStorage handoff is now cleared on read in ChestSeparatorsEditor#init
        // (consume-on-read), NOT here. Clearing it on removed() raced with the next container being
        // opened: interactBlock(B) set the position, then this removed() nulled it before B's init read
        // it, so the second chest opened in a session saved to nowhere. See the editor init for details.
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
