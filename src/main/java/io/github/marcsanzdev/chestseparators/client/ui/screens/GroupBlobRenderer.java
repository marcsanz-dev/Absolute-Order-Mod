package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;

/**
 * Renders the whitelist group "blobs" over the chest grid for the group-view screen, assigning a
 * stable color per group. Pure rendering, extracted verbatim from ScreenViewGroups.
 */
final class GroupBlobRenderer {

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

    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;
    private final Map<UUID, Integer> stableGroupColors = new HashMap<>();

    GroupBlobRenderer(ScreenViewGroups screen) {
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
    }

    void renderWhitelistGroups(DrawContext context, int mouseX, int mouseY) {
        int guiX = layout.guiX;
        int guiY = layout.guiY;
        var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();

        Set<Integer> activeGreenSlots = new HashSet<>(session.selectedSlots);
        Set<Integer> activeRedSlots = new HashSet<>();

        boolean isPreviewArea = session.currentState == EditorState.VIEW_GROUPS
                && session.isDraggingLine
                && session.wlToolMode == 0
                && session.dragStartSlot != null
                && session.dragCurrentSlot != null;

        if (isPreviewArea) {
            int sRow = session.dragStartSlot.getIndex() / 9;
            int sCol = session.dragStartSlot.getIndex() % 9;
            int cRow = session.dragCurrentSlot.getIndex() / 9;
            int cCol = session.dragCurrentSlot.getIndex() % 9;
            int minRow = Math.min(sRow, cRow);
            int maxRow = Math.max(sRow, cRow);
            int minCol = Math.min(sCol, cCol);
            int maxCol = Math.max(sCol, cCol);

            for (Slot slot : editor.accessor.getHandler().slots) {
                if (slot.inventory instanceof PlayerInventory) continue;
                int r = slot.getIndex() / 9;
                int c = slot.getIndex() % 9;
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

            // Gray indicator for slots that have no filter assigned yet.
            if (!hasWhitelist && !isActive) {
                int x = guiX + slot.x;
                int y = guiY + slot.y;
                context.fill(x + 2, y + 2, x + 14, y + 14, 0x44888888);
            }
        }

        if (whitelists != null) {
            Map<UUID, Set<Integer>> groups = new HashMap<>();
            for (Map.Entry<Integer, SlotWhitelist> entry : whitelists.entrySet()) {
                groups.computeIfAbsent(entry.getValue().groupId(), k -> new HashSet<>())
                        .add(entry.getKey());
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
                context.getMatrices().translate(guiX + slot.x + 4, guiY + slot.y + 4);
                context.getMatrices().scale(0.5f, 0.5f);

                com.mojang.blaze3d.pipeline.RenderPipeline pipeline =
                        net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
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
}
