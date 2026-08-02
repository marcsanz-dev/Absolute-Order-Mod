package io.github.marcsanzdev.chestseparators.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.marcsanzdev.chestseparators.client.ui.AutoDepositAnimator;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cross-loader replacement for Fabric's {@code WorldRenderEvents.AFTER_ENTITIES}: Architectury has no
 * world-render event, so the auto-deposit fly-over is driven from here. {@code submitEntities} is the E5
 * pass that renders every entity with the frame's camera-relative {@link PoseStack} and the render
 * {@link SubmitNodeCollector} — exactly what {@link AutoDepositAnimator#renderFlights} needs — so injecting
 * at its TAIL draws the flying items right after the world's entities, in the same space.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void chestseparators$renderAutoDepositFlights(
            PoseStack poseStack, LevelRenderState renderState, SubmitNodeCollector collector, CallbackInfo ci) {
        AutoDepositAnimator.renderFlights(poseStack, collector);
    }
}
