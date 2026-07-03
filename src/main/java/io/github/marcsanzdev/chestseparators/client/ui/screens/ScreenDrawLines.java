package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class ScreenDrawLines extends AbstractEditorScreen {

    private ToolButtonWidget btnPencilArea;
    private ToolButtonWidget btnEraserArea;
    private ToolButtonWidget btnClear;
    private ToolButtonWidget btnPencilTrace;
    private ToolButtonWidget btnEraserTrace;
    private ToolButtonWidget btnColorPicker;

    public static final int[] STANDARD_PALETTE = {
            0xFF993333, 0xFFD87F33, 0xFFE5E533, 0xFF7FCC19,
            0xFF667F33, 0xFF4C7F99, 0xFF6699D8, 0xFF334CB2,
            0xFF7F3FB2, 0xFFB24CD8, 0xFFF27FA5, 0xFF664C33,
            0xFFFFFFFF, 0xFF999999, 0xFF4C4C4C, 0xFF191919
    };

    public static final String[] STANDARD_COLOR_KEYS = {
            "red", "orange", "yellow", "lime",
            "green", "cyan", "light_blue", "blue",
            "purple", "magenta", "pink", "brown",
            "white", "light_gray", "gray", "black"
    };

    private long lastCustomColorClickTime = 0;
    private int lastCustomColorClickIndex = -1;

    private long btnCopyClickTime = 0;
    private long btnPasteClickTime = 0;
    private long btnUndoClickTime = 0;
    private long btnRedoClickTime = 0;
    private long btnClearClickTime = 0;
    private long btnColorPickerClickTime = 0;


    public ScreenDrawLines(ChestSeparatorsEditor editor) {
        super(editor);
    }

    @Override
    protected void buildWidgets() {
        // --- 1. RIGHT ACTION PANEL (Copy, Paste, Undo, Redo) ---
        int rightX = layout.rightX;
        int sy = layout.mainY;
        int btnW = layout.btnW;
        int bH = layout.bH;

        WideButtonWidget btnCopy = new WideButtonWidget(rightX, sy, btnW, bH, Text.translatable("button.chestseparators.copy_layout").getString(), ModTextures.ICON_COPY, () -> {
            btnCopyClickTime = System.currentTimeMillis();
            ChestConfigManager.getInstance().copyAllToClipboard();
            editor.showStatus(Text.translatable("message.chestseparators.layout_copied"), Formatting.GRAY);
            editor.playClickSound(1.0f);
        });
        btnCopy.keepNormalTextColor = true;
        btnCopy.tooltipText = Text.translatable("tooltip.chestseparators.desc.copy_layout").getString();
        widgets.add(btnCopy);

        WideButtonWidget btnPaste = new WideButtonWidget(rightX, sy + 24, btnW, bH, Text.translatable("button.chestseparators.paste_layout").getString(), ModTextures.ICON_PASTE, () -> {
            btnPasteClickTime = System.currentTimeMillis();
            if (ChestConfigManager.getInstance().hasClipboardData()) {
                ChestConfigManager.getInstance().saveSnapshot();
                ChestConfigManager.getInstance().pasteFromClipboard();
                editor.saveSmart();
                editor.showStatus(Text.translatable("message.chestseparators.layout_pasted"), Formatting.GREEN);
                editor.playClickSound(1.0f);
            }
        });
        btnPaste.keepNormalTextColor = true;
        btnPaste.tooltipText = Text.translatable("tooltip.chestseparators.desc.paste_layout").getString();
        widgets.add(btnPaste);

        WideButtonWidget btnUndo = new WideButtonWidget(rightX, sy + 48, btnW, bH, Text.translatable("button.chestseparators.undo_action").getString(), ModTextures.ICON_UNDO, () -> {
            btnUndoClickTime = System.currentTimeMillis();
            if (ChestConfigManager.getInstance().canUndo()) {
                ChestConfigManager.getInstance().undo();
                editor.saveSmart();
                editor.playClickSound(0.8f);
            }
        });
        btnUndo.keepNormalTextColor = true;
        btnUndo.tooltipText = Text.translatable("tooltip.chestseparators.desc.undo_action").getString();
        widgets.add(btnUndo);

        WideButtonWidget btnRedo = new WideButtonWidget(rightX, sy + 72, btnW, bH, Text.translatable("button.chestseparators.redo_action").getString(), ModTextures.ICON_REDO, () -> {
            btnRedoClickTime = System.currentTimeMillis();
            if (ChestConfigManager.getInstance().canRedo()) {
                ChestConfigManager.getInstance().redo();
                editor.saveSmart();
                editor.playClickSound(0.8f);
            }
        });
        btnRedo.keepNormalTextColor = true;
        btnRedo.tooltipText = Text.translatable("tooltip.chestseparators.desc.redo_action").getString();
        widgets.add(btnRedo);

        // --- 2. LEFT SIDEBAR TOOLS ---
        int sx = layout.sidebarX;
        int contentX = sx + 7;
        int currentY = layout.guiY + editor.getSidebarYOffset();
        int row2Y = currentY + 22;

        btnPencilArea = new ToolButtonWidget(contentX, currentY, ModTextures.PENCIL_BASE, Text.translatable("tooltip.chestseparators.draw_area").getString(), () -> {
            int tabMode = session.currentTab;
            if (tabMode == 0) { session.lineToolMode = 0; if (session.lineColorIndex == -1) session.lineColorIndex = 0; }
            else if (tabMode == 1) { session.bgToolMode = 0; if (session.bgColorIndex == -1) session.bgColorIndex = 0; }
            else { session.comboToolMode = 0; if (session.comboColorIndex == -1) session.comboColorIndex = 0; }
            editor.playClickSound(1.0f);
        });

        btnEraserArea = new ToolButtonWidget(contentX + 22, currentY, ModTextures.ERASER_AREA, Text.translatable("tooltip.chestseparators.erase_area").getString(), () -> {
            int tabMode = session.currentTab;
            if (tabMode == 0) { session.lineToolMode = 0; session.lineColorIndex = -1; session.editingLineCustomIndex = -1; }
            else if (tabMode == 1) { session.bgToolMode = 0; session.bgColorIndex = -1; session.editingBgCustomIndex = -1; }
            else { session.comboToolMode = 0; session.comboColorIndex = -1; session.editingComboCustomIndex = -1; }
            editor.playClickSound(1.0f);
        });

        btnClear = new ToolButtonWidget(contentX + 44, currentY, ModTextures.ICON_DELETE, Text.translatable("tooltip.chestseparators.clear_layer").getString(), () -> {
            btnClearClickTime = System.currentTimeMillis();
            ChestConfigManager.getInstance().saveSnapshot();
            int tabMode = session.currentTab;
            if (tabMode == 0 || tabMode == 2) ChestConfigManager.getInstance().clearAllLines();
            if (tabMode == 1 || tabMode == 2) ChestConfigManager.getInstance().clearAllBackgrounds();
            editor.saveSmart();
            editor.showStatus(Text.translatable("message.chestseparators.cleared"), Formatting.RED);
            editor.playClickSound(0.8f);
        });

        btnPencilTrace = new ToolButtonWidget(contentX, row2Y, ModTextures.PENCIL_BASE, Text.translatable("tooltip.chestseparators.draw_trace").getString(), () -> {
            int tabMode = session.currentTab;
            if (tabMode == 0) { session.lineToolMode = 1; if (session.lineColorIndex == -1) session.lineColorIndex = 0; }
            else if (tabMode == 1) { session.bgToolMode = 1; if (session.bgColorIndex == -1) session.bgColorIndex = 0; }
            else { session.comboToolMode = 1; if (session.comboColorIndex == -1) session.comboColorIndex = 0; }
            editor.playClickSound(1.0f);
        });

        btnEraserTrace = new ToolButtonWidget(contentX + 22, row2Y, ModTextures.ERASER_TRACE, Text.translatable("tooltip.chestseparators.erase_trace").getString(), () -> {
            int tabMode = session.currentTab;
            if (tabMode == 0) { session.lineToolMode = 1; session.lineColorIndex = -1; session.editingLineCustomIndex = -1; }
            else if (tabMode == 1) { session.bgToolMode = 1; session.bgColorIndex = -1; session.editingBgCustomIndex = -1; }
            else { session.comboToolMode = 1; session.comboColorIndex = -1; session.editingComboCustomIndex = -1; }
            editor.playClickSound(1.0f);
        });

        btnColorPicker = new ToolButtonWidget(contentX + 44, row2Y, ModTextures.ICON_PALETTE, Text.translatable("tooltip.chestseparators.open_palette").getString(), () -> {
            btnColorPickerClickTime = System.currentTimeMillis();
            int tabMode = session.currentTab;
            if (tabMode == 0) { if (session.editingLineCustomIndex == -1) { session.editingLineCustomIndex = 0; session.lineColorIndex = 16; } editor.openColorPicker(ChestConfigManager.getInstance().getCustomColors(0)[session.editingLineCustomIndex], 0); }
            else if (tabMode == 1) { if (session.editingBgCustomIndex == -1) { session.editingBgCustomIndex = 0; session.bgColorIndex = 16; } editor.openColorPicker(ChestConfigManager.getInstance().getCustomColors(1)[session.editingBgCustomIndex], 1); }
            else { if (session.editingComboCustomIndex == -1) { session.editingComboCustomIndex = 0; session.comboColorIndex = 16; } editor.openColorPicker(ChestConfigManager.getInstance().getCustomColors(2)[session.editingComboCustomIndex], 2); }
            editor.playClickSound(1.0f);
        });

        widgets.add(btnPencilArea);
        widgets.add(btnEraserArea);
        widgets.add(btnClear);
        widgets.add(btnPencilTrace);
        widgets.add(btnEraserTrace);
        widgets.add(btnColorPicker);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // --- LA MAGIA: Si el cuentagotas está activo, abortamos el dibujado del menú entero ---
        if (session.isEyedropperActive) {
            return;
        }

        updateWidgetStates();

        // 1. Draw Left Sidebar Background
        int sx = layout.sidebarX;
        int currentY = layout.guiY + editor.getSidebarYOffset();

        // --- CÁLCULO DE ALTURA EXACTA SIMÉTRICA ---
        // Calculamos la coordenada Y donde acaba el ÚLTIMO botón de color de la paleta
        // (el último slot de la columna de colores personalizados es el índice 7)
        int bottomOfLastColor = layout.paletteY + (7 * (layout.swatchSize + 4)) + layout.swatchSize;

        // La altura total será esa coordenada inferior, menos el punto de inicio del panel, más un margen (ej. 8px)
        int panelStartY = currentY - 4;
        int margin = 8;
        int fixedSidebarHeight = (bottomOfLastColor - panelStartY) + margin;

        boolean isDark = GlobalChestConfig.instance.darkMode;
        context.fill(sx, panelStartY, sx + layout.sidebarWidth, panelStartY + fixedSidebarHeight, isDark ? 0xFF212121 : 0xFFC6C6C6);
        drawDarkBevel(context, sx, panelStartY, layout.sidebarWidth, fixedSidebarHeight, false);


        // --- TRUCO MAGISTRAL ---
        // Si el Color Picker está abierto, engañamos a los botones de fondo
        // enviando coordenadas de ratón falsas (-1, -1) para que no dibujen sus tooltips.
        int bgMouseX = session.isColorPickerOpen ? -1 : mouseX;
        int bgMouseY = session.isColorPickerOpen ? -1 : mouseY;

        // 2. Draw Widgets (Tools and Action Panel) con el ratón falso
        super.render(context, bgMouseX, bgMouseY, delta);

        // 3. Draw Palette
        drawPaletteArea(context, mouseX, mouseY, session.currentTab);

        // 4. Draw Tabs
        drawTabs(context, mouseX, mouseY);

        // 5. Draw Grid Previews
        renderDragPreview(context, mouseX, mouseY);
        if (!session.isDraggingLine && !session.isColorPickerOpen) {
            renderHoverPreview(context, mouseX, mouseY);
        }

        // --- 6. DIBUJAR TOOLTIPS DE LOS BOTONES DERECHOS ---
        // Solo dibujamos si NO está el ColorPicker abierto tapando cosas

    }

    private void updateWidgetStates() {
        int tabMode = session.currentTab;
        int tMode = (tabMode == 0) ? session.lineToolMode : (tabMode == 1 ? session.bgToolMode : session.comboToolMode);
        int cIndex = (tabMode == 0) ? session.lineColorIndex : (tabMode == 1 ? session.bgColorIndex : session.comboColorIndex);

        int dynamicColor = 0;
        if (tabMode == 0) dynamicColor = getCurrentSelectedLineColorValue();
        else if (tabMode == 1) dynamicColor = getCurrentSelectedBgColorValue();
        else if (tabMode == 2) dynamicColor = getCurrentSelectedComboColorValue();
        if (dynamicColor == 0) dynamicColor = 0xFFFFFF;

        // Tool Active States
        btnPencilArea.isActive = (tMode == 0 && cIndex != ChestSeparatorsEditor.TOOL_ERASER_ID);
        btnPencilTrace.isActive = (tMode == 1 && cIndex != ChestSeparatorsEditor.TOOL_ERASER_ID);
        btnEraserArea.isActive = (tMode == 0 && cIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
        btnEraserTrace.isActive = (tMode == 1 && cIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);

        // Tool Icons & Colors
        Identifier baseTex = (tabMode == 0) ? ModTextures.PENCIL_BASE : ((tabMode == 1) ? ModTextures.BRUSH_BASE : ModTextures.BTN_EDIT_LINES);
        Identifier maskAreaTex = (tabMode == 1) ? ModTextures.BRUSH_MASK_AREA : ((tabMode == 2) ? ModTextures.COMBO_MASK_AREA : ModTextures.PENCIL_MASK_AREA);
        Identifier maskTraceTex = (tabMode == 1) ? ModTextures.BRUSH_MASK_TRACE : ((tabMode == 2) ? ModTextures.COMBO_MASK_TRACE : ModTextures.PENCIL_MASK_TRACE);

        btnPencilArea.baseIcon = baseTex;
        btnPencilArea.maskIcon = maskAreaTex;
        btnPencilArea.dynamicColor = dynamicColor;

        btnPencilTrace.baseIcon = baseTex;
        btnPencilTrace.maskIcon = maskTraceTex;
        btnPencilTrace.dynamicColor = dynamicColor;
        btnPencilTrace.baseOffsetX = (tabMode == 0) ? -3 : 0;
        btnPencilTrace.maskOffsetX = (tabMode == 0) ? -3 : 0;

        // Right Panel Disabled States
        // Right Panel Disabled States & Animations
        if (widgets.size() >= 4) {
            widgets.get(0).isActive = (System.currentTimeMillis() - btnCopyClickTime < 150);

            widgets.get(1).isActive = (System.currentTimeMillis() - btnPasteClickTime < 150);
            widgets.get(1).isDisabled = !ChestConfigManager.getInstance().hasClipboardData();

            widgets.get(2).isActive = (System.currentTimeMillis() - btnUndoClickTime < 150);
            widgets.get(2).isDisabled = !ChestConfigManager.getInstance().canUndo();

            widgets.get(3).isActive = (System.currentTimeMillis() - btnRedoClickTime < 150);
            widgets.get(3).isDisabled = !ChestConfigManager.getInstance().canRedo();
        }

        // Animación del botón de limpiar capa (Papelera)
        if (btnClear != null) {
            btnClear.isActive = (System.currentTimeMillis() - btnClearClickTime < 150);
        }

        if (btnColorPicker != null) {
            btnColorPicker.isActive = (System.currentTimeMillis() - btnColorPickerClickTime < 150);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (handleTabsClick(mouseX, mouseY, button)) return true;
        if (handlePaletteClick(mouseX, mouseY, button)) return true;

        // Si el Color Picker está abierto, bloqueamos los clics en el cofre
        if (session.isColorPickerOpen) return false;

        // --- LÓGICA RESTAURADA: CLICS EN EL COFRE PARA DIBUJAR ---
        if (button == 0) {
            Slot slot = editor.accessor.getFocusedSlot();
            if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
                int action = geometry.calculateAction(slot, mouseX, mouseY);

                // Si estamos en Brocha o Combo, forzamos la acción a BG
                if (session.currentTab == 1 || session.currentTab == 2) {
                    action = ChestConfigManager.ACTION_BG;
                }

                if (session.currentTab == 1 || session.currentTab == 2) {
                    ChestConfigManager.getInstance().saveSnapshot();
                    session.isDraggingLine = true;
                    session.currentDragAction = ChestConfigManager.ACTION_BG;
                    session.dragStartSlot = slot;
                    session.dragCurrentSlot = slot;
                    session.tracePath.clear();
                    session.lockedTraceAxis = 0;
                    session.tracePath.add(slot.getIndex() + "_" + ChestConfigManager.ACTION_BG);

                    int tIndex = (session.currentTab == 1) ? session.bgColorIndex : session.comboColorIndex;
                    int colorVal = (session.currentTab == 1) ? getCurrentSelectedBgColorValue() : getCurrentSelectedComboColorValue();

                    if (tIndex == ChestSeparatorsEditor.TOOL_ERASER_ID) {
                        session.isDragModeErasing = true;
                    } else {
                        if (colorVal == 0) return false;
                        int existingColor = ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_BG);
                        session.isDragModeErasing = (existingColor == (colorVal | 0xFF000000));
                    }
                    return true;

                }

                if (session.currentTab == 0) {
                    if (action != 0 && action != ChestConfigManager.ACTION_BG) {
                        ChestConfigManager.getInstance().saveSnapshot();
                        session.isDraggingLine = true;
                        session.currentDragAction = action;
                        session.dragStartSlot = slot;
                        session.dragCurrentSlot = slot;
                        session.tracePath.clear();
                        session.tracePath.add(slot.getIndex() + "_" + action);
                        session.lockedTraceAction = action;
                        if (action == ChestConfigManager.ACTION_TOP || action == ChestConfigManager.ACTION_BOTTOM) {
                            session.lockedTraceAxis = 1;
                            session.lockedTraceRowCol = slot.getIndex() / 9;
                            session.lockedLineCoord = (action == ChestConfigManager.ACTION_TOP) ? (layout.guiY + slot.y) : (layout.guiY + slot.y + 16);
                        } else {
                            session.lockedTraceAxis = 2;
                            session.lockedTraceRowCol = slot.getIndex() % 9;
                            session.lockedLineCoord = (action == ChestConfigManager.ACTION_LEFT) ? (layout.guiX + slot.x) : (layout.guiX + slot.x + 16);
                        }

                        int tIndex = session.lineColorIndex;
                        int colorVal = getCurrentSelectedLineColorValue();

                        if (tIndex == ChestSeparatorsEditor.TOOL_ERASER_ID) {
                            session.isDragModeErasing = true;
                        } else {
                            if (colorVal == 0) return false;
                            int existingColor = ChestConfigManager.getInstance().getColor(slot.getIndex(), action);
                            session.isDragModeErasing = (existingColor == (colorVal | 0xFF000000));
                        }
                        return true;
                    }
                }
            }
        }

        // --- CLICK OUTSIDE TO CLOSE ---
        boolean isInsideMain = mouseX >= layout.guiX && mouseX <= layout.guiX + layout.bgWidth && mouseY >= layout.guiY && mouseY <= layout.guiY + layout.bgHeight;
        boolean isInsideLeft = mouseX >= layout.sidebarX && mouseX <= layout.sidebarX + layout.sidebarWidth && mouseY >= layout.guiY + editor.getSidebarYOffset() - 4 && mouseY <= layout.guiY + editor.getSidebarYOffset() + layout.sidebarHeight;
        boolean isInsideRight = mouseX >= layout.rightX && mouseX <= layout.rightX + layout.btnW && mouseY >= layout.mainY && mouseY <= layout.mainY + (layout.bH * 4) + (4 * 3);

        if (!isInsideMain && !isInsideLeft && !isInsideRight && !session.isEyedropperActive) {
            if (GlobalChestConfig.instance.closeOnClickOutside) {
                editor.toggleState(EditorState.HIDDEN);
                editor.playClickSound(1.0f);
                return true;
            }
        }

        return false; // Este es tu return final original
    }

    private boolean handleTabsClick(double mx, double my, int button) {
        int tabX = layout.sidebarX - 16;
        int startY = layout.guiY + editor.getSidebarYOffset() + 10;

        if (mx >= tabX && mx < tabX + 20) {
            if (button == 0) {
                for (int i = 0; i < 3; i++) {
                    int tabY = startY + (i * 24);
                    if (my >= tabY && my < tabY + 20) {
                        session.currentTab = i;
                        editor.playClickSound(1.0f);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean handlePaletteClick(double mx, double my, int button) {
        int pY = layout.paletteY;
        int col1X = layout.paletteCol1X;
        int col2X = layout.paletteCol2X;
        int col3X = layout.paletteCol3X;

        int tabMode = session.currentTab;

        // 1. Clics en la paleta estándar (izquierda)
        for (int i = 0; i < 16; i++) {
            int x = (i / 8 == 0) ? col1X : col2X;
            int y = pY + ((i % 8) * (layout.swatchSize + 4));
            if (editor.isHovering(x, y, layout.swatchSize, layout.swatchSize, mx, my)) {
                if (button == 2) {
                    session.copiedColorRGB = STANDARD_PALETTE[i];
                    editor.showStatus(Text.translatable("message.chestseparators.color_copied"), Formatting.GOLD);
                    editor.playClickSound(0.8f);
                    return true;
                } else if (button == 0) {
                    if (tabMode == 0) { session.lineColorIndex = i; session.editingLineCustomIndex = -1; }
                    else if (tabMode == 1) { session.bgColorIndex = i; session.editingBgCustomIndex = -1; }
                    else { session.comboColorIndex = i; session.editingComboCustomIndex = -1; }
                    editor.playClickSound(1.0f);
                    return true;
                }
            }
        }

        // 2. Clics en la paleta personalizada (derecha)
        for (int i = 0; i < 8; i++) {
            int y = pY + (i * (layout.swatchSize + 4));
            if (editor.isHovering(col3X, y, layout.swatchSize, layout.swatchSize, mx, my)) {
                int currentColor = ChestConfigManager.getInstance().getCustomColors(tabMode)[i];

                if (button == 2) {
                    if (currentColor != 0) {
                        session.copiedColorRGB = currentColor;
                        editor.showStatus(Text.translatable("message.chestseparators.color_copied"), Formatting.GOLD);
                        editor.playClickSound(0.8f);
                    }
                    return true;
                }
                else if (button == 0) {
                    if (session.copiedColorRGB != 0) {
                        ChestConfigManager.getInstance().setCustomColor(i, session.copiedColorRGB, tabMode);
                        ChestConfigManager.getInstance().saveWorldPalette();
                        session.copiedColorRGB = 0; // Vaciamos el portapapeles
                        editor.showStatus(Text.translatable("message.chestseparators.color_pasted"), Formatting.GREEN);
                        editor.playClickSound(1.2f);
                    } else {
                        // --- PUNTO CLAVE: Guardamos el color que tenemos en la mano ANTES de cambiarlo ---
                        editor.restoreColorIndex = (tabMode == 0) ? session.lineColorIndex : (tabMode == 1 ? session.bgColorIndex : session.comboColorIndex);

                        if (currentColor == 0) {
                            // SI ESTÁ VACÍO: 1 CLIC ABRE EL PICKER DIRECTAMENTE
                            if (tabMode == 0) { session.lineColorIndex = 16 + i; session.editingLineCustomIndex = i; }
                            else if (tabMode == 1) { session.bgColorIndex = 16 + i; session.editingBgCustomIndex = i; }
                            else { session.comboColorIndex = 16 + i; session.editingComboCustomIndex = i; }
                            editor.openColorPicker(0, tabMode);
                            editor.playClickSound(1.0f);
                        } else {
                            // LÓGICA DE DOBLE CLIC PARA COLORES EXISTENTES
                            long now = System.currentTimeMillis();
                            boolean isDoubleClick = (i == lastCustomColorClickIndex) && (now - lastCustomColorClickTime < 300);
                            lastCustomColorClickIndex = i;
                            lastCustomColorClickTime = now;

                            if (isDoubleClick) {
                                // SI TIENE COLOR: 2 CLICS ABRE EL PICKER
                                if (tabMode == 0) { session.lineColorIndex = 16 + i; session.editingLineCustomIndex = i; }
                                else if (tabMode == 1) { session.bgColorIndex = 16 + i; session.editingBgCustomIndex = i; }
                                else { session.comboColorIndex = 16 + i; session.editingComboCustomIndex = i; }
                                editor.openColorPicker(currentColor, tabMode);
                                editor.playClickSound(1.0f);
                            } else {
                                // SI TIENE COLOR: 1 CLIC LO SELECCIONA NORMAL (PARA PINTAR)
                                if (tabMode == 0) { session.lineColorIndex = 16 + i; session.editingLineCustomIndex = i; }
                                else if (tabMode == 1) { session.bgColorIndex = 16 + i; session.editingBgCustomIndex = i; }
                                else { session.comboColorIndex = 16 + i; session.editingComboCustomIndex = i; }
                                editor.playClickSound(1.0f);
                            }
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void drawSwatch(DrawContext context, int x, int y, int color, int index, int mouseX, int mouseY, boolean isCustom, int tabMode) {
        boolean disabled = session.isColorPickerOpen && !isCustom;
        int selectedIndexToCheck = (tabMode == 0) ? session.lineColorIndex : (tabMode == 1 ? session.bgColorIndex : session.comboColorIndex);
        boolean selected = (index == selectedIndexToCheck);
        boolean hover = !disabled && editor.isHovering(x, y, layout.swatchSize, layout.swatchSize, mouseX, mouseY);

        if (color == 0) {
            // --- RESTAURACIÓN: El patrón de 2x2 que cuadra perfecto ---
            context.fill(x, y, x + layout.swatchSize, y + layout.swatchSize, 0xFF555555);
            int half = layout.swatchSize / 2;
            context.fill(x, y, x + half, y + half, 0xFF333333);
            context.fill(x + half, y + half, x + layout.swatchSize, y + layout.swatchSize, 0xFF333333);
            drawStandardBevel(context, x, y, layout.swatchSize, layout.swatchSize, selected);
        } else {
            context.fill(x, y, x + layout.swatchSize, y + layout.swatchSize, 0xFF000000 | color);
            drawColorBevel(context, x, y, layout.swatchSize, layout.swatchSize, color, selected);
        }

        if (hover && !selected) context.drawStrokedRectangle(x, y, layout.swatchSize, layout.swatchSize, isCustom ? 0x80FFFFFF : 0xFFFFFFFF);
        if (disabled) context.fill(x, y, x + layout.swatchSize, y + layout.swatchSize, 0x80000000);
    }

    private void drawPaletteArea(DrawContext context, int mouseX, int mouseY, int tabMode) {
        int pY = layout.paletteY;
        int col1X = layout.paletteCol1X;
        int col2X = layout.paletteCol2X;
        int col3X = layout.paletteCol3X;

        for (int i = 0; i < 16; i++) {
            int x = (i / 8 == 0) ? col1X : col2X;
            int y = pY + ((i % 8) * (layout.swatchSize + 4));
            drawSwatch(context, x, y, STANDARD_PALETTE[i], i, mouseX, mouseY, false, tabMode);
            if (!session.isColorPickerOpen && editor.isHovering(x, y, layout.swatchSize, layout.swatchSize, mouseX, mouseY)) {
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.translatable("color.minecraft." + STANDARD_COLOR_KEYS[i]), mouseX, mouseY);
            }
        }

        int[] worldColors = ChestConfigManager.getInstance().getCustomColors(tabMode);
        for (int i = 0; i < 8; i++) {
            int y = pY + (i * (layout.swatchSize + 4));
            drawSwatch(context, col3X, y, worldColors[i], 16 + i, mouseX, mouseY, true, tabMode);
            if (!session.isColorPickerOpen && editor.isHovering(col3X, y, layout.swatchSize, layout.swatchSize, mouseX, mouseY)) {
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.translatable("color.chestseparators.custom", (i + 1)), mouseX, mouseY);
            }
        }
    }

    private void drawBaseBevel(DrawContext context, int x, int y, int width, int height, int light, int dark, int shadow, boolean sunken) {
        if (sunken) {
            context.fill(x, y, x + width - 1, y + 1, dark);
            context.fill(x, y, x + 1, y + height - 1, dark);
            context.fill(x + width - 1, y, x + width, y + height, light);
            context.fill(x, y + height - 1, x + width, y + height, light);
        } else {
            context.fill(x, y, x + width - 1, y + 1, light);
            context.fill(x, y, x + 1, y + height - 1, light);
            context.fill(x + width - 1, y, x + width, y + height, dark);
            context.fill(x, y + height - 1, x + width, y + height, dark);
            context.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, shadow);
            context.fill(x + 1, y + height - 2, x + width - 2, y + height - 1, shadow);
        }
    }

    private void drawColorBevel(DrawContext context, int x, int y, int width, int height, int baseColor, boolean sunken) {
        int light = editor.shiftColor(baseColor, 80) | 0xFF000000;
        int dark = editor.shiftColor(baseColor, -80) | 0xFF000000;
        int shadow = editor.shiftColor(baseColor, -40) | 0xFF000000;
        drawBaseBevel(context, x, y, width, height, light, dark, shadow, sunken);
    }

    private void drawStandardBevel(DrawContext context, int x, int y, int width, int height, boolean sunken) {
        int light = 0xFFFFFFFF; int dark = 0xFF373737; int shadow = 0xFF8B8B8B;
        drawBaseBevel(context, x, y, width, height, light, dark, shadow, sunken);
    }

    private void drawTabs(DrawContext context, int mouseX, int mouseY) {
        int tabX = layout.sidebarX - 20; // Restaurado a -20 (antes le puse -16)
        int startY = layout.guiY + editor.getSidebarYOffset() + 10;

        for (int i = 0; i < 3; i++) {
            int tabY = startY + (i * 24); // Restaurado el salto Y a 24
            boolean isSelected = (session.currentTab == i);
            boolean hover = !session.isColorPickerOpen && editor.isHovering(tabX, tabY, 20, 20, mouseX, mouseY);
            boolean isDark = GlobalChestConfig.instance.darkMode;
            int bgColor = isDark ? (isSelected ? 0xFF212121 : (hover ? 0xFF303030 : 0xFF151515)) : (isSelected ? 0xFFC6C6C6 : (hover ? 0xFFA0A0A0 : 0xFF8B8B8B));

            context.fill(tabX, tabY, tabX + 20, tabY + 20, bgColor);
            drawDarkBevel(context, tabX, tabY, 20, 20, isSelected);

            if (isSelected) {
                int connectColor = isDark ? 0xFF212121 : 0xFFC6C6C6;
                context.fill(tabX + 18, tabY + 1, tabX + 22, tabY + 19, connectColor);
            }

            Identifier icon = (i == 0) ? ModTextures.ICON_TAB_LINES : (i == 1) ? ModTextures.ICON_TAB_BG : ModTextures.BTN_EDIT_LINES;
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
            context.drawTexture(pipeline, icon, tabX + 2, tabY + 2, 0.0F, 0.0F, 16, 16, 16, 16, 16, 16, -1);

            if (hover) {
                Text name = (i == 0) ? Text.translatable("tooltip.chestseparators.tab_lines") : (i == 1) ? Text.translatable("tooltip.chestseparators.tab_backgrounds") : Text.translatable("tooltip.chestseparators.tab_combo");
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, name, mouseX, mouseY);
            }
        }
    }

    // --- MIGRATED GRID DRAWING LOGIC ---

    private void renderHoverPreview(DrawContext context, int mouseX, int mouseY) {
        Slot slot = editor.accessor.getFocusedSlot();
        if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
            int tabMode = session.currentTab;
            int action = (tabMode == 2 || tabMode == 1) ? ChestConfigManager.ACTION_BG : geometry.calculateAction(slot, mouseX, mouseY);

            int colorVal = 0; boolean explicitEraser = false;
            if (tabMode == 0) { colorVal = getCurrentSelectedLineColorValue(); explicitEraser = (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID); }
            else if (tabMode == 1) { colorVal = getCurrentSelectedBgColorValue(); explicitEraser = (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID); }
            else if (tabMode == 2) { colorVal = getCurrentSelectedComboColorValue(); explicitEraser = (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID); }

            if (colorVal == 0 && !explicitEraser) return;

            boolean willErase = explicitEraser;
            if (!willErase && tabMode != 2) {
                int existingColor = ChestConfigManager.getInstance().getColor(slot.getIndex(), action);
                if (existingColor == (colorVal | 0xFF000000)) willErase = true;
            }

            int colorBg = willErase ? 0x66FFFFFF : ((colorVal & 0x00FFFFFF) | 0x66000000);
            int colorLine = willErase ? 0x88FFFFFF : ((colorVal & 0x00FFFFFF) | 0x88000000);

            int x = layout.guiX + slot.x; int y = layout.guiY + slot.y;

            if (tabMode == 1) {
                if (!willErase || ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_BG) != 0){
                    context.fill(x, y, x + 16, y + 16, colorBg);
                }
            } else if (tabMode == 0) {
                if (action != 0 && action != ChestConfigManager.ACTION_BG) {
                    if ((action & ChestConfigManager.ACTION_TOP) != 0 && (!willErase || ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_TOP) != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if ((action & ChestConfigManager.ACTION_BOTTOM) != 0 && (!willErase || ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_BOTTOM) != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if ((action & ChestConfigManager.ACTION_LEFT) != 0 && (!willErase || ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_LEFT) != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if ((action & ChestConfigManager.ACTION_RIGHT) != 0 && (!willErase || ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_RIGHT) != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                }
            } else {
                if (!willErase || ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_BG) != 0) context.fill(x, y, x + 16, y + 16, colorBg);
                if (!willErase || ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_TOP) != 0) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                if (!willErase || ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_BOTTOM) != 0) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                if (!willErase || ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_LEFT) != 0) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                if (!willErase || ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_RIGHT) != 0) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
            }
        }
    }

    private void renderDragPreview(DrawContext context, int mouseX, int mouseY) {
        if (!session.isDraggingLine) return;
        int guiX = layout.guiX; int guiY = layout.guiY;
        int tabMode = session.currentTab;

        int colorVal = 0; boolean explicitEraser = false; int tMode = 0;
        if (tabMode == 0) { colorVal = getCurrentSelectedLineColorValue(); explicitEraser = (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID); tMode = session.lineToolMode; }
        else if (tabMode == 1) { colorVal = getCurrentSelectedBgColorValue(); explicitEraser = (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID); tMode = session.bgToolMode; }
        else if (tabMode == 2) { colorVal = getCurrentSelectedComboColorValue(); explicitEraser = (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID); tMode = session.comboToolMode; }

        if (colorVal == 0 && !explicitEraser) return;

        boolean erase = explicitEraser || session.isDragModeErasing;
        int colorBg = erase ? 0x66FFFFFF : ((colorVal & 0x00FFFFFF) | 0x66000000);
        int colorLine = erase ? 0x88FFFFFF : ((colorVal & 0x00FFFFFF) | 0x88000000);

        if (tabMode == 1) {
            if (tMode == 0) {
                int startRow = session.dragStartSlot.getIndex() / 9; int startCol = session.dragStartSlot.getIndex() % 9;
                int currRow = session.dragCurrentSlot.getIndex() / 9; int currCol = session.dragCurrentSlot.getIndex() % 9;
                int minRow = Math.min(startRow, currRow); int maxRow = Math.max(startRow, currRow);
                int minCol = Math.min(startCol, currCol); int maxCol = Math.max(startCol, currCol);
                for (Slot slot : editor.accessor.getHandler().slots) {
                    if (slot.inventory instanceof PlayerInventory) continue;
                    int r = slot.getIndex() / 9; int c = slot.getIndex() % 9;
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        if (erase && ChestConfigManager.getInstance().getColor(slot.getIndex(), ChestConfigManager.ACTION_BG) == 0) continue;
                        context.fill(guiX + slot.x, guiY + slot.y, guiX + slot.x + 16, guiY + slot.y + 16, colorBg);
                    }
                }
            } else {
                for (String step : session.tracePath) {
                    int slotIdx = Integer.parseInt(step.split("_")[0]);
                    if (erase && ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BG) == 0) continue;
                    Slot slot = editor.accessor.getHandler().getSlot(slotIdx);
                    context.fill(guiX + slot.x, guiY + slot.y, guiX + slot.x + 16, guiY + slot.y + 16, colorBg);
                }
            }
        } else if (tabMode == 2) {
            if (tMode == 0) {
                int startRow = session.dragStartSlot.getIndex() / 9; int startCol = session.dragStartSlot.getIndex() % 9;
                int currRow = session.dragCurrentSlot.getIndex() / 9; int currCol = session.dragCurrentSlot.getIndex() % 9;
                int minRow = Math.min(startRow, currRow); int maxRow = Math.max(startRow, currRow);
                int minCol = Math.min(startCol, currCol); int maxCol = Math.max(startCol, currCol);
                for (Slot slot : editor.accessor.getHandler().slots) {
                    if (slot.inventory instanceof PlayerInventory) continue;
                    int slotIdx = slot.getIndex(); int r = slotIdx / 9; int c = slotIdx % 9;
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        int x = guiX + slot.x; int y = guiY + slot.y;
                        if (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BG) != 0) context.fill(x, y, x + 16, y + 16, colorBg);
                        if (r == minRow && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_TOP) != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                        if (r == maxRow && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM) != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                        if (c == minCol && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_LEFT) != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                        if (c == maxCol && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_RIGHT) != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                    }
                }
            } else {
                java.util.Set<Integer> traceSlots = new java.util.HashSet<>();
                for (String step : session.tracePath) {
                    traceSlots.add(Integer.parseInt(step.split("_")[0]));
                }
                for (int slotIdx : traceSlots) {
                    Slot slot = editor.accessor.getHandler().getSlot(slotIdx);
                    int x = guiX + slot.x; int y = guiY + slot.y;
                    boolean hasTop = traceSlots.contains(slotIdx - 9);
                    boolean hasBottom = traceSlots.contains(slotIdx + 9);
                    boolean hasLeft = (slotIdx % 9 != 0) && traceSlots.contains(slotIdx - 1);
                    boolean hasRight = (slotIdx % 9 != 8) && traceSlots.contains(slotIdx + 1);

                    if (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BG) != 0) context.fill(x, y, x + 16, y + 16, colorBg);
                    if (!hasTop && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_TOP) != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if (!hasBottom && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM) != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if (!hasLeft && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_LEFT) != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if (!hasRight && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_RIGHT) != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                }
            }
        } else {
            if (tMode == 0) {
                int sRow = session.dragStartSlot.getIndex() / 9; int sCol = session.dragStartSlot.getIndex() % 9;
                int cRow = session.dragCurrentSlot.getIndex() / 9; int cCol = session.dragCurrentSlot.getIndex() % 9;
                int minRow = Math.min(sRow, cRow); int maxRow = Math.max(sRow, cRow);
                int minCol = Math.min(sCol, cCol); int maxCol = Math.max(sCol, cCol);

                int yTopRaw = minRow * 2; int yBotRaw = maxRow * 2 + 1;
                if (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow) yTopRaw = sRow * 2 + 1;
                else if (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow) yBotRaw = sRow * 2;

                int xLeftRaw = minCol * 2; int xRightRaw = maxCol * 2 + 1;
                if (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol) xLeftRaw = sCol * 2 + 1;
                else if (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol) xRightRaw = sCol * 2;

                boolean isOuterIntent =
                        (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow) ||
                                (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow) ||
                                (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol) ||
                                (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol);

                int yTopExp = yTopRaw; int yBotExp = yBotRaw;
                int xLeftExp = xLeftRaw; int xRightExp = xRightRaw;

                if (isOuterIntent) {
                    if (yTopRaw % 2 == 0) yTopExp--;
                    if (yBotRaw % 2 != 0) yBotExp++;
                    if (xLeftRaw % 2 == 0) xLeftExp--;
                    if (xRightRaw % 2 != 0) xRightExp++;
                }

                int maxRows = geometry.getContainerSlotCount() / 9;
                yTopExp = Math.max(0, Math.min(maxRows * 2 - 1, yTopExp));
                yBotExp = Math.max(0, Math.min(maxRows * 2 - 1, yBotExp));
                xLeftExp = Math.max(0, xLeftExp);
                xRightExp = Math.max(0, Math.min(17, xRightExp));

                if (geometry.isDraggingRectangle(mouseX, mouseY)) {
                    Slot startRowSlot = editor.accessor.getHandler().getSlot((yTopExp / 2) * 9);
                    int topY = guiY + startRowSlot.y + (yTopExp % 2 == 0 ? -1 : 16);
                    Slot endRowSlot = editor.accessor.getHandler().getSlot((yBotExp / 2) * 9);
                    int botY = guiY + endRowSlot.y + (yBotExp % 2 == 0 ? 0 : 17);
                    Slot startColSlot = editor.accessor.getHandler().getSlot(xLeftExp / 2);
                    int leftX = guiX + startColSlot.x + (xLeftExp % 2 == 0 ? -1 : 16);
                    Slot endColSlot = editor.accessor.getHandler().getSlot(xRightExp / 2);
                    int rightX = guiX + endColSlot.x + (xRightExp % 2 == 0 ? 0 : 17);

                    context.fill(leftX, topY, rightX, topY + 1, colorLine);
                    context.fill(leftX, botY - 1, rightX, botY, colorLine);
                    context.fill(leftX, topY, leftX + 1, botY, colorLine);
                    context.fill(rightX - 1, topY, rightX, botY, colorLine);

                    if (erase) {
                        for (int r = Math.max(0, minRow - 1); r <= maxRow + 1; r++) {
                            for (int c = Math.max(0, minCol - 1); c <= maxCol + 1; c++) {
                                int slotIdx = r * 9 + c;
                                if (slotIdx >= geometry.getContainerSlotCount()) continue;
                                Slot s = editor.accessor.getHandler().getSlot(slotIdx);
                                int x = guiX + s.x; int y = guiY + s.y;
                                int tY = r * 2, bY = r * 2 + 1; int lX = c * 2, rX = c * 2 + 1;
                                boolean hInside = (lX >= xLeftExp) && (rX <= xRightExp);
                                boolean vInside = (tY >= yTopExp) && (bY <= yBotExp);
                                if (hInside && tY >= yTopExp && tY <= yBotExp && ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_TOP) != 0) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                                if (hInside && bY >= yTopExp && bY <= yBotExp && ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM) != 0) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                                if (vInside && lX >= xLeftExp && lX <= xRightExp && ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_LEFT) != 0) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                                if (vInside && rX >= xLeftExp && rX <= xRightExp && ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_RIGHT) != 0) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                            }
                        }
                    }
                } else {
                    for (int r = minRow; r <= maxRow; r++) {
                        for (int c = minCol; c <= maxCol; c++) {
                            int slotIdx = r * 9 + c; Slot s = editor.accessor.getHandler().getSlot(slotIdx);
                            int x = guiX + s.x; int y = guiY + s.y;
                            if ((session.currentDragAction & ChestConfigManager.ACTION_TOP) != 0) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                            if ((session.currentDragAction & ChestConfigManager.ACTION_BOTTOM) != 0) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                            if ((session.currentDragAction & ChestConfigManager.ACTION_LEFT) != 0) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                            if ((session.currentDragAction & ChestConfigManager.ACTION_RIGHT) != 0) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                        }
                    }
                }
            } else {
                for (String step : session.tracePath) {
                    String[] parts = step.split("_"); int slotIdx = Integer.parseInt(parts[0]); int act = Integer.parseInt(parts[1]);
                    Slot slot = editor.accessor.getHandler().getSlot(slotIdx); int x = guiX + slot.x; int y = guiY + slot.y;
                    if ((act & ChestConfigManager.ACTION_TOP) != 0 && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_TOP) != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if ((act & ChestConfigManager.ACTION_BOTTOM) != 0 && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM) != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if ((act & ChestConfigManager.ACTION_LEFT) != 0 && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_LEFT) != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if ((act & ChestConfigManager.ACTION_RIGHT) != 0 && (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_RIGHT) != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                }
            }
        }
    }

    public int getCurrentSelectedLineColorValue() {
        if (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID) return 0;
        if (session.lineColorIndex < 16) return STANDARD_PALETTE[session.lineColorIndex];
        if (session.lineColorIndex < 24) return ChestConfigManager.getInstance().getCustomColors(0)[session.lineColorIndex - 16];
        return 0;
    }

    public int getCurrentSelectedBgColorValue() {
        if (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID) return 0;
        if (session.bgColorIndex < 16) return STANDARD_PALETTE[session.bgColorIndex];
        if (session.bgColorIndex < 24) return ChestConfigManager.getInstance().getCustomColors(1)[session.bgColorIndex - 16];
        return 0;
    }

    public int getCurrentSelectedComboColorValue() {
        if (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID) return 0;
        if (session.comboColorIndex < 16) return STANDARD_PALETTE[session.comboColorIndex];
        if (session.comboColorIndex < 24) return ChestConfigManager.getInstance().getCustomColors(2)[session.comboColorIndex - 16];
        return 0;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (session.isColorPickerOpen) return false;

        if (session.isDraggingLine) {
            Slot slot = editor.accessor.getFocusedSlot();
            if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
                session.dragCurrentSlot = slot;

                int toolMode = (session.currentTab == 0) ? session.lineToolMode : (session.currentTab == 1 ? session.bgToolMode : session.comboToolMode);

                // Si es Brocha (1) o Combo (2)
                if (session.currentTab == 1 || session.currentTab == 2) {
                    if (toolMode == 1) {
                        editor.updateTracePath(slot.getIndex() + "_" + ChestConfigManager.ACTION_BG);
                    }
                }
                // Si es Lápiz (0)
                else {
                    if (toolMode == 1) {
                        int guiX = layout.guiX;
                        int guiY = layout.guiY;

                        // 1. UNDO LOGIC (Exact rollback from connection vertex)
                        while (session.tracePath.size() >= 2) {
                            String currStep = session.tracePath.get(session.tracePath.size() - 1);
                            String prevStep = session.tracePath.get(session.tracePath.size() - 2);

                            int currSlotIdx = Integer.parseInt(currStep.split("_")[0]);
                            int prevSlotIdx = Integer.parseInt(prevStep.split("_")[0]);
                            int currAction = Integer.parseInt(currStep.split("_")[1]);
                            int prevAction = Integer.parseInt(prevStep.split("_")[1]);

                            Slot currSlot = editor.accessor.getHandler().getSlot(currSlotIdx);
                            double cRelX = mouseX - (guiX + currSlot.x);
                            double cRelY = mouseY - (guiY + currSlot.y);

                            // Calculate vertices (P1 and P2) of the previous line
                            int c1 = prevSlotIdx % 9, r1 = prevSlotIdx / 9;
                            int v1x1 = c1, v1y1 = r1, v1x2 = c1, v1y2 = r1;
                            if (prevAction == ChestConfigManager.ACTION_TOP) { v1x2 = c1 + 1; }
                            else if (prevAction == ChestConfigManager.ACTION_BOTTOM) { v1y1 = r1 + 1; v1x2 = c1 + 1; v1y2 = r1 + 1; }
                            else if (prevAction == ChestConfigManager.ACTION_LEFT) { v1y2 = r1 + 1; }
                            else if (prevAction == ChestConfigManager.ACTION_RIGHT) { v1x1 = c1 + 1; v1x2 = c1 + 1; v1y2 = r1 + 1; }

                            // Calculate vertices of the current line
                            int c2 = currSlotIdx % 9, r2 = currSlotIdx / 9;
                            int v2x1 = c2, v2y1 = r2, v2x2 = c2, v2y2 = r2;
                            if (currAction == ChestConfigManager.ACTION_TOP) { v2x2 = c2 + 1; }
                            else if (currAction == ChestConfigManager.ACTION_BOTTOM) { v2y1 = r2 + 1; v2x2 = c2 + 1; v2y2 = r2 + 1; }
                            else if (currAction == ChestConfigManager.ACTION_LEFT) { v2y2 = r2 + 1; }
                            else if (currAction == ChestConfigManager.ACTION_RIGHT) { v2x1 = c2 + 1; v2x2 = c2 + 1; v2y2 = r2 + 1; }

                            // Which vertex of the current line connects to the previous line?
                            boolean sharedV1 = (v2x1 == v1x1 && v2y1 == v1y1) || (v2x1 == v1x2 && v2y1 == v1y2);
                            boolean sharedV2 = (v2x2 == v1x1 && v2y2 == v1y1) || (v2x2 == v1x2 && v2y2 == v1y2);

                            boolean undo = false;
                            if (currAction == ChestConfigManager.ACTION_LEFT || currAction == ChestConfigManager.ACTION_RIGHT) {
                                if (sharedV1 && cRelY < 8) undo = true;
                                if (sharedV2 && cRelY > 8) undo = true;
                            } else {
                                if (sharedV1 && cRelX < 8) undo = true;
                                if (sharedV2 && cRelX > 8) undo = true;
                            }

                            if (undo) {
                                session.tracePath.remove(session.tracePath.size() - 1);
                                session.lockedTraceAction = prevAction;
                                Slot prevSlot = editor.accessor.getHandler().getSlot(prevSlotIdx);
                                if (prevAction == ChestConfigManager.ACTION_TOP || prevAction == ChestConfigManager.ACTION_BOTTOM) {
                                    session.lockedTraceAxis = 1;
                                    session.lockedTraceRowCol = prevSlotIdx / 9;
                                    session.lockedLineCoord = (prevAction == ChestConfigManager.ACTION_TOP) ? (guiY + prevSlot.y) : (guiY + prevSlot.y + 16);
                                } else {
                                    session.lockedTraceAxis = 2;
                                    session.lockedTraceRowCol = prevSlotIdx % 9;
                                    session.lockedLineCoord = (prevAction == ChestConfigManager.ACTION_LEFT) ? (guiX + prevSlot.x) : (guiX + prevSlot.x + 16);
                                }
                            } else {
                                break;
                            }
                        }

                        // 2. PAINT LOGIC (Advance crossing the middle)
                        String rawStep = editor.geometry.calculateTraceStep(slot, mouseX, mouseY);
                        if (rawStep != null && !rawStep.isEmpty()) {
                            if (!session.tracePath.contains(rawStep)) {
                                int rawSlotIdx = Integer.parseInt(rawStep.split("_")[0]);
                                String lastStep = session.tracePath.get(session.tracePath.size() - 1);
                                int lastSlotIdx = Integer.parseInt(lastStep.split("_")[0]);

                                int rawAction = Integer.parseInt(rawStep.split("_")[1]);
                                int lastAction = Integer.parseInt(lastStep.split("_")[1]);

                                Slot rawSlot = editor.accessor.getHandler().getSlot(rawSlotIdx);
                                double rRelX = mouseX - (guiX + rawSlot.x);
                                double rRelY = mouseY - (guiY + rawSlot.y);

                                int c1 = lastSlotIdx % 9, r1 = lastSlotIdx / 9;
                                int v1x1 = c1, v1y1 = r1, v1x2 = c1, v1y2 = r1;
                                if (lastAction == ChestConfigManager.ACTION_TOP) { v1x2 = c1 + 1; }
                                else if (lastAction == ChestConfigManager.ACTION_BOTTOM) { v1y1 = r1 + 1; v1x2 = c1 + 1; v1y2 = r1 + 1; }
                                else if (lastAction == ChestConfigManager.ACTION_LEFT) { v1y2 = r1 + 1; }
                                else if (lastAction == ChestConfigManager.ACTION_RIGHT) { v1x1 = c1 + 1; v1x2 = c1 + 1; v1y2 = r1 + 1; }

                                int c2 = rawSlotIdx % 9, r2 = rawSlotIdx / 9;
                                int v2x1 = c2, v2y1 = r2, v2x2 = c2, v2y2 = r2;
                                if (rawAction == ChestConfigManager.ACTION_TOP) { v2x2 = c2 + 1; }
                                else if (rawAction == ChestConfigManager.ACTION_BOTTOM) { v2y1 = r2 + 1; v2x2 = c2 + 1; v2y2 = r2 + 1; }
                                else if (rawAction == ChestConfigManager.ACTION_LEFT) { v2y2 = r2 + 1; }
                                else if (rawAction == ChestConfigManager.ACTION_RIGHT) { v2x1 = c2 + 1; v2x2 = c2 + 1; v2y2 = r2 + 1; }

                                boolean sharedV1 = (v2x1 == v1x1 && v2y1 == v1y1) || (v2x1 == v1x2 && v2y1 == v1y2);
                                boolean sharedV2 = (v2x2 == v1x1 && v2y2 == v1y1) || (v2x2 == v1x2 && v2y2 == v1y2);

                                if (sharedV1 || sharedV2) {
                                    boolean shouldAdd = false;
                                    if (rawAction == ChestConfigManager.ACTION_LEFT || rawAction == ChestConfigManager.ACTION_RIGHT) {
                                        if (sharedV1 && rRelY >= 8) shouldAdd = true;
                                        if (sharedV2 && rRelY <= 8) shouldAdd = true;
                                    } else {
                                        if (sharedV1 && rRelX >= 8) shouldAdd = true;
                                        if (sharedV2 && rRelX <= 8) shouldAdd = true;
                                    }
                                    if (shouldAdd) editor.updateTracePath(rawStep);
                                }
                            } else {
                                editor.updateTracePath(rawStep);
                            }
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    public void commitDrag(double mouseX, double mouseY) {
        if (session.dragStartSlot == null || session.dragCurrentSlot == null) return;
        ChestConfigManager manager = ChestConfigManager.getInstance();
        boolean changeMade = false;

        // --- LOGICA MODO COMBO ---
        if (session.currentTab == EditorSessionData.TAB_COMBO) {
            int colorToPaint = getCurrentSelectedComboColorValue();
            boolean explicitEraser = (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            if (colorToPaint == 0 && !explicitEraser) return;

            int allLinesAction = ChestConfigManager.ACTION_TOP | ChestConfigManager.ACTION_BOTTOM | ChestConfigManager.ACTION_LEFT | ChestConfigManager.ACTION_RIGHT;

            if (session.comboToolMode == 0) { // Combo Area
                int startRow = session.dragStartSlot.getIndex() / 9; int startCol = session.dragStartSlot.getIndex() % 9;
                int currRow = session.dragCurrentSlot.getIndex() / 9; int currCol = session.dragCurrentSlot.getIndex() % 9;
                int minRow = Math.min(startRow, currRow); int maxRow = Math.max(startRow, currRow);
                int minCol = Math.min(startCol, currCol); int maxCol = Math.max(startCol, currCol);

                for (Slot slot : editor.accessor.getHandler().slots) {
                    if (slot.inventory instanceof PlayerInventory) continue;
                    int r = slot.getIndex() / 9; int c = slot.getIndex() % 9;
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        if (explicitEraser || session.isDragModeErasing) {
                            manager.removeAction(slot.getIndex(), ChestConfigManager.ACTION_BG);
                            manager.removeAction(slot.getIndex(), allLinesAction);
                        } else {
                            manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_BG, colorToPaint);

                            if (r == minRow) manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_TOP, colorToPaint);
                            if (r == maxRow) manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_BOTTOM, colorToPaint);
                            if (c == minCol) manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_LEFT, colorToPaint);
                            if (c == maxCol) manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_RIGHT, colorToPaint);
                        }
                        changeMade = true;
                    }
                }
            } else { // Combo Trace
                java.util.Set<Integer> traceSlots = new java.util.HashSet<>();
                for (String step : session.tracePath) {
                    traceSlots.add(Integer.parseInt(step.split("_")[0]));
                }

                for (int slotIdx : traceSlots) {
                    if (explicitEraser || session.isDragModeErasing) {
                        manager.removeAction(slotIdx, ChestConfigManager.ACTION_BG);
                        manager.removeAction(slotIdx, allLinesAction);
                    } else {
                        manager.paintAction(slotIdx, ChestConfigManager.ACTION_BG, colorToPaint);

                        boolean hasTop = traceSlots.contains(slotIdx - 9);
                        boolean hasBottom = traceSlots.contains(slotIdx + 9);
                        boolean hasLeft = (slotIdx % 9 != 0) && traceSlots.contains(slotIdx - 1);
                        boolean hasRight = (slotIdx % 9 != 8) && traceSlots.contains(slotIdx + 1);

                        if (!hasTop) manager.paintAction(slotIdx, ChestConfigManager.ACTION_TOP, colorToPaint);
                        if (!hasBottom) manager.paintAction(slotIdx, ChestConfigManager.ACTION_BOTTOM, colorToPaint);
                        if (!hasLeft) manager.paintAction(slotIdx, ChestConfigManager.ACTION_LEFT, colorToPaint);
                        if (!hasRight) manager.paintAction(slotIdx, ChestConfigManager.ACTION_RIGHT, colorToPaint);
                    }
                    changeMade = true;
                }
            }
            if (changeMade) { editor.saveSmart(); editor.playClickSound(1.0f); }
            return;
        }
        // --- FIN LÓGICA MODO COMBO ---

        if (session.currentDragAction == ChestConfigManager.ACTION_BG) {
            int colorToPaint = getCurrentSelectedBgColorValue();
            boolean explicitEraser = (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            if (colorToPaint == 0 && !explicitEraser) return;

            if (session.bgToolMode == 0) {
                int sRow = session.dragStartSlot.getIndex() / 9; int sCol = session.dragStartSlot.getIndex() % 9;
                int cRow = session.dragCurrentSlot.getIndex() / 9; int cCol = session.dragCurrentSlot.getIndex() % 9;

                int minRow = Math.min(sRow, cRow); int maxRow = Math.max(sRow, cRow);
                int minCol = Math.min(sCol, cCol); int maxCol = Math.max(sCol, cCol);

                for (Slot slot : editor.accessor.getHandler().slots) {
                    if (slot.inventory instanceof PlayerInventory) continue;
                    int r = slot.getIndex() / 9; int c = slot.getIndex() % 9;
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        if (explicitEraser || session.isDragModeErasing) manager.removeAction(slot.getIndex(), ChestConfigManager.ACTION_BG);
                        else manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_BG, colorToPaint);
                        changeMade = true;
                    }
                }
            } else {
                for (String step : session.tracePath) {
                    int slotIdx = Integer.parseInt(step.split("_")[0]);
                    if (explicitEraser || session.isDragModeErasing) manager.removeAction(slotIdx, ChestConfigManager.ACTION_BG);
                    else manager.paintAction(slotIdx, ChestConfigManager.ACTION_BG, colorToPaint);
                    changeMade = true;
                }
            }
        } else { // PENCIL MODE
            int colorToPaint = getCurrentSelectedLineColorValue();
            boolean explicitEraser = (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            if (colorToPaint == 0 && !explicitEraser) return;

            if (session.lineToolMode == 0) { // Pencil Area
                int sRow = session.dragStartSlot.getIndex() / 9; int sCol = session.dragStartSlot.getIndex() % 9;
                int cRow = session.dragCurrentSlot.getIndex() / 9; int cCol = session.dragCurrentSlot.getIndex() % 9;

                int minRow = Math.min(sRow, cRow); int maxRow = Math.max(sRow, cRow);
                int minCol = Math.min(sCol, cCol); int maxCol = Math.max(sCol, cCol);

                int yTopRaw = minRow * 2; int yBotRaw = maxRow * 2 + 1;
                if (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow) yTopRaw = sRow * 2 + 1;
                else if (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow) yBotRaw = sRow * 2;

                int xLeftRaw = minCol * 2; int xRightRaw = maxCol * 2 + 1;
                if (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol) xLeftRaw = sCol * 2 + 1;
                else if (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol) xRightRaw = sCol * 2;

                boolean isOuterIntent = (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow) ||
                        (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow) ||
                        (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol) ||
                        (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol);

                int yTopExp = yTopRaw; int yBotExp = yBotRaw;
                int xLeftExp = xLeftRaw; int xRightExp = xRightRaw;

                if (isOuterIntent) {
                    if (yTopRaw % 2 == 0) yTopExp--;
                    if (yBotRaw % 2 != 0) yBotExp++;
                    if (xLeftRaw % 2 == 0) xLeftExp--;
                    if (xRightRaw % 2 != 0) xRightExp++;
                }

                int maxRows = editor.geometry.getContainerSlotCount() / 9;
                yTopExp = Math.max(0, Math.min(maxRows * 2 - 1, yTopExp));
                yBotExp = Math.max(0, Math.min(maxRows * 2 - 1, yBotExp));
                xLeftExp = Math.max(0, Math.min(17, xLeftExp));
                xRightExp = Math.max(0, Math.min(17, xRightExp));

                boolean isRectangle = editor.geometry.isDraggingRectangle(mouseX, mouseY);
                boolean erase = explicitEraser || session.isDragModeErasing;

                if (isRectangle) {
                    if (erase) {
                        for (int r = Math.max(0, minRow - 1); r <= maxRow + 1; r++) {
                            for (int c = Math.max(0, minCol - 1); c <= maxCol + 1; c++) {
                                int slotIdx = r * 9 + c;
                                if (slotIdx >= editor.geometry.getContainerSlotCount()) continue;

                                int topY = r * 2, botY = r * 2 + 1;
                                int leftX = c * 2, rightX = c * 2 + 1;

                                boolean hInside = (leftX >= xLeftExp) && (rightX <= xRightExp);
                                boolean vInside = (topY >= yTopExp) && (botY <= yBotExp);

                                if (hInside && topY >= yTopExp && topY <= yBotExp) manager.removeAction(slotIdx, ChestConfigManager.ACTION_TOP);
                                if (hInside && botY >= yTopExp && botY <= yBotExp) manager.removeAction(slotIdx, ChestConfigManager.ACTION_BOTTOM);
                                if (vInside && leftX >= xLeftExp && leftX <= xRightExp) manager.removeAction(slotIdx, ChestConfigManager.ACTION_LEFT);
                                if (vInside && rightX >= xLeftExp && rightX <= xRightExp) manager.removeAction(slotIdx, ChestConfigManager.ACTION_RIGHT);
                            }
                        }
                    } else {
                        int fillMinCol = (xLeftExp + 1) / 2; int fillMaxCol = (xRightExp - 1) / 2;
                        int topAction = (yTopExp % 2 == 0) ? ChestConfigManager.ACTION_TOP : ChestConfigManager.ACTION_BOTTOM;
                        int topRow = yTopExp / 2;
                        int botAction = (yBotExp % 2 == 0) ? ChestConfigManager.ACTION_TOP : ChestConfigManager.ACTION_BOTTOM;
                        int botRow = yBotExp / 2;

                        for (int c = fillMinCol; c <= fillMaxCol; c++) {
                            if (topRow * 9 + c < editor.geometry.getContainerSlotCount()) manager.paintAction(topRow * 9 + c, topAction, colorToPaint);
                            if (botRow * 9 + c < editor.geometry.getContainerSlotCount()) manager.paintAction(botRow * 9 + c, botAction, colorToPaint);
                        }

                        int fillMinRow = (yTopExp + 1) / 2; int fillMaxRow = (yBotExp - 1) / 2;
                        int leftAction = (xLeftExp % 2 == 0) ? ChestConfigManager.ACTION_LEFT : ChestConfigManager.ACTION_RIGHT;
                        int leftCol = xLeftExp / 2;
                        int rightAction = (xRightExp % 2 == 0) ? ChestConfigManager.ACTION_LEFT : ChestConfigManager.ACTION_RIGHT;
                        int rightCol = xRightExp / 2;

                        for (int r = fillMinRow; r <= fillMaxRow; r++) {
                            if (r * 9 + leftCol < editor.geometry.getContainerSlotCount()) manager.paintAction(r * 9 + leftCol, leftAction, colorToPaint);
                            if (r * 9 + rightCol < editor.geometry.getContainerSlotCount()) manager.paintAction(r * 9 + rightCol, rightAction, colorToPaint);
                        }
                    }
                    changeMade = true;
                } else { // 1D Line
                    for (int r = minRow; r <= maxRow; r++) {
                        for (int c = minCol; c <= maxCol; c++) {
                            if (erase) manager.removeAction(r * 9 + c, session.currentDragAction);
                            else manager.paintAction(r * 9 + c, session.currentDragAction, colorToPaint);
                        }
                    }
                    changeMade = true;
                }
            } else { // Pencil Trace
                for (String step : session.tracePath) {
                    String[] parts = step.split("_");
                    int slotIdx = Integer.parseInt(parts[0]);
                    int act = Integer.parseInt(parts[1]);
                    if (explicitEraser || session.isDragModeErasing) manager.removeAction(slotIdx, act);
                    else manager.paintAction(slotIdx, act, colorToPaint);
                    changeMade = true;
                }
            }
        }
        if (changeMade) { editor.saveSmart(); editor.playClickSound(1.0f); }
    }
}