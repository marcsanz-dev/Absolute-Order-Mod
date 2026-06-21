package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.UiColors;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ActionIconButtonWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
    public void renderWhitelistPreviewPanel(DrawContext context, int mouseX, int mouseY) {
        previewPanelRenderer.renderWhitelistPreviewPanel(context, mouseX, mouseY);
    }

    @Override
    public void init() {
        super.init();
        popupWidgets.clear();
        buildPopupWidgets();
        selectSlotsWidgets.clear();
        buildSelectSlotsWidgets();
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
                Text.translatable("button.chestseparators.edit_filter").getString(),
                ModTextures.BTN_WHITELIST,
                () -> {
                    editor.playClickSound(1.0f);
                    if (session.selectedSlots.isEmpty()) {
                        editor.showStatus(Text.translatable("message.chestseparators.select_first"), Formatting.RED);
                        return;
                    }
                    boolean conflictFound = false;
                    var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
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
                Text.translatable("tooltip.chestseparators.desc.edit_filter").getString();
        widgets.add(btnEditFilter);

        WideButtonWidget btnClearAll = new WideButtonWidget(
                sx,
                sy + 24,
                btnW,
                bH,
                Text.translatable("button.chestseparators.delete_all_filters").getString(),
                ModTextures.ICON_TRASH,
                () -> {
                    btnClearAllClickTime = System.currentTimeMillis();
                    var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                    if (whitelists != null && !whitelists.isEmpty()) {
                        ChestConfigManager.getInstance().saveWhitelistSnapshot();
                        whitelists.clear();
                        editor.saveSmart();
                        editor.sendWhitelistToServer();

                        editor.syncClientInventoryWhitelists(whitelists);

                        editor.showStatus(
                                Text.translatable("message.chestseparators.all_filters_deleted"), Formatting.RED);
                        editor.playClickSound(0.8f);
                    }
                });
        btnClearAll.tooltipText = Text.translatable("tooltip.chestseparators.desc.delete_all_filters")
                .getString();
        btnClearAll.keepNormalTextColor = true;
        widgets.add(btnClearAll);

        // --- Block 2: Selection Tools ---
        WideButtonWidget btnAreaSelect = new WideButtonWidget(
                sx,
                sy + 53,
                btnW,
                bH,
                Text.translatable("button.chestseparators.area_select").getString(),
                ModTextures.ICON_AREA_SELECT,
                () -> {
                    session.wlToolMode = 0;
                    editor.playClickSound(1.2f);
                });
        btnAreaSelect.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.area_select").getString();
        widgets.add(btnAreaSelect);

        WideButtonWidget btnTraceSelect = new WideButtonWidget(
                sx,
                sy + 77,
                btnW,
                bH,
                Text.translatable("button.chestseparators.trace_select").getString(),
                ModTextures.ICON_TRACE_SELECT,
                () -> {
                    session.wlToolMode = 1;
                    editor.playClickSound(1.2f);
                });
        btnTraceSelect.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.trace_select").getString();
        widgets.add(btnTraceSelect);

        WideButtonWidget btnClearSelect = new WideButtonWidget(
                sx,
                sy + 101,
                btnW,
                bH,
                Text.translatable("button.chestseparators.clear_selection").getString(),
                ModTextures.ICON_DELETE,
                () -> {
                    btnClearSelectClickTime = System.currentTimeMillis();
                    session.selectedSlots.clear();
                    editor.playClickSound(0.8f);
                });
        btnClearSelect.tooltipText = Text.translatable("tooltip.chestseparators.desc.clear_selection")
                .getString();
        btnClearSelect.keepNormalTextColor = true;
        widgets.add(btnClearSelect);

        // --- Block 3: Global Actions ---
        WideButtonWidget btnCopy = new WideButtonWidget(
                sx,
                sy + 130,
                btnW,
                bH,
                Text.translatable("button.chestseparators.copy_filters").getString(),
                ModTextures.ICON_COPY,
                () -> {
                    btnCopyClickTime = System.currentTimeMillis();
                    ChestConfigManager.getInstance().copyWhitelistsToClipboard();
                    editor.showStatus(Text.translatable("message.chestseparators.filters_copied"), Formatting.GRAY);
                    editor.playClickSound(1.0f);
                });
        btnCopy.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.copy_filters").getString();
        btnCopy.keepNormalTextColor = true;
        widgets.add(btnCopy);

        WideButtonWidget btnPaste = new WideButtonWidget(
                sx,
                sy + 154,
                btnW,
                bH,
                Text.translatable("button.chestseparators.paste_filters").getString(),
                ModTextures.ICON_PASTE,
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
                                Text.translatable("message.chestseparators.filters_pasted"), Formatting.GREEN);
                        editor.playClickSound(1.0f);
                    }
                });
        btnPaste.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.paste_filters").getString();
        btnPaste.keepNormalTextColor = true;
        widgets.add(btnPaste);

        int halfW = (btnW - 4) / 2;
        WideButtonWidget btnUndo = new WideButtonWidget(
                sx,
                sy + 178,
                halfW,
                bH,
                Text.translatable("button.chestseparators.undo").getString(),
                ModTextures.ICON_UNDO,
                () -> {
                    btnUndoClickTime = System.currentTimeMillis();
                    editor.applyUndoRedo(ChestConfigManager.getInstance().undo(), false);
                });
        btnUndo.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.undo").getString();
        btnUndo.keepNormalTextColor = true;
        widgets.add(btnUndo);

        WideButtonWidget btnRedo = new WideButtonWidget(
                sx + halfW + 4,
                sy + 178,
                halfW,
                bH,
                Text.translatable("button.chestseparators.redo").getString(),
                ModTextures.ICON_REDO,
                () -> {
                    btnRedoClickTime = System.currentTimeMillis();
                    editor.applyUndoRedo(ChestConfigManager.getInstance().redo(), true);
                });
        btnRedo.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.redo").getString();
        btnRedo.keepNormalTextColor = true;
        widgets.add(btnRedo);
    }

    private void buildPopupWidgets() {
        ActionIconButtonWidget btnOverwrite = new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 45,
                240,
                16,
                "1. "
                        + Text.translatable("button.chestseparators.conflict.overwrite")
                                .getString(),
                null,
                0xFF852D2D,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    session.hasSelectionConflict = false;
                    session.selectedGroupId = UUID.randomUUID();
                    editor.playClickSound(1.0f);
                    transitionToEditFilter();
                });
        btnOverwrite.tooltipText = Text.translatable("tooltip.chestseparators.desc.conflict.overwrite")
                .getString();
        popupWidgets.add(btnOverwrite);

        ActionIconButtonWidget btnDeselect = new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 63,
                240,
                16,
                "2. "
                        + Text.translatable("button.chestseparators.conflict.deselect")
                                .getString(),
                null,
                0xFF2D852D,
                () -> {
                    var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                    if (whitelists != null) session.selectedSlots.removeIf(whitelists::containsKey);
                    session.hasSelectionConflict = false;
                    editor.playClickSound(1.0f);
                    if (!session.selectedSlots.isEmpty()) {
                        transitionToEditFilter();
                    } else {
                        editor.showStatus(Text.translatable("message.chestseparators.empty_selection"), Formatting.RED);
                    }
                });
        btnDeselect.tooltipText = Text.translatable("tooltip.chestseparators.desc.conflict.deselect")
                .getString();
        popupWidgets.add(btnDeselect);

        ActionIconButtonWidget btnCancel = new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 81,
                240,
                16,
                "3. " + Text.translatable("button.chestseparators.cancel").getString(),
                null,
                0xFF444444,
                () -> {
                    session.hasSelectionConflict = false;
                    editor.playClickSound(0.8f);
                });
        btnCancel.tooltipText = Text.translatable("tooltip.chestseparators.desc.conflict.cancel")
                .getString();
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
                Text.translatable("button.chestseparators.confirm").getString(),
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
                Text.translatable("button.chestseparators.cancel").getString(),
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
                if (slot != null && ChestSeparatorsEditor.isEditableSlot(slot)) {
                    session.dragCurrentSlot = slot;

                    // Trace mode: immediately commit each slot as the cursor moves.
                    if (session.wlToolMode == 1) {
                        if (session.isSelecting) session.selectedSlots.add(slot.getIndex());
                        else session.selectedSlots.remove(slot.getIndex());
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
            // Area mode: commit the rectangular selection on mouse release.
            if (session.wlToolMode == 0 && session.dragStartSlot != null && session.dragCurrentSlot != null) {
                int sRow = session.dragStartSlot.getIndex() / 9;
                int sCol = session.dragStartSlot.getIndex() % 9;
                int cRow = session.dragCurrentSlot.getIndex() / 9;
                int cCol = session.dragCurrentSlot.getIndex() % 9;
                int minRow = Math.min(sRow, cRow);
                int maxRow = Math.max(sRow, cRow);
                int minCol = Math.min(sCol, cCol);
                int maxCol = Math.max(sCol, cCol);

                for (Slot s : editor.accessor.getHandler().slots) {
                    if (!ChestSeparatorsEditor.isEditableSlot(s)) continue;
                    int r = s.getIndex() / 9;
                    int c = s.getIndex() % 9;
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        if (session.isSelecting) session.selectedSlots.add(s.getIndex());
                        else session.selectedSlots.remove(s.getIndex());
                    }
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

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

    private void renderConflictPopup(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0xAA000000);

        boolean isDark = GlobalChestConfig.instance.darkMode;
        context.fill(
                layout.conflictPopupX,
                layout.conflictPopupY,
                layout.conflictPopupX + layout.conflictPopupW,
                layout.conflictPopupY + layout.conflictPopupH,
                isDark ? UiColors.SURFACE_DARK : UiColors.SURFACE_LIGHT);
        drawDarkBevel(
                context,
                layout.conflictPopupX,
                layout.conflictPopupY,
                layout.conflictPopupW,
                layout.conflictPopupH,
                false);

        MinecraftClient client = MinecraftClient.getInstance();
        int maxTextWidth = 240; // Max width matching the buttons

        // 1. Auto-scaling Title
        Text title = Text.translatable("gui.chestseparators.conflict_title");
        int titleWidth = client.textRenderer.getWidth(title);
        float titleScale = titleWidth > maxTextWidth ? (float) maxTextWidth / titleWidth : 1.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices()
                .translate(
                        layout.conflictPopupX + layout.conflictPopupW / 2.0f,
                        layout.conflictPopupY + 12 + (4 * (1 - titleScale)));
        context.getMatrices().scale(titleScale, titleScale);
        context.drawCenteredTextWithShadow(client.textRenderer, title, 0, 0, 0xFFFF5555);
        context.getMatrices().popMatrix();

        // 2. Auto-scaling Description
        Text desc = Text.translatable("gui.chestseparators.conflict_desc");
        int descWidth = client.textRenderer.getWidth(desc);
        float descScale = descWidth > maxTextWidth ? (float) maxTextWidth / descWidth : 1.0f;

        int scaledDescWidth = (int) (descWidth * descScale);
        int descX = layout.conflictPopupX + (layout.conflictPopupW - scaledDescWidth) / 2;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(descX, layout.conflictPopupY + 30 + (4 * (1 - descScale)));
        context.getMatrices().scale(descScale, descScale);
        context.drawText(client.textRenderer, desc, 0, 0, isDark ? 0xFFDDDDDD : 0xFF333333, false);
        context.getMatrices().popMatrix();

        for (CustomWidget w : popupWidgets) {
            w.render(context, mouseX, mouseY, delta);
        }
    }

    private void renderSelectionOverlay(DrawContext context) {
        int guiX = layout.guiX;
        int guiY = layout.guiY;

        for (Slot slot : editor.accessor.getHandler().slots) {
            if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;

            if (session.selectedSlots.contains(slot.getIndex())) {
                context.fill(guiX + slot.x, guiY + slot.y, guiX + slot.x + 16, guiY + slot.y + 16, 0x7733FF33);
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
