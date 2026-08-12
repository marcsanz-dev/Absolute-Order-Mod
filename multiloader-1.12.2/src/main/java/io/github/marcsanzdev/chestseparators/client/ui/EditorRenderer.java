package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import java.util.Map;
import net.minecraft.client.Minecraft;
import io.github.marcsanzdev.chestseparators.client.compat.GuiGraphics;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Keyboard;

public class EditorRenderer {

    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final GuiContainer screen;
    private final HandledScreenAccessor accessor;

    private final EditorLayout layout;

    public EditorRenderer(
            ChestSeparatorsEditor editor,
            EditorSessionData session,
            EditorLayout layout,
            GuiContainer screen,
            HandledScreenAccessor accessor) {
        this.editor = editor;
        this.session = session;
        this.layout = layout;
        this.screen = screen;
        this.accessor = accessor;
    }

    /**
     * Commits everything drawn so far and clears the depth buffer, so the next draws compose flat on top by
     * draw order. 1.20.1 GUI rendering flushes render types in a fixed order (fills, items, TEXT last) and
     * depth-tests each draw; without this, item-count numbers and button labels punch through overlays drawn
     * over them. Used at every modal/overlay boundary to emulate the E5 deferred compositor's flat layering.
     */
    public static void flushAndClearDepth(GuiGraphics context) {
        context.flush();
        // 1.12.2: no RenderSystem — clear the depth buffer directly through legacy LWJGL2 GL.
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
    }

    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Fire any deferred "flash then act" button actions whose flash has finished playing.
        io.github.marcsanzdev.chestseparators.client.ui.widgets.PressAnim.tick();
        layout.update(screen, accessor, editor.getSidebarYOffset());
        editor.syncClientInventoryWhitelists(ChestConfigManager.getInstance().getCurrentWhitelists());

        boolean hideInFilter = (session.currentState == EditorState.EDIT_FILTER);
        boolean hideVanilla = session.isColorPickerOpen || hideInFilter || session.hasSelectionConflict;

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
        if (editor.fillButton != null) {
            // Same press-pulse for the pull button, so the 'D' hotkey feels like clicking it (matches push).
            editor.fillButton.isActive = (System.currentTimeMillis() - editor.fillClickTime < 150);
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
                context.pose().pushPose();
                context.pose().translate((float) accessor.getX(), (float) accessor.getY(), 0.0F);
                doRenderSavedLinesLayer(context);
                context.pose().popPose();
            }
            editor.presetsMenu.renderPanel(context, screen.width, screen.height, mouseX, mouseY);
            renderToolbar(context, mouseX, mouseY, delta);
            return;
        }

        int bgMouseX = session.hasSelectionConflict ? -1 : mouseX;
        int bgMouseY = session.hasSelectionConflict ? -1 : mouseY;

        // Background layer (rendered behind the dim overlay).
        // When a sub-menu is active, pass mouse coordinates of (-1, -1) so the buttons
        // remain visible but do not react to hover visually.
        // Toolbar (background layer).
        renderToolbar(context, hideVanilla ? -1 : bgMouseX, hideVanilla ? -1 : bgMouseY, delta);

        if (session.currentState != EditorState.HIDDEN) {
            // Transfer (push/pull) preview drawn FIRST — at the same layer the real container items sit on
            // (like the E5 flat pipeline): the ghost is faded here, then the sub-screen dim below darkens it
            // TOGETHER with the stationary items, so it integrates with the darkened inventory instead of
            // popping bright. Suppressed while the colour picker is open so the push/pull zone stays inert.
            if (!session.isColorPickerOpen) {
                renderTransferPreviewOverlay(context, mouseX, mouseY);
            }

            // Sub-screen dim. Skipped while the colour picker is open — the picker draws its OWN modal dim
            // (0xB0) below itself, and stacking both made the picker background far too black. flush + clear
            // depth first so the dim composes FLAT over the ghost AND the real items (1.20.1 depth-tests each
            // draw), fading them all uniformly to the same tone.
            if (!session.isEyedropperActive && !session.isColorPickerOpen) {
                flushAndClearDepth(context);
                // Match the presets menu's depth-1 dim (0x55) so filters/edit-layout are not darker than it.
                context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0x55000000);
            }

            // Foreground layer (rendered on top of the dim overlay).
            // Skipped when a sub-menu is open so clicks pass through to the screen below.
            // Toolbar (foreground layer, drawn above the dim when a sub-menu is open).
            if (!hideVanilla) {
                renderToolbar(context, bgMouseX, bgMouseY, delta);
            }

            context.pose().pushPose();
            context.pose().translate((float) accessor.getX(), (float) accessor.getY(), 0.0F);
            renderSavedLinesLayer(context);
            renderUndoHighlights(context);
            context.pose().popPose();

            // if/else instead of switch(enum): a switch over an enum makes javac emit a synthetic
            // EditorRenderer$1 $SwitchMap class which Forge's LaunchClassLoader fails to transform in this
            // coremod env (crash: NoClassDefFoundError EditorRenderer$1). Direct enum == comparisons avoid it.
            EditorState state = session.currentState;
            if (state == EditorState.DRAW_LINES) {
                editor.screenDrawLines.render(context, mouseX, mouseY, delta);
            } else if (state == EditorState.VIEW_GROUPS || state == EditorState.SELECT_SLOTS) {
                editor.screenViewGroups.render(context, mouseX, mouseY, delta);
            } else if (state == EditorState.EDIT_FILTER) {
                editor.screenEditFilter.render(context, mouseX, mouseY, delta);
            }

            renderStatusMessage(context);
            renderMagnifierHint(context);

            if (session.isColorPickerOpen) {
                // The colour picker is modal: flush + clear depth so the underlying edit-layout button labels
                // (text flushes last, and writes depth) cannot punch through it. It then renders cleanly on top.
                // The picker draws its OWN 0x77000000 modal dim (ScreenColorPicker#render) — the SAME depth-2
                // darkness the filter item-selection panel uses — so we must NOT add another dim here, or the
                // two stack and the background goes far too black.
                flushAndClearDepth(context);
                editor.screenColorPicker.render(context, mouseX, mouseY, delta);
            }

            renderMagnifier(context, mouseX, mouseY);
        }
    }

    // --- Right-side vertical toolbar dock ---

    /**
     * Lays out the visible toolbar icons in a horizontal bar docked on top of the container GUI, inside a
     * semi-transparent rounded panel, then renders them. Visibility depends on the per-button config flags
     * and the screen context (player inventory vs container) — never on the editor state, so the bar keeps
     * the same icons in the same places while a sub-screen is open. Hidden buttons are parked off-screen
     * so they receive no hover or clicks.
     */
    private void renderToolbar(GuiGraphics context, int hoverX, int hoverY, float delta) {
        parkToolbarButtons();
        // The bar itself stays visible while the colour picker is open — it is simply inert: render() passes
        // hoverX/hoverY = -1 in that case, so the buttons draw no hover/preview state, and input is blocked in
        // EditorInputHandler. (It must NOT early-return: hiding the whole bar was wrong.)
        if (!GlobalChestConfig.instance.showEditButtons) return;

        boolean inv = session.isPlayerInventory;
        java.util.List<io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget> vis =
                new java.util.ArrayList<>();
        if (GlobalChestConfig.instance.btnEditLines && editor.entryButton != null) vis.add(editor.entryButton);
        if (GlobalChestConfig.instance.btnFilters && editor.whitelistButton != null) vis.add(editor.whitelistButton);
        if (GlobalChestConfig.instance.showDepositButton && !inv && editor.depositButton != null) {
            vis.add(editor.depositButton);
        }
        if (GlobalChestConfig.instance.btnFillFromChest && !inv && editor.fillButton != null) {
            vis.add(editor.fillButton);
        }
        // Chest presets first when a container is open — the chest is what you're looking at, so its presets
        // should be the first of the two you reach; the inventory presets follow.
        if (GlobalChestConfig.instance.btnChestPresets && !inv && editor.chestPresetsButton != null) {
            vis.add(editor.chestPresetsButton);
        }
        if (GlobalChestConfig.instance.btnInventoryPresets && editor.presetsButton != null) {
            vis.add(editor.presetsButton);
        }

        int n = vis.size();
        if (n == 0) return;

        final int btn = 20;
        final int gap = 3;
        final int pad = 4;
        final int gapToChest = 3;
        // HORIZONTAL bar docked on TOP of the container GUI and centred on it. The editor sub-screens put
        // their button columns to the left and right of the chest, so sitting above it is the one spot
        // that can never collide with them.
        int panelW = n * btn + (n - 1) * gap + 2 * pad;
        int panelH = btn + 2 * pad;
        int panelX = accessor.getX() + (accessor.getBackgroundWidth() - panelW) / 2;
        // Anchored to the chest, always in the same place — by design it may end up behind a sub-screen's
        // taller panels, which is accepted in exchange for the bar never moving.
        int panelY = accessor.getY() - panelH - gapToChest;
        // The creative screen draws its item-group tab row above the GUI top, which would overlap the bar,
        // so lift the bar above those tabs.
        if (screen instanceof net.minecraft.client.gui.inventory.GuiContainerCreative) {
            panelY -= 30;
        }

        UiTheme.panel(context, panelX, panelY, panelW, panelH);

        for (int i = 0; i < n; i++) {
            io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget b = vis.get(i);
            b.x = panelX + pad + i * (btn + gap);
            b.y = panelY + pad;
            b.render(context, hoverX, hoverY, delta);
        }
    }

    /** Parks every toolbar button off-screen so hidden ones never register hover or clicks. */
    private void parkToolbarButtons() {
        io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget[] all = {
            editor.entryButton, editor.whitelistButton, editor.depositButton,
            editor.fillButton, editor.presetsButton, editor.chestPresetsButton
        };
        for (io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget b : all) {
            if (b != null) {
                b.x = -1000;
                b.y = -1000;
            }
        }
    }

    /** Subtle, static hint under the editor telling players how to toggle the magnifier loupe. */
    private void renderMagnifierHint(GuiGraphics context) {
        if (session.currentState != EditorState.DRAW_LINES) return;
        // 1.12.2: KeyMapping#getTranslatedKeyMessage() (Component) → KeyBinding#getDisplayName() (String).
        // TODO(1.12.2 port): client.ui.ModKeyBindings is out of scope for this cluster (not ported), so the
        // magnifier toggle key's display name is unavailable; a placeholder is shown until it is ported.
        String key = "?";
        // Reflect the current state: prompt to turn it ON when off, and OFF when on.
        String hintKey = GlobalChestConfig.instance.magnifierEnabled
                ? "message.chestseparators.magnifier_hint_off"
                : "message.chestseparators.magnifier_hint";
        net.minecraft.util.text.ITextComponent hint = new net.minecraft.util.text.TextComponentTranslation(hintKey, key);
        net.minecraft.client.gui.FontRenderer tr = Minecraft.getMinecraft().fontRenderer;
        context.drawString(tr, hint, (screen.width - tr.getStringWidth(hint.getFormattedText())) / 2, screen.height - 12, 0x70FFFFFF, false);
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
    private void renderUndoHighlights(GuiGraphics context) {
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

        // 1.12.2: AbstractContainerMenu#slots → Container#inventorySlots; Slot#x/#y → Slot#xPos/#yPos.
        for (Slot s : accessor.getHandler().inventorySlots) {
            if (!ChestSeparatorsEditor.isEditableSlot(s)) continue;
            ChestConfigManager.SlotChange ch = editor.undoHighlights.get(ChestSeparatorsEditor.slotKey(s));
            if (ch == null) continue;
            int color = (undoHighlightColor(ch) & 0x00FFFFFF) | alpha;
            // 2px frame just outside the 16x16 slot.
            context.fill(s.xPos - 2, s.yPos - 2, s.xPos + 18, s.yPos, color);
            context.fill(s.xPos - 2, s.yPos + 16, s.xPos + 18, s.yPos + 18, color);
            context.fill(s.xPos - 2, s.yPos, s.xPos, s.yPos + 16, color);
            context.fill(s.xPos + 16, s.yPos, s.xPos + 18, s.yPos + 16, color);
        }
    }

    private static int undoHighlightColor(ChestConfigManager.SlotChange ch) {
        // if/else (not switch) to avoid the synthetic EditorRenderer$1 $SwitchMap class — see render().
        if (ch == ChestConfigManager.SlotChange.FILTER_CREATED) return 0x55FF55;
        if (ch == ChestConfigManager.SlotChange.FILTER_REMOVED) return 0xFF5555;
        if (ch == ChestConfigManager.SlotChange.FILTER_MODIFIED) return 0xFFD24C;
        if (ch == ChestConfigManager.SlotChange.LAYOUT) return 0x55D6FF;
        return 0;
    }

    /** Called from the GenericContainerScreen mixin at drawSlots HEAD. Suppressed during presets menu
     *  because EditorRenderer's presets block calls doRenderSavedLinesLayer() directly instead. */
    public void renderSavedLinesLayer(GuiGraphics context) {
        if (session.isPresetsMenuOpen) return;
        doRenderSavedLinesLayer(context);
    }

    private void doRenderSavedLinesLayer(GuiGraphics context) {
        ChestConfigManager manager = ChestConfigManager.getInstance();

        int bgAlpha = (GlobalChestConfig.instance.bgTransparency * 255 / 100) << 24;
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;

        // Clear-button hover preview: render the layers that would be erased with the same white wash
        // the eraser tool uses, so the feedback is consistent across the mod.
        boolean previewActive = session.currentState == EditorState.DRAW_LINES && session.clearPreviewTab != -1;
        boolean clearLines = previewActive && (session.clearPreviewTab == 0 || session.clearPreviewTab == 2);
        boolean clearBg = previewActive && (session.clearPreviewTab == 1 || session.clearPreviewTab == 2);

        for (Slot s : accessor.getHandler().inventorySlots) {
            if (!ChestSeparatorsEditor.isEditableSlot(s)) continue;

            int key = ChestSeparatorsEditor.slotKey(s);
            int bgColor = manager.getColor(key, ChestConfigManager.ACTION_BG);
            if (bgColor != 0) {
                int fill = clearBg ? ERASE_BG_WASH : ((bgColor & 0xFFFFFF) | bgAlpha);
                context.fill(s.xPos, s.yPos, s.xPos + 16, s.yPos + 16, fill);
            }

            renderEdgesInPaintOrder(context, s.xPos, s.yPos, key, lineAlpha, clearLines);
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
            GuiGraphics context, int x, int y, int slotIndex, int lineAlpha, boolean erasePreview) {
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
            GuiGraphics context,
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

    private void drawCorner(GuiGraphics context, int cx, int cy, int colorA, int seqA, int colorB, int seqB) {
        int color;
        if (colorA == 0 && colorB == 0) return;
        else if (colorA == 0) color = colorB;
        else if (colorB == 0) color = colorA;
        else color = (seqA >= seqB) ? colorA : colorB;
        context.fill(cx, cy, cx + 1, cy + 1, color);
    }

    private void renderStatusMessage(GuiGraphics context) {
        if (session.statusMessage != null) {
            long elapsed = System.currentTimeMillis() - session.statusMessageTime;
            if (elapsed < 2000) {
                int alpha = 255;
                if (elapsed > 1500) alpha = (int) (255 * (1.0f - (elapsed - 1500) / 500.0f));
                int color = (alpha << 24) | 0xFFFFFF;
                context.drawCenteredString(
                        Minecraft.getMinecraft().fontRenderer,
                        session.statusMessage,
                        screen.width / 2,
                        screen.height - 40,
                        color);
            } else {
                session.statusMessage = null;
            }
        }
    }

    public void renderNormalModeOverlay(GuiGraphics context, int mouseX, int mouseY) {
        // The presets menu owns the screen while open: never draw the always-on left filter panel or
        // the deposit preview behind it.
        if (session.isPresetsMenuOpen) return;

        if (!editor.isEditMode()) {
            // TODO(1.12.2 port): event.KeyInputHandler / client.ui.ModKeyBindings are out of scope for this
            // cluster, so the "show panel" modifier is polled directly here. Its default binding is Left Alt
            // (LWJGL2 KEY_LMENU); a rebind via ModKeyBindings is not honoured until that class is ported.
            if (GlobalChestConfig.instance.showLeftPanel || Keyboard.isKeyDown(Keyboard.KEY_LMENU)) {
                editor.screenViewGroups.renderWhitelistPreviewPanel(context, mouseX, mouseY);
            }
        }

        // When a sub-screen is open, render() already drew the deposit preview at the right z-order
        // (above the dim so it isn't faded, below the filter indicators). Draw it here — on the bright,
        // undimmed chest — only when the editor is fully closed.
        if (session.currentState == EditorState.HIDDEN) {
            renderTransferPreviewOverlay(context, mouseX, mouseY);
        }
    }

    /**
     * Updates + draws the push (deposit) OR pull (fill) preview depending on which toolbar button is
     * hovered. Push shows outgoing player items + incoming container ghosts; pull highlights the
     * container items that would be pulled to you (accent wash + count).
     */
    void renderTransferPreviewOverlay(GuiGraphics context, int mouseX, int mouseY) {
        // 1.12.2: no GLFW window handle — poll the shift keys through the legacy LWJGL2 Keyboard.
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        boolean hoverDeposit = false;
        if (GlobalChestConfig.instance.showDepositButton
                && !session.isPlayerInventory
                && editor.depositButton != null) {
            editor.depositButton.tooltipText = shift
                    ? new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.push_to_chest.shift")
                            .getFormattedText()
                    : new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.push_to_chest").getFormattedText();
            hoverDeposit = editor.isHovering(editor.depositButton.x, editor.depositButton.y, 20, 20, mouseX, mouseY);
        }
        boolean hoverFill = false;
        if (GlobalChestConfig.instance.btnFillFromChest && !session.isPlayerInventory && editor.fillButton != null) {
            hoverFill = editor.isHovering(editor.fillButton.x, editor.fillButton.y, 20, 20, mouseX, mouseY);
        }

        if (hoverDeposit) {
            editor.isHoveringFill = false;
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
        } else if (hoverFill) {
            editor.isHoveringDeposit = false;
            editor.suspendDepositPreview = false;
            editor.isHoveringFill = true;
            editor.updateFillPreview(shift);
            renderDepositPreview(context);
        } else {
            editor.isHoveringDeposit = false;
            editor.isHoveringFill = false;
            editor.suspendDepositPreview = false;
            editor.previewSourceRemaining.clear();
            editor.previewTargetIncoming.clear();
        }
    }

    private void renderDepositPreview(GuiGraphics context) {
        if ((!editor.isHoveringDeposit && !editor.isHoveringFill) || editor.previewTargetIncoming.isEmpty()) return;

        int guiX = accessor.getX();
        int guiY = accessor.getY();

        // The real item in every preview slot is already suppressed (GenericContainerScreenMixin#drawSlot),
        // so we paint NO slot background. Translucency is faked WITHOUT altering the background: over the
        // opaque ghost we lay a wash the exact colour of the slot's own background. On the empty pixels
        // around the item that wash sits colour-over-identical-colour (invisible); over the item pixels it
        // blends the model toward the background — the exact result of real per-pixel alpha (the 1.21.11
        // deferred GUI pipeline has no item-alpha API, so this is how we honour "fade the item, not the box").
        // In edit-layout the preview is drawn BEFORE the screen dim (see render()), so the global dim fades
        // the ghost together with the stationary items; the wash therefore matches the plain, un-dimmed slot.

        // Source — items leaving. Show the projected leftover (nothing when the whole stack goes).
        for (Map.Entry<Integer, Integer> entry : editor.previewSourceRemaining.entrySet()) {
            Slot slot = accessor.getHandler().getSlot(entry.getKey());
            int remaining = entry.getValue();
            if (remaining <= 0) continue; // whole stack leaves → slot shown empty
            int x = guiX + slot.xPos;
            int y = guiY + slot.yPos;

            ItemStack ghost = slot.getStack().copy();
            ghost.setCount(remaining);
            drawGhostItem(context, ghost, x, y, previewGhostWash(ChestSeparatorsEditor.slotKey(slot)));
            if (remaining > 1) {
                drawProjectedCount(context, x, y, remaining, 0xDDFFFFFF);
            }
        }

        // Target — the slot's FINAL projected stack (already the full post-action content, including any
        // group re-sort; see ChestSeparatorsEditor#applyPreviewReorder). A slot emptied by the re-sort holds
        // an empty stack: the real item is already suppressed, so we simply draw nothing there.
        for (Map.Entry<Integer, ItemStack> entry : editor.previewTargetIncoming.entrySet()) {
            ItemStack incoming = entry.getValue();
            if (incoming.isEmpty()) continue;
            Slot slot = accessor.getHandler().getSlot(entry.getKey());
            int x = guiX + slot.xPos;
            int y = guiY + slot.yPos;

            drawGhostItem(context, incoming, x, y, previewGhostWash(ChestSeparatorsEditor.slotKey(slot)));
            if (incoming.getCount() > 1) {
                drawProjectedCount(context, x, y, incoming.getCount(), 0xDDFFFFFF);
            }
        }
    }

    /**
     * Draws an item as a translucent ghost. {@code wash} is the slot's own background colour at the ghost's
     * complementary alpha, so it fades the item to ~45% without ever changing the box behind it.
     */
    private void drawGhostItem(GuiGraphics context, ItemStack stack, int x, int y, int wash) {
        context.renderItem(stack, x, y);
        // 1.20.1 batches draws and flushes render types in a FIXED order (fills, THEN items): a fade fill
        // batched after the item gets reordered UNDER it at flush time, leaving the item fully opaque (a
        // z-lift does not help — the batch reorder wins over depth). Flush + clear depth to COMMIT the item
        // first, so the wash below then composites flat OVER it and actually fades the ghost to ~45%.
        flushAndClearDepth(context);
        context.fill(x, y, x + 16, y + 16, wash);
        drawDurabilityBar(context, stack, x, y);
    }

    /** Draws the item durability bar, mirroring vanilla's exact rendering logic. */
    private void drawDurabilityBar(GuiGraphics context, ItemStack stack, int x, int y) {
        // 1.16.5 has no ItemStack isBarVisible/getBarWidth/getBarColor (added in 1.17), and the Forge
        // showDurabilityBar/getRGBDurabilityForDisplay helpers aren't visible from common (vanilla Mojmap).
        // Replicate vanilla's own bar maths: shown when damaged, width = round(13 - dmg*13/maxDmg), colour
        // = hsvToRgb(remaining/3).
        if (stack.isItemDamaged()) {
            int step = Math.round(13.0F - (float) stack.getItemDamage() * 13.0F / (float) stack.getMaxDamage());
            float remaining = Math.max(
                    0.0F,
                    ((float) stack.getMaxDamage() - (float) stack.getItemDamage()) / (float) stack.getMaxDamage());
            int color = net.minecraft.util.math.MathHelper.hsvToRGB(remaining / 3.0F, 1.0F, 1.0F);
            int barX = x + 2;
            int barY = y + 13;
            context.fill(barX, barY, barX + 13, barY + 2, 0xFF000000);
            context.fill(barX, barY, barX + step, barY + 1, color | 0xFF000000);
        }
    }

    private void drawProjectedCount(GuiGraphics context, int x, int y, int count, int color) {
        String text = String.valueOf(count);
        int textW = Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
        // Above the item's +z (and the fade wash) so the projected count reads on top of the ghost.
        context.pose().pushPose();
        context.pose().translate(0.0, 0.0, 255.0);
        context.drawString(Minecraft.getMinecraft().fontRenderer, text, x + 17 - textW, y + 9, color, true);
        context.pose().popPose();
    }

    // 0x8C ≈ 55% alpha. Laying the slot's background colour over the opaque ghost at this alpha leaves the
    // item at the complementary ~45% opacity — the "Medio" level the user picked.
    private static final int GHOST_WASH_ALPHA = 0x8C;

    /**
     * The wash colour that fades a ghost item to ~45% while leaving the slot box untouched: the slot's own
     * background colour (vanilla grey plus any custom bg) at {@link #GHOST_WASH_ALPHA}. Painting this over the
     * item reproduces exactly what real per-pixel alpha would show, and over the empty pixels it is
     * colour-over-identical-colour, i.e. invisible. The edit-layout dim is applied globally AFTER the ghost
     * (see render()), so it is intentionally NOT baked in here — that lets the dim fade the ghost like a
     * stationary item.
     */
    private int previewGhostWash(int slotKey) {
        ChestConfigManager manager = ChestConfigManager.getInstance();
        // Vanilla container slots have a flat 0x8B8B8B interior under the 16×16 item area.
        int base = 0x8B8B8B;

        int customBg = manager.getColor(slotKey, ChestConfigManager.ACTION_BG);
        if (customBg != 0) {
            int bgAlpha = GlobalChestConfig.instance.bgTransparency * 255 / 100;
            base = compositeOver(base, customBg & 0xFFFFFF, bgAlpha);
        }
        // The ghost is drawn BEFORE the sub-screen dim (see render()), which then darkens the ghost together
        // with the stationary items. The wash is therefore computed for the PLAIN, un-dimmed slot and NOT
        // baked with any darkening here — the global dim applies it uniformly afterwards, so the preview slot
        // ends up at exactly the same tone as its neighbours. (Baking it here would double-dim the slot.)
        return (base & 0xFFFFFF) | (GHOST_WASH_ALPHA << 24);
    }

    /** Composites an RGB source at {@code srcAlpha} (0–255) over an opaque RGB destination. */
    private static int compositeOver(int dstRgb, int srcRgb, int srcAlpha) {
        float a = srcAlpha / 255f;
        int dr = (dstRgb >> 16) & 0xFF, dg = (dstRgb >> 8) & 0xFF, db = dstRgb & 0xFF;
        int sr = (srcRgb >> 16) & 0xFF, sg = (srcRgb >> 8) & 0xFF, sb = srcRgb & 0xFF;
        int rr = Math.round(sr * a + dr * (1 - a));
        int rg = Math.round(sg * a + dg * (1 - a));
        int rb = Math.round(sb * a + db * (1 - a));
        return (rr << 16) | (rg << 8) | rb;
    }

    /**
     * Painting loupe: only while drawing separators (not the picker) and only when the cursor is over
     * the chest's slot area. The eyedropper loupe is handled separately in ScreenColorPicker so it can
     * sample the whole screen. Delegates to {@link MagnifierRenderer}, which magnifies the real
     * framebuffer (so item counts, previews and separators all show exactly as on screen).
     */
    private void renderMagnifier(GuiGraphics context, int mouseX, int mouseY) {
        if (!GlobalChestConfig.instance.magnifierEnabled) return;
        if (session.currentState != EditorState.DRAW_LINES || session.isColorPickerOpen) return;
        if (!magnifierEnabledForTab(session.currentTab)) return;
        if (!isCursorOverChestSlots(mouseX, mouseY)) return;
        boolean circle = GlobalChestConfig.instance.magnifierShape == GlobalChestConfig.MagnifierShape.CIRCLE;
        MagnifierRenderer.render(context, mouseX, mouseY, circle);
    }

    private static boolean magnifierEnabledForTab(int tab) {
        switch (tab) {
            case EditorSessionData.TAB_BG: return GlobalChestConfig.instance.magnifierTabBackgrounds;
            case EditorSessionData.TAB_COMBO: return GlobalChestConfig.instance.magnifierTabCombo;
            default: return GlobalChestConfig.instance.magnifierTabLines;
        }
    }

    // Extra margin around the slot area so the loupe also shows on the outer chest borders, where the
    // edge separator lines can still be painted.
    private static final int SLOT_AREA_MARGIN = 2;

    /** True when the cursor is within the chest (non-player) slot area, expanded by a small margin. */
    private boolean isCursorOverChestSlots(int mouseX, int mouseY) {
        int guiX = accessor.getX();
        int guiY = accessor.getY();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Slot s : accessor.getHandler().inventorySlots) {
            if (!ChestSeparatorsEditor.isEditableSlot(s)) continue;
            minX = Math.min(minX, guiX + s.xPos);
            minY = Math.min(minY, guiY + s.yPos);
            maxX = Math.max(maxX, guiX + s.xPos + 16);
            maxY = Math.max(maxY, guiY + s.yPos + 16);
        }
        if (minX == Integer.MAX_VALUE) return false;
        return mouseX >= minX - SLOT_AREA_MARGIN
                && mouseX < maxX + SLOT_AREA_MARGIN
                && mouseY >= minY - SLOT_AREA_MARGIN
                && mouseY < maxY + SLOT_AREA_MARGIN;
    }
}
