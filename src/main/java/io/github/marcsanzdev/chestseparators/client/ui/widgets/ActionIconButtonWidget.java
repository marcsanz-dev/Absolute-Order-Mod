package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class ActionIconButtonWidget extends CustomWidget {

    public String label;
    public Identifier icon;
    public int baseColor;

    public ActionIconButtonWidget(int x, int y, int width, int height, String label, Identifier icon, int baseColor, Runnable onClickAction) {
        super(x, y, width, height, onClickAction);
        this.label = label;
        this.icon = icon;
        this.baseColor = baseColor;
    }

    private int shiftColor(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = MathHelper.clamp(r + amount, 0, 255);
        g = MathHelper.clamp(g + amount, 0, 255);
        b = MathHelper.clamp(b + amount, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.isDisabled) return;

        boolean hover = isHovering(mouseX, mouseY);
        boolean isDark = GlobalChestConfig.instance.darkMode;

        // Auto-adapt base color from Dark to Light Vanilla Gray
        int renderBaseColor = this.baseColor;
        if (!isDark && this.baseColor == 0xFF212121) {
            renderBaseColor = 0xFFC6C6C6;
        }

        // Shift darker on hover for light mode, brighter for dark mode
        int hoverShift = isDark ? 30 : -20;
        int color = hover ? shiftColor(renderBaseColor, hoverShift) : renderBaseColor;

        context.fill(x, y, x + width, y + height, 0xFF000000 | color);
        drawDarkBevel(context, x, y, width, height, false);

        if (hover) {
            context.drawStrokedRectangle(x, y, width, height, 0x40FFFFFF);
        }

        float scale = 0.85f;
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(label);
        int iconSpace = (icon != null ? 20 : 0);
        int availableTextWidth = width - 8 - iconSpace;

        String displayText = label;

        if (textWidth * scale > availableTextWidth) {
            scale = (float) availableTextWidth / textWidth;
            if (scale < 0.60f) {
                scale = 0.60f;
                int maxTextWidth = (int) (availableTextWidth / scale);
                displayText = MinecraftClient.getInstance().textRenderer.trimToWidth(label, maxTextWidth - 8) + "...";
            }
        }

        int scaledTextWidth = (int)(MinecraftClient.getInstance().textRenderer.getWidth(displayText) * scale);
        int contentWidth = iconSpace + scaledTextWidth;
        int startX = x + (width - contentWidth) / 2;

        if (icon != null) {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
            context.drawTexture(pipeline, icon, startX, y + (height - 16) / 2, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, -1);
            startX += 20;
        }

        // Adaptive text color and shadow based on the button background
        int textColor = (isDark || renderBaseColor != 0xFFC6C6C6) ? 0xFFFFFFFF : 0xFF202020;
        boolean drawShadow = (isDark || renderBaseColor != 0xFFC6C6C6);

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)startX, (float)(y + (height - 9 * scale) / 2));
        context.getMatrices().scale(scale, scale);
        context.drawText(MinecraftClient.getInstance().textRenderer, displayText, 0, 0, textColor, drawShadow);
        context.getMatrices().popMatrix();

        if (hover && this.tooltipText != null && !this.isDisabled) {
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.literal(this.tooltipText), mouseX, mouseY);
        }
    }
}