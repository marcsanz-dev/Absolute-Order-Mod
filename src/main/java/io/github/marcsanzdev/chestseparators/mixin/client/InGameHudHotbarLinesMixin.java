package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the saved separator lines/backgrounds on the hotbar slots in the game HUD.
 * Hotbar data is keyed by PlayerInventory index 0-8 in playerInventoryVisual.
 */
@Mixin(InGameHud.class)
public class InGameHudHotbarLinesMixin {

    @Inject(method = "renderHotbar", at = @At("RETURN"))
    private void chestseparators$renderHotbarLines(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        ChestConfigManager m = ChestConfigManager.getInstance();
        if (m.getPlayerInventoryVisual().isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();

        int baseX = (scaledWidth - 182) / 2 + 3;
        int baseY = scaledHeight - 19;

        int bgAlpha = (GlobalChestConfig.instance.bgTransparency * 255 / 100) << 24;
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;

        for (int i = 0; i < 9; i++) {
            int x = baseX + i * 20;
            int y = baseY;

            int bgColor = m.getInventoryColor(i, ChestConfigManager.ACTION_BG);
            if (bgColor != 0) {
                context.fill(x, y, x + 16, y + 16, (bgColor & 0xFFFFFF) | bgAlpha);
            }

            int rTop   = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_TOP), lineAlpha);
            int rBot   = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_BOTTOM), lineAlpha);
            int rLeft  = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_LEFT), lineAlpha);
            int rRight = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_RIGHT), lineAlpha);

            if (rTop == 0 && rBot == 0 && rLeft == 0 && rRight == 0) continue;

            int sTop   = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_TOP);
            int sBot   = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_BOTTOM);
            int sLeft  = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_LEFT);
            int sRight = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_RIGHT);

            if (rTop   != 0) context.fill(x,      y - 1, x + 16, y,      rTop);
            if (rBot   != 0) context.fill(x,      y + 16, x + 16, y + 17, rBot);
            if (rLeft  != 0) context.fill(x - 1,  y,     x,      y + 16, rLeft);
            if (rRight != 0) context.fill(x + 16, y,     x + 17, y + 16, rRight);

            drawCorner(context, x - 1,  y - 1,  rTop,  sTop,  rLeft,  sLeft);
            drawCorner(context, x + 16, y - 1,  rTop,  sTop,  rRight, sRight);
            drawCorner(context, x - 1,  y + 16, rBot,  sBot,  rLeft,  sLeft);
            drawCorner(context, x + 16, y + 16, rBot,  sBot,  rRight, sRight);
        }
    }

    private static int applyAlpha(int color, int lineAlpha) {
        return color == 0 ? 0 : (color & 0x00FFFFFF) | lineAlpha;
    }

    private static void drawCorner(DrawContext context, int cx, int cy, int colorA, int seqA, int colorB, int seqB) {
        if (colorA == 0 && colorB == 0) return;
        int color;
        if (colorA == 0)       color = colorB;
        else if (colorB == 0)  color = colorA;
        else                   color = (seqA >= seqB) ? colorA : colorB;
        context.fill(cx, cy, cx + 1, cy + 1, color);
    }
}
