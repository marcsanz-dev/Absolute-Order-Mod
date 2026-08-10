package io.github.marcsanzdev.chestseparators.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * E4 {@code net.minecraft.client.gui.GuiGraphics} compatibility shim for the E3 (1.19.2) render era, where
 * that class does not exist yet. It wraps the {@link PoseStack} the vanilla render pipeline hands us and
 * re-exposes exactly the slice of the E4 GuiGraphics API this mod uses, delegating each call to the E3
 * primitives ({@link GuiComponent} public statics, {@link Font}, the item renderer, and {@link Screen}
 * tooltips).
 *
 * <p>The whole editor/screen cluster keeps calling {@code context.fill/blit/drawString/renderItem/...} and
 * {@code context.pose()} unchanged — only the vanilla boundaries (the screen/HUD mixins) construct this shim
 * from the {@link PoseStack} they receive. This mirrors the {@code client/input} compat records used to cross
 * the E5→E4 input boundary in the 1.20.1 backport.
 *
 * <p>Deliberately does NOT extend {@link GuiComponent}: E3 declares {@code enableScissor}/{@code disableScissor}
 * as static members on it, which an instance method of the same name cannot legally override.
 */
public class GuiGraphics {

    private final PoseStack poseStack;
    private final Screen screen;

    public GuiGraphics(PoseStack poseStack) {
        this(poseStack, null);
    }

    public GuiGraphics(PoseStack poseStack, Screen screen) {
        this.poseStack = poseStack;
        this.screen = screen;
    }

    /** E4 {@code GuiGraphics.pose()} — the underlying matrix stack. */
    public PoseStack pose() {
        return poseStack;
    }

    // --- Fills ---------------------------------------------------------------------------------------------

    public void fill(int x1, int y1, int x2, int y2, int color) {
        // E2 gotcha: GuiComponent.fill does NOT manage the GL LIGHTING or ALPHA-TEST state, and vanilla item /
        // text rendering leaves lighting ENABLED. A lit fill comes out brighter — visibly the first panel/button
        // drawn before any texture blit (a blit resets lighting), which is why only the FIRST button of each row
        // looked hover-lightened. Force the flat 2D GUI state (no lighting, no alpha clip, white shader colour)
        // before every fill so they all render identically. On E4 this shim was a no-op (GuiGraphics batched it).
        RenderSystem.disableLighting();
        RenderSystem.disableAlphaTest();
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GuiComponent.fill(poseStack, x1, y1, x2, y2, color);
    }

    /** E4 hollow 1px rectangle outline. */
    public void renderOutline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    // --- Textures ------------------------------------------------------------------------------------------

    /** E4 {@code GuiGraphics.setColor}: tints subsequent draws via the shader colour. */
    public void setColor(float r, float g, float b, float a) {
        // 1.16.5 (E2) has no shader-colour API; the fixed-function tint is RenderSystem.color4f.
        RenderSystem.color4f(r, g, b, a);
    }

    /**
     * E4 {@code GuiGraphics.blit(ResourceLocation, x,y, w,h, u,v, regionW,regionH, texW,texH)}: E4 binds the
     * texture internally, so bind it here and forward to the matching E3 static blit (identical arg order).
     */
    public void blit(ResourceLocation atlas, int x, int y, int width, int height,
                     float u, float v, int regionWidth, int regionHeight, int texWidth, int texHeight) {
        // 1.16.5 has no RenderSystem.setShaderTexture; bind through the texture manager instead.
        Minecraft.getInstance().getTextureManager().bind(atlas);
        RenderSystem.enableBlend();
        GuiComponent.blit(poseStack, x, y, width, height, u, v, regionWidth, regionHeight, texWidth, texHeight);
    }

    // --- Text (delegated straight to Font so the shadow flag is honoured) ----------------------------------

    public int drawString(Font font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(poseStack, text, x, y, color) : font.draw(poseStack, text, x, y, color);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(poseStack, text, x, y, color) : font.draw(poseStack, text, x, y, color);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(poseStack, text, x, y, color) : font.draw(poseStack, text, x, y, color);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawCenteredString(poseStack, font, text, x, y, color);
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        GuiComponent.drawCenteredString(poseStack, font, text, x, y, color);
    }

    public void drawCenteredString(Font font, FormattedCharSequence text, int x, int y, int color) {
        // 1.16.5 GuiComponent has no FormattedCharSequence overload of drawCenteredString; centre by hand.
        font.drawShadow(poseStack, text, x - font.width(text) / 2f, (float) y, color);
    }

    // --- Items ---------------------------------------------------------------------------------------------

    /**
     * E4 {@code renderItem}: the item MODEL only (no count/durability decorations — the mod draws its own).
     *
     * <p>E3 {@code ItemRenderer.renderGuiItem} renders through the RenderSystem model-view stack and IGNORES
     * this GuiGraphics' {@link PoseStack}, so any {@code pose().translate/scale(...)} the caller applied
     * (e.g. the reorder drag ghost) would be lost and the item would land at raw (x,y) in the top-left. Bridge
     * our pose onto the model-view stack so items follow the transform exactly as they do on E4.
     */
    public void renderItem(ItemStack stack, int x, int y) {
        // E2 renderGuiItem draws through the legacy GL matrix stack and ignores this PoseStack, so bridge the
        // pose onto it (1.16.5 has no RenderSystem.getModelViewStack/applyModelViewMatrix): push the GL matrix,
        // multiply in our pose, render, then pop — items follow the caller's transform as they do on E4.
        RenderSystem.pushMatrix();
        RenderSystem.multMatrix(poseStack.last().pose());
        Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x, y);
        RenderSystem.popMatrix();
    }

    // --- Tooltips (require the owning screen) ---------------------------------------------------------------
    //
    // E4/E5 defer tooltips to the very end of the frame, so they always paint on top of everything. E3 draws
    // immediately, so a tooltip drawn mid-render (e.g. from a hovered toolbar button) gets covered by whatever
    // is drawn after it. To reproduce the E4/E5 behaviour we DEFER: renderTooltip/renderComponentTooltip just
    // record the last requested tooltip, and {@link #flushTooltip()} (called once at the end of the editor
    // overlay) paints it on top. Only one tooltip can be visible at a time, so last-wins is correct.

    private List<Component> pendingTooltip;
    private int pendingTooltipX;
    private int pendingTooltipY;

    public void renderTooltip(Font font, Component text, int x, int y) {
        pendingTooltip = java.util.Collections.singletonList(text);
        pendingTooltipX = x;
        pendingTooltipY = y;
    }

    public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        pendingTooltip = lines;
        pendingTooltipX = x;
        pendingTooltipY = y;
    }

    /** Paints the last deferred tooltip on top of everything, then clears it. Call once at overlay end. */
    public void flushTooltip() {
        if (pendingTooltip != null && screen != null) {
            screen.renderComponentTooltip(poseStack, pendingTooltip, pendingTooltipX, pendingTooltipY);
        }
        pendingTooltip = null;
    }

    // --- Scissor -------------------------------------------------------------------------------------------

    /** E4 {@code enableScissor} takes GUI-space coords; convert to the GL window scissor box (Y-flipped). */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int sx = (int) (x1 * scale);
        int sy = (int) (window.getHeight() - y2 * scale);
        int sw = (int) ((x2 - x1) * scale);
        int sh = (int) ((y2 - y1) * scale);
        RenderSystem.enableScissor(sx, sy, sw, sh);
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }

    // --- Batching ------------------------------------------------------------------------------------------

    /**
     * E4 {@code GuiGraphics.flush()} committed the deferred draw batch. E3 GUI primitives draw immediately
     * (Tesselator end per call), so there is nothing to flush here — the depth-order artefacts that made this
     * necessary on E4 do not arise. Kept as a no-op so shared editor code compiles unchanged.
     */
    public void flush() {
        // no-op on E3
    }
}
