package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class WideButtonWidget extends CustomWidget {

    public String label;
    public Identifier icon;
    public Identifier disabledIconFallback;
    public boolean keepNormalTextColor = false;

    public WideButtonWidget(int x, int y, int width, int height, String label, Identifier icon, Runnable onClickAction) {
        super(x, y, width, height, onClickAction);
        this.label = label;
        this.icon = icon;
        this.disabledIconFallback = icon;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.isDisabled) {
            renderDisabled(context);
            return;
        }

        boolean hover = isHovering(mouseX, mouseY);
        boolean sunken = this.isActive;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        int bgColor = isDark ? (sunken ? 0xFF101010 : 0xFF212121) : (sunken ? 0xFFA0A0A0 : 0xFFC6C6C6);
        context.fill(x, y, x + width, y + height, bgColor);
        drawDarkBevel(context, x, y, width, height, sunken);

        int iconColor;
        if (this.isActive && !this.keepNormalTextColor) {
            iconColor = isDark ? 0xFF55FF55 : 0xFF00AA00;
        } else {
            iconColor = isDark ? 0xFFFFFFFF : 0xFF202020;
        }

        if (this.icon != null) {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
            context.drawTexture(pipeline, this.icon, x + 2, y + 2, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, -1);
        }

        float scale = 0.85f;
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(label);
        int availableWidth = width - 26;
        String displayText = label;

        if (textWidth * scale > availableWidth) {
            scale = (float) availableWidth / textWidth;
            if (scale < 0.60f) {
                scale = 0.60f;
                int maxTextWidth = (int) (availableWidth / scale);
                displayText = MinecraftClient.getInstance().textRenderer.trimToWidth(label, maxTextWidth - 6) + "...";
            }
        }

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)(x + 22), (float)(y + (height - 9 * scale) / 2));
        context.getMatrices().scale(scale, scale);
        // Shadow only when in dark mode
        context.drawText(MinecraftClient.getInstance().textRenderer, displayText, 0, 0, iconColor, isDark);
        context.getMatrices().popMatrix();

        if (hover) {
            context.drawStrokedRectangle(x, y, width, height, 0x40FFFFFF);
            if (this.tooltipText != null && !this.isDisabled) {
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.literal(this.tooltipText), mouseX, mouseY);
            }
        }
    }

    private void renderDisabled(DrawContext context) {
        boolean isDark = GlobalChestConfig.instance.darkMode;
        int bgColor = isDark ? 0xFF454545 : 0xFFA0A0A0;

        context.fill(x, y, x + width, y + height, bgColor);
        drawDarkBevel(context, x, y, width, height, false);

        Identifier iconToDraw = this.disabledIconFallback != null ? this.disabledIconFallback : this.icon;
        int disabledColor = isDark ? 0xFFAAAAAA : 0xFF777777;

        if (iconToDraw != null) {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
            context.drawTexture(pipeline, iconToDraw, x + 2, y + 2, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, disabledColor);
        }

        float scale = 0.85f;
        int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(label);
        int availableWidth = width - 26;
        String displayText = label;

        if (textWidth * scale > availableWidth) {
            scale = (float) availableWidth / textWidth;
            if (scale < 0.60f) {
                scale = 0.60f;
                int maxTextWidth = (int) (availableWidth / scale);
                displayText = MinecraftClient.getInstance().textRenderer.trimToWidth(label, maxTextWidth - 6) + "...";
            }
        }

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)(x + 22), (float)(y + (height - 9 * scale) / 2));
        context.getMatrices().scale(scale, scale);
        context.drawText(MinecraftClient.getInstance().textRenderer, displayText, 0, 0, disabledColor, isDark);
        context.getMatrices().popMatrix();
    }
}