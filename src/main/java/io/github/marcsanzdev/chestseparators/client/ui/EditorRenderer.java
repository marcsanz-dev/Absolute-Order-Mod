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
        // No deposit button when editing the player inventory (you don't deposit into your own inventory).
        boolean showDeposit = GlobalChestConfig.instance.showDepositButton && !session.isPlayerInventory;

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
        // The presets icons are pressed only while their menu is open (radio-like with layout/filters).
        if (editor.presetsButton != null) {
            editor.presetsButton.isActive = session.isPresetsMenuOpen && !session.presetsMenuChestMode;
        }
        if (editor.chestPresetsButton != null) {
            editor.chestPresetsButton.isActive = session.isPresetsMenuOpen && session.presetsMenuChestMode;
        }

        // Presets menu: a light overlay that keeps the top buttons and the container slots visible (the
        // hover preview paints onto the real slots), while the sub-screen panels stay hidden.
        // Phase 1 (dim + preview) runs first; then the saved lines are injected on top of the dim
        // (only when no Load-button preview is active); then Phase 2 draws the panel.
        if (session.isPresetsMenuOpen) {
            if (editor.presetsButton != null) editor.presetsButton.isActive = !session.presetsMenuChestMode;
            if (editor.chestPresetsButton != null) editor.chestPresetsButton.isActive = session.presetsMenuChestMode;
            editor.presetsMenu.renderBackground(context, screen.width, screen.height, mouseX, mouseY);
            if (!editor.presetsMenu.isPreviewActive()) {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate((float) accessor.getX(), (float) accessor.getY());
                doRenderSavedLinesLayer(context);
                context.getMatrices().popMatrix();
            }
            editor.presetsMenu.renderPanel(context, screen.width, screen.height, mouseX, mouseY);
            if (showButton) {
                if (editor.entryButton != null) editor.entryButton.render(context, mouseX, mouseY, delta);
                if (editor.whitelistButton != null) editor.whitelistButton.render(context, mouseX, mouseY, delta);
                if (editor.fillButton != null && !session.isPlayerInventory)
                    editor.fillButton.render(context, mouseX, mouseY, delta);
                if (editor.presetsButton != null) editor.presetsButton.render(context, mouseX, mouseY, delta);
                if (editor.chestPresetsButton != null && !session.isPlayerInventory)
                    editor.chestPresetsButton.render(context, mouseX, mouseY, delta);
            }
            return;
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
            if (editor.fillButton != null && !session.isPlayerInventory)
                editor.fillButton.render(context, bMouseX, bMouseY, delta);
            if (editor.presetsButton != null) editor.presetsButton.render(context, bMouseX, bMouseY, delta);
            if (editor.chestPresetsButton != null && !session.isPlayerInventory)
                editor.chestPresetsButton.render(context, bMouseX, bMouseY, delta);
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
                if (editor.fillButton != null && !session.isPlayerInventory)
                    editor.fillButton.render(context, bgMouseX, bgMouseY, delta);
                if (editor.presetsButton != null) editor.presetsButton.render(context, bgMouseX, bgMouseY, delta);
                if (editor.chestPresetsButton != null && !session.isPlayerInventory)
                    editor.chestPresetsButton.render(context, bgMouseX, bgMouseY, delta);
            }

            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) accessor.getX(), (float) accessor.getY());
            renderSavedLinesLayer(context);
            renderUndoHighlights(context);
            context.getMatrices().popMatrix();

            switch (session.currentState) {
                case DRAW_LINES -> editor.screenDrawLines.render(context, mouseX, mouseY, delta);
                case VIEW_GROUPS, SELECT_SLOTS -> editor.screenViewGroups.render(context, mouseX, mouseY, delta);
                case EDIT_FILTER -> editor.screenEditFilter.render(context, mouseX, mouseY, delta);
                default -> {}
            }

            renderStatusMessage(context);
            renderMagnifierHint(context);

            if (session.isColorPickerOpen) {
                editor.screenColorPicker.render(context, mouseX, mouseY, delta);
            }

            renderMagnifier(context, mouseX, mouseY);
        }
    }

    /** Subtle, static hint pointing players to the magnifier loupe while painting, when it is off. */
    private void renderMagnifierHint(DrawContext context) {
        if (session.currentState != EditorState.DRAW_LINES || GlobalChestConfig.instance.magnifierEnabled) return;
        net.minecraft.text.Text key = ModKeyBindings.toggleMagnifierKey.getBoundKeyLocalizedText();
        net.minecraft.text.Text hint =
                net.minecraft.text.Text.translatable("message.chestseparators.magnifier_hint", key);
        var tr = MinecraftClient.getInstance().textRenderer;
        context.drawText(tr, hint, (screen.width - tr.getWidth(hint)) / 2, screen.height - 12, 0x70FFFFFF, false);
    }

    // White washes matching the eraser tool's drag preview, reused for the Clear-button hover preview.
    private static final int ERASE_BG_WASH = 0x66FFFFFF;
    private static final int ERASE_LINE_WASH = 0x88FFFFFF;

    private static final long UNDO_HIGHLIGHT_MS = 1300;

    /**
     * Blinks a colored frame around each slot changed by the last undo/redo, fading out over
     * {@link #UNDO_HIGHLIGHT_MS}. Color encodes the exact change: green = a filter (re)appeared,
     * red = a filter was removed, amber = a filter's items changed, cyan = separators changed.
     */
    private void renderUndoHighlights(DrawContext context) {
        if (editor.undoHighlights.isEmpty()) return;
        long elapsed = System.currentTimeMillis() - editor.undoHighlightStart;
        if (elapsed > UNDO_HIGHLIGHT_MS) {
            editor.undoHighlights.clear();
            return;
        }
        float fade = 1.0f - (elapsed / (float) UNDO_HIGHLIGHT_MS);
        float blink = 0.45f + 0.55f * (float) Math.abs(Math.sin(elapsed / 110.0));
        int a = (int) (255 * fade * blink);
        if (a <= 0) return;
        int alpha = a << 24;

        for (Slot s : accessor.getHandler().slots) {
            if (!ChestSeparatorsEditor.isEditableSlot(s)) continue;
            ChestConfigManager.SlotChange ch = editor.undoHighlights.get(ChestSeparatorsEditor.slotKey(s));
            if (ch == null) continue;
            int color = (undoHighlightColor(ch) & 0x00FFFFFF) | alpha;
            // 2px frame just outside the 16x16 slot.
            context.fill(s.x - 2, s.y - 2, s.x + 18, s.y, color);
            context.fill(s.x - 2, s.y + 16, s.x + 18, s.y + 18, color);
            context.fill(s.x - 2, s.y, s.x, s.y + 16, color);
            context.fill(s.x + 16, s.y, s.x + 18, s.y + 16, color);
        }
    }

    private static int undoHighlightColor(ChestConfigManager.SlotChange ch) {
        return switch (ch) {
            case FILTER_CREATED -> 0x55FF55;
            case FILTER_REMOVED -> 0xFF5555;
            case FILTER_MODIFIED -> 0xFFD24C;
            case LAYOUT -> 0x55D6FF;
        };
    }

    /** Called from the GenericContainerScreen mixin at drawSlots HEAD. Suppressed during presets menu
     *  because EditorRenderer's presets block calls doRenderSavedLinesLayer() directly instead. */
    public void renderSavedLinesLayer(DrawContext context) {
        if (session.isPresetsMenuOpen) return;
        doRenderSavedLinesLayer(context);
    }

    private void doRenderSavedLinesLayer(DrawContext context) {
        ChestConfigManager manager = ChestConfigManager.getInstance();

        int bgAlpha = (GlobalChestConfig.instance.bgTransparency * 255 / 100) << 24;
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;

        // Clear-button hover preview: render the layers that would be erased with the same white wash
        // the eraser tool uses, so the feedback is consistent across the mod.
        boolean previewActive = session.currentState == EditorState.DRAW_LINES && session.clearPreviewTab != -1;
        boolean clearLines = previewActive && (session.clearPreviewTab == 0 || session.clearPreviewTab == 2);
        boolean clearBg = previewActive && (session.clearPreviewTab == 1 || session.clearPreviewTab == 2);

        for (Slot s : accessor.getHandler().slots) {
            if (!ChestSeparatorsEditor.isEditableSlot(s)) continue;

            int key = ChestSeparatorsEditor.slotKey(s);
            int bgColor = manager.getColor(key, ChestConfigManager.ACTION_BG);
            if (bgColor != 0) {
                int fill = clearBg ? ERASE_BG_WASH : ((bgColor & 0xFFFFFF) | bgAlpha);
                context.fill(s.x, s.y, s.x + 16, s.y + 16, fill);
            }

            renderEdgesInPaintOrder(context, s.x, s.y, key, lineAlpha, clearLines);
        }
    }

    private static int resolveEdge(int color, int lineAlpha, boolean erasePreview) {
        if (color == 0) return 0;
        return erasePreview ? ERASE_LINE_WASH : ((color & 0x00FFFFFF) | lineAlpha);
    }

    /**
     * Draws a slot's four edge lines as four bodies plus four corners. Each corner pixel is painted
     * exactly once — by the edge painted later (higher sequence) — so corners respect paint order
     * (newest on top) without the double-draw that brightened overlapping translucent edges.
     */
    private void renderEdgesInPaintOrder(
            DrawContext context, int x, int y, int slotIndex, int lineAlpha, boolean erasePreview) {
        ChestConfigManager m = ChestConfigManager.getInstance();
        int rTop = resolveEdge(m.getColor(slotIndex, ChestConfigManager.ACTION_TOP), lineAlpha, erasePreview);
        int rBot = resolveEdge(m.getColor(slotIndex, ChestConfigManager.ACTION_BOTTOM), lineAlpha, erasePreview);
        int rLeft = resolveEdge(m.getColor(slotIndex, ChestConfigManager.ACTION_LEFT), lineAlpha, erasePreview);
        int rRight = resolveEdge(m.getColor(slotIndex, ChestConfigManager.ACTION_RIGHT), lineAlpha, erasePreview);

        int sTop = m.getPaintSeq(slotIndex, ChestConfigManager.ACTION_TOP);
        int sBot = m.getPaintSeq(slotIndex, ChestConfigManager.ACTION_BOTTOM);
        int sLeft = m.getPaintSeq(slotIndex, ChestConfigManager.ACTION_LEFT);
        int sRight = m.getPaintSeq(slotIndex, ChestConfigManager.ACTION_RIGHT);

        drawEdges(context, x, y, rTop, rBot, rLeft, rRight, sTop, sBot, sLeft, sRight);
    }

    /** Shared edge+corner drawing used by both the container config and the inventory render cache. */
    private void drawEdges(
            DrawContext context,
            int x,
            int y,
            int rTop,
            int rBot,
            int rLeft,
            int rRight,
            int sTop,
            int sBot,
            int sLeft,
            int sRight) {
        // Bodies (no corners) — never overlap each other.
        if (rTop != 0) context.fill(x, y - 1, x + 16, y, rTop);
        if (rBot != 0) context.fill(x, y + 16, x + 16, y + 17, rBot);
        if (rLeft != 0) context.fill(x - 1, y, x, y + 16, rLeft);
        if (rRight != 0) context.fill(x + 16, y, x + 17, y + 16, rRight);

        // Corners — one fill each, by the later-painted edge.
        drawCorner(context, x - 1, y - 1, rTop, sTop, rLeft, sLeft);
        drawCorner(context, x + 16, y - 1, rTop, sTop, rRight, sRight);
        drawCorner(context, x - 1, y + 16, rBot, sBot, rLeft, sLeft);
        drawCorner(context, x + 16, y + 16, rBot, sBot, rRight, sRight);
    }

    private void drawCorner(DrawContext context, int cx, int cy, int colorA, int seqA, int colorB, int seqB) {
        int color;
        if (colorA == 0 && colorB == 0) return;
        else if (colorA == 0) color = colorB;
        else if (colorB == 0) color = colorA;
        else color = (seqA >= seqB) ? colorA : colorB;
        context.fill(cx, cy, cx + 1, cy + 1, color);
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
        // The presets menu owns the screen while open: never draw the always-on left filter panel or
        // the deposit preview behind it.
        if (session.isPresetsMenuOpen) return;

        if (!editor.isEditMode()) {
            if (GlobalChestConfig.instance.showLeftPanel || KeyInputHandler.isModifierPressed()) {
                editor.screenViewGroups.renderWhitelistPreviewPanel(context, mouseX, mouseY);
            }
        }

        long window = MinecraftClient.getInstance().getWindow().getHandle();
        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        boolean hover = false;
        if (!editor.isEditMode()
                && GlobalChestConfig.instance.showDepositButton
                && !session.isPlayerInventory
                && editor.depositButton != null) {
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
        renderEdgesInPaintOrder(context, x, y, slotIndex, lineAlpha, false);
    }

    /**
     * Painting loupe: only while drawing separators (not the picker) and only when the cursor is over
     * the chest's slot area. The eyedropper loupe is handled separately in ScreenColorPicker so it can
     * sample the whole screen. Delegates to {@link MagnifierRenderer}, which magnifies the real
     * framebuffer (so item counts, previews and separators all show exactly as on screen).
     */
    private void renderMagnifier(DrawContext context, int mouseX, int mouseY) {
        if (!GlobalChestConfig.instance.magnifierEnabled) return;
        if (session.currentState != EditorState.DRAW_LINES || session.isColorPickerOpen) return;
        if (!magnifierEnabledForTab(session.currentTab)) return;
        if (!isCursorOverChestSlots(mouseX, mouseY)) return;
        boolean circle = GlobalChestConfig.instance.magnifierShape == GlobalChestConfig.MagnifierShape.CIRCLE;
        MagnifierRenderer.render(context, mouseX, mouseY, circle);
    }

    private static boolean magnifierEnabledForTab(int tab) {
        return switch (tab) {
            case EditorSessionData.TAB_BG -> GlobalChestConfig.instance.magnifierTabBackgrounds;
            case EditorSessionData.TAB_COMBO -> GlobalChestConfig.instance.magnifierTabCombo;
            default -> GlobalChestConfig.instance.magnifierTabLines;
        };
    }

    // Extra margin around the slot area so the loupe also shows on the outer chest borders, where the
    // edge separator lines can still be painted.
    private static final int SLOT_AREA_MARGIN = 2;

    /** True when the cursor is within the chest (non-player) slot area, expanded by a small margin. */
    private boolean isCursorOverChestSlots(int mouseX, int mouseY) {
        int guiX = accessor.getX();
        int guiY = accessor.getY();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Slot s : accessor.getHandler().slots) {
            if (!ChestSeparatorsEditor.isEditableSlot(s)) continue;
            minX = Math.min(minX, guiX + s.x);
            minY = Math.min(minY, guiY + s.y);
            maxX = Math.max(maxX, guiX + s.x + 16);
            maxY = Math.max(maxY, guiY + s.y + 16);
        }
        if (minX == Integer.MAX_VALUE) return false;
        return mouseX >= minX - SLOT_AREA_MARGIN
                && mouseX < maxX + SLOT_AREA_MARGIN
                && mouseY >= minY - SLOT_AREA_MARGIN
                && mouseY < maxY + SLOT_AREA_MARGIN;
    }
}
