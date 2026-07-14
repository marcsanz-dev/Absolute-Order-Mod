package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.UiColors;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ToolButtonWidget extends CustomWidget {

    /** Modern flat glyphs drawn in code instead of a texture (see {@link #drawGlyph}). */
    public enum Glyph {
        NONE,
        PENCIL,
        FUNNEL,
        DEPOSIT,
        FILL,
        FLOPPY,
        COPY
    }

    public Glyph glyph = Glyph.NONE;

    public Identifier baseIcon;
    public Identifier maskIcon;
    public Identifier disabledIconFallback;
    public int dynamicColor = 0xFFFFFF;

    public boolean isTempClicked = false;
    private long clickedTime = 0;

    public int baseOffsetX = 0;
    public int maskOffsetX = 0;

    public ToolButtonWidget(int x, int y, Identifier baseIcon, String tooltip, Runnable onClickAction) {
        super(x, y, 20, 20, onClickAction);
        this.baseIcon = baseIcon;
        this.disabledIconFallback = ModTextures.ICON_PASTE;
        this.tooltipText = tooltip;
    }

    public void triggerClickAnimation() {
        this.isTempClicked = true;
        this.clickedTime = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.isTempClicked && System.currentTimeMillis() - this.clickedTime > 200) {
            this.isTempClicked = false;
        }

        boolean isDark = GlobalChestConfig.instance.darkMode;

        if (this.isDisabled) {
            int bgDisabled = isDark ? 0xFF454545 : 0xFFA0A0A0;
            context.fill(x, y, x + width, y + height, bgDisabled);
            drawDarkBevel(context, x, y, width, height, false);
            Identifier iconToDraw = this.disabledIconFallback != null ? this.disabledIconFallback : this.baseIcon;
            drawIcon(context, iconToDraw, null, 0xFFFFFF);

            int overlayColor = isDark ? 0xAA212121 : 0xAAC6C6C6;
            context.fill(x + 2, y + 2, x + 18, y + 18, overlayColor);
            return;
        }

        boolean hover = isHovering(mouseX, mouseY);
        boolean sunken = this.isActive || this.isTempClicked;

        int bgColor =
                isDark ? (sunken ? 0xFF101010 : UiColors.SURFACE_DARK) : (sunken ? 0xFFA0A0A0 : UiColors.SURFACE_LIGHT);
        context.fill(x, y, x + width, y + height, bgColor);
        drawDarkBevel(context, x, y, width, height, sunken);

        drawIcon(context, this.baseIcon, this.maskIcon, this.dynamicColor);

        if (hover && !this.isDisabled) {
            context.drawStrokedRectangle(x, y, width, height, 0x40FFFFFF);
            if (this.tooltipText != null) {
                java.util.List<Text> tooltipLines = new java.util.ArrayList<>();
                for (String line : this.tooltipText.split("\n")) {
                    tooltipLines.add(Text.literal(line));
                }
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltipLines, mouseX, mouseY);
            }
        }
    }

    private void drawIcon(DrawContext context, Identifier base, Identifier mask, int color) {
        if (this.glyph != Glyph.NONE) {
            boolean sunken = this.isActive || this.isTempClicked;
            int gcol = sunken ? 0xFF9CC3FF : 0xFFDDDDDD;
            drawGlyph(context, this.glyph, x + 2, y + 2, gcol);
            return;
        }
        if (base == null) return;
        com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;

        context.drawTexture(pipeline, base, x + 2 + baseOffsetX, y + 2, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, -1);

        if (mask != null) {
            int colorARGB = color | 0xFF000000;
            context.drawTexture(
                    pipeline, mask, x + 2 + maskOffsetX, y + 2, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, colorARGB);
        }
    }

    // --- Code-drawn flat glyphs (16x16 icon area, origin ox/oy) ---

    /** Fills a rectangle in glyph-local coordinates. */
    private static void g(DrawContext c, int ox, int oy, int x0, int y0, int x1, int y1, int col) {
        c.fill(ox + x0, oy + y0, ox + x1, oy + y1, col);
    }

    private void drawGlyph(DrawContext c, Glyph gl, int ox, int oy, int col) {
        switch (gl) {
            case PENCIL -> {
                for (int i = 0; i < 8; i++) {
                    g(c, ox, oy, 11 - i, 3 + i, 13 - i, 5 + i, col);
                }
                g(c, ox, oy, 3, 11, 5, 13, col);
                g(c, ox, oy, 2, 13, 4, 14, col);
                g(c, ox, oy, 11, 2, 14, 5, col);
            }
            case FUNNEL -> {
                g(c, ox, oy, 2, 2, 14, 4, col);
                g(c, ox, oy, 3, 4, 13, 5, col);
                g(c, ox, oy, 4, 5, 12, 6, col);
                g(c, ox, oy, 5, 6, 11, 7, col);
                g(c, ox, oy, 6, 7, 10, 8, col);
                g(c, ox, oy, 7, 8, 9, 14, col);
            }
            case DEPOSIT -> {
                g(c, ox, oy, 7, 2, 9, 9, col);
                g(c, ox, oy, 5, 8, 11, 9, col);
                g(c, ox, oy, 6, 9, 10, 10, col);
                g(c, ox, oy, 7, 10, 9, 12, col);
                g(c, ox, oy, 3, 14, 13, 15, col);
            }
            case FILL -> {
                g(c, ox, oy, 7, 5, 9, 12, col);
                g(c, ox, oy, 5, 6, 11, 7, col);
                g(c, ox, oy, 6, 5, 10, 6, col);
                g(c, ox, oy, 7, 3, 9, 5, col);
                g(c, ox, oy, 3, 14, 13, 15, col);
            }
            case FLOPPY -> {
                g(c, ox, oy, 2, 2, 14, 3, col);
                g(c, ox, oy, 2, 13, 14, 14, col);
                g(c, ox, oy, 2, 2, 3, 14, col);
                g(c, ox, oy, 13, 2, 14, 14, col);
                g(c, ox, oy, 9, 3, 12, 6, col);
                g(c, ox, oy, 5, 9, 11, 13, col);
            }
            case COPY -> {
                g(c, ox, oy, 6, 2, 15, 3, col);
                g(c, ox, oy, 6, 2, 7, 10, col);
                g(c, ox, oy, 14, 2, 15, 10, col);
                g(c, ox, oy, 6, 9, 15, 10, col);
                g(c, ox, oy, 1, 6, 11, 7, col);
                g(c, ox, oy, 1, 6, 2, 15, col);
                g(c, ox, oy, 10, 6, 11, 15, col);
                g(c, ox, oy, 1, 14, 11, 15, col);
            }
            default -> {}
        }
    }
}
