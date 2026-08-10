package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
// TODO(1.12.2 port): HandledScreenAccessor — the GuiContainer accessor mixin (mixin.client) is not ported yet.
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import java.util.UUID;
import net.minecraft.inventory.Slot;

public class EditorGeometry {

    private final EditorSessionData session;
    private final HandledScreenAccessor accessor;

    public EditorGeometry(EditorSessionData session, HandledScreenAccessor accessor) {
        this.session = session;
        this.accessor = accessor;
    }

    public int getContainerSlotCount() {
        int count = 0;
        // 1.12.2: AbstractContainerMenu#slots → Container#inventorySlots.
        for (Slot slot : accessor.getHandler().inventorySlots) {
            // TODO(1.12.2 port): ChestSeparatorsEditor
            if (ChestSeparatorsEditor.isEditableSlot(slot)) {
                count++;
            }
        }
        return count;
    }

    /** Number of editable slots in one namespace (player inventory vs container), for grid bounds. */
    public int getNamespaceSlotCount(boolean player) {
        int count = 0;
        for (Slot slot : accessor.getHandler().inventorySlots) {
            // TODO(1.12.2 port): ChestSeparatorsEditor
            if (ChestSeparatorsEditor.isEditableSlot(slot) && ChestSeparatorsEditor.isPlayerSlot(slot) == player) {
                count++;
            }
        }
        return count;
    }

    public UUID getGroupIdForSlot(int slotIndex) {
        java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
        if (whitelists != null && whitelists.containsKey(slotIndex)) {
            return whitelists.get(slotIndex).groupId();
        }
        return null;
    }

    public int calculateAction(Slot slot, double mouseX, double mouseY) {
        int guiX = accessor.getX();
        int guiY = accessor.getY();
        // 1.12.2: Mojmap Slot#x / #y → MCP Slot#xPos / #yPos (the slot's GUI-local coords).
        double relativeX = mouseX - (guiX + slot.xPos);
        double relativeY = mouseY - (guiY + slot.yPos);

        // Shrink the dead zone (ACTION_BG) to a small 4x4 pixel square in the exact center.
        if (relativeX >= 6 && relativeX <= 10 && relativeY >= 6 && relativeY <= 10) return ChestConfigManager.ACTION_BG;

        double distTop = Math.abs(relativeY);
        double distBottom = Math.abs(relativeY - 16);
        double distLeft = Math.abs(relativeX);
        double distRight = Math.abs(relativeX - 16);
        double minDist = Math.min(Math.min(distTop, distBottom), Math.min(distLeft, distRight));

        // Increase the edge detection range to cover the rest of the slot
        if (minDist <= 8.0) {
            if (minDist == distTop) return ChestConfigManager.ACTION_TOP;
            else if (minDist == distBottom) return ChestConfigManager.ACTION_BOTTOM;
            else if (minDist == distLeft) return ChestConfigManager.ACTION_LEFT;
            else if (minDist == distRight) return ChestConfigManager.ACTION_RIGHT;
        }
        return 0;
    }

    public String calculateTraceStep(Slot hoverSlot, double mouseX, double mouseY) {
        int guiX = accessor.getX();
        int guiY = accessor.getY();

        if (session.lockedTraceAxis == 1) {
            // A pull of more than 7.5 px away from the locked horizontal axis transitions to vertical rail.
            if (Math.abs(mouseY - session.lockedLineCoord) > 7.5) {
                session.lockedTraceAxis = 2;
                double relativeX = mouseX - (guiX + hoverSlot.xPos);
                double distLeft = Math.abs(relativeX);
                double distRight = Math.abs(relativeX - 16);
                session.lockedTraceAction =
                        (distLeft <= distRight) ? ChestConfigManager.ACTION_LEFT : ChestConfigManager.ACTION_RIGHT;
                session.lockedLineCoord = (distLeft <= distRight) ? (guiX + hoverSlot.xPos) : (guiX + hoverSlot.xPos + 16);
                // TODO(1.12.2 port): ChestSeparatorsEditor
                session.lockedTraceRowCol = ChestSeparatorsEditor.realIndex(hoverSlot) % 9;
                return ChestSeparatorsEditor.slotKey(hoverSlot) + "_" + session.lockedTraceAction;
            } else {
                // Armor/offhand are isolated non-grid cells: keep the locked action but paint it on
                // the hovered cell itself instead of projecting along a meaningless grid row/col.
                // TODO(1.12.2 port): ChestSeparatorsEditor
                if (ChestConfigManager.isNonGridInventoryKey(ChestSeparatorsEditor.slotKey(hoverSlot)))
                    return ChestSeparatorsEditor.slotKey(hoverSlot) + "_" + session.lockedTraceAction;
                int col = ChestSeparatorsEditor.realIndex(hoverSlot) % 9;
                int correctSlotIndex = (session.lockedTraceRowCol * 9) + col;
                return (correctSlotIndex
                                + (ChestSeparatorsEditor.isPlayerSlot(hoverSlot)
                                        ? ChestConfigManager.PLAYER_KEY_OFFSET
                                        : 0))
                        + "_" + session.lockedTraceAction;
            }
        } else if (session.lockedTraceAxis == 2) {
            // A pull of more than 7.5 px away from the locked vertical axis transitions to horizontal rail.
            if (Math.abs(mouseX - session.lockedLineCoord) > 7.5) {
                session.lockedTraceAxis = 1;
                double relativeY = mouseY - (guiY + hoverSlot.yPos);
                double distTop = Math.abs(relativeY);
                double distBottom = Math.abs(relativeY - 16);
                session.lockedTraceAction =
                        (distTop <= distBottom) ? ChestConfigManager.ACTION_TOP : ChestConfigManager.ACTION_BOTTOM;
                session.lockedLineCoord = (distTop <= distBottom) ? (guiY + hoverSlot.yPos) : (guiY + hoverSlot.yPos + 16);
                // TODO(1.12.2 port): ChestSeparatorsEditor
                session.lockedTraceRowCol = ChestSeparatorsEditor.realIndex(hoverSlot) / 9;
                return ChestSeparatorsEditor.slotKey(hoverSlot) + "_" + session.lockedTraceAction;
            } else {
                // Armor/offhand are isolated non-grid cells: keep the locked action but paint it on
                // the hovered cell itself instead of projecting along a meaningless grid row/col.
                // TODO(1.12.2 port): ChestSeparatorsEditor
                if (ChestConfigManager.isNonGridInventoryKey(ChestSeparatorsEditor.slotKey(hoverSlot)))
                    return ChestSeparatorsEditor.slotKey(hoverSlot) + "_" + session.lockedTraceAction;
                int row = ChestSeparatorsEditor.realIndex(hoverSlot) / 9;
                int correctSlotIndex = (row * 9) + session.lockedTraceRowCol;
                return (correctSlotIndex
                                + (ChestSeparatorsEditor.isPlayerSlot(hoverSlot)
                                        ? ChestConfigManager.PLAYER_KEY_OFFSET
                                        : 0))
                        + "_" + session.lockedTraceAction;
            }
        }

        int action = calculateAction(hoverSlot, mouseX, mouseY);
        // TODO(1.12.2 port): ChestSeparatorsEditor
        return action == 0 ? "" : (ChestSeparatorsEditor.slotKey(hoverSlot) + "_" + action);
    }

    public boolean isDraggingRectangle(double mouseX, double mouseY) {
        // TODO(1.12.2 port): ChestSeparatorsEditor
        int minRow = Math.min(
                ChestSeparatorsEditor.realIndex(session.dragStartSlot) / 9,
                ChestSeparatorsEditor.realIndex(session.dragCurrentSlot) / 9);
        int maxRow = Math.max(
                ChestSeparatorsEditor.realIndex(session.dragStartSlot) / 9,
                ChestSeparatorsEditor.realIndex(session.dragCurrentSlot) / 9);
        int minCol = Math.min(
                ChestSeparatorsEditor.realIndex(session.dragStartSlot) % 9,
                ChestSeparatorsEditor.realIndex(session.dragCurrentSlot) % 9);
        int maxCol = Math.max(
                ChestSeparatorsEditor.realIndex(session.dragStartSlot) % 9,
                ChestSeparatorsEditor.realIndex(session.dragCurrentSlot) % 9);

        // Spanning multiple rows AND columns is always a 2-D rectangle.
        if (minRow != maxRow && minCol != maxCol) return true;

        int guiX = accessor.getX();
        int guiY = accessor.getY();

        if (minRow == maxRow) {
            // Crossing the slot midpoint vertically while on a horizontal edge converts to a rectangle.
            if (session.currentDragAction == ChestConfigManager.ACTION_TOP
                    && mouseY > (guiY + session.dragStartSlot.yPos + 8)) return true;
            if (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM
                    && mouseY < (guiY + session.dragStartSlot.yPos + 8)) return true;

            // Dragging a left/right edge across multiple columns also forms a rectangle.
            if (minCol != maxCol
                    && (session.currentDragAction == ChestConfigManager.ACTION_LEFT
                            || session.currentDragAction == ChestConfigManager.ACTION_RIGHT)) return true;
        }

        if (minCol == maxCol) {
            // Crossing the slot midpoint horizontally while on a vertical edge converts to a rectangle.
            if (session.currentDragAction == ChestConfigManager.ACTION_LEFT
                    && mouseX > (guiX + session.dragStartSlot.xPos + 8)) return true;
            if (session.currentDragAction == ChestConfigManager.ACTION_RIGHT
                    && mouseX < (guiX + session.dragStartSlot.xPos + 8)) return true;

            // Dragging a top/bottom edge across multiple rows also forms a rectangle.
            if (minRow != maxRow
                    && (session.currentDragAction == ChestConfigManager.ACTION_TOP
                            || session.currentDragAction == ChestConfigManager.ACTION_BOTTOM)) return true;
        }

        return false;
    }
}
