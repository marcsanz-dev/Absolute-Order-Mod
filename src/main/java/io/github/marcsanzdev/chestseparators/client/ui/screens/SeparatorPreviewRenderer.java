package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorGeometry;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.slot.Slot;

/**
 * Draws the live hover and drag previews for the separator editor. Pure rendering: it reads
 * editor/session state and paints, never mutating anything. Extracted verbatim from ScreenDrawLines.
 */
final class SeparatorPreviewRenderer {

    private final ScreenDrawLines screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;
    private final EditorGeometry geometry;

    SeparatorPreviewRenderer(ScreenDrawLines screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
        this.geometry = screen.geometry;
    }

    void renderHoverPreview(DrawContext context, int mouseX, int mouseY) {
        Slot slot = editor.accessor.getFocusedSlot();
        if (slot != null && ChestSeparatorsEditor.isEditableSlot(slot)) {
            int tabMode = session.currentTab;
            int action = (tabMode == 2 || tabMode == 1)
                    ? ChestConfigManager.ACTION_BG
                    : geometry.calculateAction(slot, mouseX, mouseY);

            int colorVal = 0;
            boolean explicitEraser = false;
            if (tabMode == 0) {
                colorVal = screen.getCurrentSelectedLineColorValue();
                explicitEraser = (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            } else if (tabMode == 1) {
                colorVal = screen.getCurrentSelectedBgColorValue();
                explicitEraser = (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            } else if (tabMode == 2) {
                colorVal = screen.getCurrentSelectedComboColorValue();
                explicitEraser = (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            }

            if (colorVal == 0 && !explicitEraser) return;

            boolean willErase = explicitEraser;
            if (!willErase && tabMode != 2) {
                int existingColor = ChestConfigManager.getInstance().getColor(slot.getIndex(), action);
                if (existingColor == (colorVal | 0xFF000000)) willErase = true;
            }

            int colorBg = willErase ? 0x66FFFFFF : ((colorVal & 0x00FFFFFF) | 0x66000000);
            int colorLine = willErase ? 0x88FFFFFF : ((colorVal & 0x00FFFFFF) | 0x88000000);

            int x = layout.guiX + slot.x;
            int y = layout.guiY + slot.y;

            if (tabMode == 1) {
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_BG)
                                != 0) {
                    context.fill(x, y, x + 16, y + 16, colorBg);
                }
            } else if (tabMode == 0) {
                if (action != 0 && action != ChestConfigManager.ACTION_BG) {
                    if ((action & ChestConfigManager.ACTION_TOP) != 0
                            && (!willErase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(
                                                            ChestSeparatorsEditor.slotKey(slot),
                                                            ChestConfigManager.ACTION_TOP)
                                            != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if ((action & ChestConfigManager.ACTION_BOTTOM) != 0
                            && (!willErase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(
                                                            ChestSeparatorsEditor.slotKey(slot),
                                                            ChestConfigManager.ACTION_BOTTOM)
                                            != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if ((action & ChestConfigManager.ACTION_LEFT) != 0
                            && (!willErase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(
                                                            ChestSeparatorsEditor.slotKey(slot),
                                                            ChestConfigManager.ACTION_LEFT)
                                            != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if ((action & ChestConfigManager.ACTION_RIGHT) != 0
                            && (!willErase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(
                                                            ChestSeparatorsEditor.slotKey(slot),
                                                            ChestConfigManager.ACTION_RIGHT)
                                            != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                }
            } else {
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_BG)
                                != 0) context.fill(x, y, x + 16, y + 16, colorBg);
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_TOP)
                                != 0) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_BOTTOM)
                                != 0) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_LEFT)
                                != 0) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                if (!willErase
                        || ChestConfigManager.getInstance()
                                        .getColor(ChestSeparatorsEditor.slotKey(slot), ChestConfigManager.ACTION_RIGHT)
                                != 0) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
            }
        }
    }

    void renderDragPreview(DrawContext context, int mouseX, int mouseY) {
        if (!session.isDraggingLine) return;
        // The live drag preview uses raw grid indices; skip it for inventory drags (offset keys) to
        // avoid mismatched/out-of-range lookups. The actual paint still applies correctly on commit.
        if (session.dragStartSlot != null && ChestSeparatorsEditor.isPlayerSlot(session.dragStartSlot)) return;
        int guiX = layout.guiX;
        int guiY = layout.guiY;
        int tabMode = session.currentTab;

        int colorVal = 0;
        boolean explicitEraser = false;
        int tMode = 0;
        if (tabMode == 0) {
            colorVal = screen.getCurrentSelectedLineColorValue();
            explicitEraser = (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            tMode = session.lineToolMode;
        } else if (tabMode == 1) {
            colorVal = screen.getCurrentSelectedBgColorValue();
            explicitEraser = (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            tMode = session.bgToolMode;
        } else if (tabMode == 2) {
            colorVal = screen.getCurrentSelectedComboColorValue();
            explicitEraser = (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            tMode = session.comboToolMode;
        }

        if (colorVal == 0 && !explicitEraser) return;

        boolean erase = explicitEraser || session.isDragModeErasing;
        int colorBg = erase ? 0x66FFFFFF : ((colorVal & 0x00FFFFFF) | 0x66000000);
        int colorLine = erase ? 0x88FFFFFF : ((colorVal & 0x00FFFFFF) | 0x88000000);

        if (tabMode == 1) {
            if (tMode == 0) {
                int startRow = session.dragStartSlot.getIndex() / 9;
                int startCol = session.dragStartSlot.getIndex() % 9;
                int currRow = session.dragCurrentSlot.getIndex() / 9;
                int currCol = session.dragCurrentSlot.getIndex() % 9;
                int minRow = Math.min(startRow, currRow);
                int maxRow = Math.max(startRow, currRow);
                int minCol = Math.min(startCol, currCol);
                int maxCol = Math.max(startCol, currCol);
                for (Slot slot : editor.accessor.getHandler().slots) {
                    if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
                    int r = slot.getIndex() / 9;
                    int c = slot.getIndex() % 9;
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        if (erase
                                && ChestConfigManager.getInstance()
                                                .getColor(
                                                        ChestSeparatorsEditor.slotKey(slot),
                                                        ChestConfigManager.ACTION_BG)
                                        == 0) continue;
                        context.fill(guiX + slot.x, guiY + slot.y, guiX + slot.x + 16, guiY + slot.y + 16, colorBg);
                    }
                }
            } else {
                for (String step : session.tracePath) {
                    int slotIdx = Integer.parseInt(step.split("_")[0]);
                    if (erase && ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BG) == 0)
                        continue;
                    Slot slot = editor.accessor.getHandler().getSlot(slotIdx);
                    context.fill(guiX + slot.x, guiY + slot.y, guiX + slot.x + 16, guiY + slot.y + 16, colorBg);
                }
            }
        } else if (tabMode == 2) {
            if (tMode == 0) {
                int startRow = session.dragStartSlot.getIndex() / 9;
                int startCol = session.dragStartSlot.getIndex() % 9;
                int currRow = session.dragCurrentSlot.getIndex() / 9;
                int currCol = session.dragCurrentSlot.getIndex() % 9;
                int minRow = Math.min(startRow, currRow);
                int maxRow = Math.max(startRow, currRow);
                int minCol = Math.min(startCol, currCol);
                int maxCol = Math.max(startCol, currCol);
                for (Slot slot : editor.accessor.getHandler().slots) {
                    if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
                    int slotIdx = slot.getIndex();
                    int r = slotIdx / 9;
                    int c = slotIdx % 9;
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        int x = guiX + slot.x;
                        int y = guiY + slot.y;
                        if (!erase
                                || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BG)
                                        != 0) context.fill(x, y, x + 16, y + 16, colorBg);
                        if (r == minRow
                                && (!erase
                                        || ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_TOP)
                                                != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                        if (r == maxRow
                                && (!erase
                                        || ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM)
                                                != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                        if (c == minCol
                                && (!erase
                                        || ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_LEFT)
                                                != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                        if (c == maxCol
                                && (!erase
                                        || ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_RIGHT)
                                                != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                    }
                }
            } else {
                java.util.Set<Integer> traceSlots = new java.util.HashSet<>();
                for (String step : session.tracePath) {
                    traceSlots.add(Integer.parseInt(step.split("_")[0]));
                }
                for (int slotIdx : traceSlots) {
                    Slot slot = editor.accessor.getHandler().getSlot(slotIdx);
                    int x = guiX + slot.x;
                    int y = guiY + slot.y;
                    boolean hasTop = traceSlots.contains(slotIdx - 9);
                    boolean hasBottom = traceSlots.contains(slotIdx + 9);
                    boolean hasLeft = (slotIdx % 9 != 0) && traceSlots.contains(slotIdx - 1);
                    boolean hasRight = (slotIdx % 9 != 8) && traceSlots.contains(slotIdx + 1);

                    if (!erase || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_BG) != 0)
                        context.fill(x, y, x + 16, y + 16, colorBg);
                    if (!hasTop
                            && (!erase
                                    || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_TOP)
                                            != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if (!hasBottom
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM)
                                            != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if (!hasLeft
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_LEFT)
                                            != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if (!hasRight
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_RIGHT)
                                            != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                }
            }
        } else {
            if (tMode == 0) {
                int sRow = session.dragStartSlot.getIndex() / 9;
                int sCol = session.dragStartSlot.getIndex() % 9;
                int cRow = session.dragCurrentSlot.getIndex() / 9;
                int cCol = session.dragCurrentSlot.getIndex() % 9;
                int minRow = Math.min(sRow, cRow);
                int maxRow = Math.max(sRow, cRow);
                int minCol = Math.min(sCol, cCol);
                int maxCol = Math.max(sCol, cCol);

                int yTopRaw = minRow * 2;
                int yBotRaw = maxRow * 2 + 1;
                if (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow)
                    yTopRaw = sRow * 2 + 1;
                else if (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow) yBotRaw = sRow * 2;

                int xLeftRaw = minCol * 2;
                int xRightRaw = maxCol * 2 + 1;
                if (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol)
                    xLeftRaw = sCol * 2 + 1;
                else if (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol)
                    xRightRaw = sCol * 2;

                boolean isOuterIntent = (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && cRow > sRow)
                        || (session.currentDragAction == ChestConfigManager.ACTION_TOP && cRow < sRow)
                        || (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && cCol > sCol)
                        || (session.currentDragAction == ChestConfigManager.ACTION_LEFT && cCol < sCol);

                int yTopExp = yTopRaw;
                int yBotExp = yBotRaw;
                int xLeftExp = xLeftRaw;
                int xRightExp = xRightRaw;

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
                                int x = guiX + s.x;
                                int y = guiY + s.y;
                                int tY = r * 2, bY = r * 2 + 1;
                                int lX = c * 2, rX = c * 2 + 1;
                                boolean hInside = (lX >= xLeftExp) && (rX <= xRightExp);
                                boolean vInside = (tY >= yTopExp) && (bY <= yBotExp);
                                if (hInside
                                        && tY >= yTopExp
                                        && tY <= yBotExp
                                        && ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_TOP)
                                                != 0) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                                if (hInside
                                        && bY >= yTopExp
                                        && bY <= yBotExp
                                        && ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM)
                                                != 0) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                                if (vInside
                                        && lX >= xLeftExp
                                        && lX <= xRightExp
                                        && ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_LEFT)
                                                != 0) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                                if (vInside
                                        && rX >= xLeftExp
                                        && rX <= xRightExp
                                        && ChestConfigManager.getInstance()
                                                        .getColor(slotIdx, ChestConfigManager.ACTION_RIGHT)
                                                != 0) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                            }
                        }
                    }
                } else {
                    for (int r = minRow; r <= maxRow; r++) {
                        for (int c = minCol; c <= maxCol; c++) {
                            int slotIdx = r * 9 + c;
                            Slot s = editor.accessor.getHandler().getSlot(slotIdx);
                            int x = guiX + s.x;
                            int y = guiY + s.y;
                            if ((session.currentDragAction & ChestConfigManager.ACTION_TOP) != 0)
                                context.fill(x - 1, y - 1, x + 17, y, colorLine);
                            if ((session.currentDragAction & ChestConfigManager.ACTION_BOTTOM) != 0)
                                context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                            if ((session.currentDragAction & ChestConfigManager.ACTION_LEFT) != 0)
                                context.fill(x - 1, y - 1, x, y + 17, colorLine);
                            if ((session.currentDragAction & ChestConfigManager.ACTION_RIGHT) != 0)
                                context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                        }
                    }
                }
            } else {
                for (String step : session.tracePath) {
                    String[] parts = step.split("_");
                    int slotIdx = Integer.parseInt(parts[0]);
                    int act = Integer.parseInt(parts[1]);
                    Slot slot = editor.accessor.getHandler().getSlot(slotIdx);
                    int x = guiX + slot.x;
                    int y = guiY + slot.y;
                    if ((act & ChestConfigManager.ACTION_TOP) != 0
                            && (!erase
                                    || ChestConfigManager.getInstance().getColor(slotIdx, ChestConfigManager.ACTION_TOP)
                                            != 0)) context.fill(x - 1, y - 1, x + 17, y, colorLine);
                    if ((act & ChestConfigManager.ACTION_BOTTOM) != 0
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM)
                                            != 0)) context.fill(x - 1, y + 16, x + 17, y + 17, colorLine);
                    if ((act & ChestConfigManager.ACTION_LEFT) != 0
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_LEFT)
                                            != 0)) context.fill(x - 1, y - 1, x, y + 17, colorLine);
                    if ((act & ChestConfigManager.ACTION_RIGHT) != 0
                            && (!erase
                                    || ChestConfigManager.getInstance()
                                                    .getColor(slotIdx, ChestConfigManager.ACTION_RIGHT)
                                            != 0)) context.fill(x + 16, y - 1, x + 17, y + 17, colorLine);
                }
            }
        }
    }
}
