package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import io.github.marcsanzdev.chestseparators.client.input.CharacterEvent;
import io.github.marcsanzdev.chestseparators.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Injects custom rendering and input handling logic into the base AbstractContainerScreen class.
// This allows the mod to overlay the editor UI onto existing vanilla container screens.
@Mixin(AbstractContainerScreen.class)
public abstract class GenericContainerScreenMixin extends Screen {

    @Unique
    private ChestSeparatorsEditor editor;

    protected GenericContainerScreenMixin(Component title) {
        super(title);
    }

    // Intercepts the initialization phase of the screen to instantiate our custom editor.
    @Inject(method = "init", at = @At("TAIL"))
    protected void init(CallbackInfo ci) {
        // Validates that the current screen is a supported container type.
        // ContainerScreen handles standard Chests, Barrels, Chest Minecarts, and Chest Boats.
        // ShulkerBoxScreen is explicitly allowed for Shulker Box support.
        // HorseInventoryScreen is allowed for donkeys, mules, llamas, and alpacas (entities with cargo slots).
        boolean isValidScreen = (Object) this instanceof ContainerScreen
                || (Object) this instanceof ShulkerBoxScreen
                || (Object) this instanceof InventoryScreen
                || (Object) this instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
                || isHorseScreenWithCargo();

        if (!isValidScreen) return;

        this.editor = new ChestSeparatorsEditor((AbstractContainerScreen<?>) (Object) this);
        this.editor.init();
    }

    // Returns true only for horse-type screens that have cargo slots (donkeys, mules, llamas, alpacas).
    // Regular horses only have saddle + armor slots (≤ 2 non-player slots), so they are excluded.
    @Unique
    private boolean isHorseScreenWithCargo() {
        if (!((Object) this instanceof HorseInventoryScreen)) return false;
        AbstractContainerMenu handler = ((HandledScreenAccessor) this).getHandler();
        // 36 = player inventory slots. More than 2 container slots means there is cargo.
        return handler.slots.size() - 36 > 2;
    }

    // Injects the editor's custom rendering logic or normal mode overlays to be drawn on top of the
    // vanilla GUI. Hooked at renderContents (not render) because InventoryScreen -> recipe-book screens call
    // renderContents directly and never AbstractContainerScreen#render; renderContents is the common path for
    // both chests and the inventory, and runs inside the render flow so deferred tooltips still flush.
    @Inject(method = "render", at = @At("TAIL"))
    public void renderEditorOverlay(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.editor != null) {
            // 1.20.1 draws the GUI in batches flushed in a FIXED render-type order (fills, then items, then
            // TEXT last) AND depth-tests each draw, so vanilla item-count numbers / labels punch through
            // anything the editor draws after them. Commit all vanilla content, then CLEAR the depth buffer so
            // the editor overlay composes flat on top by draw order (emulating E5's deferred compositor),
            // instead of being depth-occluded by the already-drawn item counts.
            io.github.marcsanzdev.chestseparators.client.ui.EditorRenderer.flushAndClearDepth(context);
            this.editor.render(context, mouseX, mouseY, delta);
            this.editor.renderNormalModeOverlay(context, mouseX, mouseY);
            // Commit the overlay itself before vanilla draws anything further (e.g. the carried/cursor item).
            context.flush();
            // NOTE: E5's context.requestCursor(CursorType.DEFAULT) — forcing the arrow cursor over the
            // creative tabs/scrollbar while editing — has no 1.20.1 equivalent (no cursor API), so it is dropped.
        }
    }

    // While a mod sub-screen (layout / filters / presets) is open on a recipe-book screen, keep the book
    // closed AND recenter the container. Opening the book shifts the whole GUI sideways; if we only closed
    // the book the container would stay shifted and everything the mod draws (panels, slot decorations)
    // would sit out of place. Doing it at renderContents HEAD — before the GUI is drawn — makes the container
    // render centered this very frame, as if the book had never been open.
    // Remembers that we force-closed a recipe book the player had open, so it can be restored when the mod
    // sub-screen is dismissed and the normal inventory returns.
    @Unique
    private boolean chestseparators$bookWasOpen = false;

    // The horizontal shift we applied to recenter the container while editing. Kept so it can be undone
    // exactly on exit, returning the container (and its GUI-anchored widgets) to the book-open position.
    @Unique
    private int chestseparators$appliedShift = 0;

    @Inject(method = "render", at = @At("HEAD"))
    public void normalizeRecipeBookWhileEditing(
            GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.editor == null) return;
        // 1.20.1 has no common AbstractRecipeBookScreen; the recipe-book-bearing screens implement
        // RecipeUpdateListener, which exposes the component directly (no accessor mixin needed).
        if (!((Object) this instanceof RecipeUpdateListener rul)) return;

        net.minecraft.client.gui.screens.recipebook.RecipeBookComponent book = rul.getRecipeBookComponent();
        if (book == null) return;

        boolean editing = this.editor.isEditMode() || this.editor.getSession().isPresetsMenuOpen;
        if (!editing) {
            // Back in the normal inventory: reopen the book the player had left open, and UNDO our recentering
            // shift so the container, its widgets and the effect panel all slide back to the book-open layout.
            if (chestseparators$bookWasOpen && !book.isVisible()) {
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
        if (book.isVisible()) {
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
        for (net.minecraft.client.gui.components.events.GuiEventListener el : this.children()) {
            if (el instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.setX(widget.getX() + dx);
            }
        }
    }

    // Renders the previously saved separator lines underneath the item slots, plus the player's own
    // inventory decorations on the player slots (visible in every screen, including chests). 1.20.1 has no
    // renderSlots pass, so this injects right after the background is drawn (before the slot/item loop).
    @Inject(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
                            shift = At.Shift.AFTER))
    public void renderSavedLinesLayer(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.editor != null) {
            // renderSavedLinesLayer draws at raw slot coords (s.x/s.y are relative to the container origin).
            // 1.20.1 renderBg runs with the pose at the screen origin (unlike E5's renderSlots, which ran
            // already translated), so translate to (leftPos, topPos) here or the layer lands in the top-left.
            HandledScreenAccessor acc = (HandledScreenAccessor) this;
            context.pose().pushPose();
            context.pose().translate(acc.getX(), acc.getY(), 0.0);
            this.editor.renderSavedLinesLayer(context);
            context.pose().popPose();
        }
    }

    // Suppresses vanilla item tooltips while the editor mode is active
    // to prevent visual clutter while drawing lines.
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void onDrawMouseoverTooltip(GuiGraphics context, int x, int y, CallbackInfo ci) {
        if (this.editor != null && (this.editor.isEditMode() || this.editor.getSession().isPresetsMenuOpen)) {
            ci.cancel();
        }
    }

    // Suppresses the vanilla hovered-slot highlight while editing. 1.20.1 draws it via the single static
    // AbstractContainerScreen#renderSlotHighlight (no separate back/front passes); redirect that call so the
    // instance can gate it on editor state (a HEAD inject can't, since the method is static).
    // require = 0: Forge patches AbstractContainerScreen#render and the vanilla static renderSlotHighlight
    // INVOKE is not present there, so the redirect finds 0 targets on Forge (it applies normally on Fabric).
    // Kept lenient so the shared mixin boots on both loaders; the only Forge-side effect is that the vanilla
    // hovered-slot highlight is not suppressed while editing (cosmetic).
    @Redirect(
            method = "render",
            require = 0,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;III)V"))
    private void chestseparators$suppressSlotHighlight(GuiGraphics context, int x, int y, int blitOffset) {
        if (this.editor == null || !(this.editor.isEditMode() || this.editor.getSession().isPresetsMenuOpen)) {
            AbstractContainerScreen.renderSlotHighlight(context, x, y, blitOffset);
        }
    }

    // Skips the vanilla item render for slots taking part in the push/pull preview, so the editor can
    // draw a clean ghost with no real item (or its count) showing through underneath. 1.20.1 renderSlot has
    // no mouseX/mouseY parameters.
    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void onDrawSlot(GuiGraphics context, net.minecraft.world.inventory.Slot slot, CallbackInfo ci) {
        if (this.editor != null && this.editor.isPreviewSlot(slot)) {
            ci.cancel();
        }
    }

    // ---- Mouse routing (replaces the old Fabric ScreenMouseEvents registration) ----
    // Each editor handler returns the Fabric-style "allow" boolean: false means the editor consumed the
    // event, so we cancel the vanilla handler by returning true (handled).

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.editor != null && !this.editor.inputHandler.handleClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.editor != null && !this.editor.inputHandler.onMouseReleased(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(
            double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (this.editor != null && !this.editor.inputHandler.onMouseDragged(mouseX, mouseY, deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }

    // Forwards keyboard input to the editor seamlessly and intercepts Deposit Hotkeys.
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        KeyEvent input = new KeyEvent(keyCode, scanCode, modifiers);
        if (this.editor != null) {

            if (this.editor.keyPressed(input)) {
                cir.setReturnValue(true);
                return;
            }

            // The magnifier toggle works in any editor state, since it is used while painting. Feedback
            // goes through the editor's status overlay so it shows above the chest GUI.
            if (ModKeyBindings.toggleMagnifierKey.matches(input.key(), input.scancode())) {
                this.editor.showStatus(
                        io.github.marcsanzdev.chestseparators.event.KeyInputHandler.toggleMagnifier(),
                        net.minecraft.ChatFormatting.GRAY);
                this.editor.playClickSound(1.0f);
                cir.setReturnValue(true);
                return;
            }

            // The same UI toggles available with the chest closed (deposit button, edit buttons,
            // preview panel) also work with the chest open, applied instantly. Only when no editor
            // sub-menu is active, so they don't clash with typing in search boxes.
            if (!this.editor.isEditMode()) {
                if (ModKeyBindings.openEditorKey.matches(input.key(), input.scancode())) {
                    this.editor.showStatus(
                            io.github.marcsanzdev.chestseparators.event.KeyInputHandler.togglePreviewPanel(),
                            net.minecraft.ChatFormatting.GRAY);
                    this.editor.playClickSound(1.0f);
                    cir.setReturnValue(true);
                    return;
                }
            }

            // Push/Pull hotkeys act on the open container and only fire when no editor sub-menu is active.
            // Holding Shift runs the "+ empty slots" variant of each — push also into empty slots, pull also
            // unfiltered items — matching the Shift behaviour of the deposit and fill buttons.
            if (!this.editor.isEditMode() && GlobalChestConfig.instance.showDepositButton) {
                long window = net.minecraft.client.Minecraft.getInstance()
                        .getWindow().getWindow();
                boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                                == org.lwjgl.glfw.GLFW.GLFW_PRESS
                        || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT)
                                == org.lwjgl.glfw.GLFW.GLFW_PRESS;

                if (ModKeyBindings.pushKey.matches(input.key(), input.scancode())) {
                    this.editor.depositClickTime = System.currentTimeMillis();
                    this.editor.executeDeposit(shift);
                    this.editor.playClickSound(1.2f);
                    cir.setReturnValue(true);
                    return;
                }
                if (ModKeyBindings.pullKey.matches(input.key(), input.scancode())) {
                    this.editor.fillClickTime = System.currentTimeMillis();
                    // requestFillFromOpenChest already plays the click sound; don't play a second one.
                    this.editor.requestFillFromOpenChest(shift);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    // Forwards text input (typing) to the editor's search box. Overrides the vanilla E4 primitive signature
    // and wraps it in the mod's compat CharacterEvent before handing it to the editor.
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.editor != null && this.editor.charTyped(new CharacterEvent(codePoint, modifiers))) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
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

    // 1.20.1 AbstractContainerScreen does NOT declare mouseScrolled (unlike mouseClicked/Released/Dragged/
    // keyPressed), so it can't be an @Inject target here — override it instead (mirrors charTyped). Single
    // scroll amount (no horizontal axis); pass 0 for horizontal.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.editor != null && !this.editor.inputHandler.onMouseScrolled(mouseX, mouseY, 0.0, amount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
}
