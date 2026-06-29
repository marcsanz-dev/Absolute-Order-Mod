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
 * Draws separator backgrounds and 2px border lines on the HUD hotbar slots.
 *
 * Everything is drawn at RETURN (on top of items and the hotbar sprite).
 * The selected slot is skipped entirely so the vanilla selection highlight
 * remains intact. Slots adjacent to the selected slot suppress the border
 * edge that faces the selected slot (right edge of selected-1, left edge of
 * selected+1), because the vanilla selection highlight sprite is 24×23 px
 * and extends 4px beyond the item area — those 2px border lines would
 * visually clip the highlight's outer border.
 */
@Mixin(InGameHud.class)
public class InGameHudHotbarLinesMixin {

    @Inject(method = "renderHotbar", at = @At("RETURN"))
    private void chestseparators$renderHotbarOverlay(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        ChestConfigManager m = ChestConfigManager.getInstance();
        if (m.getPlayerInventoryVisual().isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int selected = client.player.getInventory().selectedSlot;

        int[] pos = hotbarBase();
        int baseX = pos[0], baseY = pos[1];
        int bgAlpha = (GlobalChestConfig.instance.bgTransparency * 255 / 100) << 24;
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;

        for (int i = 0; i < 9; i++) {
            if (i == selected) continue;

            int x = baseX + i * 20;
            int y = baseY;

            // Background
            int bgColor = m.getInventoryColor(i, ChestConfigManager.ACTION_BG);
            if (bgColor != 0) {
                context.fill(x, y, x + 16, y + 16, (bgColor & 0xFFFFFF) | bgAlpha);
            }

            // The vanilla selection highlight is 24×23 px starting 4px left of the
            // selected slot's item area. The 2px right border of selected-1 and the
            // 2px left border of selected+1 land inside that 4px margin — suppress them
            // (and the corners on that side) so the highlight always shows intact.
            boolean skipRight = (i == selected - 1);
            boolean skipLeft = (i == selected + 1);

            int rTop = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_TOP), lineAlpha);
            int rBot = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_BOTTOM), lineAlpha);
            int rLeft = skipLeft ? 0 : applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_LEFT), lineAlpha);
            int rRight = skipRight ? 0 : applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_RIGHT), lineAlpha);

            if (rTop == 0 && rBot == 0 && rLeft == 0 && rRight == 0) continue;

            int sTop = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_TOP);
            int sBot = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_BOTTOM);
            int sLeft = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_LEFT);
            int sRight = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_RIGHT);

            if (rTop != 0) context.fill(x, y - 2, x + 16, y, rTop);
            if (rBot != 0) context.fill(x, y + 16, x + 16, y + 18, rBot);
            if (rLeft != 0) context.fill(x - 2, y, x, y + 16, rLeft);
            if (rRight != 0) context.fill(x + 16, y, x + 18, y + 16, rRight);

            // Corners on the selection-highlight side are suppressed with full guard
            // (not just zeroing rRight/rLeft) because drawCorner falls back to colorA
            // if colorB is 0, which would still paint in the highlight area.
            if (!skipLeft) drawCorner(context, x - 2, y - 2, rTop, sTop, rLeft, sLeft);
            if (!skipRight) drawCorner(context, x + 16, y - 2, rTop, sTop, rRight, sRight);
            if (!skipLeft) drawCorner(context, x - 2, y + 16, rBot, sBot, rLeft, sLeft);
            if (!skipRight) drawCorner(context, x + 16, y + 16, rBot, sBot, rRight, sRight);
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

    /** Paints a 2×2 corner using the edge with the higher paint sequence. */
    private static void drawCorner(DrawContext context, int cx, int cy, int colorA, int seqA, int colorB, int seqB) {
        if (colorA == 0 && colorB == 0) return;
        int color;
        if (colorA == 0) color = colorB;
        else if (colorB == 0) color = colorA;
        else color = (seqA >= seqB) ? colorA : colorB;
        context.fill(cx, cy, cx + 2, cy + 2, color);
    }
}
