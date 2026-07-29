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
    /** Source texture size to sample. 32 = legacy pixel icons; 128 = smooth vector icons (with blur mcmeta). */
    public int texSize = 32;

    public WideButtonWidget(
            int x, int y, int width, int height, String label, Identifier icon, Runnable onClickAction) {
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
        boolean sunken = this.isActive || PressAnim.active(x, y);
        boolean isDark = GlobalChestConfig.instance.darkMode;

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.button(context, x, y, width, height, hover, sunken);

        // The sunken (active) button draws 1px smaller; shrink its icon+label by the same proportion.
        if (sunken) {
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.pushActiveContent(context, x, y, width, height);
        }

        int iconColor;
        if (sunken && !this.keepNormalTextColor) {
            iconColor = io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ON_ACCENT;
        } else {
            iconColor = io.github.marcsanzdev.chestseparators.client.ui.UiTheme.TEXT;
        }

        if (this.icon != null) {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
            context.drawTexture(pipeline, this.icon, x + 2, y + 2, 0.0F, 0.0F, 16, 16, texSize, texSize, texSize, texSize, -1);
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
        context.getMatrices().translate((float) (x + 22), (float) (y + (height - 9 * scale) / 2));
        context.getMatrices().scale(scale, scale);
        // Shadow only when in dark mode
        context.drawText(MinecraftClient.getInstance().textRenderer, displayText, 0, 0, iconColor, isDark);
        context.getMatrices().popMatrix();

        if (sunken) {
            context.getMatrices().popMatrix();
        }

        if (hover && this.tooltipText != null && !this.isDisabled) {
            context.drawTooltip(
                    MinecraftClient.getInstance().textRenderer, Text.literal(this.tooltipText), mouseX, mouseY);
        }
    }

    private void renderDisabled(DrawContext context) {
        boolean isDark = GlobalChestConfig.instance.darkMode;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(context, x, y, width, height, 0x0AFFFFFF);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundBorder(context, x, y, width, height, 0x14FFFFFF);

        Identifier iconToDraw = this.disabledIconFallback != null ? this.disabledIconFallback : this.icon;
        int disabledColor = 0xFF6A6A72;

        if (iconToDraw != null) {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
            context.drawTexture(pipeline, iconToDraw, x + 2, y + 2, 0.0F, 0.0F, 16, 16, texSize, texSize, texSize, texSize, disabledColor);
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
        context.getMatrices().translate((float) (x + 22), (float) (y + (height - 9 * scale) / 2));
        context.getMatrices().scale(scale, scale);
        context.drawText(MinecraftClient.getInstance().textRenderer, displayText, 0, 0, disabledColor, isDark);
        context.getMatrices().popMatrix();
    }
}
