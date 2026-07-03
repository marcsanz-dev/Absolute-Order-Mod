package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ActionIconButtonWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ScreenViewGroups extends AbstractEditorScreen {

    // Lista exclusiva para los botones del popup modal
    private final List<CustomWidget> popupWidgets = new ArrayList<>();
    private final List<CustomWidget> selectSlotsWidgets = new ArrayList<>();

    private static final int[] GROUP_PALETTE = {
            0x99E53935, 0x99F57C00, 0x99FBC02D, 0x997CB342,
            0x99388E3C, 0x9900897B, 0x9900ACC1, 0x991E88E5,
            0x993949AB, 0x998E24AA, 0x99D81B60, 0x99795548,
            0x99D50000, 0x99C51162, 0x99AA00FF, 0x996200EA,
            0x99304FFE, 0x992962FF, 0x9900B8D4, 0x9900BFA5,
            0x9900C853, 0x9964DD17, 0x99AEEA00, 0x99FFD600,
            0x99FFAB00, 0x99FF6D00, 0x99DD2C00, 0x995D4037,
            0x99827717, 0x99E65100, 0x99F4511E, 0x99C0CA33
    };

    private final Map<UUID, Integer> stableGroupColors = new HashMap<>();

    // --- NUEVAS VARIABLES PARA ANIMACIÓN DE BOTONES ---
    private long btnClearAllClickTime = 0;
    private long btnClearSelectClickTime = 0;
    private long btnCopyClickTime = 0;
    private long btnPasteClickTime = 0;
    private long btnUndoClickTime = 0;
    private long btnRedoClickTime = 0;

    public ScreenViewGroups(ChestSeparatorsEditor editor) {
        super(editor);
    }

    @Override
    public void init() {
        super.init(); // Limpia y construye los 'widgets' principales
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
        WideButtonWidget btnEditFilter = new WideButtonWidget(sx, sy, btnW, bH, Text.translatable("button.chestseparators.edit_filter").getString(), ModTextures.BTN_WHITELIST, () -> {
            editor.playClickSound(1.0f);
            if (session.selectedSlots.isEmpty()) {
                editor.showStatus(Text.translatable("message.chestseparators.select_first"), Formatting.RED);
                return;
            }
            boolean conflictFound = false;
            var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
            if (whitelists != null) {
                for (int slotIdx : session.selectedSlots) {
                    if (whitelists.containsKey(slotIdx)) { conflictFound = true; break; }
                }
            }
            if (conflictFound) session.hasSelectionConflict = true;
            else {
                session.selectedGroupId = UUID.randomUUID();
                transitionToEditFilter();
            }
        });
        btnEditFilter.tooltipText = Text.translatable("tooltip.chestseparators.desc.edit_filter").getString();
        widgets.add(btnEditFilter);

        WideButtonWidget btnClearAll = new WideButtonWidget(sx, sy + 24, btnW, bH, Text.translatable("button.chestseparators.delete_all_filters").getString(), ModTextures.ICON_TRASH, () -> {
            btnClearAllClickTime = System.currentTimeMillis();
            var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
            if (whitelists != null && !whitelists.isEmpty()) {
                ChestConfigManager.getInstance().saveWhitelistSnapshot();
                whitelists.clear();
                editor.saveSmart();
                editor.sendWhitelistToServer();

                editor.syncClientInventoryWhitelists(whitelists);

                editor.showStatus(Text.translatable("message.chestseparators.all_filters_deleted"), Formatting.RED);
                editor.playClickSound(0.8f);
            }
        });
        btnClearAll.tooltipText = Text.translatable("tooltip.chestseparators.desc.delete_all_filters").getString();
        btnClearAll.keepNormalTextColor = true;
        widgets.add(btnClearAll);

        // --- Block 2: Selection Tools ---
        WideButtonWidget btnAreaSelect = new WideButtonWidget(sx, sy + 53, btnW, bH, Text.translatable("button.chestseparators.area_select").getString(), ModTextures.ICON_AREA_SELECT, () -> {
            session.wlToolMode = 0; editor.playClickSound(1.2f);
        });
        btnAreaSelect.tooltipText = Text.translatable("tooltip.chestseparators.desc.area_select").getString();
        widgets.add(btnAreaSelect);

        WideButtonWidget btnTraceSelect = new WideButtonWidget(sx, sy + 77, btnW, bH, Text.translatable("button.chestseparators.trace_select").getString(), ModTextures.ICON_TRACE_SELECT, () -> {
            session.wlToolMode = 1; editor.playClickSound(1.2f);
        });
        btnTraceSelect.tooltipText = Text.translatable("tooltip.chestseparators.desc.trace_select").getString();
        widgets.add(btnTraceSelect);

        WideButtonWidget btnClearSelect = new WideButtonWidget(sx, sy + 101, btnW, bH, Text.translatable("button.chestseparators.clear_selection").getString(), ModTextures.ICON_DELETE, () -> {
            btnClearSelectClickTime = System.currentTimeMillis();
            session.selectedSlots.clear(); editor.playClickSound(0.8f);
        });
        btnClearSelect.tooltipText = Text.translatable("tooltip.chestseparators.desc.clear_selection").getString();
        btnClearSelect.keepNormalTextColor = true;
        widgets.add(btnClearSelect);

        // --- Block 3: Global Actions ---
        WideButtonWidget btnCopy = new WideButtonWidget(sx, sy + 130, btnW, bH, Text.translatable("button.chestseparators.copy_filters").getString(), ModTextures.ICON_COPY, () -> {
            btnCopyClickTime = System.currentTimeMillis();
            ChestConfigManager.getInstance().copyWhitelistsToClipboard();
            editor.showStatus(Text.translatable("message.chestseparators.filters_copied"), Formatting.GRAY);
            editor.playClickSound(1.0f);
        });
        btnCopy.tooltipText = Text.translatable("tooltip.chestseparators.desc.copy_filters").getString();
        btnCopy.keepNormalTextColor = true;
        widgets.add(btnCopy);

        WideButtonWidget btnPaste = new WideButtonWidget(sx, sy + 154, btnW, bH, Text.translatable("button.chestseparators.paste_filters").getString(), ModTextures.ICON_PASTE, () -> {
            btnPasteClickTime = System.currentTimeMillis();

            if (ChestConfigManager.getInstance().hasWhitelistClipboardData()) {
                ChestConfigManager.getInstance().saveWhitelistSnapshot();
                ChestConfigManager.getInstance().pasteWhitelistsFromClipboard();
                editor.saveSmart();
                editor.sendWhitelistToServer();

                editor.syncClientInventoryWhitelists(ChestConfigManager.getInstance().getCurrentWhitelists());

                editor.showStatus(Text.translatable("message.chestseparators.filters_pasted"), Formatting.GREEN);
                editor.playClickSound(1.0f);
            }
        });
        btnPaste.tooltipText = Text.translatable("tooltip.chestseparators.desc.paste_filters").getString();
        btnPaste.keepNormalTextColor = true;
        widgets.add(btnPaste);

        // --- BOTÓN UNDO ACTUALIZADO ---
        int halfW = (btnW - 4) / 2;
        WideButtonWidget btnUndo = new WideButtonWidget(sx, sy + 178, halfW, bH, Text.translatable("button.chestseparators.undo").getString(), ModTextures.ICON_UNDO, () -> {
            btnUndoClickTime = System.currentTimeMillis();
            if (ChestConfigManager.getInstance().canUndoWhitelist()) {
                ChestConfigManager.getInstance().undoWhitelist();

                // --- LA CURA: Sincronizamos el nuevo estado tras el Undo ---
                var currentWhitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                editor.saveSmart();
                editor.sendWhitelistToServer();
                editor.syncClientInventoryWhitelists(currentWhitelists);

                editor.playClickSound(0.8f);
            }
        });
        btnUndo.tooltipText = Text.translatable("tooltip.chestseparators.desc.undo").getString();
        btnUndo.keepNormalTextColor = true;
        widgets.add(btnUndo);

        // --- BOTÓN REDO ACTUALIZADO ---
        WideButtonWidget btnRedo = new WideButtonWidget(sx + halfW + 4, sy + 178, halfW, bH, Text.translatable("button.chestseparators.redo").getString(), ModTextures.ICON_REDO, () -> {
            btnRedoClickTime = System.currentTimeMillis();
            if (ChestConfigManager.getInstance().canRedoWhitelist()) {
                ChestConfigManager.getInstance().redoWhitelist();

                // --- LA CURA: Sincronizamos el nuevo estado tras el Redo ---
                var currentWhitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                editor.saveSmart();
                editor.sendWhitelistToServer();
                editor.syncClientInventoryWhitelists(currentWhitelists);

                editor.playClickSound(0.8f);
            }
        });
        btnRedo.tooltipText = Text.translatable("tooltip.chestseparators.desc.redo").getString();
        btnRedo.keepNormalTextColor = true;
        widgets.add(btnRedo);
    }

    private void buildPopupWidgets() {
        ActionIconButtonWidget btnOverwrite = new ActionIconButtonWidget(layout.conflictPopupX + 10, layout.conflictPopupY + 45, 240, 16, "1. " + Text.translatable("button.chestseparators.conflict.overwrite").getString(), null, 0xFF852D2D, () -> {
            ChestConfigManager.getInstance().saveWhitelistSnapshot();
            session.hasSelectionConflict = false;
            session.selectedGroupId = UUID.randomUUID();
            editor.playClickSound(1.0f);
            transitionToEditFilter();
        });
        btnOverwrite.tooltipText = Text.translatable("tooltip.chestseparators.desc.conflict.overwrite").getString();
        popupWidgets.add(btnOverwrite);

        ActionIconButtonWidget btnDeselect = new ActionIconButtonWidget(layout.conflictPopupX + 10, layout.conflictPopupY + 63, 240, 16, "2. " + Text.translatable("button.chestseparators.conflict.deselect").getString(), null, 0xFF2D852D, () -> {
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
        btnDeselect.tooltipText = Text.translatable("tooltip.chestseparators.desc.conflict.deselect").getString();
        popupWidgets.add(btnDeselect);

        ActionIconButtonWidget btnCancel = new ActionIconButtonWidget(layout.conflictPopupX + 10, layout.conflictPopupY + 81, 240, 16, "3. " + Text.translatable("button.chestseparators.cancel").getString(), null, 0xFF444444, () -> {
            session.hasSelectionConflict = false;
            editor.playClickSound(0.8f);
        });
        btnCancel.tooltipText = Text.translatable("tooltip.chestseparators.desc.conflict.cancel").getString();
        popupWidgets.add(btnCancel);
    }

    private void buildSelectSlotsWidgets() {
        int btnX = layout.guiX + layout.bgWidth + 4;
        int btnY = layout.guiY;

        selectSlotsWidgets.add(new ActionIconButtonWidget(btnX, btnY, 60, 20, Text.translatable("button.chestseparators.confirm").getString(), null, 0xFF2D852D, () -> {
            if (!session.selectedSlots.isEmpty()) {
                transitionToEditFilter();
            }
            editor.playClickSound(1.0f);
        }));

        selectSlotsWidgets.add(new ActionIconButtonWidget(btnX, btnY + 25, 60, 20, Text.translatable("button.chestseparators.cancel").getString(), null, 0xFF852D2D, () -> {
            editor.toggleState(EditorState.VIEW_GROUPS);
            editor.playClickSound(1.0f);
        }));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. Interceptar clics del Popup Modal
        if (session.hasSelectionConflict) {
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget widget : popupWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return true; // Bloquea los clics en el fondo
        }

        // 2. Interceptar clics de los botones de Confirmar/Cancelar
        if (session.currentState == EditorState.SELECT_SLOTS) {
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget widget : selectSlotsWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }
        // 3. Botones del panel lateral derecho (Solo en VIEW_GROUPS)
        else if (session.currentState == EditorState.VIEW_GROUPS) {
            // CORRECCIÓN: Usamos isMouseOver y setFocused para evitar el error de mappings
            if (editor.whitelistSearchBox != null) {
                if (editor.whitelistSearchBox.isMouseOver(mouseX, mouseY)) {
                    if (button == 0) editor.whitelistSearchBox.setFocused(true);
                    return true;
                } else if (button == 0) {
                    editor.whitelistSearchBox.setFocused(false);
                }
            }
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
        }

        // 4. LÓGICA DE SELECCIÓN DE SLOTS EN EL COFRE
        if (button == 0 && (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS)) {
            Slot slot = editor.accessor.getFocusedSlot();
            if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
                java.util.UUID groupId = editor.geometry.getGroupIdForSlot(slot.getIndex());
                long now = System.currentTimeMillis();
                boolean isDoubleClick = (slot.getIndex() == session.lastClickedSlotIndex && (now - session.lastSlotClickTime) < 300);

                boolean isShiftDown = org.lwjgl.glfw.GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                        org.lwjgl.glfw.GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

                if (session.currentState == EditorState.VIEW_GROUPS && groupId != null && session.selectedSlots.isEmpty() && !isShiftDown && !isDoubleClick) {
                    session.selectedGroupId = groupId;
                    session.selectedSlots.clear();
                    var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
                    if (whitelists != null) {
                        for (java.util.Map.Entry<Integer, SlotWhitelist> entry : whitelists.entrySet()) {
                            if (groupId.equals(entry.getValue().groupId())) {
                                session.selectedSlots.add(entry.getKey());
                            }
                        }
                    }
                    transitionToEditFilter();
                    editor.playClickSound(1.0f);
                    return true;
                }
                else if (isDoubleClick) {
                    int maxSlots = editor.geometry.getContainerSlotCount();
                    java.util.Set<Integer> contiguous = ChestConfigManager.getInstance().getContiguousSlots(slot.getIndex(), maxSlots);

                    if (session.isSelecting) session.selectedSlots.addAll(contiguous);
                    else session.selectedSlots.removeAll(contiguous);

                    session.lastClickedSlotIndex = slot.getIndex();
                    session.lastSlotClickTime = now;
                    editor.playClickSound(1.2f);
                    return true;
                }
                else {
                    if (session.selectedSlots.contains(slot.getIndex())) {
                        session.selectedSlots.remove(slot.getIndex());
                        session.isSelecting = false;
                    } else {
                        session.selectedSlots.add(slot.getIndex());
                        session.isSelecting = true;
                    }

                    session.isDraggingLine = true;
                    session.dragStartSlot = slot;
                    session.dragCurrentSlot = slot;

                    session.lastClickedSlotIndex = slot.getIndex();
                    session.lastSlotClickTime = now;
                    editor.playClickSound(1.2f);
                    return true;
                }
            }
        }
        // --- CLICK OUTSIDE TO CLOSE ---
        if (button == 0 && !session.hasSelectionConflict && GlobalChestConfig.instance.closeOnClickOutside) {
            boolean isInsideMain = mouseX >= layout.guiX && mouseX <= layout.guiX + layout.bgWidth && mouseY >= layout.guiY && mouseY <= layout.guiY + layout.bgHeight;
            // Zona segura: Desde el primer botón hasta el final físico del último bloque de botones (sy + 178 + altura del botón)
            boolean isInsideRight = mouseX >= layout.rightX && mouseX <= layout.rightX + layout.btnW && mouseY >= layout.mainY && mouseY <= layout.mainY + 178 + layout.bH;

            // Comprobamos si el clic fue dentro de la preview de whitelist (si se está mostrando)
            boolean isInsideLeft = false;
            if (GlobalChestConfig.instance.showLeftPanel || io.github.marcsanzdev.chestseparators.event.KeyInputHandler.isModifierPressed()) {
                isInsideLeft = mouseX >= layout.listX && mouseX <= layout.listX + layout.listW && mouseY >= layout.listY && mouseY <= layout.listY + layout.listH;
            }

            if (!isInsideMain && !isInsideRight && !isInsideLeft) {
                if (session.currentState == EditorState.SELECT_SLOTS) {
                    editor.toggleState(EditorState.VIEW_GROUPS); // Si estaba seleccionando, cancela la selección
                } else {
                    editor.toggleState(EditorState.HIDDEN); // Si estaba viendo, cierra el mod
                }
                editor.playClickSound(1.0f);
                return true;
            }
        }

        return false; // Este es tu return final original
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (session.hasSelectionConflict) return true;

        if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
            if (session.isDraggingLine) {
                Slot slot = editor.accessor.getFocusedSlot();
                if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
                    session.dragCurrentSlot = slot;

                    // Selección instantánea si usamos la herramienta de Trazado (Trace)
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
        if (button == 0 && session.isDraggingLine && (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS)) {
            // Confirmar la selección de Área al soltar el ratón
            if (session.wlToolMode == 0 && session.dragStartSlot != null && session.dragCurrentSlot != null) {
                int sRow = session.dragStartSlot.getIndex() / 9; int sCol = session.dragStartSlot.getIndex() % 9;
                int cRow = session.dragCurrentSlot.getIndex() / 9; int cCol = session.dragCurrentSlot.getIndex() % 9;
                int minRow = Math.min(sRow, cRow); int maxRow = Math.max(sRow, cRow);
                int minCol = Math.min(sCol, cCol); int maxCol = Math.max(sCol, cCol);

                for (Slot s : editor.accessor.getHandler().slots) {
                    if (s.inventory instanceof PlayerInventory) continue;
                    int r = s.getIndex() / 9; int c = s.getIndex() % 9;
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
            // Animación de botones instantáneos (< 150ms)
            widgets.get(1).isActive = (System.currentTimeMillis() - btnClearAllClickTime < 150); // Delete All Filters
            widgets.get(4).isActive = (System.currentTimeMillis() - btnClearSelectClickTime < 150); // Clear Selection
            widgets.get(5).isActive = (System.currentTimeMillis() - btnCopyClickTime < 150); // Copy

            widgets.get(6).isActive = (System.currentTimeMillis() - btnPasteClickTime < 150); // Paste
            widgets.get(6).isDisabled = !ChestConfigManager.getInstance().hasWhitelistClipboardData();

            widgets.get(7).isActive = (System.currentTimeMillis() - btnUndoClickTime < 150); // Undo
            widgets.get(7).isDisabled = !ChestConfigManager.getInstance().canUndoWhitelist();

            widgets.get(8).isActive = (System.currentTimeMillis() - btnRedoClickTime < 150); // Redo
            widgets.get(8).isDisabled = !ChestConfigManager.getInstance().canRedoWhitelist();

            // Botones de herramientas (Toggle fijo)
            widgets.get(2).isActive = (session.wlToolMode == 0); // Area Select
            widgets.get(3).isActive = (session.wlToolMode == 1); // Trace Select
        }

        // --- TRUCO MAGISTRAL ---
        // Si hay conflicto, engañamos a los métodos de renderizado de fondo
        // enviando coordenadas de ratón falsas (-1, -1) para que no detecten "hovers" ni dibujen tooltips.
        int bgMouseX = session.hasSelectionConflict ? -1 : mouseX;
        int bgMouseY = session.hasSelectionConflict ? -1 : mouseY;

        renderWhitelistPreviewPanel(context, bgMouseX, bgMouseY);
        renderWhitelistGroups(context, bgMouseX, bgMouseY);

        if (session.currentState == EditorState.SELECT_SLOTS) {
            renderSelectionOverlay(context);
            // Renderizamos los botones de confirmación del SELECT_SLOTS (sus tooltips se pintan solos)
            for (CustomWidget w : selectSlotsWidgets) w.render(context, bgMouseX, bgMouseY, delta);
        }

        // Renderizamos los botones principales (y sus tooltips) AL FINAL para que queden por encima del cofre
        if (session.currentState == EditorState.VIEW_GROUPS) {
            super.render(context, bgMouseX, bgMouseY, delta);
        }

        if (session.hasSelectionConflict) {
            // Al popup SÍ le pasamos las coordenadas reales para que podamos clickar los botones
            renderConflictPopup(context, mouseX, mouseY, delta);
        }
    }

    private void renderConflictPopup(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0xAA000000);

        boolean isDark = GlobalChestConfig.instance.darkMode;
        context.fill(layout.conflictPopupX, layout.conflictPopupY, layout.conflictPopupX + layout.conflictPopupW, layout.conflictPopupY + layout.conflictPopupH, isDark ? 0xFF212121 : 0xFFC6C6C6);
        drawDarkBevel(context, layout.conflictPopupX, layout.conflictPopupY, layout.conflictPopupW, layout.conflictPopupH, false);

        MinecraftClient client = MinecraftClient.getInstance();
        int maxTextWidth = 240; // Max width matching the buttons

        // 1. Auto-scaling Title
        Text title = Text.translatable("gui.chestseparators.conflict_title");
        int titleWidth = client.textRenderer.getWidth(title);
        float titleScale = titleWidth > maxTextWidth ? (float) maxTextWidth / titleWidth : 1.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(layout.conflictPopupX + layout.conflictPopupW / 2.0f, layout.conflictPopupY + 12 + (4 * (1 - titleScale)));
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
            if (slot.inventory instanceof PlayerInventory) continue;

            if (session.selectedSlots.contains(slot.getIndex())) {
                context.fill(guiX + slot.x, guiY + slot.y, guiX + slot.x + 16, guiY + slot.y + 16, 0x7733FF33);
            }
        }
    }

    // --- MIGRATED RENDERING LOGIC ---

    public void renderWhitelistPreviewPanel(DrawContext context, int mouseX, int mouseY) {
        int listW = layout.listW;
        int listH = layout.listH;
        int listX = layout.listX;
        int listY = layout.listY;

        int guiX = layout.guiX;
        int guiY = layout.guiY;

        boolean isDark = GlobalChestConfig.instance.darkMode;

        context.fill(listX, listY, listX + listW, listY + listH, isDark ? 0xFF212121 : 0xFFC6C6C6);
        drawDarkBevel(context, listX, listY, listW, listH, false);

        Slot hoveredSlot = null;
        for (Slot slot : editor.accessor.getHandler().slots) {
            if (!(slot.inventory instanceof PlayerInventory) && editor.isHovering(guiX + slot.x - 1, guiY + slot.y - 1, 18, 18, mouseX, mouseY)) {
                hoveredSlot = slot;
                break;
            }
        }

        if (hoveredSlot != null) {
            var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
            if (whitelists != null && whitelists.containsKey(hoveredSlot.getIndex())) {
                SlotWhitelist wl = whitelists.get(hoveredSlot.getIndex());
                UUID currentGroupId = wl.groupId();

                if (!session.isPreviewing || !currentGroupId.equals(session.lastHoveredGroupId)) {
                    session.previewItems = new ArrayList<>(wl.allowedItems());
                    session.listScrollY = 0f;
                    session.isPreviewing = true;
                    session.lastHoveredGroupId = currentGroupId;

                    long now = System.currentTimeMillis();
                    session.previewStartTime = now;
                    session.lastPreviewTime = now;
                    session.previewScrollDirectionDown = true;
                    session.userOverrodePreviewScroll = false;
                }
            } else {
                session.isPreviewing = false;
                session.previewItems.clear();
                session.lastHoveredGroupId = null;
            }
        } else {
            if (!editor.isHovering(listX, listY, listW, listH, mouseX, mouseY)) {
                session.isPreviewing = false;
                session.previewItems.clear();
                session.lastHoveredGroupId = null;
            }
        }

        Text title = session.isPreviewing ? Text.translatable("gui.chestseparators.group_filter") : Text.translatable("gui.chestseparators.no_filter");
        int colorTitle = session.isPreviewing ? 0xFF55FF55 : 0xFFAAAAAA;
        int itemCount = session.isPreviewing ? session.previewItems.size() : 0;

        context.getMatrices().pushMatrix();
        float scaleC = 0.8f;
        context.getMatrices().scale(scaleC, scaleC);
        context.drawText(MinecraftClient.getInstance().textRenderer, title, (int)((listX + 8) / scaleC), (int)((listY + 8) / scaleC), colorTitle, false);
        context.drawText(MinecraftClient.getInstance().textRenderer, Text.translatable("gui.chestseparators.items_count", itemCount), (int)((listX + 8) / scaleC), (int)((listY + 18) / scaleC), 0xFFFFFFFF, false);
        context.getMatrices().popMatrix();

        int listViewY = listY + 32;
        int listViewH = listH - 38;
        context.fill(listX + 6, listViewY, listX + listW - 6, listViewY + listViewH, isDark ? 0xFF101010 : 0xFFE0E0E0);
        drawDarkBevel(context, listX + 6, listViewY, listW - 12, listViewH, true);

        if (!session.isPreviewing) {
            Text hoverText = Text.translatable("gui.chestseparators.hover_group");
            int textX = listX + 12;
            int textY = listViewY + 10;
            int maxTextW = listW - 24; // Dejamos un margen seguro

            for (net.minecraft.text.OrderedText line : MinecraftClient.getInstance().textRenderer.wrapLines(hoverText, maxTextW)) {
                context.drawText(MinecraftClient.getInstance().textRenderer, line, textX, textY, 0xFF666666, false);
                textY += MinecraftClient.getInstance().textRenderer.fontHeight + 2; // Salto de línea
            }
            return;
        }

        int totalListItems = session.previewItems.size();
        int totalListHeight = totalListItems * 18;
        float maxListScroll = Math.max(0, totalListHeight - listViewH);

        long now = System.currentTimeMillis();
        float dt = (now - session.lastPreviewTime) / 1000f;
        session.lastPreviewTime = now;

        if (!session.userOverrodePreviewScroll && maxListScroll > 0 && (now - session.previewStartTime > 1000)) {
            if (GlobalChestConfig.instance.enablePreviewAnimation) {
                float speed = 25f;
                if (session.previewScrollDirectionDown) {
                    session.listScrollY += speed * dt;
                    if (session.listScrollY >= maxListScroll) {
                        session.listScrollY = maxListScroll;
                        session.previewScrollDirectionDown = false;
                        session.previewStartTime = now;
                    }
                } else {
                    session.listScrollY -= speed * dt;
                    if (session.listScrollY <= 0) {
                        session.listScrollY = 0;
                        session.previewScrollDirectionDown = true;
                        session.previewStartTime = now;
                    }
                }
            }
        }

        session.listScrollY = MathHelper.clamp(session.listScrollY, 0, maxListScroll);

        context.enableScissor(listX + 6, listViewY + 1, listX + listW - 14, listViewY + listViewH - 1);
        int startIndex = (int)(session.listScrollY / 18);
        int visibleCount = (listViewH / 18) + 2;

        if (session.previewItems.isEmpty()) {
            Text emptyText = Text.translatable("gui.chestseparators.empty_whitelist");
            int textX = listX + 12;
            int textY = listViewY + 10;
            int maxTextW = listW - 24;

            for (net.minecraft.text.OrderedText line : MinecraftClient.getInstance().textRenderer.wrapLines(emptyText, maxTextW)) {
                context.drawText(MinecraftClient.getInstance().textRenderer, line, textX, textY, 0xFF888888, false);
                textY += MinecraftClient.getInstance().textRenderer.fontHeight + 2;
            }
        }

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;

            String itemIdStr = session.previewItems.get(idx);
            net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.tryParse(itemIdStr));

            int itemY = listViewY + (i * 18) - (int)(session.listScrollY % 18) + 2;
            context.drawItem(item.getDefaultStack(), listX + 8, itemY);

            String name = item.getName().getString();
            context.getMatrices().pushMatrix();
            float scale = 0.75f;
            context.getMatrices().scale(scale, scale);
            int maxNameW = (int)((listW - 35) / scale);
            if (MinecraftClient.getInstance().textRenderer.getWidth(name) > maxNameW) {
                name = MinecraftClient.getInstance().textRenderer.trimToWidth(name, maxNameW - 6) + "...";
            }
            int textColor = isDark ? 0xFFDDDDDD : 0xFF222222;
            context.drawText(MinecraftClient.getInstance().textRenderer, name, (int)((listX + 26) / scale), (int)((itemY + 6) / scale), textColor, false);
            context.getMatrices().popMatrix();
        }
        context.disableScissor();

        // --- DIBUJAR PESTAÑAS DE REGLAS A LA IZQUIERDA ---
        if (session.isPreviewing && session.lastHoveredGroupId != null) {
            SlotWhitelist activeWl = null;
            var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
            if (whitelists != null) {
                for (SlotWhitelist wl : whitelists.values()) {
                    if (wl.groupId().equals(session.lastHoveredGroupId)) {
                        activeWl = wl;
                        break;
                    }
                }
            }

            if (activeWl != null) {
                int tabX = listX - 20; // Exactamente 20px de ancho
                int startY = listY + 12;

                drawRuleTab(context, tabX, startY, ModTextures.ICON_CURSOR, activeWl.allowManual());
                drawRuleTab(context, tabX, startY + 24, ModTextures.ICON_SHIFT, activeWl.allowShift());
                drawRuleTab(context, tabX, startY + 48, ModTextures.ICON_HOPPER, activeWl.allowHopper());
            }
        }

        int lsbX = listX + listW - 13;
        int lsbH = listViewH - 1;
        context.fill(lsbX, listViewY, lsbX + 6, listViewY + lsbH, isDark ? 0xFF000000 : 0xFFAAAAAA);
        int listThumbH = maxListScroll > 0 ? Math.max(10, (int)((listViewH / (float)Math.max(1, totalListHeight)) * lsbH)) : lsbH;
        int listThumbY = maxListScroll > 0 ? listViewY + (int)((session.listScrollY / maxListScroll) * (lsbH - listThumbH)) : listViewY;
        context.fill(lsbX + 1, listThumbY + 1, lsbX + 5, listThumbY + listThumbH - 1, isDark ? 0xFF888888 : 0xFF666666);

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;
            net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.tryParse(session.previewItems.get(idx)));
            int itemY = listViewY + (i * 18) - (int)(session.listScrollY % 18) + 2;

            if (editor.isHovering(listX + 8, itemY, 16, 16, mouseX, mouseY) && mouseY >= listViewY && mouseY <= listViewY + listViewH) {
                context.drawStrokedRectangle(listX + 7, itemY - 1, 18, 18, 0xFFFFFFFF);
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, item.getName(), mouseX, mouseY);
            }
        }
    }

    private void renderWhitelistGroups(DrawContext context, int mouseX, int mouseY) {
        int guiX = layout.guiX;
        int guiY = layout.guiY;
        var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();

        Set<Integer> activeGreenSlots = new HashSet<>(session.selectedSlots);
        Set<Integer> activeRedSlots = new HashSet<>();

        boolean isPreviewArea = session.currentState == EditorState.VIEW_GROUPS && session.isDraggingLine && session.wlToolMode == 0 && session.dragStartSlot != null && session.dragCurrentSlot != null;

        if (isPreviewArea) {
            int sRow = session.dragStartSlot.getIndex() / 9;
            int sCol = session.dragStartSlot.getIndex() % 9;
            int cRow = session.dragCurrentSlot.getIndex() / 9;
            int cCol = session.dragCurrentSlot.getIndex() % 9;
            int minRow = Math.min(sRow, cRow); int maxRow = Math.max(sRow, cRow);
            int minCol = Math.min(sCol, cCol); int maxCol = Math.max(sCol, cCol);

            for (Slot slot : editor.accessor.getHandler().slots) {
                if (slot.inventory instanceof PlayerInventory) continue;
                int r = slot.getIndex() / 9; int c = slot.getIndex() % 9;
                if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                    if (session.isSelecting) {
                        activeGreenSlots.add(slot.getIndex());
                    } else {
                        activeGreenSlots.remove(slot.getIndex());
                        activeRedSlots.add(slot.getIndex());
                    }
                }
            }
        }

        for (Slot slot : editor.accessor.getHandler().slots) {
            if (slot.inventory instanceof PlayerInventory) continue;
            int idx = slot.getIndex();

            boolean hasWhitelist = whitelists != null && whitelists.containsKey(idx);
            boolean isActive = activeGreenSlots.contains(idx) || activeRedSlots.contains(idx);

            // Estos son los "cuadritos oscurecidos" funcionales.
            // Se quedan siempre para ver qué slots no tienen filtro aún.
            if (!hasWhitelist && !isActive) {
                int x = guiX + slot.x;
                int y = guiY + slot.y;
                context.fill(x + 2, y + 2, x + 14, y + 14, 0x44888888);
            }
        }

        if (whitelists != null) {
            Map<UUID, Set<Integer>> groups = new HashMap<>();
            for (Map.Entry<Integer, SlotWhitelist> entry : whitelists.entrySet()) {
                groups.computeIfAbsent(entry.getValue().groupId(), k -> new HashSet<>()).add(entry.getKey());
            }

            List<UUID> orderedGroupIds = new ArrayList<>(groups.keySet());
            orderedGroupIds.sort(UUID::compareTo);

            Set<Integer> usedColors = new HashSet<>();
            Map<UUID, Integer> assignedColors = new HashMap<>();

            // 1. Assign explicit user-painted colors first
            for (UUID groupId : orderedGroupIds) {
                int explicit = getExplicitGroupColor(groups.get(groupId));
                if (explicit != 0) {
                    assignedColors.put(groupId, explicit);
                    usedColors.add(explicit);
                }
            }

            // 2. Rescue stable colors from cache to prevent flickering/shifting
            for (UUID groupId : orderedGroupIds) {
                if (!assignedColors.containsKey(groupId) && stableGroupColors.containsKey(groupId)) {
                    int stableColor = stableGroupColors.get(groupId);
                    if (!usedColors.contains(stableColor)) {
                        assignedColors.put(groupId, stableColor);
                        usedColors.add(stableColor);
                    }
                }
            }

            // 3. Assign new sequential colors to brand new groups
            int paletteIdx = 0;
            for (UUID groupId : orderedGroupIds) {
                if (!assignedColors.containsKey(groupId)) {
                    while (paletteIdx < GROUP_PALETTE.length && usedColors.contains(GROUP_PALETTE[paletteIdx])) {
                        paletteIdx++;
                    }

                    int color;
                    if (paletteIdx < GROUP_PALETTE.length) {
                        color = GROUP_PALETTE[paletteIdx];
                        usedColors.add(color);
                        paletteIdx++;
                    } else {
                        color = GROUP_PALETTE[Math.abs(groupId.hashCode()) % GROUP_PALETTE.length];
                    }

                    assignedColors.put(groupId, color);
                    stableGroupColors.put(groupId, color); // Cache it for future frames
                }
            }

            for (Map.Entry<UUID, Set<Integer>> groupEntry : groups.entrySet()) {
                drawBlobGroup(context, groupEntry.getValue(), assignedColors.get(groupEntry.getKey()), guiX, guiY);
            }
        }

        if (!activeGreenSlots.isEmpty()) drawBlobGroup(context, activeGreenSlots, 0xAA33FF33, guiX, guiY);
        if (!activeRedSlots.isEmpty()) drawBlobGroup(context, activeRedSlots, 0xAAFF3333, guiX, guiY);

        for (int slotIdx : activeGreenSlots) {
            if (whitelists != null && whitelists.containsKey(slotIdx)) {
                Slot slot = editor.accessor.getHandler().getSlot(slotIdx);

                context.getMatrices().pushMatrix();
                // FIX: Offset de +4 en X e Y para centrar perfectamente un icono de 8x8 en un slot de 16x16
                context.getMatrices().translate(guiX + slot.x + 4, guiY + slot.y + 4);
                context.getMatrices().scale(0.5f, 0.5f); // Mitad de tamaño

                com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
                context.drawTexture(pipeline, ModTextures.ICON_CONFLICT, 0, 0, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, -1);

                context.getMatrices().popMatrix();
            }
        }
    }

    private void drawBlobGroup(DrawContext context, Set<Integer> groupSlots, int colorARGB, int guiX, int guiY) {
        for (int slotIdx : groupSlots) {
            if (slotIdx >= editor.accessor.getHandler().slots.size()) continue;
            Slot slot = editor.accessor.getHandler().getSlot(slotIdx);
            if (slot.inventory instanceof PlayerInventory) continue;

            int x = guiX + slot.x;
            int y = guiY + slot.y;

            context.fill(x + 2, y + 2, x + 14, y + 14, colorARGB);

            boolean hasRight = groupSlots.contains(slotIdx + 1) && (slotIdx % 9 != 8);
            boolean hasDown = groupSlots.contains(slotIdx + 9);
            boolean hasCorner = hasRight && hasDown && groupSlots.contains(slotIdx + 10);

            if (hasRight) context.fill(x + 14, y + 2, x + 20, y + 14, colorARGB);
            if (hasDown) context.fill(x + 2, y + 14, x + 14, y + 20, colorARGB);
            if (hasCorner) context.fill(x + 14, y + 14, x + 20, y + 20, colorARGB);
        }
    }

    private int getExplicitGroupColor(Set<Integer> groupSlots) {
        ChestConfigManager manager = ChestConfigManager.getInstance();
        Set<Integer> commonColors = null;

        for (int slotIdx : groupSlots) {
            Set<Integer> slotColors = new HashSet<>();

            int bg = manager.getColor(slotIdx, ChestConfigManager.ACTION_BG) & 0x00FFFFFF;
            if (bg != 0) slotColors.add(bg);
            int top = manager.getColor(slotIdx, ChestConfigManager.ACTION_TOP) & 0x00FFFFFF;
            if (top != 0) slotColors.add(top);
            int bottom = manager.getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM) & 0x00FFFFFF;
            if (bottom != 0) slotColors.add(bottom);
            int left = manager.getColor(slotIdx, ChestConfigManager.ACTION_LEFT) & 0x00FFFFFF;
            if (left != 0) slotColors.add(left);
            int right = manager.getColor(slotIdx, ChestConfigManager.ACTION_RIGHT) & 0x00FFFFFF;
            if (right != 0) slotColors.add(right);

            // If a single slot has no colors, the group cannot have a unanimous color
            if (slotColors.isEmpty()) {
                return 0;
            }

            if (commonColors == null) {
                // Initialize with the colors of the first slot
                commonColors = slotColors;
            } else {
                // Intersection: keep only colors present in all evaluated slots
                commonColors.retainAll(slotColors);
            }

            // If at any point the intersection is empty, abort
            if (commonColors.isEmpty()) {
                return 0;
            }
        }

        // If we reach here, commonColors contains at least one unanimous color
        if (commonColors != null && !commonColors.isEmpty()) {
            return commonColors.iterator().next() | 0x99000000;
        }

        return 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (session.hasSelectionConflict) return false;

        if (session.isPreviewing && session.previewItems != null) {
            // ZONA 1: El ratón está sobre el propio panel lateral izquierdo
            boolean overPanel = mouseX >= layout.listX && mouseX <= layout.listX + layout.listW;

            // ZONA 2: El ratón está sobre el área central del cofre (donde están los slots)
            boolean overChest = mouseX >= layout.guiX && mouseX <= layout.guiX + layout.bgWidth;

            // Si estamos en cualquiera de las dos zonas, permitimos el scroll manual
            if (overPanel || overChest) {
                float scrollSpeed = 18f;
                int listViewH = layout.listH - 38;
                float maxListScroll = Math.max(0, session.previewItems.size() * 18 - listViewH);

                session.listScrollY = MathHelper.clamp(session.listScrollY - (float)(verticalAmount * scrollSpeed), 0, maxListScroll);
                session.userOverrodePreviewScroll = true; // Esto detiene la animación automática
                return true;
            }
        }
        return false;
    }

    private void drawRuleTab(DrawContext context, int x, int y, net.minecraft.util.Identifier icon, boolean isActive) {
        int w = 20;
        int h = 20;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        // Fondo de la pestaña
        int bgColor = isDark ? (isActive ? 0xFF212121 : 0xFF151515) : (isActive ? 0xFFC6C6C6 : 0xFF8B8B8B);
        context.fill(x, y, x + w, y + h, bgColor);

        drawDarkBevel(context, x, y, w, h, isActive);

        // TRUCO DE ACOPLAMIENTO
        if (isActive) {
            int patchColor = isDark ? 0xFF212121 : 0xFFC6C6C6;
            context.fill(x + 18, y + 1, x + 22, y + 19, patchColor);
        }

        // Icono
        int color = isActive ? -1 : (isDark ? 0xFF555555 : 0xFF555555);
        com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
        context.drawTexture(pipeline, icon, x + 2, y + 2, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, color);
    }

    private void transitionToEditFilter() {
        // 1. Carga los ítems del filtro guardado en la sesión actual
        editor.prepareFilterMenu();

        // 2. Toma la fotografía EXACTA de lo que se acaba de cargar
        session.originalItemsSnapshot = new ArrayList<>(session.currentAllowedItems);
        session.originalRuleManual = session.ruleManual;
        session.originalRuleShift = session.ruleShift;
        session.originalRuleHopper = session.ruleHopper;
        session.isUnsavedPopupOpen = false;

        // 3. Cambia la pantalla
        editor.toggleState(EditorState.EDIT_FILTER);
    }
}