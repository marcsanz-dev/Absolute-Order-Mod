package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.screen.slot.Slot;

/**
 * Applies a completed separator drag (area or trace, across the line/background/combo tabs) to
 * ChestConfigManager. Extracted verbatim from ScreenDrawLines.
 */
final class SeparatorDragCommitter {

    private final ScreenDrawLines screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;

    SeparatorDragCommitter(ScreenDrawLines screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
    }

    void commit(double mouseX, double mouseY) {
        if (session.dragStartSlot == null || session.dragCurrentSlot == null) return;
        ChestConfigManager manager = ChestConfigManager.getInstance();
        boolean changeMade = false;

        if (session.currentTab == EditorSessionData.TAB_COMBO) {
            int colorToPaint = screen.getCurrentSelectedComboColorValue();
            boolean explicitEraser = (session.comboColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            if (colorToPaint == 0 && !explicitEraser) return;

            int allLinesAction = ChestConfigManager.ACTION_TOP
                    | ChestConfigManager.ACTION_BOTTOM
                    | ChestConfigManager.ACTION_LEFT
                    | ChestConfigManager.ACTION_RIGHT;

            if (session.comboToolMode == 0) { // Combo Area
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
                        if (explicitEraser || session.isDragModeErasing) {
                            manager.removeAction(slot.getIndex(), ChestConfigManager.ACTION_BG);
                            manager.removeAction(slot.getIndex(), allLinesAction);
                        } else {
                            manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_BG, colorToPaint);

                            if (r == minRow)
                                manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_TOP, colorToPaint);
                            if (r == maxRow)
                                manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_BOTTOM, colorToPaint);
                            if (c == minCol)
                                manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_LEFT, colorToPaint);
                            if (c == maxCol)
                                manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_RIGHT, colorToPaint);
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
            if (changeMade) {
                editor.saveSmart();
                editor.playClickSound(1.0f);
            }
            return;
        }

        if (session.currentDragAction == ChestConfigManager.ACTION_BG) {
            int colorToPaint = screen.getCurrentSelectedBgColorValue();
            boolean explicitEraser = (session.bgColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            if (colorToPaint == 0 && !explicitEraser) return;

            if (session.bgToolMode == 0) {
                int sRow = session.dragStartSlot.getIndex() / 9;
                int sCol = session.dragStartSlot.getIndex() % 9;
                int cRow = session.dragCurrentSlot.getIndex() / 9;
                int cCol = session.dragCurrentSlot.getIndex() % 9;

                int minRow = Math.min(sRow, cRow);
                int maxRow = Math.max(sRow, cRow);
                int minCol = Math.min(sCol, cCol);
                int maxCol = Math.max(sCol, cCol);

                for (Slot slot : editor.accessor.getHandler().slots) {
                    if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
                    int r = slot.getIndex() / 9;
                    int c = slot.getIndex() % 9;
                    if (r >= minRow && r <= maxRow && c >= minCol && c <= maxCol) {
                        if (explicitEraser || session.isDragModeErasing)
                            manager.removeAction(slot.getIndex(), ChestConfigManager.ACTION_BG);
                        else manager.paintAction(slot.getIndex(), ChestConfigManager.ACTION_BG, colorToPaint);
                        changeMade = true;
                    }
                }
            } else {
                for (String step : session.tracePath) {
                    int slotIdx = Integer.parseInt(step.split("_")[0]);
                    if (explicitEraser || session.isDragModeErasing)
                        manager.removeAction(slotIdx, ChestConfigManager.ACTION_BG);
                    else manager.paintAction(slotIdx, ChestConfigManager.ACTION_BG, colorToPaint);
                    changeMade = true;
                }
            }
        } else { // PENCIL MODE
            int colorToPaint = screen.getCurrentSelectedLineColorValue();
            boolean explicitEraser = (session.lineColorIndex == ChestSeparatorsEditor.TOOL_ERASER_ID);
            if (colorToPaint == 0 && !explicitEraser) return;

            if (session.lineToolMode == 0) { // Pencil Area
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

                                if (hInside && topY >= yTopExp && topY <= yBotExp)
                                    manager.removeAction(slotIdx, ChestConfigManager.ACTION_TOP);
                                if (hInside && botY >= yTopExp && botY <= yBotExp)
                                    manager.removeAction(slotIdx, ChestConfigManager.ACTION_BOTTOM);
                                if (vInside && leftX >= xLeftExp && leftX <= xRightExp)
                                    manager.removeAction(slotIdx, ChestConfigManager.ACTION_LEFT);
                                if (vInside && rightX >= xLeftExp && rightX <= xRightExp)
                                    manager.removeAction(slotIdx, ChestConfigManager.ACTION_RIGHT);
                            }
                        }
                    } else {
                        int fillMinCol = (xLeftExp + 1) / 2;
                        int fillMaxCol = (xRightExp - 1) / 2;
                        int topAction =
                                (yTopExp % 2 == 0) ? ChestConfigManager.ACTION_TOP : ChestConfigManager.ACTION_BOTTOM;
                        int topRow = yTopExp / 2;
                        int botAction =
                                (yBotExp % 2 == 0) ? ChestConfigManager.ACTION_TOP : ChestConfigManager.ACTION_BOTTOM;
                        int botRow = yBotExp / 2;

                        for (int c = fillMinCol; c <= fillMaxCol; c++) {
                            if (topRow * 9 + c < editor.geometry.getContainerSlotCount())
                                manager.paintAction(topRow * 9 + c, topAction, colorToPaint);
                            if (botRow * 9 + c < editor.geometry.getContainerSlotCount())
                                manager.paintAction(botRow * 9 + c, botAction, colorToPaint);
                        }

                        int fillMinRow = (yTopExp + 1) / 2;
                        int fillMaxRow = (yBotExp - 1) / 2;
                        int leftAction =
                                (xLeftExp % 2 == 0) ? ChestConfigManager.ACTION_LEFT : ChestConfigManager.ACTION_RIGHT;
                        int leftCol = xLeftExp / 2;
                        int rightAction =
                                (xRightExp % 2 == 0) ? ChestConfigManager.ACTION_LEFT : ChestConfigManager.ACTION_RIGHT;
                        int rightCol = xRightExp / 2;

                        for (int r = fillMinRow; r <= fillMaxRow; r++) {
                            if (r * 9 + leftCol < editor.geometry.getContainerSlotCount())
                                manager.paintAction(r * 9 + leftCol, leftAction, colorToPaint);
                            if (r * 9 + rightCol < editor.geometry.getContainerSlotCount())
                                manager.paintAction(r * 9 + rightCol, rightAction, colorToPaint);
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
        if (changeMade) {
            editor.saveSmart();
            editor.playClickSound(1.0f);
        }
    }
}
