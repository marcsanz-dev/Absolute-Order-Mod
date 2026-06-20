package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.event.KeyInputHandler;
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class EditorRenderer {

    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final HandledScreen<?> screen;
    private final HandledScreenAccessor accessor;

    private final EditorLayout layout;

    public EditorRenderer(
            ChestSeparatorsEditor editor,
            EditorSessionData session,
            EditorLayout layout,
            HandledScreen<?> screen,
            HandledScreenAccessor accessor) {
        this.editor = editor;
        this.session = session;
        this.layout = layout;
        this.screen = screen;
        this.accessor = accessor;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        layout.update(screen, accessor, editor.getSidebarYOffset());
        editor.syncClientInventoryWhitelists(ChestConfigManager.getInstance().getCurrentWhitelists());

        boolean showButton = GlobalChestConfig.instance.showEditButtons;
        boolean showDeposit = GlobalChestConfig.instance.showDepositButton;

        boolean hideInFilter = (session.currentState == EditorState.EDIT_FILTER);
        boolean hideVanilla = session.isColorPickerOpen || hideInFilter || session.hasSelectionConflict;
        boolean isEditorClosed = (session.currentState == EditorState.HIDDEN);

        // Reflect current editor state in each button's pressed/released visual.
        if (editor.entryButton != null) {
            editor.entryButton.isActive = (session.currentState == EditorState.DRAW_LINES);
        }
        if (editor.whitelistButton != null) {
            editor.whitelistButton.isActive = (session.currentState == EditorState.VIEW_GROUPS
                    || session.currentState == EditorState.SELECT_SLOTS
                    || session.currentState == EditorState.EDIT_FILTER);
        }
        if (editor.depositButton != null) {
            // Hold the pressed state for 150 ms after the click for visual feedback.
            editor.depositButton.isActive = (System.currentTimeMillis() - editor.depositClickTime < 150);
        }

        int bgMouseX = session.hasSelectionConflict ? -1 : mouseX;
        int bgMouseY = session.hasSelectionConflict ? -1 : mouseY;

        // Background layer (rendered behind the dim overlay).
        // When a sub-menu is active, pass mouse coordinates of (-1, -1) so the buttons
        // remain visible but do not react to hover visually.
        if (showButton) {
            int bMouseX = hideVanilla ? -1 : bgMouseX;
            int bMouseY = hideVanilla ? -1 : bgMouseY;
            if (editor.entryButton != null) editor.entryButton.render(context, bMouseX, bMouseY, delta);
            if (editor.whitelistButton != null) editor.whitelistButton.render(context, bMouseX, bMouseY, delta);
        }

        // Deposit button is only shown in the default (closed) editor state.
        if (showDeposit && isEditorClosed) {
            if (editor.depositButton != null) editor.depositButton.render(context, bgMouseX, bgMouseY, delta);
        }

        if (session.currentState != EditorState.HIDDEN) {
            if (!session.isEyedropperActive) {
                context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0x66000000);
            }

            // Foreground layer (rendered on top of the dim overlay).
            // Skipped when a sub-menu is open so clicks pass through to the screen below.
            if (showButton && !hideVanilla) {
                if (editor.entryButton != null) editor.entryButton.render(context, bgMouseX, bgMouseY, delta);
                if (editor.whitelistButton != null) editor.whitelistButton.render(context, bgMouseX, bgMouseY, delta);
            }

            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) accessor.getX(), (float) accessor.getY());
            renderSavedLinesLayer(context);
            context.getMatrices().popMatrix();

            switch (session.currentState) {
                case DRAW_LINES -> editor.screenDrawLines.render(context, mouseX, mouseY, delta);
                case VIEW_GROUPS, SELECT_SLOTS -> editor.screenViewGroups.render(context, mouseX, mouseY, delta);
                case EDIT_FILTER -> editor.screenEditFilter.render(context, mouseX, mouseY, delta);
                default -> {}
            }

            renderStatusMessage(context);

            if (session.isColorPickerOpen) {
                editor.screenColorPicker.render(context, mouseX, mouseY, delta);
            }
        }
    }

    public void renderSavedLinesLayer(DrawContext context) {
        ChestConfigManager manager = ChestConfigManager.getInstance();

        int bgAlphaBase = GlobalChestConfig.instance.bgTransparency * 255 / 100;
        int lineAlphaBase = GlobalChestConfig.instance.lineTransparency * 255 / 100;

        // Clear-button hover preview: fade out the layers that would be erased so the user sees the
        // result before clicking. Only the active tab's affected layers fade (lines/bg/both).
        boolean previewActive = session.currentState == EditorState.DRAW_LINES && session.clearPreviewTab != -1;
        boolean fadeLines = previewActive && (session.clearPreviewTab == 0 || session.clearPreviewTab == 2);
        boolean fadeBg = previewActive && (session.clearPreviewTab == 1 || session.clearPreviewTab == 2);
        if (previewActive) {
            float pulse = 0.10f + 0.12f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 320.0));
            if (fadeLines) lineAlphaBase = (int) (lineAlphaBase * pulse);
            if (fadeBg) bgAlphaBase = (int) (bgAlphaBase * pulse);
        }

        int bgAlpha = bgAlphaBase << 24;
        int lineAlpha = lineAlphaBase << 24;

        for (Slot s : accessor.getHandler().slots) {
            if (s.inventory instanceof PlayerInventory) continue;

            int bgColor = manager.getColor(s.getIndex(), ChestConfigManager.ACTION_BG);
            if (bgColor != 0) context.fill(s.x, s.y, s.x + 16, s.y + 16, (bgColor & 0xFFFFFF) | bgAlpha);

            renderLineRaw(
                    context,
                    s.x,
                    s.y,
                    manager.getColor(s.getIndex(), ChestConfigManager.ACTION_TOP),
                    ChestConfigManager.ACTION_TOP,
                    lineAlpha);
            renderLineRaw(
                    context,
                    s.x,
                    s.y,
                    manager.getColor(s.getIndex(), ChestConfigManager.ACTION_BOTTOM),
                    ChestConfigManager.ACTION_BOTTOM,
                    lineAlpha);
            renderLineRaw(
                    context,
                    s.x,
                    s.y,
                    manager.getColor(s.getIndex(), ChestConfigManager.ACTION_LEFT),
                    ChestConfigManager.ACTION_LEFT,
                    lineAlpha);
            renderLineRaw(
                    context,
                    s.x,
                    s.y,
                    manager.getColor(s.getIndex(), ChestConfigManager.ACTION_RIGHT),
                    ChestConfigManager.ACTION_RIGHT,
                    lineAlpha);
        }
    }

    private void renderLineRaw(DrawContext context, int x, int y, int color, int action, int alpha) {
        if (color == 0) return;
        int renderColor = (color & 0x00FFFFFF) | alpha;

        if (action == ChestConfigManager.ACTION_TOP) context.fill(x - 1, y - 1, x + 17, y, renderColor);
        else if (action == ChestConfigManager.ACTION_BOTTOM) context.fill(x - 1, y + 16, x + 17, y + 17, renderColor);
        else if (action == ChestConfigManager.ACTION_LEFT) context.fill(x - 1, y - 1, x, y + 17, renderColor);
        else if (action == ChestConfigManager.ACTION_RIGHT) context.fill(x + 16, y - 1, x + 17, y + 17, renderColor);
    }

    private void renderStatusMessage(DrawContext context) {
        if (session.statusMessage != null) {
            long elapsed = System.currentTimeMillis() - session.statusMessageTime;
            if (elapsed < 2000) {
                int alpha = 255;
                if (elapsed > 1500) alpha = (int) (255 * (1.0f - (elapsed - 1500) / 500.0f));
                int color = (alpha << 24) | 0xFFFFFF;
                context.drawCenteredTextWithShadow(
                        MinecraftClient.getInstance().textRenderer,
                        session.statusMessage,
                        screen.width / 2,
                        screen.height - 40,
                        color);
            } else {
                session.statusMessage = null;
            }
        }
    }

    public void renderNormalModeOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!editor.isEditMode()) {
            if (GlobalChestConfig.instance.showLeftPanel || KeyInputHandler.isModifierPressed()) {
                editor.screenViewGroups.renderWhitelistPreviewPanel(context, mouseX, mouseY);
            }
        }

        long window = MinecraftClient.getInstance().getWindow().getHandle();
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        boolean hover = false;
        if (!editor.isEditMode() && GlobalChestConfig.instance.showDepositButton && editor.depositButton != null) {
            editor.depositButton.tooltipText = shift
                    ? Text.translatable("key.chestseparators.deposit_all").getString()
                    : Text.translatable("key.chestseparators.deposit_filter").getString();

            hover = editor.isHovering(editor.depositButton.x, editor.depositButton.y, 20, 20, mouseX, mouseY);
        }

        if (hover) {
            // Toggling Shift is an explicit request for the other preview (filtered vs. all),
            // so lift the post-deposit suspension and let it recompute below.
            if (editor.suspendDepositPreview && shift != editor.depositPreviewShift) {
                editor.suspendDepositPreview = false;
            }

            // Otherwise, do not re-show the preview right after a deposit — wait for the cursor to leave first.
            if (editor.suspendDepositPreview) {
                editor.isHoveringDeposit = false;
                return;
            }

            editor.isHoveringDeposit = true;
            if (editor.depositPreviewShift != shift || editor.previewSourceRemaining.isEmpty()) {
                editor.depositPreviewShift = shift;
                editor.updateDepositPreview(shift);
            }
            renderDepositPreview(context);
        } else {
            editor.isHoveringDeposit = false;
            editor.suspendDepositPreview = false;

            editor.previewSourceRemaining.clear();
            editor.previewTargetIncoming.clear();
        }
    }

    private void renderDepositPreview(DrawContext context) {
        if (!editor.isHoveringDeposit || editor.previewTargetIncoming.isEmpty()) return;

        int guiX = accessor.getX();
        int guiY = accessor.getY();

        // Player inventory — outgoing items (dimmed with remaining count).
        for (Map.Entry<Integer, Integer> entry : editor.previewSourceRemaining.entrySet()) {
            Slot slot = accessor.getHandler().getSlot(entry.getKey());
            int remaining = entry.getValue();
            int x = guiX + slot.x;
            int y = guiY + slot.y;

            drawVanillaSlotBevel(context, x, y);

            if (remaining > 0) {
                ItemStack visualStack = slot.getStack().copy();
                visualStack.setCount(remaining);
                context.drawItem(visualStack, x, y);
                drawDurabilityBar(context, visualStack, x, y);

                // Gray wash to signal items leaving the player inventory.
                context.fill(x, y, x + 16, y + 16, 0xAA8B8B8B);

                if (remaining > 1) {
                    drawProjectedCount(context, x, y, remaining, 0xAAFFFFFF);
                }
            } else {
                context.drawItem(slot.getStack(), x, y);
                drawDurabilityBar(context, slot.getStack(), x, y);
                context.fill(x, y, x + 16, y + 16, 0xAA8B8B8B);
            }
        }

        // Container — incoming items (ghost overlay with projected total).
        for (Map.Entry<Integer, ItemStack> entry : editor.previewTargetIncoming.entrySet()) {
            Slot slot = accessor.getHandler().getSlot(entry.getKey());
            ItemStack incoming = entry.getValue();
            int x = guiX + slot.x;
            int y = guiY + slot.y;

            int total = incoming.getCount();
            if (slot.hasStack()) total += slot.getStack().getCount();

            drawVanillaSlotBevel(context, x, y);
            context.drawItem(incoming, x, y);
            drawDurabilityBar(context, incoming, x, y);
            drawCustomPreviewGlow(context, x, y, slot.getIndex());

            if (total > 1) {
                drawProjectedCount(context, x, y, total, 0xAAFFFFFF);
            }
        }
    }

    /** Draws the item durability bar, mirroring vanilla's exact rendering logic. */
    private void drawDurabilityBar(DrawContext context, ItemStack stack, int x, int y) {
        if (stack.isItemBarVisible()) {
            int step = stack.getItemBarStep();
            int color = stack.getItemBarColor();
            int barX = x + 2;
            int barY = y + 13;
            context.fill(barX, barY, barX + 13, barY + 2, 0xFF000000);
            context.fill(barX, barY, barX + step, barY + 1, color | 0xFF000000);
        }
    }

    private void drawProjectedCount(DrawContext context, int x, int y, int count, int color) {
        String text = String.valueOf(count);
        int textW = MinecraftClient.getInstance().textRenderer.getWidth(text);
        context.drawText(MinecraftClient.getInstance().textRenderer, text, x + 17 - textW, y + 9, color, true);
    }

    private void drawVanillaSlotBevel(DrawContext context, int x, int y) {
        context.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        context.fill(x - 1, y - 1, x + 16, y, 0xFF373737);
        context.fill(x - 1, y, x, y + 16, 0xFF373737);
        context.fill(x, y + 16, x + 17, y + 17, 0xFFFFFFFF);
        context.fill(x + 16, y, x + 17, y + 16, 0xFFFFFFFF);
    }

    /**
     * Draws the ghost glow for a container slot during the deposit preview.
     * Respects any custom background color and separator lines the player has configured.
     */
    private void drawCustomPreviewGlow(DrawContext context, int x, int y, int slotIndex) {
        ChestConfigManager manager = ChestConfigManager.getInstance();
        int previewAlpha = 0x88000000;

        int customBg = manager.getColor(slotIndex, ChestConfigManager.ACTION_BG);
        if (customBg != 0) {
            context.fill(x, y, x + 16, y + 16, (customBg & 0x00FFFFFF) | previewAlpha);
        } else {
            context.fill(x, y, x + 16, y + 16, 0xAA8B8B8B);
        }

        // Draw separator lines at the user-configured opacity so they read over the vanilla bevel.
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;
        renderLineRaw(
                context,
                x,
                y,
                manager.getColor(slotIndex, ChestConfigManager.ACTION_TOP),
                ChestConfigManager.ACTION_TOP,
                lineAlpha);
        renderLineRaw(
                context,
                x,
                y,
                manager.getColor(slotIndex, ChestConfigManager.ACTION_BOTTOM),
                ChestConfigManager.ACTION_BOTTOM,
                lineAlpha);
        renderLineRaw(
                context,
                x,
                y,
                manager.getColor(slotIndex, ChestConfigManager.ACTION_LEFT),
                ChestConfigManager.ACTION_LEFT,
                lineAlpha);
        renderLineRaw(
                context,
                x,
                y,
                manager.getColor(slotIndex, ChestConfigManager.ACTION_RIGHT),
                ChestConfigManager.ACTION_RIGHT,
                lineAlpha);
    }
}
