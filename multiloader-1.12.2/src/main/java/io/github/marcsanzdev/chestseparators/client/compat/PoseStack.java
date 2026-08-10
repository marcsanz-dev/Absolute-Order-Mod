package io.github.marcsanzdev.chestseparators.client.compat;

import net.minecraft.client.renderer.GlStateManager;

/**
 * E1 compatibility stand-in for the modern {@code com.mojang.blaze3d.vertex.PoseStack}, which does not exist
 * in 1.12.2 (render era E1 = immediate-mode GL, no matrix-stack object). The editor cluster is written
 * against {@code context.pose().pushPose()/translate()/scale()/popPose()}; here each call forwards straight
 * to the legacy fixed-function GL matrix ({@link GlStateManager}), which is exactly the ambient transform the
 * immediate-mode draws in {@link GuiGraphics} already respect — so callers (drag ghosts, scaled previews)
 * behave as they do on the modern eras without any per-call-site change.
 */
public final class PoseStack {

    public void pushPose() {
        GlStateManager.pushMatrix();
    }

    public void popPose() {
        GlStateManager.popMatrix();
    }

    public void translate(double x, double y, double z) {
        GlStateManager.translate(x, y, z);
    }

    public void scale(float x, float y, float z) {
        GlStateManager.scale(x, y, z);
    }
}
