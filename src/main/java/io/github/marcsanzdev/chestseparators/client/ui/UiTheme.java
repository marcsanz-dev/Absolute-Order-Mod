package io.github.marcsanzdev.chestseparators.client.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * "Cristal" (glass) design tokens + reusable draw helpers for the whole editor UI: translucent dark
 * panels with soft 2px corners, thin light hairline borders, and a blue accent for active/selected
 * state. Centralised here so every panel and button in the mod shares one visual language.
 */
public final class UiTheme {

    private UiTheme() {}

    // Panels
    public static final int PANEL_BG = 0xC8121218; // translucent dark
    public static final int PANEL_BORDER = 0x22FFFFFF; // thin light hairline
    public static final int PANEL_HILITE = 0x14FFFFFF; // subtle top edge highlight

    // Buttons (icon + wide)
    public static final int BTN_BG = 0x12FFFFFF; // faint glass fill
    public static final int BTN_BG_HOVER = 0x24FFFFFF;
    public static final int BTN_BORDER = 0x2AFFFFFF;

    // Accent (blue) — active/selected. Active buttons use a near-solid blue FILL (clearly "on") rather
    // than a faint tint, so they read as toggled without any muddy inset shadow.
    public static final int ACCENT = 0xFF4A9EFF;
    public static final int ACCENT_BG = 0xF02C6BAE; // near-solid blue fill for active buttons
    public static final int ACCENT_BORDER = 0xFF5FA8FF;

    // Icons / text
    public static final int ICON = 0xFFDDDDDD;
    public static final int ICON_HOVER = 0xFFFFFFFF;
    public static final int ICON_ACTIVE = 0xFFFFFFFF; // white on the solid blue fill
    public static final int ON_ACCENT = 0xFFFFFFFF; // text/icon color on an active (blue) button
    public static final int TEXT = 0xFFE6E6E6;
    public static final int TEXT_MUTED = 0xFF9A9AA2;

    /**
     * Fills a rounded rectangle (2px corners) with NON-OVERLAPPING rects so a translucent color keeps a
     * uniform opacity everywhere (overlapping fills would double-blend the center and leave the edges
     * looking lighter — the cause of the mismatched left/right vertical strips).
     */
    public static void roundRect(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 2, y, x + w - 2, y + h, col); // center band, full height
        c.fill(x, y + 2, x + 2, y + h - 2, col); // left band, middle rows
        c.fill(x + w - 2, y + 2, x + w, y + h - 2, col); // right band, middle rows
    }

    /** Draws a thin (1px) rounded border around the rectangle. */
    public static void roundBorder(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 2, y, x + w - 2, y + 1, col);
        c.fill(x + 2, y + h - 1, x + w - 2, y + h, col);
        c.fill(x, y + 2, x + 1, y + h - 2, col);
        c.fill(x + w - 1, y + 2, x + w, y + h - 2, col);
        c.fill(x + 1, y + 1, x + 2, y + 2, col);
        c.fill(x + w - 2, y + 1, x + w - 1, y + 2, col);
        c.fill(x + 1, y + h - 2, x + 2, y + h - 1, col);
        c.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, col);
    }

    /** Cristal glass panel: translucent fill + thin border + top-edge highlight. */
    public static void panel(DrawContext c, int x, int y, int w, int h) {
        roundRect(c, x, y, w, h, PANEL_BG);
        roundBorder(c, x, y, w, h, PANEL_BORDER);
        c.fill(x + 3, y + 1, x + w - 3, y + 2, PANEL_HILITE);
    }

    /** Cristal glass button background for the given hover/active state. */
    public static void button(DrawContext c, int x, int y, int w, int h, boolean hover, boolean active) {
        int bg = active ? ACCENT_BG : (hover ? BTN_BG_HOVER : BTN_BG);
        int border = active ? ACCENT_BORDER : BTN_BORDER;
        roundRect(c, x, y, w, h, bg);
        roundBorder(c, x, y, w, h, border);
    }
}
