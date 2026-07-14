package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.UiTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ToolButtonWidget extends CustomWidget {

    // Source texture size in px: 32 for the legacy pixel-art icons, larger (e.g. 128) for the smooth
    // vector-exported icons. Used as the u/v region and texture dimensions when sampling.
    public int texSize = 32;
    // When true the base icon is a single-color (white) glyph that is tinted by the button state
    // (light-gray normally, brighter on hover, accent blue when active). Used by the smooth line icons.
    public boolean tintByState = false;
    private boolean hovered = false;

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

        this.hovered = isHovering(mouseX, mouseY);
        boolean active = this.isActive || this.isTempClicked;

        if (this.isDisabled) {
            UiTheme.roundRect(context, x, y, width, height, 0x0AFFFFFF);
            UiTheme.roundBorder(context, x, y, width, height, 0x14FFFFFF);
            drawIcon(context, this.baseIcon, this.maskIcon, 0xFFFFFF);
            UiTheme.roundRect(context, x, y, width, height, 0x66121218);
            return;
        }

        UiTheme.button(context, x, y, width, height, this.hovered, active);
        drawIcon(context, this.baseIcon, this.maskIcon, this.dynamicColor);

        if (this.hovered && this.tooltipText != null) {
            java.util.List<Text> tooltipLines = new java.util.ArrayList<>();
            for (String line : this.tooltipText.split("\n")) {
                tooltipLines.add(Text.literal(line));
            }
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltipLines, mouseX, mouseY);
        }
    }

    private void drawIcon(DrawContext context, Identifier base, Identifier mask, int color) {
        if (base == null) return;
        com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;

        int baseColor = -1;
        if (this.tintByState) {
            boolean active = this.isActive || this.isTempClicked;
            baseColor = active ? UiTheme.ICON_ACTIVE : (this.hovered ? UiTheme.ICON_HOVER : UiTheme.ICON);
        }
        context.drawTexture(
                pipeline, base, x + 2 + baseOffsetX, y + 2, 0.0F, 0.0F, 16, 16, texSize, texSize, texSize, texSize, baseColor);

        if (mask != null) {
            int colorARGB = color | 0xFF000000;
            context.drawTexture(
                    pipeline, mask, x + 2 + maskOffsetX, y + 2, 0.0F, 0.0F, 16, 16, texSize, texSize, texSize, texSize, colorARGB);
        }
    }

}
