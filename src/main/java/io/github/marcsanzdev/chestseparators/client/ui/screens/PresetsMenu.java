package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.UiColors;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * The dedicated presets overlay, docked on the left. Lists each preset slot with a saved/empty
 * indicator and Load/Save buttons (in the mod's standard button style). Hovering a saved row previews
 * that preset directly on the real inventory/chest slots, alternating every couple of seconds between
 * the saved layout colors and the saved filter distribution. Drives either the inventory or the chest
 * preset store depending on {@code session.presetsMenuChestMode}. The container slots stay visible (the
 * sub-screen panels are hidden by the renderer) so the preview reads as "what would land where".
 */
public final class PresetsMenu {

    private static final int PANEL_X = 8;
    private static final int PANEL_W = 184;
    private static final int HEADER_H = 26;
    private static final int ROW_H = 22;
    private static final int FOOTER_H = 10;

    private static final int BTN_W = 52;
    private static final int BTN_H = 18;

    // How often the on-slot preview flips between the layout view and the filter view.
    private static final long PREVIEW_FLIP_MS = 2000L;

    // Stable colors for filter groups in the preview, mirroring the group view's palette.
    private static final int[] GROUP_PALETTE = {
        0xFFE53935, 0xFFF57C00, 0xFFFBC02D, 0xFF7CB342,
        0xFF388E3C, 0xFF00897B, 0xFF00ACC1, 0xFF1E88E5,
        0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFF795548
    };

    private final ChestSeparatorsEditor editor;

    /** Rebuilt every render; reused by {@link #onClick} so hit-testing matches what was drawn. */
    private final List<WideButtonWidget> rowButtons = new ArrayList<>();

    public PresetsMenu(ChestSeparatorsEditor editor) {
        this.editor = editor;
    }

    public static int presetCount() {
        return Math.max(1, Math.min(12, GlobalChestConfig.instance.inventoryPresetCount));
    }

    private boolean chestMode() {
        return editor.getSession().presetsMenuChestMode;
    }

    private boolean exists(int slot) {
        return chestMode()
                ? ChestConfigManager.getInstance().chestPresetExists(slot)
                : ChestConfigManager.getInstance().inventoryPresetExists(slot);
    }

    private int panelH() {
        return HEADER_H + presetCount() * ROW_H + FOOTER_H;
    }

    private int panelY(int screenH) {
        return Math.max(8, (screenH - panelH()) / 2);
    }

    private int rowY(int screenH, int i) {
        return panelY(screenH) + HEADER_H + i * ROW_H;
    }

    private int loadX() {
        return PANEL_X + PANEL_W - 8 - BTN_W * 2 - 4;
    }

    private int saveX() {
        return PANEL_X + PANEL_W - 8 - BTN_W;
    }

    public void render(DrawContext context, int screenW, int screenH, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean isDark = GlobalChestConfig.instance.darkMode;
        int py = panelY(screenH);
        int ph = panelH();

        // Light dim only: the real container slots stay clearly visible for the on-slot preview.
        context.fill(0, 0, screenW, screenH, 0x55000000);

        int hoveredRow = -1;
        rowButtons.clear();
        int count = presetCount();
        for (int i = 0; i < count; i++) {
            int ry = rowY(screenH, i);
            if (mouseX >= PANEL_X && mouseX <= PANEL_X + PANEL_W && mouseY >= ry && mouseY < ry + ROW_H) {
                hoveredRow = i;
            }
        }

        // Preview the hovered preset directly on the real slots (under the panel, which sits far left).
        if (hoveredRow >= 0 && exists(hoveredRow + 1)) {
            ChestConfigManager.PresetPreview preview = chestMode()
                    ? ChestConfigManager.getInstance().readChestPresetPreview(hoveredRow + 1)
                    : ChestConfigManager.getInstance().readInventoryPresetPreview(hoveredRow + 1);
            if (preview != null) {
                boolean showFilters = ((System.currentTimeMillis() / PREVIEW_FLIP_MS) % 2) == 1;
                renderPreviewOnSlots(context, preview, chestMode(), showFilters);
                drawPreviewBadge(context, screenW, showFilters);
            }
        }

        // Panel.
        context.fill(PANEL_X, py, PANEL_X + PANEL_W, py + ph, isDark ? UiColors.SURFACE_DARK : UiColors.SURFACE_LIGHT);
        context.drawStrokedRectangle(PANEL_X, py, PANEL_W, ph, 0xFF000000);
        Text title = Text.translatable(
                chestMode() ? "gui.chestseparators.chest_presets_title" : "gui.chestseparators.presets_title");
        context.drawCenteredTextWithShadow(client.textRenderer, title, PANEL_X + PANEL_W / 2, py + 9, 0xFFFFE066);

        for (int i = 0; i < count; i++) {
            int slot = i + 1;
            int ry = rowY(screenH, i);
            boolean saved = exists(slot);

            // Saved/empty indicator: a generated check icon when saved, an empty box otherwise.
            int ind = PANEL_X + 8;
            int indY = ry + (ROW_H - 14) / 2;
            context.fill(ind, indY, ind + 14, indY + 14, saved ? 0xFF24341F : 0xFF3A3A3A);
            context.drawStrokedRectangle(ind, indY, 14, 14, 0xFF000000);
            if (saved) {
                context.drawTexture(
                        net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                        ModTextures.ICON_CHECK,
                        ind + 1,
                        indY + 1,
                        0.0F,
                        0.0F,
                        12,
                        12,
                        32,
                        32,
                        32,
                        32,
                        -1);
            }

            context.drawText(
                    client.textRenderer,
                    Text.translatable("gui.chestseparators.preset_slot", slot),
                    PANEL_X + 26,
                    ry + (ROW_H - 8) / 2,
                    isDark ? 0xFFFFFFFF : 0xFF202020,
                    isDark);

            int by = ry + (ROW_H - BTN_H) / 2;
            WideButtonWidget load = new WideButtonWidget(
                    loadX(),
                    by,
                    BTN_W,
                    BTN_H,
                    Text.translatable("button.chestseparators.preset_load").getString(),
                    ModTextures.ICON_IMPORT,
                    () -> {
                        if (chestMode()) editor.loadChestPresetSlot(slot);
                        else {
                            editor.loadInventoryPresetSlot(slot);
                            editor.toggleState(io.github.marcsanzdev.chestseparators.client.EditorState.HIDDEN);
                        }
                    });
            load.isDisabled = !saved;
            WideButtonWidget save = new WideButtonWidget(
                    saveX(),
                    by,
                    BTN_W,
                    BTN_H,
                    Text.translatable("button.chestseparators.preset_save").getString(),
                    ModTextures.ICON_SAVE,
                    () -> {
                        if (chestMode()) editor.saveChestPresetSlot(slot);
                        else editor.saveInventoryPresetSlot(slot);
                    });
            load.render(context, mouseX, mouseY, 0);
            save.render(context, mouseX, mouseY, 0);
            rowButtons.add(load);
            rowButtons.add(save);
        }

        // Close button (X).
        int cx = PANEL_X + PANEL_W - 18;
        int cy = py + 6;
        boolean closeHover = inside(mouseX, mouseY, cx, cy, 14, 14);
        context.fill(cx, cy, cx + 14, cy + 14, closeHover ? 0xFFB23030 : 0xFF852D2D);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("x"), cx + 7, cy + 3, 0xFFFFFFFF);
    }

    /** Small label near the top of the screen telling the player which view the on-slot preview shows. */
    private void drawPreviewBadge(DrawContext context, int screenW, boolean showFilters) {
        MinecraftClient client = MinecraftClient.getInstance();
        Text label = Text.translatable(
                showFilters ? "gui.chestseparators.preview_filters" : "gui.chestseparators.preview_layout");
        int w = client.textRenderer.getWidth(label) + 10;
        int x = (screenW - w) / 2;
        context.fill(x, 4, x + w, 18, 0xCC000000);
        context.drawCenteredTextWithShadow(
                client.textRenderer, label, x + w / 2, 7, showFilters ? 0xFF8AD6FF : 0xFFFFE066);
    }

    // ---- On-slot preview ----

    private void renderPreviewOnSlots(
            DrawContext context, ChestConfigManager.PresetPreview preview, boolean chestKind, boolean showFilters) {
        int guiX = editor.accessor.getX();
        int guiY = editor.accessor.getY();
        Map<UUID, Integer> groupColors = assignGroupColors(preview.filters());

        for (Slot slot : editor.accessor.getHandler().slots) {
            if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
            boolean player = ChestSeparatorsEditor.isPlayerSlot(slot);
            if (chestKind == player) continue; // chest preset -> chest slots; inventory preset -> player slots
            int idx = slot.getIndex();
            int x = guiX + slot.x;
            int y = guiY + slot.y;

            if (showFilters) {
                SlotWhitelist wl = preview.filters().get(idx);
                if (wl != null) {
                    int col = (groupColors.getOrDefault(wl.groupId(), 0xFF888888) & 0x00FFFFFF) | 0xAA000000;
                    context.fill(x, y, x + 16, y + 16, col);
                }
            } else {
                int[] cc = ChestConfigManager.previewColors(preview.visual().get(idx));
                if (cc[4] != 0) context.fill(x, y, x + 16, y + 16, (cc[4] & 0x00FFFFFF) | 0x99000000);
                if (cc[0] != 0) context.fill(x - 1, y - 1, x + 17, y, (cc[0] & 0x00FFFFFF) | 0xFF000000);
                if (cc[1] != 0) context.fill(x - 1, y + 16, x + 17, y + 17, (cc[1] & 0x00FFFFFF) | 0xFF000000);
                if (cc[2] != 0) context.fill(x - 1, y - 1, x, y + 17, (cc[2] & 0x00FFFFFF) | 0xFF000000);
                if (cc[3] != 0) context.fill(x + 16, y - 1, x + 17, y + 17, (cc[3] & 0x00FFFFFF) | 0xFF000000);
            }
        }
    }

    private Map<UUID, Integer> assignGroupColors(Map<Integer, SlotWhitelist> filters) {
        Map<UUID, Integer> colors = new HashMap<>();
        List<UUID> order = new ArrayList<>();
        for (SlotWhitelist wl : filters.values()) {
            if (!order.contains(wl.groupId())) order.add(wl.groupId());
        }
        order.sort(UUID::compareTo);
        for (int i = 0; i < order.size(); i++) {
            colors.put(order.get(i), GROUP_PALETTE[i % GROUP_PALETTE.length]);
        }
        return colors;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Routes a click inside the open menu. Returns true when the click was consumed. */
    public boolean onClick(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (button != 0) return true;
        int py = panelY(screenH);
        int ph = panelH();

        if (inside(mouseX, mouseY, PANEL_X + PANEL_W - 18, py + 6, 14, 14)) {
            editor.getSession().isPresetsMenuOpen = false;
            editor.playClickSound(0.8f);
            return true;
        }

        for (WideButtonWidget b : rowButtons) {
            if (b.mouseClicked(mouseX, mouseY, button)) {
                editor.playClickSound(1.1f);
                return true;
            }
        }

        // A click on the visible container (right of the panel) is allowed to fall through so the menu
        // does not feel like a wall; clicks on the panel area are swallowed.
        if (!inside(mouseX, mouseY, PANEL_X, py, PANEL_W, ph)) {
            return false;
        }
        return true;
    }
}
