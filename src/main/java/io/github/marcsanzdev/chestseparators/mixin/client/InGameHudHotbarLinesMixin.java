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
 * Draws separator backgrounds and lines on the HUD hotbar slots.
 *
 * Two injection points:
 * - Backgrounds at HEAD (before the hotbar sprite), so the sprite and items
 *   composite on top. The vanilla slot areas are semi-transparent, letting
 *   the background color show through while items remain fully visible.
 *   The currently selected slot is skipped so the vanilla selection highlight
 *   is not obscured.
 * - 2px border lines at RETURN, always drawn on top of everything.
 */
@Mixin(InGameHud.class)
public class InGameHudHotbarLinesMixin {

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void chestseparators$renderHotbarBg(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        ChestConfigManager m = ChestConfigManager.getInstance();
        if (m.getPlayerInventoryVisual().isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int selected = client.player.getInventory().selectedSlot;

        int[] pos = hotbarBase();
        int baseX = pos[0], baseY = pos[1];
        int bgAlpha = (GlobalChestConfig.instance.bgTransparency * 255 / 100) << 24;

        for (int i = 0; i < 9; i++) {
            if (i == selected) continue; // keep vanilla selection highlight visible
            int bgColor = m.getInventoryColor(i, ChestConfigManager.ACTION_BG);
            if (bgColor != 0) {
                int x = baseX + i * 20;
                context.fill(x, baseY, x + 16, baseY + 16, (bgColor & 0xFFFFFF) | bgAlpha);
            }
        }
    }

    @Inject(method = "renderHotbar", at = @At("RETURN"))
    private void chestseparators$renderHotbarLines(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        ChestConfigManager m = ChestConfigManager.getInstance();
        if (m.getPlayerInventoryVisual().isEmpty()) return;

        int[] pos = hotbarBase();
        int baseX = pos[0], baseY = pos[1];
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;

        for (int i = 0; i < 9; i++) {
            int x = baseX + i * 20;
            int y = baseY;

            int rTop = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_TOP), lineAlpha);
            int rBot = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_BOTTOM), lineAlpha);
            int rLeft = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_LEFT), lineAlpha);
            int rRight = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_RIGHT), lineAlpha);

            if (rTop == 0 && rBot == 0 && rLeft == 0 && rRight == 0) continue;

            int sTop = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_TOP);
            int sBot = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_BOTTOM);
            int sLeft = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_LEFT);
            int sRight = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_RIGHT);

            // 2px-wide lines to completely cover the grey slot dividers in the hotbar texture.
            if (rTop != 0) context.fill(x, y - 2, x + 16, y, rTop);
            if (rBot != 0) context.fill(x, y + 16, x + 16, y + 18, rBot);
            if (rLeft != 0) context.fill(x - 2, y, x, y + 16, rLeft);
            if (rRight != 0) context.fill(x + 16, y, x + 18, y + 16, rRight);

            drawCorner(context, x - 2, y - 2, rTop, sTop, rLeft, sLeft);
            drawCorner(context, x + 16, y - 2, rTop, sTop, rRight, sRight);
            drawCorner(context, x - 2, y + 16, rBot, sBot, rLeft, sLeft);
            drawCorner(context, x + 16, y + 16, rBot, sBot, rRight, sRight);
        }
    }

    private static int[] hotbarBase() {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();
        return new int[] {(w - 182) / 2 + 3, h - 19};
    }

    private static int applyAlpha(int color, int lineAlpha) {
        return color == 0 ? 0 : (color & 0x00FFFFFF) | lineAlpha;
    }

    /** Paints a 2×2 corner pixel using the edge with the higher paint sequence. */
    private static void drawCorner(DrawContext context, int cx, int cy, int colorA, int seqA, int colorB, int seqB) {
        if (colorA == 0 && colorB == 0) return;
        int color;
        if (colorA == 0) color = colorB;
        else if (colorB == 0) color = colorA;
        else color = (seqA >= seqB) ? colorA : colorB;
        context.fill(cx, cy, cx + 2, cy + 2, color);
    }
}
