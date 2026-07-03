package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ToolButtonWidget extends CustomWidget {

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

        int bgColor = isDark ? (sunken ? 0xFF101010 : 0xFF212121) : (sunken ? 0xFFA0A0A0 : 0xFFC6C6C6);
        context.fill(x, y, x + width, y + height, bgColor);
        drawDarkBevel(context, x, y, width, height, sunken);

        drawIcon(context, this.baseIcon, this.maskIcon, this.dynamicColor);

        if (hover && !this.isDisabled) {
            context.drawStrokedRectangle(x, y, width, height, 0x40FFFFFF);
            if (this.tooltipText != null) {
                // Separamos el string en varias líneas para que Minecraft lo renderice correctamente
                java.util.List<Text> tooltipLines = new java.util.ArrayList<>();
                for (String line : this.tooltipText.split("\n")) {
                    tooltipLines.add(Text.literal(line));
                }
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltipLines, mouseX, mouseY);
            }
        }
    }

    private void drawIcon(DrawContext context, Identifier base, Identifier mask, int color) {
        if (base == null) return;
        com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;

        context.drawTexture(pipeline, base, x + 2 + baseOffsetX, y + 2, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, -1);

        if (mask != null) {
            int colorARGB = color | 0xFF000000;
            context.drawTexture(pipeline, mask, x + 2 + maskOffsetX, y + 2, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, colorARGB);
        }
    }
}