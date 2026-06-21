package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.network.AutoDepositResultPayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Renders the auto-deposit feedback: each deposited item flies from the player toward its destination
 * chest as a real 3D item model (the same "dropped item" look as pressing Q), rendered in the world
 * via the entity render command queue exposed on {@link WorldRenderContext}. Items follow a gentle
 * arc, spin as they travel, and optionally leave a particle trail before vanishing at the chest.
 *
 * <p>The whole animation and the trail are independently toggleable in the config.
 */
public final class AutoDepositAnimator {

    private AutoDepositAnimator() {}

    /** One item in flight: a snapshot stack, fixed world endpoints, start time, and trail bookkeeping. */
    private static final class FlyingItem {
        final ItemStack stack;
        final Vec3d start;
        final Vec3d end;
        final long startTime;
        long lastParticleMs;

        FlyingItem(ItemStack stack, Vec3d start, Vec3d end, long startTime) {
            this.stack = stack;
            this.start = start;
            this.end = end;
            this.startTime = startTime;
            this.lastParticleMs = 0L;
        }
    }

    private static final List<FlyingItem> FLIGHTS = new ArrayList<>();

    private static final long DURATION_MS = 1900L;
    private static final long STAGGER_MS = 130L;
    private static final double ARC_HEIGHT = 1.2;
    private static final long TRAIL_INTERVAL_MS = 30L;
    private static final int FULL_BRIGHT = 0xF000F0;

    // Reused per frame to avoid per-item allocation churn while still rebuilding state (some models,
    // e.g. compass/clock, depend on position).
    private static final ItemRenderState RENDER_STATE = new ItemRenderState();

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(AutoDepositAnimator::onWorldRender);
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

        int total = 0;
        for (AutoDepositResultPayload.Flight flight : flights)
            total += flight.stack().getCount();

        client.player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.5f, 1.4f);
        client.player.sendMessage(
                Text.translatable("message.chestseparators.auto_deposit_done", total)
                        .formatted(Formatting.GREEN),
                true);

        // The deposit already happened server-side; the flying items are pure cosmetics.
        if (!GlobalChestConfig.instance.autoDepositAnimation) return;

        // Items appear to leave the player around body height.
        Vec3d source = new Vec3d(client.player.getX(), client.player.getY() + 1.0, client.player.getZ());
        long now = System.currentTimeMillis();

        int index = 0;
        for (AutoDepositResultPayload.Flight flight : flights) {
            Vec3d target = Vec3d.ofCenter(flight.target());
            FLIGHTS.add(new FlyingItem(flight.stack(), source, target, now + (long) index * STAGGER_MS));
            index++;
        }
    }

    private static void onWorldRender(WorldRenderContext context) {
        if (FLIGHTS.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        MatrixStack matrices = context.matrices();
        OrderedRenderCommandQueue queue = context.commandQueue();
        if (world == null || client.player == null || matrices == null || queue == null) {
            FLIGHTS.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        ItemModelManager modelManager = client.getItemModelManager();
        boolean trail = GlobalChestConfig.instance.autoDepositTrail;

        Iterator<FlyingItem> it = FLIGHTS.iterator();
        while (it.hasNext()) {
            FlyingItem flight = it.next();
            long age = now - flight.startTime;
            if (age < 0) continue; // staggered start not reached yet
            double t = age / (double) DURATION_MS;
            if (t >= 1.0) {
                it.remove();
                continue;
            }

            double ease = t * t * (3.0 - 2.0 * t); // smoothstep
            Vec3d pos = lerp(flight.start, flight.end, ease).add(0.0, Math.sin(Math.PI * t) * ARC_HEIGHT, 0.0);

            if (trail && now - flight.lastParticleMs >= TRAIL_INTERVAL_MS) {
                world.addParticleClient(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
                flight.lastParticleMs = now;
            }

            // Build the dropped-item (GROUND) model for this stack at its current position.
            HeldItemContext heldContext = heldContextAt(world, pos);
            modelManager.clearAndUpdate(RENDER_STATE, flight.stack, ItemDisplayContext.GROUND, world, heldContext, 0);
            if (RENDER_STATE.isEmpty()) continue;

            float spin = (age * 0.18f) % 360.0f;

            matrices.push();
            matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
            matrices.scale(1.25f, 1.25f, 1.25f);
            RENDER_STATE.render(matrices, queue, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0);
            matrices.pop();
        }
    }

    private static HeldItemContext heldContextAt(World world, Vec3d pos) {
        return new HeldItemContext() {
            @Override
            public World getEntityWorld() {
                return world;
            }

            @Override
            public Vec3d getEntityPos() {
                return pos;
            }

            @Override
            public float getBodyYaw() {
                return 0.0f;
            }
        };
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
