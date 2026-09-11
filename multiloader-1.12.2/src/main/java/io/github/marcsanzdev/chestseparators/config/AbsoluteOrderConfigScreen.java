package io.github.marcsanzdev.chestseparators.config;

import io.github.marcsanzdev.chestseparators.client.compat.GuiGraphics;
import io.github.marcsanzdev.chestseparators.client.ui.UiTheme;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Custom "Cristal" (dark glass) mod-settings screen for 1.12.2, replacing Forge's grey {@code GuiConfig}.
 * A centred translucent panel holds a scrollable list of rows grouped by category; booleans render as
 * ON/OFF pill toggles and ints as drag sliders. Every change writes straight to
 * {@link GlobalChestConfig#instance} and persists via {@link GlobalChestConfig#saveConfig()}.
 *
 * <p>All drawing goes through {@link GuiGraphics} (never {@code Gui.drawRect} directly) so it inherits the
 * E1 GL-colour reset the shim performs after every {@code fill}/{@code blit}.
 */
public class AbsoluteOrderConfigScreen extends GuiScreen {

    // --- tiny primitive accessors (avoid Boolean/Integer boxing seen with java.util.function) ---
    private interface BGet { boolean get(); }

    private interface BSet { void set(boolean v); }

    private interface IGet { int get(); }

    private interface ISet { void set(int v); }

    private static final int T_HEADER = 0;
    private static final int T_BOOL = 1;
    private static final int T_INT = 2;

    private static final class Row {
        final int type;
        final String label;
        BGet bget;
        BSet bset;
        IGet iget;
        ISet iset;
        int min;
        int max;
        int contentTop; // filled in each render/click pass

        Row(int type, String label) {
            this.type = type;
            this.label = label;
        }
    }

    // Layout constants.
    private static final int PANEL_W = 320;
    private static final int PAD = 14;
    private static final int TITLE_H = 22;
    private static final int FOOTER_H = 30;
    private static final int HEADER_H = 22;
    private static final int OPTION_H = 20;
    private static final int SCROLL_STEP = 18;

    private final GuiScreen parent;
    private final List<Row> rows = new ArrayList<Row>();

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private Row draggingRow = null;

    // Computed panel geometry (recomputed at the top of drawScreen and each input handler).
    private int panelX;
    private int panelY;
    private int panelH;
    private int viewportTop;
    private int viewportBottom;

    public AbsoluteOrderConfigScreen(GuiScreen parent) {
        this.parent = parent;
    }

    // ------------------------------------------------------------------------------------------------
    // Row model
    // ------------------------------------------------------------------------------------------------

    private void header(String label) {
        rows.add(new Row(T_HEADER, label));
    }

    private void bool(String label, BGet get, BSet set) {
        Row r = new Row(T_BOOL, label);
        r.bget = get;
        r.bset = set;
        rows.add(r);
    }

    private void intRow(String label, int min, int max, IGet get, ISet set) {
        Row r = new Row(T_INT, label);
        r.min = min;
        r.max = max;
        r.iget = get;
        r.iset = set;
        rows.add(r);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        rows.clear();

        header("Interface");
        bool("Dark mode", () -> GlobalChestConfig.instance.darkMode, v -> GlobalChestConfig.instance.darkMode = v);
        bool("Show left panel", () -> GlobalChestConfig.instance.showLeftPanel, v -> GlobalChestConfig.instance.showLeftPanel = v);
        bool("Preview animation", () -> GlobalChestConfig.instance.enablePreviewAnimation, v -> GlobalChestConfig.instance.enablePreviewAnimation = v);
        bool("Shared custom colors", () -> GlobalChestConfig.instance.sharedCustomColors, v -> GlobalChestConfig.instance.sharedCustomColors = v);
        bool("Close on click outside", () -> GlobalChestConfig.instance.closeOnClickOutside, v -> GlobalChestConfig.instance.closeOnClickOutside = v);
        intRow("Background transparency", 0, 100, () -> GlobalChestConfig.instance.bgTransparency, v -> GlobalChestConfig.instance.bgTransparency = v);
        intRow("Line transparency", 0, 100, () -> GlobalChestConfig.instance.lineTransparency, v -> GlobalChestConfig.instance.lineTransparency = v);

        header("Buttons");
        bool("Show edit buttons", () -> GlobalChestConfig.instance.showEditButtons, v -> GlobalChestConfig.instance.showEditButtons = v);
        bool("Show deposit button", () -> GlobalChestConfig.instance.showDepositButton, v -> GlobalChestConfig.instance.showDepositButton = v);
        bool("Edit lines button", () -> GlobalChestConfig.instance.btnEditLines, v -> GlobalChestConfig.instance.btnEditLines = v);
        bool("Filters button", () -> GlobalChestConfig.instance.btnFilters, v -> GlobalChestConfig.instance.btnFilters = v);
        bool("Fill from chest button", () -> GlobalChestConfig.instance.btnFillFromChest, v -> GlobalChestConfig.instance.btnFillFromChest = v);
        bool("Inventory presets button", () -> GlobalChestConfig.instance.btnInventoryPresets, v -> GlobalChestConfig.instance.btnInventoryPresets = v);
        bool("Chest presets button", () -> GlobalChestConfig.instance.btnChestPresets, v -> GlobalChestConfig.instance.btnChestPresets = v);

        header("Magnifier");
        bool("Magnifier enabled", () -> GlobalChestConfig.instance.magnifierEnabled, v -> GlobalChestConfig.instance.magnifierEnabled = v);
        bool("Magnifier on Lines tab", () -> GlobalChestConfig.instance.magnifierTabLines, v -> GlobalChestConfig.instance.magnifierTabLines = v);
        bool("Magnifier on Backgrounds tab", () -> GlobalChestConfig.instance.magnifierTabBackgrounds, v -> GlobalChestConfig.instance.magnifierTabBackgrounds = v);
        bool("Magnifier on Combo tab", () -> GlobalChestConfig.instance.magnifierTabCombo, v -> GlobalChestConfig.instance.magnifierTabCombo = v);

        header("Behavior");
        bool("Lock hotbar on reorder", () -> GlobalChestConfig.instance.lockHotbarOnReorder, v -> GlobalChestConfig.instance.lockHotbarOnReorder = v);
        intRow("Inventory preset count", 1, 9, () -> GlobalChestConfig.instance.inventoryPresetCount, v -> GlobalChestConfig.instance.inventoryPresetCount = v);

        header("Auto-deposit");
        bool("Auto-deposit enabled", () -> GlobalChestConfig.instance.autoDepositEnabled, v -> GlobalChestConfig.instance.autoDepositEnabled = v);
        bool("Double-sneak trigger", () -> GlobalChestConfig.instance.autoDepositDoubleSneak, v -> GlobalChestConfig.instance.autoDepositDoubleSneak = v);
        intRow("Auto-deposit radius", 1, 32, () -> GlobalChestConfig.instance.autoDepositRadius, v -> GlobalChestConfig.instance.autoDepositRadius = v);
        bool("Deposit through walls", () -> GlobalChestConfig.instance.autoDepositThroughWalls, v -> GlobalChestConfig.instance.autoDepositThroughWalls = v);
        bool("Deposit animation", () -> GlobalChestConfig.instance.autoDepositAnimation, v -> GlobalChestConfig.instance.autoDepositAnimation = v);
        bool("Deposit particle trail", () -> GlobalChestConfig.instance.autoDepositTrail, v -> GlobalChestConfig.instance.autoDepositTrail = v);

        header("Rules");
        bool("Default rule: Manual", () -> GlobalChestConfig.instance.defaultRuleManual, v -> GlobalChestConfig.instance.defaultRuleManual = v);
        bool("Default rule: Shift", () -> GlobalChestConfig.instance.defaultRuleShift, v -> GlobalChestConfig.instance.defaultRuleShift = v);
        bool("Default rule: Hopper", () -> GlobalChestConfig.instance.defaultRuleHopper, v -> GlobalChestConfig.instance.defaultRuleHopper = v);
        bool("Default rule: Pick Up", () -> GlobalChestConfig.instance.defaultRulePickup, v -> GlobalChestConfig.instance.defaultRulePickup = v);
        bool("Expel invalid items", () -> GlobalChestConfig.instance.expelInvalidItems, v -> GlobalChestConfig.instance.expelInvalidItems = v);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    // ------------------------------------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------------------------------------

    private void computeLayout() {
        panelH = Math.min(this.height - 40, 300);
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - panelH) / 2;
        viewportTop = panelY + TITLE_H;
        viewportBottom = panelY + panelH - FOOTER_H;

        int content = contentHeight();
        int viewportH = viewportBottom - viewportTop;
        maxScroll = Math.max(0, content - viewportH);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);
    }

    private int rowHeight(Row r) {
        return r.type == T_HEADER ? HEADER_H : OPTION_H;
    }

    private int contentHeight() {
        int h = 0;
        for (Row r : rows) {
            h += rowHeight(r);
        }
        return h;
    }

    private int rowRight() {
        return panelX + PANEL_W - PAD;
    }

    /** Pill toggle rect {x,y,w,h} for a bool row whose top edge is at {@code rowScreenY}. */
    private int[] pillRect(int rowScreenY) {
        int w = 44;
        int h = 14;
        int x = rowRight() - w;
        int y = rowScreenY + (OPTION_H - h) / 2;
        return new int[] {x, y, w, h};
    }

    /** Slider track rect {x,y,w,h} for an int row whose top edge is at {@code rowScreenY}. */
    private int[] trackRect(int rowScreenY) {
        int w = 90;
        int h = 5;
        int x = rowRight() - 24 - w; // reserve ~24px at the right for the value text
        int y = rowScreenY + (OPTION_H - h) / 2;
        return new int[] {x, y, w, h};
    }

    private int[] doneRect() {
        int w = 90;
        int h = 18;
        int x = panelX + (PANEL_W - w) / 2;
        int y = panelY + panelH - 24;
        return new int[] {x, y, w, h};
    }

    private static boolean inRect(int mx, int my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    // ------------------------------------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        computeLayout();

        GuiGraphics ctx = new GuiGraphics(this);
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Central glass panel.
        UiTheme.panel(ctx, panelX, panelY, PANEL_W, panelH);
        ctx.drawCenteredString(font, "Absolute Order", panelX + PANEL_W / 2, panelY + 7, UiTheme.TEXT);
        ctx.fill(panelX + 10, panelY + TITLE_H - 3, panelX + PANEL_W - 10, panelY + TITLE_H - 2, UiTheme.PANEL_BORDER);

        // Scrollable list, clipped to the viewport.
        ctx.enableScissor(panelX, viewportTop, panelX + PANEL_W, viewportBottom);
        int y = viewportTop - scrollOffset;
        for (Row r : rows) {
            r.contentTop = y - (viewportTop - scrollOffset);
            int h = rowHeight(r);
            if (y + h > viewportTop && y < viewportBottom) {
                renderRow(ctx, font, r, y, mouseX, mouseY);
            }
            y += h;
        }
        ctx.disableScissor();

        // Scrollbar.
        if (maxScroll > 0) {
            int trackX = panelX + PANEL_W - 6;
            int trackY = viewportTop;
            int trackH = viewportBottom - viewportTop;
            int content = contentHeight();
            int thumbH = Math.max(16, trackH * trackH / content);
            int thumbY = trackY + (int) ((trackH - thumbH) * (scrollOffset / (float) maxScroll));
            UiTheme.scrollbar(ctx, trackX, trackY, 4, trackH, thumbY, thumbH);
        }

        // Footer "Done".
        int[] d = doneRect();
        boolean doneHover = inRect(mouseX, mouseY, d);
        UiTheme.button(ctx, d[0], d[1], d[2], d[3], doneHover, false);
        ctx.drawCenteredString(font, "Done", d[0] + d[2] / 2, d[1] + (d[3] - 8) / 2, UiTheme.TEXT);
    }

    private void renderRow(GuiGraphics ctx, FontRenderer font, Row r, int rowScreenY, int mouseX, int mouseY) {
        int rowX = panelX + PAD;

        if (r.type == T_HEADER) {
            ctx.drawString(font, r.label.toUpperCase(), rowX, rowScreenY + HEADER_H - 11, UiTheme.ACCENT);
            ctx.fill(rowX, rowScreenY + HEADER_H - 2, rowRight(), rowScreenY + HEADER_H - 1, 0x22FFFFFF);
            return;
        }

        boolean hover = mouseX >= panelX && mouseX <= panelX + PANEL_W
                && mouseY >= rowScreenY && mouseY < rowScreenY + OPTION_H
                && mouseY >= viewportTop && mouseY < viewportBottom;
        if (hover) {
            ctx.fill(panelX + 4, rowScreenY, panelX + PANEL_W - 4, rowScreenY + OPTION_H, 0x10FFFFFF);
        }

        int labelY = rowScreenY + (OPTION_H - 8) / 2;
        ctx.drawString(font, r.label, rowX, labelY, UiTheme.TEXT);

        if (r.type == T_BOOL) {
            boolean on = r.bget.get();
            int[] p = pillRect(rowScreenY);
            int col = on ? 0xFF3BA55D : 0xFF4A4A55;
            UiTheme.roundRect(ctx, p[0], p[1], p[2], p[3], col);
            UiTheme.roundBorder(ctx, p[0], p[1], p[2], p[3], on ? 0xFF4FD07A : 0xFF6A6A75);
            ctx.drawCenteredString(font, on ? "ON" : "OFF", p[0] + p[2] / 2, p[1] + (p[3] - 8) / 2, on ? 0xFFFFFFFF : UiTheme.TEXT_MUTED);
        } else { // T_INT
            int val = r.iget.get();
            int[] t = trackRect(rowScreenY);
            float frac = (r.max == r.min) ? 0f : (val - r.min) / (float) (r.max - r.min);
            int handleCx = t[0] + Math.round(frac * t[2]);

            UiTheme.inset(ctx, t[0], t[1] - 1, t[2], t[3] + 2);
            // filled portion up to the handle
            if (handleCx > t[0]) {
                ctx.fill(t[0] + 1, t[1], handleCx, t[1] + t[3], UiTheme.ACCENT_BG);
            }
            // handle
            int hw = 6;
            int hh = 12;
            int hx = MathHelper.clamp(handleCx - hw / 2, t[0], t[0] + t[2] - hw);
            int hy = rowScreenY + (OPTION_H - hh) / 2;
            UiTheme.roundRect(ctx, hx, hy, hw, hh, 0xFFEAEAEA);
            UiTheme.roundBorder(ctx, hx, hy, hw, hh, UiTheme.ACCENT_BORDER);
            // value, right-aligned
            String vs = String.valueOf(val);
            ctx.drawString(font, vs, rowRight() - font.getStringWidth(vs), labelY, UiTheme.TEXT);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------------------------------------

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0 && maxScroll > 0) {
            scrollOffset = MathHelper.clamp(scrollOffset - Integer.signum(dWheel) * SCROLL_STEP, 0, maxScroll);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) {
            return;
        }
        computeLayout();

        if (inRect(mouseX, mouseY, doneRect())) {
            close();
            return;
        }

        if (mouseX < panelX || mouseX > panelX + PANEL_W || mouseY < viewportTop || mouseY >= viewportBottom) {
            return;
        }

        int y = viewportTop - scrollOffset;
        for (Row r : rows) {
            int h = rowHeight(r);
            if (mouseY >= y && mouseY < y + h) {
                if (r.type == T_BOOL) {
                    r.bset.set(!r.bget.get());
                    GlobalChestConfig.saveConfig();
                } else if (r.type == T_INT) {
                    int[] t = trackRect(y);
                    if (mouseX >= t[0] - 6 && mouseX <= t[0] + t[2] + 6) {
                        draggingRow = r;
                        setIntFromMouse(r, y, mouseX);
                    }
                }
                return;
            }
            y += h;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedButton, long timeSinceLastClick) {
        if (draggingRow != null) {
            int rowScreenY = viewportTop - scrollOffset + draggingRow.contentTop;
            setIntFromMouse(draggingRow, rowScreenY, mouseX);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingRow = null;
    }

    private void setIntFromMouse(Row r, int rowScreenY, int mouseX) {
        int[] t = trackRect(rowScreenY);
        float frac = MathHelper.clamp((mouseX - t[0]) / (float) t[2], 0f, 1f);
        int val = r.min + Math.round(frac * (r.max - r.min));
        if (val != r.iget.get()) {
            r.iset.set(val);
            GlobalChestConfig.saveConfig();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void close() {
        this.mc.displayGuiScreen(parent);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
