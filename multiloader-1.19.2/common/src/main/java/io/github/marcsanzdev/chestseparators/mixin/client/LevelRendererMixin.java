package io.github.marcsanzdev.chestseparators.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.marcsanzdev.chestseparators.client.ui.AutoDepositAnimator;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import com.mojang.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cross-loader replacement for Fabric's {@code WorldRenderEvents.AFTER_ENTITIES}: Architectury has no
 * world-render event, so the auto-deposit fly-over is driven from here. Injecting at the TAIL of
 * {@code renderLevel} — after the world's geometry, entities and particles are drawn but before the GUI —
 * draws the flying items in world space using the frame's camera-relative {@link PoseStack}. The animator
 * renders through its own buffer source and flushes it immediately.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void chestseparators$renderAutoDepositFlights(
            PoseStack poseStack,
            float partialTick,
            long finishNanoTime,
            boolean renderBlockOutline,
            Camera camera,
            GameRenderer gameRenderer,
            LightTexture lightTexture,
            Matrix4f projectionMatrix,
            CallbackInfo ci) {
        AutoDepositAnimator.renderFlights(poseStack);
    }
}
