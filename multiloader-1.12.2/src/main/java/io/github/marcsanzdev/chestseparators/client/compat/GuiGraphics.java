package io.github.marcsanzdev.chestseparators.client.compat;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.opengl.GL11;

/**
 * E4 {@code net.minecraft.client.gui.GuiGraphics} compatibility shim for the E1 (1.12.2) render era. It
 * re-exposes exactly the slice of the E4 GuiGraphics API the editor cluster uses and delegates each call to
 * legacy immediate-mode GL primitives ({@link Gui} statics, {@link FontRenderer}, the {@code RenderItem}, and
 * {@link GuiScreen} tooltips). The whole editor/screen cluster keeps calling {@code context.fill/blit/
 * drawString/renderItem/pose()/...} unchanged; only the vanilla boundaries (the screen mixins) construct this
 * shim. This mirrors the compat shims used to cross the render-era boundary in the modern backports, but E1
 * has no matrix-stack object — {@link #pose()} returns a {@link PoseStack} that forwards to {@link GlStateManager}.
 */
public class GuiGraphics {

    private final PoseStack poseStack = new PoseStack();
    private final GuiScreen screen;

    public GuiGraphics() {
        this(null);
    }

    public GuiGraphics(GuiScreen screen) {
        this.screen = screen;
    }

    /** E4 {@code GuiGraphics.pose()} — the (compat) matrix stack, forwarding to the legacy GL matrix. */
    public PoseStack pose() {
        return poseStack;
    }

    // --- Fills ---------------------------------------------------------------------------------------------

    /** Filled ARGB rectangle. */
    public void fill(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1, y1, x2, y2, color);
        // E1 GL-state leak: Gui.drawRect leaves GlStateManager.color set to the rect's colour. Any textured
        // draw afterwards (blit/renderItem) with no explicit setColor would be tinted by it — e.g. the sub-menu
        // dim (0x55000000) tinted every toolbar icon drawn after it black/translucent. Reset to white so fills
        // never leak their colour into later draws.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** E4 hollow 1px rectangle outline. */
    public void renderOutline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    // --- Textures ------------------------------------------------------------------------------------------

    // A tint requested via setColor() for the NEXT blit only. On E1 the GL colour is global and leaks between
    // draws (a coloured fill or dark text left it non-white, which then tinted every icon drawn afterwards),
    // so blit() forces white unless a tint was just requested. Mirrors UiTheme's setColor→blit→setColor(white).
    private float[] pendingBlitColor = null;

    /** E4 {@code GuiGraphics.setColor}: tints the next {@link #blit} draw. */
    public void setColor(float r, float g, float b, float a) {
        this.pendingBlitColor = new float[] {r, g, b, a};
        GlStateManager.color(r, g, b, a);
    }

    /**
     * E4 {@code GuiGraphics.blit(ResourceLocation, x,y, w,h, u,v, regionW,regionH, texW,texH)}: draws a w×h
     * quad sampling the [u..u+regionW] × [v..v+regionH] region of a texW×texH texture. E1 has no equivalent
     * static that supports a separate sample-region and draw-size (vanilla's drawModalRectWithCustomSizedTexture
     * scales 1:1), so blit a manual textured quad — this is what lets the 128px vector icons draw crisply at
     * ~16px.
     */
    public void blit(ResourceLocation atlas, int x, int y, int width, int height,
                     float u, float v, int regionWidth, int regionHeight, int texWidth, int texHeight) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(atlas);
        // Reset the leaked GL colour: white unless setColor() just requested a one-shot tint.
        if (this.pendingBlitColor != null) {
            GlStateManager.color(
                    pendingBlitColor[0], pendingBlitColor[1], pendingBlitColor[2], pendingBlitColor[3]);
            this.pendingBlitColor = null;
        } else {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        float uMin = u / texWidth;
        float uMax = (u + regionWidth) / texWidth;
        float vMin = v / texHeight;
        float vMax = (v + regionHeight) / texHeight;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0.0D).tex(uMin, vMax).endVertex();
        buffer.pos(x + width, y + height, 0.0D).tex(uMax, vMax).endVertex();
        buffer.pos(x + width, y, 0.0D).tex(uMax, vMin).endVertex();
        buffer.pos(x, y, 0.0D).tex(uMin, vMin).endVertex();
        tessellator.draw();
    }

    // --- Text (delegated straight to FontRenderer so the shadow flag is honoured) --------------------------

    public int drawString(FontRenderer font, String text, int x, int y, int color) {
        return drawString(font, text, x, y, color, true);
    }

    public int drawString(FontRenderer font, String text, int x, int y, int color, boolean shadow) {
        return font.drawString(text, x, y, color, shadow);
    }

    public int drawString(FontRenderer font, ITextComponent text, int x, int y, int color) {
        return drawString(font, text.getFormattedText(), x, y, color, true);
    }

    public int drawString(FontRenderer font, ITextComponent text, int x, int y, int color, boolean shadow) {
        return drawString(font, text.getFormattedText(), x, y, color, shadow);
    }

    public void drawCenteredString(FontRenderer font, String text, int x, int y, int color) {
        // Gui.drawCenteredString is an INSTANCE method on Gui in 1.12.2 (not static), so centre by hand.
        font.drawStringWithShadow(text, x - font.getStringWidth(text) / 2.0f, (float) y, color);
    }

    public void drawCenteredString(FontRenderer font, ITextComponent text, int x, int y, int color) {
        drawCenteredString(font, text.getFormattedText(), x, y, color);
    }

    // --- Items ---------------------------------------------------------------------------------------------

    /**
     * E4 {@code renderItem}: the item MODEL only (no count/durability decorations — the mod draws its own).
     * E1 renders through the legacy GL matrix, so the ambient {@link PoseStack}/GlStateManager transform the
     * caller applied is respected automatically.
     */
    public void renderItem(ItemStack stack, int x, int y) {
        // Clear any leaked GL tint so the item model renders at its true colours.
        this.pendingBlitColor = null;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
    }

    // --- Tooltips (require the owning screen; deferred to paint on top) -------------------------------------

    private List<String> pendingTooltip;
    private int pendingTooltipX;
    private int pendingTooltipY;

    public void renderTooltip(FontRenderer font, ITextComponent text, int x, int y) {
        pendingTooltip = Arrays.asList(text.getFormattedText().replace("\\n", "\n").split("\n"));
        pendingTooltipX = x;
        pendingTooltipY = y;
    }

    public void renderComponentTooltip(FontRenderer font, List<ITextComponent> lines, int x, int y) {
        java.util.List<String> strings = new java.util.ArrayList<>(lines.size());
        for (ITextComponent line : lines) {
            strings.add(line.getFormattedText());
        }
        pendingTooltip = strings;
        pendingTooltipX = x;
        pendingTooltipY = y;
    }

    /** Paints the last deferred tooltip on top of everything, then clears it. Call once at overlay end. */
    public void flushTooltip() {
        if (pendingTooltip != null && screen != null) {
            screen.drawHoveringText(pendingTooltip, pendingTooltipX, pendingTooltipY);
        }
        pendingTooltip = null;
    }

    // --- Scissor -------------------------------------------------------------------------------------------

    /** E4 {@code enableScissor} takes GUI-space coords; convert to the GL window scissor box (Y-flipped, scaled). */
    public void enableScissor(int x1, int y1, int x2, int y2) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x1 * scale, mc.displayHeight - y2 * scale, (x2 - x1) * scale, (y2 - y1) * scale);
    }

    public void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // --- Batching ------------------------------------------------------------------------------------------

    /** E4 {@code GuiGraphics.flush()} — E1 primitives draw immediately, so nothing to flush. */
    public void flush() {
        // no-op on E1
    }
}
