package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.compat.GuiGraphics;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the 2px separator border lines on the HUD hotbar slots.
 *
 * <p>1.12.2 (E1) port of the modern {@code chestseparators$renderHotbarLines} pass. The Forge hotbar is
 * drawn by {@link GuiIngameForge#renderHotbar(ScaledResolution, float)}; injecting at RETURN paints the
 * lines on top of everything, under the identity model-view (screen space), so an immediate-mode
 * {@link GuiGraphics} fill lands at the same coords vanilla uses.
 *
 * <p>Only the border lines are ported. The per-slot backgrounds have no clean E1 injection point (E1 has
 * no {@code renderSlot} draw-context hook) and are dropped here — see the TODO below.
 */
@Mixin(GuiIngameForge.class)
public abstract class InGameHudHotbarLinesMixin {

    // TODO(1.12.2 port): the per-slot hotbar backgrounds (modern renderSlot HEAD inject) have no clean E1
    // equivalent (no draw-context hook on the slot render), so only the border lines are drawn here.

    @Inject(method = "renderHotbar", at = @At("RETURN"))
    private void chestseparators$renderHotbarLines(ScaledResolution res, float partialTicks, CallbackInfo ci) {
        // Bail before touching ChestConfigManager when there is no live player/world (e.g. the HUD frame drawn
        // during world teardown) — triggering the class's first load in that torn-down state crashed the client.
        Minecraft client = Minecraft.getMinecraft();
        if (client.player == null || client.world == null) return;

        ChestConfigManager m = ChestConfigManager.getInstance();
        if (m.getPlayerInventoryVisual().isEmpty()) return;

        GuiGraphics context = new GuiGraphics();
        int selected = client.player.inventory.currentItem;

        int[] pos = hotbarBase(res);
        int baseX = pos[0], baseY = pos[1];
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;

        for (int i = 0; i < 9; i++) {
            if (i == selected) continue; // selection highlight owns this slot's border

            int x = baseX + i * 20;
            int y = baseY;

            // Suppress the edge (and its corners) facing the selected slot: the 24x23 highlight frame
            // extends 4px into the neighbour, where the 2px line would land.
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

            // 2px lines fully cover the grey slot dividers of the hotbar texture.
            if (rTop != 0) context.fill(x, y - 2, x + 16, y, rTop);
            if (rBot != 0) context.fill(x, y + 16, x + 16, y + 18, rBot);
            if (rLeft != 0) context.fill(x - 2, y, x, y + 16, rLeft);
            if (rRight != 0) context.fill(x + 16, y, x + 18, y + 16, rRight);

            // Corners on the selection-facing side are guarded by boolean (not just a zero color) because
            // drawCorner falls back to the other edge's color when one is 0.
            if (!skipLeft) drawCorner(context, x - 2, y - 2, rTop, sTop, rLeft, sLeft);
            if (!skipRight) drawCorner(context, x + 16, y - 2, rTop, sTop, rRight, sRight);
            if (!skipLeft) drawCorner(context, x - 2, y + 16, rBot, sBot, rLeft, sLeft);
            if (!skipRight) drawCorner(context, x + 16, y + 16, rBot, sBot, rRight, sRight);
        }
    }

    private static int[] hotbarBase(ScaledResolution res) {
        int w = res.getScaledWidth();
        int h = res.getScaledHeight();
        return new int[] {(w - 182) / 2 + 3, h - 19};
    }

    private static int applyAlpha(int color, int lineAlpha) {
        return color == 0 ? 0 : (color & 0x00FFFFFF) | lineAlpha;
    }

    /** Paints a 2x2 corner using the edge with the higher paint sequence. */
    private static void drawCorner(GuiGraphics context, int cx, int cy, int colorA, int seqA, int colorB, int seqB) {
        if (colorA == 0 && colorB == 0) return;
        int color;
        if (colorA == 0) color = colorB;
        else if (colorB == 0) color = colorA;
        else color = (seqA >= seqB) ? colorA : colorB;
        context.fill(cx, cy, cx + 2, cy + 2, color);
    }
}
