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
 * The dedicated presets overlay, docked on the left and styled like the mod's other windows (beveled
 * container, icon buttons, an Exit button). Each preset row has a saved/empty indicator and Load/Save
 * buttons; hovering a saved row's Load button previews that preset directly on the real container
 * slots, alternating every couple of seconds between the saved layout colors and the saved filter
 * distribution. Drives the inventory or the chest preset store per {@code session.presetsMenuChestMode}.
 *
 * <p>The render is split into two phases so EditorRenderer can inject the saved-lines layer between
 * the dim overlay ({@link #renderBackground}) and the panel itself ({@link #renderPanel}).
 */
public final class PresetsMenu {

    private static final int PANEL_X = 8;
    private static final int PANEL_W = 220;
    private static final int HEADER_H = 26;
    private static final int ROW_H = 22;
    private static final int FOOTER_H = 28;

    private static final int BTN_W = 50;
    private static final int BTN_H = 18;

    // Each phase (layout, then filters) lasts this long; a full layout->filters->layout cycle is ~3s.
    private static final long PREVIEW_FLIP_MS = 1500L;

    // Which row's Load button is currently being previewed and when that hover began, so each new
    // hover restarts the cycle from the layout view rather than continuing a global clock.
    private int lastPreviewRow = -1;
    private long previewStartTime = 0L;

    /** Updated by renderBackground(); read by isPreviewActive() and EditorRenderer. */
    private int currentPreviewRow = -1;

    private static final int[] GROUP_PALETTE = {
        0xFFE53935, 0xFFF57C00, 0xFFFBC02D, 0xFF7CB342,
        0xFF388E3C, 0xFF00897B, 0xFF00ACC1, 0xFF1E88E5,
        0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFF795548
    };

    private final ChestSeparatorsEditor editor;

    /** Rebuilt every render; reused by {@link #onClick} so hit-testing matches what was drawn. */
    private final List<WideButtonWidget> clickables = new ArrayList<>();

    public PresetsMenu(ChestSeparatorsEditor editor) {
        this.editor = editor;
    }

    /** True when a Load-button hover preview is currently being painted on the real slots. */
    public boolean isPreviewActive() {
        return currentPreviewRow >= 0;
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

    private int saveX() {
        return PANEL_X + PANEL_W - 8 - BTN_W;
    }

    private int loadX() {
        return saveX() - 4 - BTN_W;
    }

    /**
     * Phase 1: full-screen dim overlay and optional on-slot Load preview.
     * Must be called before {@link #renderPanel} so EditorRenderer can paint the saved lines between.
     */
    public void renderBackground(DrawContext context, int screenW, int screenH, int mouseX, int mouseY) {
        // Light dim so the real container slots stay clearly visible for the on-slot preview.
        context.fill(0, 0, screenW, screenH, 0x55000000);

        // Determine which Load button (if any) is being hovered.
        int previewRow = -1;
        int count = presetCount();
        for (int i = 0; i < count; i++) {
            int by = rowY(screenH, i) + (ROW_H - BTN_H) / 2;
            if (exists(i + 1) && inside(mouseX, mouseY, loadX(), by, BTN_W, BTN_H)) {
                previewRow = i;
                break;
            }
        }

        // Restart the layout<->filters cycle from the layout view whenever the hovered Load changes.
        long now = System.currentTimeMillis();
        if (previewRow != lastPreviewRow) {
            lastPreviewRow = previewRow;
            previewStartTime = now;
        }
        currentPreviewRow = previewRow;

        if (previewRow >= 0) {
            ChestConfigManager.PresetPreview preview = chestMode()
                    ? ChestConfigManager.getInstance().readChestPresetPreview(previewRow + 1)
                    : ChestConfigManager.getInstance().readInventoryPresetPreview(previewRow + 1);
            if (preview != null) {
                boolean showFilters = ((now - previewStartTime) / PREVIEW_FLIP_MS) % 2 == 1;
                renderPreviewOnSlots(context, preview, chestMode(), showFilters);
                drawPreviewBadge(context, screenW, showFilters);
            }
        }
    }

    /**
     * Phase 2: beveled panel, rows, and buttons.
     * Call after {@link #renderBackground} (and after any lines injected by EditorRenderer).
     */
    public void renderPanel(DrawContext context, int screenW, int screenH, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean isDark = GlobalChestConfig.instance.darkMode;
        int py = panelY(screenH);
        int ph = panelH();

        context.fill(PANEL_X, py, PANEL_X + PANEL_W, py + ph, isDark ? UiColors.SURFACE_DARK : UiColors.SURFACE_LIGHT);
        drawBevel(context, PANEL_X, py, PANEL_W, ph, false);

        Text title = Text.translatable(
                chestMode() ? "gui.chestseparators.chest_presets_title" : "gui.chestseparators.presets_title");
        context.drawCenteredTextWithShadow(client.textRenderer, title, PANEL_X + PANEL_W / 2, py + 9, 0xFFFFE066);

        clickables.clear();
        int count = presetCount();
        for (int i = 0; i < count; i++) {
            int slot = i + 1;
            int ry = rowY(screenH, i);
            boolean saved = exists(slot);

            int by = ry + (ROW_H - BTN_H) / 2;
            boolean hoveringSave = inside(mouseX, mouseY, saveX(), by, BTN_W, BTN_H);

            // Saved/empty indicator with ghost-tick preview when hovering Save.
            int ind = PANEL_X + 8;
            int indY = ry + (ROW_H - 14) / 2;
            context.fill(ind, indY, ind + 14, indY + 14, (saved || hoveringSave) ? 0xFF24341F : 0xFF2B2B2B);
            drawBevel(context, ind, indY, 14, 14, true);
            if (saved) {
                // Dim the tick when hovering Save to hint the preset will be overwritten.
                drawCheckIcon(context, ind + 1, indY + 1, hoveringSave ? 0xAAFFFFFF : -1);
            } else if (hoveringSave) {
                // Ghost tick: preview of what pressing Save would produce.
                drawCheckIcon(context, ind + 1, indY + 1, 0x66FFFFFF);
            }

            context.drawText(
                    client.textRenderer,
                    Text.translatable("gui.chestseparators.preset_slot", slot),
                    PANEL_X + 28,
                    ry + (ROW_H - 8) / 2,
                    isDark ? 0xFFFFFFFF : 0xFF202020,
                    isDark);

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
            clickables.add(load);
            clickables.add(save);
        }

        int exitW = 70;
        WideButtonWidget exit = new WideButtonWidget(
                PANEL_X + (PANEL_W - exitW) / 2,
                py + ph - FOOTER_H + 5,
                exitW,
                BTN_H,
                Text.translatable("button.chestseparators.exit").getString(),
                ModTextures.ICON_CANCEL,
                () -> editor.getSession().isPresetsMenuOpen = false);
        exit.render(context, mouseX, mouseY, 0);
        clickables.add(exit);
    }

    /** Convenience: calls both phases in order (used when no line injection is needed). */
    public void render(DrawContext context, int screenW, int screenH, int mouseX, int mouseY) {
        renderBackground(context, screenW, screenH, mouseX, mouseY);
        renderPanel(context, screenW, screenH, mouseX, mouseY);
    }

    private void drawCheckIcon(DrawContext context, int x, int y, int color) {
        context.drawTexture(
                net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                ModTextures.ICON_CHECK,
                x,
                y,
                0.0F,
                0.0F,
                12,
                12,
                32,
                32,
                32,
                32,
                color);
    }

    /** Small label near the top telling the player which view the on-slot preview is showing. */
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
            if (chestKind == player) continue;
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

    /** Raised/sunken bevel matching {@code CustomWidget#drawDarkBevel} so the panel fits the mod style. */
    private void drawBevel(DrawContext context, int x, int y, int width, int height, boolean sunken) {
        boolean isDark = GlobalChestConfig.instance.darkMode;
        int light = isDark ? 0xFF505050 : 0xFFFFFFFF;
        int dark = isDark ? 0xFF000000 : 0xFF555555;
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
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Routes a click inside the open menu. Returns true when the click was consumed. */
    public boolean onClick(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (button != 0) return true;
        int py = panelY(screenH);
        int ph = panelH();

        for (WideButtonWidget b : clickables) {
            if (b.mouseClicked(mouseX, mouseY, button)) {
                editor.playClickSound(1.1f);
                return true;
            }
        }

        return inside(mouseX, mouseY, PANEL_X, py, PANEL_W, ph);
    }
}
