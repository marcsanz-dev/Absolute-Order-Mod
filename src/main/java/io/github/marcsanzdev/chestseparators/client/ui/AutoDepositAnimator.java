package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.network.AutoDepositResultPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Renders the auto-deposit feedback: each deposited item flies from the player toward its destination
 * chest as a 2D icon overlaid on the HUD. World positions are projected to screen space every frame
 * (via {@link net.minecraft.client.render.GameRenderer#project}) so the icons stay anchored to the
 * chest even as the camera moves. The icons follow a gentle arc, shrink as they approach, and vanish
 * on arrival. A flat HUD overlay is used deliberately: Minecraft 1.21.11's deferred world-render
 * command pipeline makes true in-world item rendering fragile, while this path reuses the proven
 * {@link DrawContext#drawItem} API.
 */
public final class AutoDepositAnimator {

    private AutoDepositAnimator() {}

    /** One item in flight: a snapshot stack, its fixed world start/end, and when it should appear. */
    private record Flight(ItemStack stack, Vec3d start, Vec3d end, long startTime) {}

    private static final List<Flight> FLIGHTS = new ArrayList<>();

    private static final long DURATION_MS = 750L;
    private static final long STAGGER_MS = 70L;
    private static final double ARC_HEIGHT = 0.9;

    public static void register() {
        HudRenderCallback.EVENT.register(AutoDepositAnimator::onHudRender);
    }

    /** Queues animations for a completed auto-deposit. Called on the client thread from networking. */
    public static void addFlights(List<AutoDepositResultPayload.Flight> flights) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (flights.isEmpty()) {
            client.player.sendMessage(
                    Text.translatable("message.chestseparators.auto_deposit_none")
                            .formatted(Formatting.GRAY),
                    true);
            return;
        }

        // Items appear to leave the player's chest, slightly below eye level.
        Vec3d source = client.player.getEyePos().add(0.0, -0.2, 0.0);
        long now = System.currentTimeMillis();

        int index = 0;
        int total = 0;
        for (AutoDepositResultPayload.Flight flight : flights) {
            Vec3d target = Vec3d.ofCenter(flight.target());
            FLIGHTS.add(new Flight(flight.stack(), source, target, now + (long) index * STAGGER_MS));
            total += flight.stack().getCount();
            index++;
        }

        client.player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.5f, 1.4f);
        client.player.sendMessage(
                Text.translatable("message.chestseparators.auto_deposit_done", total)
                        .formatted(Formatting.GREEN),
                true);
    }

    private static void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (FLIGHTS.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || client.options.hudHidden) {
            // Drop pending animations rather than letting them stack up while hidden.
            FLIGHTS.clear();
            return;
        }

        long now = System.currentTimeMillis();
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        Vector3f forward = camera.getRotation().transform(new Vector3f(0.0f, 0.0f, -1.0f));

        Iterator<Flight> it = FLIGHTS.iterator();
        while (it.hasNext()) {
            Flight flight = it.next();
            long age = now - flight.startTime();
            if (age < 0) continue; // staggered start not reached yet
            double t = age / (double) DURATION_MS;
            if (t >= 1.0) {
                it.remove();
                continue;
            }

            double ease = t * t * (3.0 - 2.0 * t); // smoothstep
            Vec3d pos = lerp(flight.start(), flight.end(), ease).add(0.0, Math.sin(Math.PI * t) * ARC_HEIGHT, 0.0);

            // Skip points behind the camera: their projection mirrors and would draw garbage on screen.
            Vec3d rel = pos.subtract(camPos);
            if (rel.x * forward.x + rel.y * forward.y + rel.z * forward.z <= 0.05) continue;

            // GameRenderer.project returns normalized device coordinates in [-1, 1] (y up); map to HUD.
            Vec3d ndc = client.gameRenderer.project(pos);
            float screenX = (float) ((ndc.x * 0.5 + 0.5) * sw);
            float screenY = (float) ((1.0 - (ndc.y * 0.5 + 0.5)) * sh);

            float scale = (float) (1.15 - 0.65 * ease); // largest at the player, smallest at the chest

            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(screenX, screenY);
            matrices.scale(scale, scale);
            context.drawItem(flight.stack(), -8, -8);
            // Show the moved count briefly at the start of the flight.
            if (flight.stack().getCount() > 1 && t < 0.6) {
                context.drawStackOverlay(client.textRenderer, flight.stack(), -8, -8);
            }
            matrices.popMatrix();
        }
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
