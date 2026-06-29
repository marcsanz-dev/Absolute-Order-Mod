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
 * <p>MC 1.21.6+ uses a deferred GUI renderer (Matrix3x2fStack, no z): elements
 * composite in submission order, items included. So the inject point decides
 * layering:
 * <ul>
 *   <li><b>Backgrounds</b> inject at the first {@code renderHotbarItem} call —
 *       i.e. after the opaque hotbar sprite + selection highlight are drawn but
 *       before any item — so backgrounds sit UNDER the items (no color mixing,
 *       same as the inventory screen).</li>
 *   <li><b>Lines</b> inject at RETURN, on top of everything.</li>
 * </ul>
 *
 * <p>The vanilla selection highlight is a 24×23 frame extending 4px around the
 * 16×16 item area. A 16×16 background fills only the frame's transparent center,
 * leaving the white border intact — so the selected slot's background is drawn
 * normally. Its 2px separator lines, however, land on the highlight frame, so
 * the selected slot's own lines and the selection-facing edges of its two
 * neighbours are suppressed to keep the highlight pristine.
 */
@Mixin(InGameHud.class)
public class InGameHudHotbarLinesMixin {

    private static final String RENDER_HOTBAR_ITEM =
            "Lnet/minecraft/client/gui/hud/InGameHud;renderHotbarItem(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/client/render/RenderTickCounter;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V";

    @Inject(method = "renderHotbar", at = @At(value = "INVOKE", target = RENDER_HOTBAR_ITEM, ordinal = 0))
    private void chestseparators$renderHotbarBackgrounds(
            DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        ChestConfigManager m = ChestConfigManager.getInstance();
        if (m.getPlayerInventoryVisual().isEmpty()) return;

        int[] pos = hotbarBase();
        int baseX = pos[0], baseY = pos[1];
        int bgAlpha = (GlobalChestConfig.instance.bgTransparency * 255 / 100) << 24;

        for (int i = 0; i < 9; i++) {
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

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        int selected = client.player.getInventory().selectedSlot;

        int[] pos = hotbarBase();
        int baseX = pos[0], baseY = pos[1];
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;

        for (int i = 0; i < 9; i++) {
            if (i == selected) continue; // selection highlight owns this slot's border

            int x = baseX + i * 20;
            int y = baseY;

            // Suppress the edge (and its corners) facing the selected slot: the 24×23
            // highlight frame extends 4px into the neighbour, where the 2px line would land.
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

            // Corners on the selection-facing side are guarded by boolean (not just a zero
            // color) because drawCorner falls back to the other edge's color when one is 0.
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
