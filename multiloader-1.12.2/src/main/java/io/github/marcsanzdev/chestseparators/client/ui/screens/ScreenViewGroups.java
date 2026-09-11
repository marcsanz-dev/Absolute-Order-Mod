package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ActionIconButtonWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import io.github.marcsanzdev.chestseparators.client.compat.GuiGraphics;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.MathHelper;

public class ScreenViewGroups extends AbstractEditorScreen {

    // Package-private so the extracted ViewGroupsClickHandler can route clicks to these widgets.
    final List<CustomWidget> popupWidgets = new ArrayList<>();
    final List<CustomWidget> selectSlotsWidgets = new ArrayList<>();

    private long btnClearAllClickTime = 0;
    private long btnClearSelectClickTime = 0;
    private long btnCopyClickTime = 0;
    private long btnPasteClickTime = 0;
    private long btnUndoClickTime = 0;
    private long btnRedoClickTime = 0;

    private final GroupBlobRenderer groupRenderer;
    private final WhitelistPreviewPanelRenderer previewPanelRenderer;
    private final ViewGroupsClickHandler clickHandler;

    public ScreenViewGroups(ChestSeparatorsEditor editor) {
        super(editor);
        this.groupRenderer = new GroupBlobRenderer(this);
        this.previewPanelRenderer = new WhitelistPreviewPanelRenderer(this);
        this.clickHandler = new ViewGroupsClickHandler(this);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return clickHandler.onMouseClicked(mouseX, mouseY, button);
    }

    /** Draws the always-on left whitelist preview panel. Delegates to {@link WhitelistPreviewPanelRenderer}. */
    public void renderWhitelistPreviewPanel(GuiGraphics context, int mouseX, int mouseY) {
        previewPanelRenderer.renderWhitelistPreviewPanel(context, mouseX, mouseY);
    }

    @Override
    public void init() {
        super.init();
        popupWidgets.clear();
        buildPopupWidgets();
        selectSlotsWidgets.clear();
        buildSelectSlotsWidgets();

        // Every conflict-popup button dismisses the popup, so flash the press before acting — otherwise
        // the popup closes on the same frame and the click is never seen.
        for (CustomWidget w : popupWidgets) w.deferAction = true;
    }

    @Override
    protected void buildWidgets() {
        int sx = layout.rightX;
        int sy = layout.mainY;
        int btnW = layout.btnW;
        int bH = layout.bH;

        // --- Block 1: Edit & Clear All ---
        WideButtonWidget btnEditFilter = new WideButtonWidget(
                sx,
                sy,
                btnW,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.edit_filter").getFormattedText(),
                ModTextures.ICON_SM_FILTER,
                () -> {
                    editor.playClickSound(1.0f);
                    if (session.selectedSlots.isEmpty()) {
                        editor.showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.select_first"), TextFormatting.RED);
                        return;
                    }
                    boolean conflictFound = false;
                    java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                    if (whitelists != null) {
                        for (int slotIdx : session.selectedSlots) {
                            if (whitelists.containsKey(slotIdx)) {
                                conflictFound = true;
                                break;
                            }
                        }
                    }
                    if (conflictFound) session.hasSelectionConflict = true;
                    else {
                        session.selectedGroupId = UUID.randomUUID();
                        transitionToEditFilter();
                    }
                });
        btnEditFilter.tooltipText =
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.edit_filter").getFormattedText();
        btnEditFilter.texSize = 128;
        widgets.add(btnEditFilter);

        WideButtonWidget btnClearAll = new WideButtonWidget(
                sx,
                sy + 24,
                btnW,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.delete_all_filters").getFormattedText(),
                ModTextures.ICON_SM_TRASH,
                () -> {
                    btnClearAllClickTime = System.currentTimeMillis();
                    java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                    if (whitelists != null && !whitelists.isEmpty()) {
                        ChestConfigManager.getInstance().saveWhitelistSnapshot();
                        whitelists.clear();
                        editor.saveSmart();
                        editor.sendWhitelistToServer();

                        editor.syncClientInventoryWhitelists(whitelists);

                        editor.showStatus(
                                new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.all_filters_deleted"), TextFormatting.RED);
                        editor.playClickSound(0.8f);
                    }
                });
        btnClearAll.tooltipText = new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.delete_all_filters")
                .getFormattedText();
        btnClearAll.keepNormalTextColor = true;
        btnClearAll.texSize = 128;
        widgets.add(btnClearAll);

        // --- Block 2: Selection Tools ---
        WideButtonWidget btnAreaSelect = new WideButtonWidget(
                sx,
                sy + 53,
                btnW,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.area_select").getFormattedText(),
                ModTextures.ICON_SM_AREA_SELECT,
                () -> {
                    session.wlToolMode = 0;
                    editor.playClickSound(1.2f);
                });
        btnAreaSelect.tooltipText =
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.area_select").getFormattedText();
        btnAreaSelect.texSize = 128;
        widgets.add(btnAreaSelect);

        WideButtonWidget btnTraceSelect = new WideButtonWidget(
                sx,
                sy + 77,
                btnW,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.trace_select").getFormattedText(),
                ModTextures.ICON_SM_TRACE_SELECT,
                () -> {
                    session.wlToolMode = 1;
                    editor.playClickSound(1.2f);
                });
        btnTraceSelect.tooltipText =
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.trace_select").getFormattedText();
        btnTraceSelect.texSize = 128;
        widgets.add(btnTraceSelect);

        WideButtonWidget btnClearSelect = new WideButtonWidget(
                sx,
                sy + 101,
                btnW,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.clear_selection").getFormattedText(),
                ModTextures.ICON_SM_TRASH,
                () -> {
                    btnClearSelectClickTime = System.currentTimeMillis();
                    session.selectedSlots.clear();
                    editor.playClickSound(0.8f);
                });
        btnClearSelect.tooltipText = new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.clear_selection")
                .getFormattedText();
        btnClearSelect.keepNormalTextColor = true;
        btnClearSelect.texSize = 128;
        widgets.add(btnClearSelect);

        // --- Block 3: Global Actions ---
        WideButtonWidget btnCopy = new WideButtonWidget(
                sx,
                sy + 130,
                btnW,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.copy_filters").getFormattedText(),
                ModTextures.ICON_SM_COPY,
                () -> {
                    btnCopyClickTime = System.currentTimeMillis();
                    ChestConfigManager.getInstance().copyWhitelistsToClipboard();
                    editor.showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.filters_copied"), TextFormatting.GRAY);
                    editor.playClickSound(1.0f);
                });
        btnCopy.tooltipText =
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.copy_filters").getFormattedText();
        btnCopy.keepNormalTextColor = true;
        btnCopy.texSize = 128;
        widgets.add(btnCopy);

        WideButtonWidget btnPaste = new WideButtonWidget(
                sx,
                sy + 154,
                btnW,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.paste_filters").getFormattedText(),
                ModTextures.ICON_SM_PASTE,
                () -> {
                    btnPasteClickTime = System.currentTimeMillis();

                    if (ChestConfigManager.getInstance().hasWhitelistClipboardData()) {
                        ChestConfigManager.getInstance().saveWhitelistSnapshot();
                        ChestConfigManager.getInstance().pasteWhitelistsFromClipboard();
                        editor.saveSmart();
                        editor.sendWhitelistToServer();

                        editor.syncClientInventoryWhitelists(
                                ChestConfigManager.getInstance().getCurrentWhitelists());

                        editor.showStatus(
                                new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.filters_pasted"), TextFormatting.GREEN);
                        editor.playClickSound(1.0f);
                    }
                });
        btnPaste.tooltipText =
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.paste_filters").getFormattedText();
        btnPaste.keepNormalTextColor = true;
        btnPaste.texSize = 128;
        widgets.add(btnPaste);

        int halfW = (btnW - 4) / 2;
        WideButtonWidget btnUndo = new WideButtonWidget(
                sx,
                sy + 178,
                halfW,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.undo").getFormattedText(),
                ModTextures.ICON_SM_UNDO,
                () -> {
                    btnUndoClickTime = System.currentTimeMillis();
                    editor.applyUndoRedo(ChestConfigManager.getInstance().undo(), false);
                });
        btnUndo.tooltipText =
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.undo").getFormattedText();
        btnUndo.keepNormalTextColor = true;
        btnUndo.texSize = 128;
        widgets.add(btnUndo);

        WideButtonWidget btnRedo = new WideButtonWidget(
                sx + halfW + 4,
                sy + 178,
                halfW,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.redo").getFormattedText(),
                ModTextures.ICON_SM_REDO,
                () -> {
                    btnRedoClickTime = System.currentTimeMillis();
                    editor.applyUndoRedo(ChestConfigManager.getInstance().redo(), true);
                });
        btnRedo.tooltipText =
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.redo").getFormattedText();
        btnRedo.keepNormalTextColor = true;
        btnRedo.texSize = 128;
        widgets.add(btnRedo);
    }

    private void buildPopupWidgets() {
        ActionIconButtonWidget btnOverwrite = new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 45,
                240,
                16,
                "1. "
                        + new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.conflict.overwrite")
                                .getFormattedText(),
                null,
                0xFF852D2D,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    session.hasSelectionConflict = false;
                    session.selectedGroupId = UUID.randomUUID();
                    editor.playClickSound(1.0f);
                    transitionToEditFilter();
                });
        btnOverwrite.tooltipText = new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.conflict.overwrite")
                .getFormattedText();
        popupWidgets.add(btnOverwrite);

        ActionIconButtonWidget btnDeselect = new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 63,
                240,
                16,
                "2. "
                        + new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.conflict.deselect")
                                .getFormattedText(),
                null,
                0xFF2D852D,
                () -> {
                    java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                    if (whitelists != null) session.selectedSlots.removeIf(whitelists::containsKey);
                    session.hasSelectionConflict = false;
                    editor.playClickSound(1.0f);
                    if (!session.selectedSlots.isEmpty()) {
                        transitionToEditFilter();
                    } else {
                        editor.showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.empty_selection"), TextFormatting.RED);
                    }
                });
        btnDeselect.tooltipText = new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.conflict.deselect")
                .getFormattedText();
        popupWidgets.add(btnDeselect);

        ActionIconButtonWidget btnCancel = new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 81,
                240,
                16,
                "3. " + new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.cancel").getFormattedText(),
                null,
                0xFF444444,
                () -> {
                    session.hasSelectionConflict = false;
                    editor.playClickSound(0.8f);
                });
        btnCancel.tooltipText = new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.conflict.cancel")
                .getFormattedText();
        popupWidgets.add(btnCancel);
    }

    private void buildSelectSlotsWidgets() {
        int btnX = layout.guiX + layout.bgWidth + 4;
        int btnY = layout.guiY;

        selectSlotsWidgets.add(new ActionIconButtonWidget(
                btnX,
                btnY,
                60,
                20,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.confirm").getFormattedText(),
                null,
                0xFF2D852D,
                () -> {
                    if (!session.selectedSlots.isEmpty()) {
                        transitionToEditFilter();
                    }
                    editor.playClickSound(1.0f);
                }));

        selectSlotsWidgets.add(new ActionIconButtonWidget(
                btnX,
                btnY + 25,
                60,
                20,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.cancel").getFormattedText(),
                null,
                0xFF852D2D,
                () -> {
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(1.0f);
                }));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (session.hasSelectionConflict) return true;

        if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
            if (session.isDraggingLine) {
                Slot slot = editor.accessor.getFocusedSlot();
                // Confine the drag to the namespace it started in (no chest<->inventory crossover).
                if (slot != null
                        && ChestSeparatorsEditor.isEditableSlot(slot)
                        && (session.dragStartSlot == null
                                || ChestSeparatorsEditor.isPlayerSlot(slot)
                                        == ChestSeparatorsEditor.isPlayerSlot(session.dragStartSlot))) {
                    session.dragCurrentSlot = slot;

                    // Trace mode: immediately commit each slot as the cursor moves.
                    if (session.wlToolMode == 1) {
                        int k = ChestSeparatorsEditor.slotKey(slot);
                        if (session.isSelecting) session.selectedSlots.add(k);
                        else session.selectedSlots.remove(k);
                    }
                }
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0
                && session.isDraggingLine
                && (session.currentState == EditorState.VIEW_GROUPS
                        || session.currentState == EditorState.SELECT_SLOTS)) {
            // Area mode: commit the rectangular selection on mouse release. Membership is by visual box
            // (see slotsInDragBox) so crossing hotbar↔inventory selects only the cells actually swept, and
            // the selection stays confined to the namespace the drag started in.
            if (session.wlToolMode == 0 && session.dragStartSlot != null && session.dragCurrentSlot != null) {
                for (Slot s : editor.slotsInDragBox(session.dragStartSlot, session.dragCurrentSlot)) {
                    int k = ChestSeparatorsEditor.slotKey(s);
                    if (session.isSelecting) session.selectedSlots.add(k);
                    else session.selectedSlots.remove(k);
                }
            }
            session.isDraggingLine = false;
            session.dragStartSlot = null;
            session.dragCurrentSlot = null;
            return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {

        if (!widgets.isEmpty()) {
            // Button press animations: hold the active state for 150 ms after each click.
            widgets.get(1).isActive = (System.currentTimeMillis() - btnClearAllClickTime < 150); // Delete All Filters
            widgets.get(4).isActive = (System.currentTimeMillis() - btnClearSelectClickTime < 150); // Clear Selection
            widgets.get(5).isActive = (System.currentTimeMillis() - btnCopyClickTime < 150); // Copy

            widgets.get(6).isActive = (System.currentTimeMillis() - btnPasteClickTime < 150); // Paste
            widgets.get(6).isDisabled = !ChestConfigManager.getInstance().hasWhitelistClipboardData();

            widgets.get(7).isActive = (System.currentTimeMillis() - btnUndoClickTime < 150); // Undo
            widgets.get(7).isDisabled = !ChestConfigManager.getInstance().canUndoWhitelist();

            widgets.get(8).isActive = (System.currentTimeMillis() - btnRedoClickTime < 150); // Redo
            widgets.get(8).isDisabled = !ChestConfigManager.getInstance().canRedoWhitelist();

            // Tool toggle buttons: reflect the currently active selection mode.
            widgets.get(2).isActive = (session.wlToolMode == 0); // Area Select
            widgets.get(3).isActive = (session.wlToolMode == 1); // Trace Select
        }

        // When a conflict popup is active, pass (-1, -1) as mouse coordinates so background
        // buttons and slots do not react to hover or draw tooltips.
        int bgMouseX = session.hasSelectionConflict ? -1 : mouseX;
        int bgMouseY = session.hasSelectionConflict ? -1 : mouseY;

        renderWhitelistPreviewPanel(context, bgMouseX, bgMouseY);
        groupRenderer.renderWhitelistGroups(context, bgMouseX, bgMouseY);

        if (session.currentState == EditorState.SELECT_SLOTS) {
            renderSelectionOverlay(context);
            for (CustomWidget w : selectSlotsWidgets) w.render(context, bgMouseX, bgMouseY, delta);
        }

        // Main panel buttons are drawn last so their tooltips render on top of the chest.
        if (session.currentState == EditorState.VIEW_GROUPS) {
            super.render(context, bgMouseX, bgMouseY, delta);
        }

        if (session.hasSelectionConflict) {
            // Pass real mouse coordinates to the popup so its buttons remain clickable.
            renderConflictPopup(context, mouseX, mouseY, delta);
        }
    }

    private void renderConflictPopup(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0xAA000000);

        boolean isDark = GlobalChestConfig.instance.darkMode;
        // Cristal panel (rounded, glass) — matches the unsaved-changes/expel popups in the filter screen.
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(
                context, layout.conflictPopupX, layout.conflictPopupY, layout.conflictPopupW, layout.conflictPopupH);

        Minecraft client = Minecraft.getMinecraft();
        int maxTextWidth = 240; // Max width matching the buttons

        // 1. Auto-scaling Title
        ITextComponent title = new net.minecraft.util.text.TextComponentTranslation("gui.chestseparators.conflict_title");
        int titleWidth = client.fontRenderer.getStringWidth(title.getFormattedText());
        float titleScale = titleWidth > maxTextWidth ? (float) maxTextWidth / titleWidth : 1.0f;

        context.pose().pushPose();
        context.pose()
                .translate(
                        layout.conflictPopupX + layout.conflictPopupW / 2.0f,
                        layout.conflictPopupY + 12 + (4 * (1 - titleScale)), 0.0F);
        context.pose().scale(titleScale, titleScale, 1.0F);
        context.drawCenteredString(client.fontRenderer, title, 0, 0, 0xFFFF5555);
        context.pose().popPose();

        // 2. Auto-scaling Description
        ITextComponent desc = new net.minecraft.util.text.TextComponentTranslation("gui.chestseparators.conflict_desc");
        int descWidth = client.fontRenderer.getStringWidth(desc.getFormattedText());
        float descScale = descWidth > maxTextWidth ? (float) maxTextWidth / descWidth : 1.0f;

        int scaledDescWidth = (int) (descWidth * descScale);
        int descX = layout.conflictPopupX + (layout.conflictPopupW - scaledDescWidth) / 2;

        context.pose().pushPose();
        context.pose().translate(descX, layout.conflictPopupY + 30 + (4 * (1 - descScale)), 0.0F);
        context.pose().scale(descScale, descScale, 1.0F);
        context.drawString(client.fontRenderer, desc, 0, 0, isDark ? 0xFFDDDDDD : 0xFF333333, false);
        context.pose().popPose();

        for (CustomWidget w : popupWidgets) {
            w.render(context, mouseX, mouseY, delta);
        }
    }

    private void renderSelectionOverlay(GuiGraphics context) {
        int guiX = layout.guiX;
        int guiY = layout.guiY;

        for (Slot slot : editor.accessor.getHandler().inventorySlots) {
            if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;

            // selectedSlots is keyed by slotKey (player slots carry PLAYER_KEY_OFFSET), so match on that —
            // using the raw index would fail to highlight selected inventory slots.
            if (session.selectedSlots.contains(ChestSeparatorsEditor.slotKey(slot))) {
                context.fill(guiX + slot.xPos, guiY + slot.yPos, guiX + slot.xPos + 16, guiY + slot.yPos + 16, 0x7733FF33);
            }
        }
    }

    // --- MIGRATED RENDERING LOGIC ---

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (session.hasSelectionConflict) return false;

        if (session.isPreviewing && session.previewItems != null) {
            // Allow manual scroll when the cursor is over the preview panel or the chest grid.
            boolean overPanel = mouseX >= layout.listX && mouseX <= layout.listX + layout.listW;
            boolean overChest = mouseX >= layout.guiX && mouseX <= layout.guiX + layout.bgWidth;

            if (overPanel || overChest) {
                float scrollSpeed = 18f;
                int listViewH = layout.listH - 38;
                float maxListScroll = Math.max(0, session.previewItems.size() * 18 - listViewH);

                session.listScrollY = MathHelper.clamp(
                        session.listScrollY - (float) (verticalAmount * scrollSpeed), 0, maxListScroll);
                session.userOverrodePreviewScroll = true;
                return true;
            }
        }
        return false;
    }

    void transitionToEditFilter() {
        editor.prepareFilterMenu();

        // Snapshot the just-loaded state so we can detect unsaved changes later.
        session.originalItemsSnapshot = new ArrayList<>(session.currentAllowedItems);
        session.originalRuleManual = session.ruleManual;
        session.originalRuleShift = session.ruleShift;
        session.originalRuleHopper = session.ruleHopper;
        session.isUnsavedPopupOpen = false;

        editor.toggleState(EditorState.EDIT_FILTER);
    }
}
