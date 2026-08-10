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
import net.minecraft.inventory.Slot;

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
                // 1.12.2: GuiTextField has no isMouseOver; hit-test its public bounds. setFocus -> setFocused.
                if (editor.isHovering(editor.whitelistSearchBox.x, editor.whitelistSearchBox.y,
                        editor.whitelistSearchBox.width, editor.whitelistSearchBox.height, mouseX, mouseY)) {
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
            if (slot != null && ChestSeparatorsEditor.isEditableSlot(slot)) {
                int key = ChestSeparatorsEditor.slotKey(slot);
                // Mutual exclusion: ignore clicks on the dimmed (other-namespace) side once a selection
                // exists — a single filter cannot span the chest and the player inventory.
                if (!session.selectedSlots.isEmpty()
                        && ChestConfigManager.isInventoryKey(
                                        session.selectedSlots.iterator().next())
                                != ChestSeparatorsEditor.isPlayerSlot(slot)) {
                    return true;
                }
                java.util.UUID groupId = editor.geometry.getGroupIdForSlot(key);
                long now = System.currentTimeMillis();
                boolean isDoubleClick =
                        (key == session.lastClickedSlotIndex && (now - session.lastSlotClickTime) < 300);

                // 1.12.2: GLFW window key polling → GuiScreen#isShiftKeyDown (checks both L/R shift).
                boolean isShiftDown = net.minecraft.client.gui.GuiScreen.isShiftKeyDown();

                if (session.currentState == EditorState.VIEW_GROUPS
                        && groupId != null
                        && session.selectedSlots.isEmpty()
                        && !isShiftDown
                        && !isDoubleClick) {
                    session.selectedGroupId = groupId;
                    session.selectedSlots.clear();
                    java.util.Map<Integer, SlotWhitelist> whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
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
                    boolean player = ChestSeparatorsEditor.isPlayerSlot(slot);
                    int lo = player ? ChestConfigManager.PLAYER_KEY_OFFSET : 0;
                    int hi = lo + editor.geometry.getNamespaceSlotCount(player);
                    java.util.Set<Integer> contiguous =
                            ChestConfigManager.getInstance().getContiguousSlots(key, lo, hi);

                    if (session.isSelecting) session.selectedSlots.addAll(contiguous);
                    else session.selectedSlots.removeAll(contiguous);

                    session.lastClickedSlotIndex = key;
                    session.lastSlotClickTime = now;
                    editor.playClickSound(1.2f);
                    return true;
                } else {
                    if (session.selectedSlots.contains(key)) {
                        session.selectedSlots.remove(key);
                        session.isSelecting = false;
                    } else {
                        session.selectedSlots.add(key);
                        session.isSelecting = true;
                    }

                    session.isDraggingLine = true;
                    session.dragStartSlot = slot;
                    session.dragCurrentSlot = slot;

                    session.lastClickedSlotIndex = key;
                    session.lastSlotClickTime = now;
                    editor.playClickSound(1.2f);
                    return true;
                }
            }
        }
        // --- CLICK OUTSIDE / ON DEAD SPACE TO CLOSE ---
        if (button == 0 && !session.hasSelectionConflict && GlobalChestConfig.instance.closeOnClickOutside) {
            // Close on any click that is not over an editable slot and not on the side panels — including
            // dead space inside the container, which selects nothing and so should dismiss the panel.
            boolean overSlot = editor.accessor.getFocusedSlot() != null
                    && ChestSeparatorsEditor.isEditableSlot(editor.accessor.getFocusedSlot());
            boolean isInsideRight = mouseX >= layout.rightX
                    && mouseX <= layout.rightX + layout.btnW
                    && mouseY >= layout.mainY
                    && mouseY <= layout.mainY + 178 + layout.bH;

            boolean isInsideLeft = false;
            // TODO(1.12.2 port): event.KeyInputHandler / client.ui.ModKeyBindings are out of scope for this
            // cluster; the "show panel" modifier is polled directly (default Left Alt = LWJGL2 KEY_LMENU).
            if (GlobalChestConfig.instance.showLeftPanel
                    || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LMENU)) {
                isInsideLeft = mouseX >= layout.listX
                        && mouseX <= layout.listX + layout.listW
                        && mouseY >= layout.listY
                        && mouseY <= layout.listY + layout.listH;
            }

            if (!overSlot && !isInsideRight && !isInsideLeft) {
                if (session.currentState == EditorState.SELECT_SLOTS) {
                    editor.toggleState(EditorState.VIEW_GROUPS);
                } else {
                    editor.toggleState(EditorState.HIDDEN);
                }
                editor.playCloseSound();
                return true;
            }
        }

        return false;
    }
}
