package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.UiColors;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * The dedicated inventory-presets overlay: a centered panel listing each preset slot with a Load and
 * a Save button, plus a saved/empty indicator. Rendering and hit-testing share the same geometry so
 * the buttons always line up. Owned by the editor and shown while {@code session.isPresetsMenuOpen}.
 */
public final class PresetsMenu {

    private static final int PANEL_W = 232;
    private static final int HEADER_H = 30;
    private static final int ROW_H = 24;
    private static final int FOOTER_H = 12;

    private static final int BTN_W = 60;
    private static final int BTN_H = 18;

    private final ChestSeparatorsEditor editor;

    public PresetsMenu(ChestSeparatorsEditor editor) {
        this.editor = editor;
    }

    /** Number of preset slots shown (configurable, clamped to a sensible range). */
    public static int presetCount() {
        return Math.max(1, Math.min(12, GlobalChestConfig.instance.inventoryPresetCount));
    }

    private int panelX(int screenW) {
        return (screenW - PANEL_W) / 2;
    }

    private int panelH() {
        return HEADER_H + presetCount() * ROW_H + FOOTER_H;
    }

    private int panelY(int screenH) {
        return (screenH - panelH()) / 2;
    }

    private int rowY(int screenH, int i) {
        return panelY(screenH) + HEADER_H + i * ROW_H;
    }

    private int loadX(int screenW) {
        return panelX(screenW) + 100;
    }

    private int saveX(int screenW) {
        return panelX(screenW) + 165;
    }

    public void render(DrawContext context, int screenW, int screenH, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        int px = panelX(screenW);
        int py = panelY(screenH);
        int ph = panelH();

        // Dim the whole screen, then draw the panel.
        context.fill(0, 0, screenW, screenH, 0xCC000000);
        context.fill(px, py, px + PANEL_W, py + ph, UiColors.SURFACE_DARK);
        context.drawStrokedRectangle(px, py, PANEL_W, ph, 0xFF000000);

        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.translatable("gui.chestseparators.presets_title"),
                px + PANEL_W / 2,
                py + 10,
                0xFFFFE066);

        int count = presetCount();
        for (int i = 0; i < count; i++) {
            int ry = rowY(screenH, i);
            boolean saved = ChestConfigManager.getInstance().inventoryPresetExists(i + 1);

            // Slot label and saved/empty indicator dot.
            context.drawText(
                    client.textRenderer,
                    Text.translatable("gui.chestseparators.preset_slot", i + 1),
                    px + 12,
                    ry + 6,
                    0xFFFFFFFF,
                    false);
            context.fill(px + 78, ry + 5, px + 90, ry + 17, saved ? 0xFF55FF55 : 0xFF555555);

            drawButton(
                    context,
                    loadX(screenW),
                    ry + 3,
                    Text.translatable("button.chestseparators.preset_load").getString(),
                    saved ? 0xFF2E7D32 : 0xFF3A3A3A,
                    saved,
                    mouseX,
                    mouseY);
            drawButton(
                    context,
                    saveX(screenW),
                    ry + 3,
                    Text.translatable("button.chestseparators.preset_save").getString(),
                    0xFF1565C0,
                    true,
                    mouseX,
                    mouseY);
        }

        // Close button (X) in the header.
        int cx = px + PANEL_W - 20;
        int cy = py + 6;
        boolean closeHover = inside(mouseX, mouseY, cx, cy, 14, 14);
        context.fill(cx, cy, cx + 14, cy + 14, closeHover ? 0xFFB23030 : 0xFF852D2D);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("x"), cx + 7, cy + 3, 0xFFFFFFFF);
    }

    private void drawButton(
            DrawContext context, int x, int y, String label, int color, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && inside(mouseX, mouseY, x, y, BTN_W, BTN_H);
        int bg = hover ? brighten(color) : color;
        context.fill(x, y, x + BTN_W, y + BTN_H, bg);
        context.drawStrokedRectangle(x, y, BTN_W, BTN_H, 0xFF000000);
        var tr = MinecraftClient.getInstance().textRenderer;
        context.drawText(tr, label, x + (BTN_W - tr.getWidth(label)) / 2, y + 5, 0xFFFFFFFF, false);
    }

    private static int brighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 25);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 25);
        int b = Math.min(255, (argb & 0xFF) + 25);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Routes a click inside the open menu. Returns true when the click was consumed. */
    public boolean onClick(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (button != 0) return true; // swallow non-left clicks while open
        int px = panelX(screenW);
        int py = panelY(screenH);
        int ph = panelH();

        // Close button.
        if (inside(mouseX, mouseY, px + PANEL_W - 20, py + 6, 14, 14)) {
            editor.getSession().isPresetsMenuOpen = false;
            editor.playClickSound(0.8f);
            return true;
        }

        // Click outside the panel closes it.
        if (!inside(mouseX, mouseY, px, py, PANEL_W, ph)) {
            editor.getSession().isPresetsMenuOpen = false;
            editor.playClickSound(0.8f);
            return true;
        }

        int count = presetCount();
        for (int i = 0; i < count; i++) {
            int ry = rowY(screenH, i);
            if (inside(mouseX, mouseY, loadX(screenW), ry + 3, BTN_W, BTN_H)) {
                editor.loadInventoryPresetSlot(i + 1);
                return true;
            }
            if (inside(mouseX, mouseY, saveX(screenW), ry + 3, BTN_W, BTN_H)) {
                editor.saveInventoryPresetSlot(i + 1);
                return true;
            }
        }
        return true; // clicks inside the panel are always consumed
    }
}
