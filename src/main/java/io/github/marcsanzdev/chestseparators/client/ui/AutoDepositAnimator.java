package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.mixin.client.ChestLidAccessor;
import io.github.marcsanzdev.chestseparators.mixin.client.ShulkerAnimationAccessor;
import io.github.marcsanzdev.chestseparators.network.AutoDepositResultPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.enums.ChestType;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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
    private static final long TRAIL_INTERVAL_MS = 110L;
    private static final int FULL_BRIGHT = 0xF000F0;

    // How long a destination chest stays open after its last item lands, before the lid closes.
    private static final long CHEST_DWELL_MS = 250L;

    /** A chest whose lid we forced open: when to close it, and whether it should play open/close sounds. */
    private static final class ChestOpen {
        long closeAt;
        boolean sound;
    }

    /** Chests (and double-chest neighbours) currently held open by the animation, client-side only. */
    private static final Map<BlockPos, ChestOpen> OPEN_CHESTS = new HashMap<>();

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(AutoDepositAnimator::onWorldRender);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickChests());
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

        // Track, per destination chest, when its last item arrives so we know when to close the lid.
        Map<BlockPos, Long> lastArrival = new HashMap<>();
        int index = 0;
        for (AutoDepositResultPayload.Flight flight : flights) {
            long startTime = now + (long) index * STAGGER_MS;
            Vec3d target = Vec3d.ofCenter(flight.target());
            FLIGHTS.add(new FlyingItem(flight.stack(), source, target, startTime));
            lastArrival.merge(flight.target(), startTime + DURATION_MS, Math::max);
            index++;
        }

        // Open every destination container so the player sees where items are headed.
        ClientWorld world = client.world;
        if (world != null) {
            for (Map.Entry<BlockPos, Long> entry : lastArrival.entrySet()) {
                openContainer(world, entry.getKey(), entry.getValue() + CHEST_DWELL_MS);
            }
        }
    }

    // Plays the opening animation of whatever container sits at the position: chests and ender chests
    // raise their lid, shulker boxes run their open animation. Containers without an animation (e.g.
    // barrels, or a minecart whose target maps to an empty block) are skipped silently.
    private static void openContainer(ClientWorld world, BlockPos pos, long closeAt) {
        BlockEntity be = world.getBlockEntity(pos);

        if (be instanceof ChestLidAccessor lid) {
            boolean firstOpen = !OPEN_CHESTS.containsKey(pos);
            lid.getLidAnimator().setOpen(true);
            markOpen(pos, closeAt, true);
            if (firstOpen) playContainerSound(world, pos, openSoundFor(be));

            // Open the other half of a double chest in sync (silently, so the sound plays once).
            BlockPos neighbor = doubleNeighbor(world, pos);
            if (neighbor != null && world.getBlockEntity(neighbor) instanceof ChestLidAccessor neighborLid) {
                neighborLid.getLidAnimator().setOpen(true);
                markOpen(neighbor, closeAt, false);
            }
        } else if (be instanceof ShulkerAnimationAccessor shulker) {
            boolean firstOpen = !OPEN_CHESTS.containsKey(pos);
            shulker.setAnimationStage(ShulkerBoxBlockEntity.AnimationStage.OPENING);
            markOpen(pos, closeAt, true);
            if (firstOpen) playContainerSound(world, pos, SoundEvents.BLOCK_SHULKER_BOX_OPEN);
        }
    }

    private static void markOpen(BlockPos pos, long closeAt, boolean sound) {
        ChestOpen state = OPEN_CHESTS.computeIfAbsent(pos, k -> new ChestOpen());
        state.closeAt = Math.max(state.closeAt, closeAt);
        state.sound = state.sound || sound;
    }

    private static void tickChests() {
        if (OPEN_CHESTS.isEmpty()) return;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            OPEN_CHESTS.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, ChestOpen>> it = OPEN_CHESTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ChestOpen> entry = it.next();
            if (now < entry.getValue().closeAt) continue;
            closeContainer(world, entry.getKey(), entry.getValue().sound);
            it.remove();
        }
    }

    private static void closeContainer(ClientWorld world, BlockPos pos, boolean playSound) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof ChestLidAccessor lid) {
            lid.getLidAnimator().setOpen(false);
            if (playSound) playContainerSound(world, pos, closeSoundFor(be));
        } else if (be instanceof ShulkerAnimationAccessor shulker) {
            shulker.setAnimationStage(ShulkerBoxBlockEntity.AnimationStage.CLOSING);
            if (playSound) playContainerSound(world, pos, SoundEvents.BLOCK_SHULKER_BOX_CLOSE);
        }
    }

    private static SoundEvent openSoundFor(BlockEntity be) {
        return be instanceof EnderChestBlockEntity ? SoundEvents.BLOCK_ENDER_CHEST_OPEN : SoundEvents.BLOCK_CHEST_OPEN;
    }

    private static SoundEvent closeSoundFor(BlockEntity be) {
        return be instanceof EnderChestBlockEntity
                ? SoundEvents.BLOCK_ENDER_CHEST_CLOSE
                : SoundEvents.BLOCK_CHEST_CLOSE;
    }

    private static BlockPos doubleNeighbor(ClientWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return null;
        ChestType type = state.get(ChestBlock.CHEST_TYPE);
        if (type == ChestType.SINGLE) return null;
        Direction facing = state.get(ChestBlock.FACING);
        Direction neighborDir = type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
        return pos.offset(neighborDir);
    }

    private static void playContainerSound(ClientWorld world, BlockPos pos, SoundEvent sound) {
        world.playSoundClient(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                sound,
                SoundCategory.BLOCKS,
                0.5f,
                world.getRandom().nextFloat() * 0.1f + 0.9f,
                false);
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

            // Build the dropped-item (GROUND) model for this stack at its current position. A fresh
            // state is required per item: the render command queue is deferred, so a shared/reused
            // state would be mutated before it is drawn, corrupting every item but the last.
            ItemRenderState renderState = new ItemRenderState();
            HeldItemContext heldContext = heldContextAt(world, pos);
            modelManager.clearAndUpdate(renderState, flight.stack, ItemDisplayContext.GROUND, world, heldContext, 0);
            if (renderState.isEmpty()) continue;

            float spin = (age * 0.18f) % 360.0f;

            matrices.push();
            matrices.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
            matrices.scale(1.25f, 1.25f, 1.25f);
            renderState.render(matrices, queue, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0);
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
