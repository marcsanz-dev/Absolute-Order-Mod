package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ActionIconButtonWidget extends CustomWidget {

    public String label;
    public Identifier icon;
    public int baseColor;

    public ActionIconButtonWidget(
            int x, int y, int width, int height, String label, Identifier icon, int baseColor, Runnable onClickAction) {
        super(x, y, width, height, onClickAction);
        this.label = label;
        this.icon = icon;
        this.baseColor = baseColor;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.isDisabled) return;

        boolean hover = isHovering(mouseX, mouseY);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.button(
                context, x, y, width, height, hover, this.isActive);

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

        int scaledTextWidth = (int) (MinecraftClient.getInstance().textRenderer.getWidth(displayText) * scale);
        int contentWidth = iconSpace + scaledTextWidth;
        int startX = x + (width - contentWidth) / 2;

        if (icon != null) {
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
            context.drawTexture(pipeline, icon, startX, y + (height - 16) / 2, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, -1);
            startX += 20;
        }

        int textColor = this.isActive
                ? io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ON_ACCENT
                : io.github.marcsanzdev.chestseparators.client.ui.UiTheme.TEXT;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) startX, (float) (y + (height - 9 * scale) / 2));
        context.getMatrices().scale(scale, scale);
        context.drawText(MinecraftClient.getInstance().textRenderer, displayText, 0, 0, textColor, true);
        context.getMatrices().popMatrix();

        if (hover && this.tooltipText != null && !this.isDisabled) {
            context.drawTooltip(
                    MinecraftClient.getInstance().textRenderer, Text.literal(this.tooltipText), mouseX, mouseY);
        }
    }
}
