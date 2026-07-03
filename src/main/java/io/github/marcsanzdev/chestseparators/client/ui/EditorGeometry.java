package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;

import java.util.UUID;

public class EditorGeometry {

    private final EditorSessionData session;
    private final HandledScreenAccessor accessor;

    public EditorGeometry(EditorSessionData session, HandledScreenAccessor accessor) {
        this.session = session;
        this.accessor = accessor;
    }

    public int getContainerSlotCount() {
        int count = 0;
        for (Slot slot : accessor.getHandler().slots) {
            if (!(slot.inventory instanceof PlayerInventory)) {
                count++;
            }
        }
        return count;
    }

    public UUID getGroupIdForSlot(int slotIndex) {
        var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
        if (whitelists != null && whitelists.containsKey(slotIndex)) {
            return whitelists.get(slotIndex).groupId();
        }
        return null;
    }

    public int calculateAction(Slot slot, double mouseX, double mouseY) {
        int guiX = accessor.getX(); int guiY = accessor.getY();
        double relativeX = mouseX - (guiX + slot.x); double relativeY = mouseY - (guiY + slot.y);

        // Shrink the dead zone (ACTION_BG) to a small 4x4 pixel square in the exact center.
        if (relativeX >= 6 && relativeX <= 10 && relativeY >= 6 && relativeY <= 10) return ChestConfigManager.ACTION_BG;

        double distTop = Math.abs(relativeY); double distBottom = Math.abs(relativeY - 16);
        double distLeft = Math.abs(relativeX); double distRight = Math.abs(relativeX - 16);
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
        int guiX = accessor.getX(); int guiY = accessor.getY();

        if (session.lockedTraceAxis == 1) { // Rail Horizontal
            // FIX: Bajamos la tolerancia de 14.0 a 7.5 para que el giro se dispare al cruzar el centro
            if (Math.abs(mouseY - session.lockedLineCoord) > 7.5) { // Tirón Vertical
                session.lockedTraceAxis = 2;
                double relativeX = mouseX - (guiX + hoverSlot.x);
                double distLeft = Math.abs(relativeX); double distRight = Math.abs(relativeX - 16);
                session.lockedTraceAction = (distLeft <= distRight) ? ChestConfigManager.ACTION_LEFT : ChestConfigManager.ACTION_RIGHT;
                session.lockedLineCoord = (distLeft <= distRight) ? (guiX + hoverSlot.x) : (guiX + hoverSlot.x + 16);
                session.lockedTraceRowCol = hoverSlot.getIndex() % 9; // Anclamos la nueva Columna
                return hoverSlot.getIndex() + "_" + session.lockedTraceAction;
            } else { // Atrapado en la horizontal (Ignora el temblor de mano)
                int col = hoverSlot.getIndex() % 9;
                int correctSlotIndex = (session.lockedTraceRowCol * 9) + col; // Fuerza el slot de la fila original
                return correctSlotIndex + "_" + session.lockedTraceAction;
            }
        } else if (session.lockedTraceAxis == 2) { // Rail Vertical
            // FIX: Bajamos la tolerancia de 14.0 a 7.5 para que el giro se dispare al cruzar el centro
            if (Math.abs(mouseX - session.lockedLineCoord) > 7.5) { // Tirón Horizontal
                session.lockedTraceAxis = 1;
                double relativeY = mouseY - (guiY + hoverSlot.y);
                double distTop = Math.abs(relativeY); double distBottom = Math.abs(relativeY - 16);
                session.lockedTraceAction = (distTop <= distBottom) ? ChestConfigManager.ACTION_TOP : ChestConfigManager.ACTION_BOTTOM;
                session.lockedLineCoord = (distTop <= distBottom) ? (guiY + hoverSlot.y) : (guiY + hoverSlot.y + 16);
                session.lockedTraceRowCol = hoverSlot.getIndex() / 9; // Anclamos la nueva Fila
                return hoverSlot.getIndex() + "_" + session.lockedTraceAction;
            } else { // Atrapado en la vertical
                int row = hoverSlot.getIndex() / 9;
                int correctSlotIndex = (row * 9) + session.lockedTraceRowCol; // Fuerza el slot de la columna original
                return correctSlotIndex + "_" + session.lockedTraceAction;
            }
        }

        int action = calculateAction(hoverSlot, mouseX, mouseY);
        return action == 0 ? "" : (hoverSlot.getIndex() + "_" + action);
    }

    public boolean isDraggingRectangle(double mouseX, double mouseY) {
        int minRow = Math.min(session.dragStartSlot.getIndex() / 9, session.dragCurrentSlot.getIndex() / 9);
        int maxRow = Math.max(session.dragStartSlot.getIndex() / 9, session.dragCurrentSlot.getIndex() / 9);
        int minCol = Math.min(session.dragStartSlot.getIndex() % 9, session.dragCurrentSlot.getIndex() % 9);
        int maxCol = Math.max(session.dragStartSlot.getIndex() % 9, session.dragCurrentSlot.getIndex() % 9);

        // Si abarca múltiples filas Y múltiples columnas, es SIEMPRE un rectángulo 2D
        if (minRow != maxRow && minCol != maxCol) return true;

        int guiX = accessor.getX(); int guiY = accessor.getY();

        // Arrastre horizontal (1xN) o en un solo slot (1x1)
        if (minRow == maxRow) {
            // Si bajamos o subimos más allá de la mitad del slot (8 píxeles), se convierte en rectángulo
            if (session.currentDragAction == ChestConfigManager.ACTION_TOP && mouseY > (guiY + session.dragStartSlot.y + 8)) return true;
            if (session.currentDragAction == ChestConfigManager.ACTION_BOTTOM && mouseY < (guiY + session.dragStartSlot.y + 8)) return true;

            // Si empezó en una pared (LEFT/RIGHT) pero arrastramos en horizontal cruzando columnas, hace la caja
            if (minCol != maxCol && (session.currentDragAction == ChestConfigManager.ACTION_LEFT || session.currentDragAction == ChestConfigManager.ACTION_RIGHT)) return true;
        }

        // Arrastre vertical (Nx1) o en un solo slot (1x1)
        if (minCol == maxCol) {
            // Si nos movemos a los lados más allá de la mitad del slot (8 píxeles), se convierte en rectángulo
            if (session.currentDragAction == ChestConfigManager.ACTION_LEFT && mouseX > (guiX + session.dragStartSlot.x + 8)) return true;
            if (session.currentDragAction == ChestConfigManager.ACTION_RIGHT && mouseX < (guiX + session.dragStartSlot.x + 8)) return true;

            // Si empezó en una pared (TOP/BOTTOM) pero arrastramos en vertical cruzando filas, hace la caja
            if (minRow != maxRow && (session.currentDragAction == ChestConfigManager.ACTION_TOP || session.currentDragAction == ChestConfigManager.ACTION_BOTTOM)) return true;
        }

        return false;
    }

}