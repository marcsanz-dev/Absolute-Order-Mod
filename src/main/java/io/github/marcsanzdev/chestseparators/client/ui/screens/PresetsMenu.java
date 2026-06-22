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
import net.minecraft.text.Text;

/**
 * The dedicated presets overlay, docked on the left. Lists each preset slot with a saved/empty
 * indicator and Load/Save buttons (in the mod's standard button style). Hovering a row shows a large
 * preview panel to the right, alternating every couple of seconds between the saved layout colors and
 * the saved filter distribution. Drives either the inventory or the chest preset store depending on
 * {@code session.presetsMenuChestMode}.
 */
public final class PresetsMenu {

    private static final int PANEL_X = 10;
    private static final int PANEL_W = 196;
    private static final int HEADER_H = 26;
    private static final int ROW_H = 22;
    private static final int FOOTER_H = 10;

    private static final int BTN_W = 56;
    private static final int BTN_H = 18;

    // How often the hover preview flips between the layout view and the filter view.
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

        context.fill(0, 0, screenW, screenH, 0xCC000000);
        context.fill(PANEL_X, py, PANEL_X + PANEL_W, py + ph, isDark ? UiColors.SURFACE_DARK : UiColors.SURFACE_LIGHT);
        context.drawStrokedRectangle(PANEL_X, py, PANEL_W, ph, 0xFF000000);

        Text title = Text.translatable(
                chestMode() ? "gui.chestseparators.chest_presets_title" : "gui.chestseparators.presets_title");
        context.drawCenteredTextWithShadow(client.textRenderer, title, PANEL_X + PANEL_W / 2, py + 9, 0xFFFFE066);

        rowButtons.clear();
        int count = presetCount();
        int hoveredRow = -1;
        for (int i = 0; i < count; i++) {
            int slot = i + 1;
            int ry = rowY(screenH, i);
            boolean saved = exists(slot);

            if (mouseX >= PANEL_X && mouseX <= PANEL_X + PANEL_W && mouseY >= ry && mouseY < ry + ROW_H) {
                hoveredRow = i;
            }

            // Saved/empty indicator (the "tick"): a check on green when saved, an empty box otherwise.
            int ind = PANEL_X + 8;
            int indY = ry + (ROW_H - 12) / 2;
            context.fill(ind, indY, ind + 12, indY + 12, saved ? 0xFF2E7D32 : 0xFF3A3A3A);
            context.drawStrokedRectangle(ind, indY, 12, 12, 0xFF000000);
            if (saved) {
                // A small check mark drawn from two strokes so it never depends on a font glyph.
                context.fill(ind + 3, indY + 6, ind + 5, indY + 9, 0xFFFFFFFF);
                context.fill(ind + 4, indY + 7, ind + 6, indY + 9, 0xFFFFFFFF);
                context.fill(ind + 6, indY + 4, ind + 9, indY + 7, 0xFFFFFFFF);
                context.fill(ind + 8, indY + 3, ind + 9, indY + 5, 0xFFFFFFFF);
            }

            context.drawText(
                    client.textRenderer,
                    Text.translatable("gui.chestseparators.preset_slot", slot),
                    PANEL_X + 24,
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

        // Close button (X) in the header.
        int cx = PANEL_X + PANEL_W - 18;
        int cy = py + 6;
        boolean closeHover = inside(mouseX, mouseY, cx, cy, 14, 14);
        context.fill(cx, cy, cx + 14, cy + 14, closeHover ? 0xFFB23030 : 0xFF852D2D);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("x"), cx + 7, cy + 3, 0xFFFFFFFF);

        // Hover preview panel to the right of the menu, alternating layout <-> filters.
        if (hoveredRow >= 0 && exists(hoveredRow + 1)) {
            ChestConfigManager.PresetPreview preview = chestMode()
                    ? ChestConfigManager.getInstance().readChestPresetPreview(hoveredRow + 1)
                    : ChestConfigManager.getInstance().readInventoryPresetPreview(hoveredRow + 1);
            if (preview != null) {
                boolean showFilters = ((System.currentTimeMillis() / PREVIEW_FLIP_MS) % 2) == 1;
                drawPreviewPanel(context, PANEL_X + PANEL_W + 12, py, preview, chestMode(), showFilters, isDark);
            }
        }
    }

    // ---- Preview panel ----

    private void drawPreviewPanel(
            DrawContext context,
            int x,
            int y,
            ChestConfigManager.PresetPreview preview,
            boolean chestKind,
            boolean showFilters,
            boolean isDark) {
        MinecraftClient client = MinecraftClient.getInstance();
        int cell = 12;
        int gap = 1;
        int cols = 9;
        int rows = chestKind ? 6 : 5; // chest: 6 rows; inventory: armor + 3 main + hotbar
        int gridW = cols * (cell + gap) - gap;
        int gridH = rows * (cell + gap) - gap;
        int pad = 8;
        int header = 14;
        int pw = gridW + pad * 2;
        int phh = gridH + pad * 2 + header;

        context.fill(x, y, x + pw, y + phh, isDark ? UiColors.SURFACE_DARK : UiColors.SURFACE_LIGHT);
        context.drawStrokedRectangle(x, y, pw, phh, 0xFF000000);
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.translatable(
                        showFilters ? "gui.chestseparators.preview_filters" : "gui.chestseparators.preview_layout"),
                x + pw / 2,
                y + 3,
                showFilters ? 0xFF8AD6FF : 0xFFFFE066);

        Map<UUID, Integer> groupColors = assignGroupColors(preview.filters());
        int gx = x + pad;
        int gy = y + pad + header;

        if (chestKind) {
            for (int idx = 0; idx < 54; idx++) {
                int r = idx / 9;
                int c = idx % 9;
                drawCell(
                        context,
                        gx + c * (cell + gap),
                        gy + r * (cell + gap),
                        cell,
                        idx,
                        preview,
                        groupColors,
                        showFilters);
            }
        } else {
            // Armor (36..39) + offhand (40) on the top row, main inventory (9..35), then hotbar (0..8).
            for (int k = 0; k < 5; k++) {
                drawCell(context, gx + k * (cell + gap), gy, cell, 36 + k, preview, groupColors, showFilters);
            }
            for (int idx = 9; idx < 36; idx++) {
                int r = 1 + (idx - 9) / 9;
                int c = (idx - 9) % 9;
                drawCell(
                        context,
                        gx + c * (cell + gap),
                        gy + r * (cell + gap),
                        cell,
                        idx,
                        preview,
                        groupColors,
                        showFilters);
            }
            for (int idx = 0; idx < 9; idx++) {
                drawCell(
                        context,
                        gx + idx * (cell + gap),
                        gy + 4 * (cell + gap),
                        cell,
                        idx,
                        preview,
                        groupColors,
                        showFilters);
            }
        }
    }

    private void drawCell(
            DrawContext context,
            int cx,
            int cy,
            int cell,
            int idx,
            ChestConfigManager.PresetPreview preview,
            Map<UUID, Integer> groupColors,
            boolean showFilters) {
        context.fill(cx, cy, cx + cell, cy + cell, 0xFF2B2B2B);

        if (showFilters) {
            SlotWhitelist wl = preview.filters().get(idx);
            if (wl != null) {
                int col = groupColors.getOrDefault(wl.groupId(), 0xFF888888);
                context.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, col);
            }
        } else {
            int[] cc = ChestConfigManager.previewColors(preview.visual().get(idx));
            if (cc[4] != 0) context.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, cc[4] | 0xFF000000);
            if (cc[0] != 0) context.fill(cx, cy, cx + cell, cy + 2, cc[0] | 0xFF000000);
            if (cc[1] != 0) context.fill(cx, cy + cell - 2, cx + cell, cy + cell, cc[1] | 0xFF000000);
            if (cc[2] != 0) context.fill(cx, cy, cx + 2, cy + cell, cc[2] | 0xFF000000);
            if (cc[3] != 0) context.fill(cx + cell - 2, cy, cx + cell, cy + cell, cc[3] | 0xFF000000);
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

        // Buttons built during the last render carry the correct row index and store action.
        for (WideButtonWidget b : rowButtons) {
            if (b.mouseClicked(mouseX, mouseY, button)) {
                editor.playClickSound(1.1f);
                return true;
            }
        }

        // Click outside the panel closes it.
        if (!inside(mouseX, mouseY, PANEL_X, py, PANEL_W, ph)) {
            editor.getSession().isPresetsMenuOpen = false;
            editor.playClickSound(0.8f);
            return true;
        }
        return true;
    }
}
