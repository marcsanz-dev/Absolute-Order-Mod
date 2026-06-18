package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorGeometry;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;

/**
 * Handles mouse-click routing for the group-view screen: the conflict popup, the right-panel action
 * buttons, slot selection (area/trace), and the per-slot rule tabs. Extracted from ScreenViewGroups.
 */
final class ViewGroupsClickHandler {

    private final ScreenViewGroups screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;
    private final EditorGeometry geometry;

    ViewGroupsClickHandler(ScreenViewGroups screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
        this.geometry = screen.geometry;
    }

    boolean onMouseClicked(double mouseX, double mouseY, int button) {
        // Forward clicks to the conflict resolution popup; block all background clicks.
        if (session.hasSelectionConflict) {
            for (CustomWidget widget : screen.popupWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return true;
        }

        // Forward clicks to the confirm/cancel buttons during slot-selection mode.
        if (session.currentState == EditorState.SELECT_SLOTS) {
            for (CustomWidget widget : screen.selectSlotsWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }
        // Right-panel buttons (only active in VIEW_GROUPS state).
        else if (session.currentState == EditorState.VIEW_GROUPS) {
            if (editor.whitelistSearchBox != null) {
                if (editor.whitelistSearchBox.isMouseOver(mouseX, mouseY)) {
                    if (button == 0) editor.whitelistSearchBox.setFocused(true);
                    return true;
                } else if (button == 0) {
                    editor.whitelistSearchBox.setFocused(false);
                }
            }
            if (screen.clickWidgets(mouseX, mouseY, button)) return true;
        }

        // Slot selection logic for VIEW_GROUPS and SELECT_SLOTS.
        if (button == 0
                && (session.currentState == EditorState.VIEW_GROUPS
                        || session.currentState == EditorState.SELECT_SLOTS)) {
            Slot slot = editor.accessor.getFocusedSlot();
            if (slot != null && !(slot.inventory instanceof PlayerInventory)) {
                java.util.UUID groupId = editor.geometry.getGroupIdForSlot(slot.getIndex());
                long now = System.currentTimeMillis();
                boolean isDoubleClick =
                        (slot.getIndex() == session.lastClickedSlotIndex && (now - session.lastSlotClickTime) < 300);

                boolean isShiftDown = org.lwjgl.glfw.GLFW.glfwGetKey(
                                        MinecraftClient.getInstance()
                                                .getWindow()
                                                .getHandle(),
                                        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                                == org.lwjgl.glfw.GLFW.GLFW_PRESS
                        || org.lwjgl.glfw.GLFW.glfwGetKey(
                                        MinecraftClient.getInstance()
                                                .getWindow()
                                                .getHandle(),
                                        org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT)
                                == org.lwjgl.glfw.GLFW.GLFW_PRESS;

                if (session.currentState == EditorState.VIEW_GROUPS
                        && groupId != null
                        && session.selectedSlots.isEmpty()
                        && !isShiftDown
                        && !isDoubleClick) {
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
                    screen.transitionToEditFilter();
                    editor.playClickSound(1.0f);
                    return true;
                } else if (isDoubleClick) {
                    int maxSlots = editor.geometry.getContainerSlotCount();
                    java.util.Set<Integer> contiguous =
                            ChestConfigManager.getInstance().getContiguousSlots(slot.getIndex(), maxSlots);

                    if (session.isSelecting) session.selectedSlots.addAll(contiguous);
                    else session.selectedSlots.removeAll(contiguous);

                    session.lastClickedSlotIndex = slot.getIndex();
                    session.lastSlotClickTime = now;
                    editor.playClickSound(1.2f);
                    return true;
                } else {
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
            boolean isInsideMain = mouseX >= layout.guiX
                    && mouseX <= layout.guiX + layout.bgWidth
                    && mouseY >= layout.guiY
                    && mouseY <= layout.guiY + layout.bgHeight;
            boolean isInsideRight = mouseX >= layout.rightX
                    && mouseX <= layout.rightX + layout.btnW
                    && mouseY >= layout.mainY
                    && mouseY <= layout.mainY + 178 + layout.bH;

            boolean isInsideLeft = false;
            if (GlobalChestConfig.instance.showLeftPanel
                    || io.github.marcsanzdev.chestseparators.event.KeyInputHandler.isModifierPressed()) {
                isInsideLeft = mouseX >= layout.listX
                        && mouseX <= layout.listX + layout.listW
                        && mouseY >= layout.listY
                        && mouseY <= layout.listY + layout.listH;
            }

            if (!isInsideMain && !isInsideRight && !isInsideLeft) {
                if (session.currentState == EditorState.SELECT_SLOTS) {
                    editor.toggleState(EditorState.VIEW_GROUPS);
                } else {
                    editor.toggleState(EditorState.HIDDEN);
                }
                editor.playClickSound(1.0f);
                return true;
            }
        }

        return false;
    }
}
