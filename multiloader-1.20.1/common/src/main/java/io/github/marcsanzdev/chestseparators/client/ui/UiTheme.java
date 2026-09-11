package io.github.marcsanzdev.chestseparators.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * "Cristal" (glass) design tokens + reusable draw helpers for the whole editor UI: translucent dark
 * panels with soft 2px corners, thin light hairline borders, and a blue accent for active/selected
 * state. Centralised here so every panel and button in the mod shares one visual language.
 */
public final class UiTheme {

    private UiTheme() {}

    /**
     * E4 equivalent of the E5 tinted {@code GuiGraphics.blit(RenderPipeline, ...)}. 1.20.1 blit has no
     * pipeline/color parameters, so the ARGB tint is applied via {@link GuiGraphics#setColor} around the
     * scaled blit and reset afterwards. Argument order after {@code atlas} mirrors the old E5 blit (minus
     * the pipeline), so call sites port with a mechanical rename.
     */
    public static void blitTex(
            GuiGraphics c,
            net.minecraft.resources.ResourceLocation atlas,
            int x,
            int y,
            float u,
            float v,
            int w,
            int h,
            int regionW,
            int regionH,
            int texW,
            int texH,
            int argb) {
        float a = ((argb >>> 24) & 0xFF) / 255.0F;
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        c.setColor(r, g, b, a);
        c.blit(atlas, x, y, w, h, u, v, regionW, regionH, texW, texH);
        c.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

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
    public static void roundRect(GuiGraphics c, int x, int y, int w, int h, int col) {
        c.fill(x + 2, y, x + w - 2, y + h, col); // center band, full height
        c.fill(x, y + 2, x + 2, y + h - 2, col); // left band, middle rows
        c.fill(x + w - 2, y + 2, x + w, y + h - 2, col); // right band, middle rows
    }

    /** Draws a thin (1px) rounded border around the rectangle. */
    public static void roundBorder(GuiGraphics c, int x, int y, int w, int h, int col) {
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
    public static void panel(GuiGraphics c, int x, int y, int w, int h) {
        roundRect(c, x, y, w, h, PANEL_BG);
        roundBorder(c, x, y, w, h, PANEL_BORDER);
        c.fill(x + 3, y + 1, x + w - 3, y + 2, PANEL_HILITE);
    }

    // Which edge of a tab is attached to (embedded into) the main panel. That edge gets NO border and
    // square corners, so the tab reads as carved into the panel instead of a chip sitting beside it.
    public static final int ATTACH_TOP = 0;
    public static final int ATTACH_BOTTOM = 1;
    public static final int ATTACH_LEFT = 2;
    public static final int ATTACH_RIGHT = 3;

    /**
     * OPAQUE tab (category tabs, editor mode tabs) embedded into a panel along its {@code attach} edge.
     * The attached edge is drawn flush and border-less (no "end of tab" line, no gap); the three free
     * edges keep the soft 2px rounded corners of every other button. When selected the tab shrinks 1px,
     * but ONLY on the free sides — the attached edge stays anchored so no gap ever opens against the panel.
     */
    public static void tab(GuiGraphics c, int x, int y, int w, int h, boolean hover, boolean active, int attach) {
        int bg = active ? 0xFF2C6BAE : (hover ? 0xFF2E2E3A : 0xFF23232C);
        int border = active ? ACCENT_BORDER : 0xFF3A3A45;
        if (active) {
            if (attach != ATTACH_TOP) {
                y += 1;
                h -= 1;
            }
            if (attach != ATTACH_BOTTOM) {
                h -= 1;
            }
            if (attach != ATTACH_LEFT) {
                x += 1;
                w -= 1;
            }
            if (attach != ATTACH_RIGHT) {
                w -= 1;
            }
        }
        // Corners are rounded only on the two OUTER corners (away from the attached edge); the two corners
        // touching the panel stay square so the tab meets it flush.
        boolean rTL = attach != ATTACH_TOP && attach != ATTACH_LEFT;
        boolean rTR = attach != ATTACH_TOP && attach != ATTACH_RIGHT;
        boolean rBL = attach != ATTACH_BOTTOM && attach != ATTACH_LEFT;
        boolean rBR = attach != ATTACH_BOTTOM && attach != ATTACH_RIGHT;

        // --- opaque fill ---
        c.fill(x + 2, y, x + w - 2, y + h, bg); // center band, full height
        c.fill(x, y + 2, x + 2, y + h - 2, bg); // left band, middle rows
        if (!rTL) c.fill(x, y, x + 2, y + 2, bg); // square top-left
        if (!rBL) c.fill(x, y + h - 2, x + 2, y + h, bg); // square bottom-left
        c.fill(x + w - 2, y + 2, x + w, y + h - 2, bg); // right band, middle rows
        if (!rTR) c.fill(x + w - 2, y, x + w, y + 2, bg); // square top-right
        if (!rBR) c.fill(x + w - 2, y + h - 2, x + w, y + h, bg); // square bottom-right

        // --- border on the three FREE sides only (attached side is left open so it merges into the panel) ---
        if (attach != ATTACH_TOP) {
            c.fill((attach == ATTACH_LEFT) ? x : x + 2, y, (attach == ATTACH_RIGHT) ? x + w : x + w - 2, y + 1, border);
        }
        if (attach != ATTACH_BOTTOM) {
            c.fill(
                    (attach == ATTACH_LEFT) ? x : x + 2,
                    y + h - 1,
                    (attach == ATTACH_RIGHT) ? x + w : x + w - 2,
                    y + h,
                    border);
        }
        if (attach != ATTACH_LEFT) {
            c.fill(x, (attach == ATTACH_TOP) ? y : y + 2, x + 1, (attach == ATTACH_BOTTOM) ? y + h : y + h - 2, border);
        }
        if (attach != ATTACH_RIGHT) {
            c.fill(
                    x + w - 1,
                    (attach == ATTACH_TOP) ? y : y + 2,
                    x + w,
                    (attach == ATTACH_BOTTOM) ? y + h : y + h - 2,
                    border);
        }
        // rounded outer-corner pixels
        if (rTL) c.fill(x + 1, y + 1, x + 2, y + 2, border);
        if (rTR) c.fill(x + w - 2, y + 1, x + w - 1, y + 2, border);
        if (rBL) c.fill(x + 1, y + h - 2, x + 2, y + h - 1, border);
        if (rBR) c.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, border);
    }

    /**
     * Cristal scrollbar: a recessed track (same inset language as the list/grid viewports) with a light
     * glass thumb. The thumb is a pill — its ends are capped 1px narrower so they follow the rounded
     * start/end of the track instead of butting into it with square corners.
     */
    public static void scrollbar(GuiGraphics c, int x, int y, int w, int h, int thumbY, int thumbH) {
        inset(c, x, y, w, h);
        int col = 0x70FFFFFF;
        int tx = x + 1;
        int tw = w - 2;
        c.fill(tx, thumbY + 2, tx + tw, thumbY + thumbH - 2, col); // body
        c.fill(tx + 1, thumbY + 1, tx + tw - 1, thumbY + 2, col); // rounded top cap
        c.fill(tx + 1, thumbY + thumbH - 2, tx + tw - 1, thumbY + thumbH - 1, col); // rounded bottom cap
    }

    /** Recessed inset box (search fields, list viewports): darker translucent fill + faint border. */
    public static void inset(GuiGraphics c, int x, int y, int w, int h) {
        roundRect(c, x, y, w, h, 0x50000000);
        roundBorder(c, x, y, w, h, 0x16FFFFFF);
    }

    /**
     * Shrinks whatever is drawn next around the centre of a button, by the same proportion the button
     * itself shrinks when active (1px inset per side), so the icon/label never overflows the sunken
     * button and the whole thing scales as one piece. Uniform scale (the tighter axis) so icons keep
     * their aspect ratio. ALWAYS pair with {@code context.pose().popPose()}.
     */
    public static void pushActiveContent(GuiGraphics c, int x, int y, int w, int h) {
        float s = Math.min((w - 2f) / w, (h - 2f) / h);
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        c.pose().pushPose();
        c.pose().translate(cx, cy, 0.0F);
        c.pose().scale(s, s, 1.0F);
        c.pose().translate(-cx, -cy, 0.0F);
    }

    /** Cristal glass button background for the given hover/active state. */
    public static void button(GuiGraphics c, int x, int y, int w, int h, boolean hover, boolean active) {
        if (active) {
            // Pushed-in: draw the active (blue) button 1px smaller on every side, so it reads as slightly
            // recessed/sunken. The 1px gap shows the panel behind — a clean "pressed" cue with no shadow.
            roundRect(c, x + 1, y + 1, w - 2, h - 2, ACCENT_BG);
            roundBorder(c, x + 1, y + 1, w - 2, h - 2, ACCENT_BORDER);
            return;
        }
        roundRect(c, x, y, w, h, hover ? BTN_BG_HOVER : BTN_BG);
        roundBorder(c, x, y, w, h, BTN_BORDER);
    }
}
