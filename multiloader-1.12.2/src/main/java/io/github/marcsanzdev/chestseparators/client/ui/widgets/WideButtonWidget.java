package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.minecraft.client.Minecraft;
import io.github.marcsanzdev.chestseparators.client.compat.GuiGraphics;
import net.minecraft.util.ResourceLocation;

public class WideButtonWidget extends CustomWidget {

    public String label;
    public ResourceLocation icon;
    public ResourceLocation disabledIconFallback;
    public boolean keepNormalTextColor = false;
    /** Source texture size to sample. 32 = legacy pixel icons; 128 = smooth vector icons (with blur mcmeta). */
    public int texSize = 32;

    public WideButtonWidget(
            int x, int y, int width, int height, String label, ResourceLocation icon, Runnable onClickAction) {
        super(x, y, width, height, onClickAction);
        this.label = label;
        this.icon = icon;
        this.disabledIconFallback = icon;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
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
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context, this.icon, x + 2, y + 2, 0.0F, 0.0F, 16, 16, texSize, texSize, texSize, texSize, -1);
        }

        float scale = 0.85f;
        int textWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(label);
        int availableWidth = width - 26;
        String displayText = label;

        if (textWidth * scale > availableWidth) {
            scale = (float) availableWidth / textWidth;
            if (scale < 0.60f) {
                scale = 0.60f;
                int maxTextWidth = (int) (availableWidth / scale);
                displayText = Minecraft.getMinecraft().fontRenderer.trimStringToWidth(label, maxTextWidth - 6) + "...";
            }
        }

        context.pose().pushPose();
        context.pose().translate((float) (x + 22), (float) (y + (height - 9 * scale) / 2), 0.0F);
        context.pose().scale(scale, scale, 1.0F);
        // Shadow only when in dark mode
        context.drawString(Minecraft.getMinecraft().fontRenderer, displayText, 0, 0, iconColor, isDark);
        context.pose().popPose();

        if (sunken) {
            context.pose().popPose();
        }

        if (hover && this.tooltipText != null && !this.isDisabled) {
            context.renderTooltip(
                    Minecraft.getMinecraft().fontRenderer, new net.minecraft.util.text.TextComponentString(this.tooltipText), mouseX, mouseY);
        }
    }

    private void renderDisabled(GuiGraphics context) {
        boolean isDark = GlobalChestConfig.instance.darkMode;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundRect(context, x, y, width, height, 0x0AFFFFFF);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundBorder(context, x, y, width, height, 0x14FFFFFF);

        ResourceLocation iconToDraw = this.disabledIconFallback != null ? this.disabledIconFallback : this.icon;
        int disabledColor = 0xFF6A6A72;

        if (iconToDraw != null) {
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context,
                    iconToDraw,
                    x + 2,
                    y + 2,
                    0.0F,
                    0.0F,
                    16,
                    16,
                    texSize,
                    texSize,
                    texSize,
                    texSize,
                    disabledColor);
        }

        float scale = 0.85f;
        int textWidth = Minecraft.getMinecraft().fontRenderer.getStringWidth(label);
        int availableWidth = width - 26;
        String displayText = label;

        if (textWidth * scale > availableWidth) {
            scale = (float) availableWidth / textWidth;
            if (scale < 0.60f) {
                scale = 0.60f;
                int maxTextWidth = (int) (availableWidth / scale);
                displayText = Minecraft.getMinecraft().fontRenderer.trimStringToWidth(label, maxTextWidth - 6) + "...";
            }
        }

        context.pose().pushPose();
        context.pose().translate((float) (x + 22), (float) (y + (height - 9 * scale) / 2), 0.0F);
        context.pose().scale(scale, scale, 1.0F);
        context.drawString(Minecraft.getMinecraft().fontRenderer, displayText, 0, 0, disabledColor, isDark);
        context.pose().popPose();
    }
}
