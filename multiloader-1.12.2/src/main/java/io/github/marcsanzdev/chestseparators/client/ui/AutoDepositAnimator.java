package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.network.AutoDepositResultPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Renders the auto-deposit feedback: each deposited item flies from the player toward its destination
 * chest as a real 3D item model (the same "dropped item" look as pressing Q), rendered in the world
 * via {@link #renderFlights} (called from a LevelRenderer mixin with the frame's pose stack and render
 * collector). Items follow a gentle arc, spin as they travel, and optionally leave a particle trail.
 *
 * <p>The whole animation and the trail are independently toggleable in the config.
 *
 * <p>1.12.2 (render era E1): there is no world-render event in Architectury and no LevelRenderer pose-stack
 * hook, so the flying-item render is driven from Forge {@link RenderWorldLastEvent} and drawn with legacy
 * immediate-mode GL ({@link GlStateManager} + {@link RenderItem}) instead of a PoseStack/MultiBufferSource.
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
        // The flying-item render is driven by Forge's RenderWorldLastEvent (Architectury has no world-render
        // event, and 1.12.2 has no LevelRenderer pose-stack mixin here). The chest-lid tick runs on the
        // client TickEvent END phase. Both are wired through a single Forge event subscriber.
        MinecraftForge.EVENT_BUS.register(new AnimatorEvents());
    }

    /** Forge event bridge (replaces the modern Architectury ClientTickEvent + LevelRenderer mixin hook). */
    public static final class AnimatorEvents {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) tickChests();
        }

        @SubscribeEvent
        public void onRenderWorldLast(RenderWorldLastEvent event) {
            renderFlights(event.getPartialTicks());
        }
    }

    /** Queues animations for a completed auto-deposit (player -> chest). */
    public static void addFlights(List<AutoDepositResultPayload.Flight> flights) {
        addFlights(flights, false);
    }

    /**
     * Queues animations for a completed transfer. When {@code reverse} is true the items fly from each
     * container toward the player (grab); otherwise from the player toward each container (deposit).
     * Called on the client thread from networking.
     */
    public static void addFlights(List<AutoDepositResultPayload.Flight> flights, boolean reverse) {
        Minecraft client = Minecraft.getMinecraft();
        if (client.player == null) return;

        // When a container GUI is open (the Pull button), feedback goes ABOVE the interface via the
        // editor's status overlay — exactly like the Push button — instead of the action bar behind it.
        boolean guiOpen = client.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer;
        io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor editor =
                io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.getInstance();

        if (flights.isEmpty()) {
            // Reverse = pull from the open chest. Be specific about WHY nothing moved: no inventory filters
            // at all vs. filters exist but nothing in the chest matched. Non-reverse = radius auto-deposit.
            String key;
            if (reverse) {
                boolean invHasFilters = !io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                        .getPlayerInventoryFilters()
                        .isEmpty();
                key = invHasFilters
                        ? "message.chestseparators.pull_no_match"
                        : "message.chestseparators.pull_no_filters";
            } else {
                key = "message.chestseparators.auto_deposit_none";
            }
            ITextComponent noneMsg = new net.minecraft.util.text.TextComponentTranslation(key);
            noneMsg.getStyle().setColor(TextFormatting.GRAY);
            if (guiOpen && editor != null) editor.showStatus(noneMsg, TextFormatting.GRAY);
            else client.player.sendStatusMessage(noneMsg, true);
            return;
        }

        int total = 0;
        for (AutoDepositResultPayload.Flight flight : flights)
            total += flight.stack().getCount();

        client.player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.5f, reverse ? 1.0f : 1.4f);
        ITextComponent doneMsg = new net.minecraft.util.text.TextComponentTranslation(
                reverse
                        ? "message.chestseparators.auto_grab_done"
                        : "message.chestseparators.auto_deposit_done",
                total);
        doneMsg.getStyle().setColor(TextFormatting.GREEN);

        // GUI open: show it above the interface and finish instantly — no world fly-over you can't see.
        if (guiOpen) {
            if (editor != null) editor.showStatus(doneMsg, TextFormatting.GREEN);
            return;
        }
        client.player.sendStatusMessage(doneMsg, true);

        // The transfer already happened server-side; the flying items are pure cosmetics.
        if (!GlobalChestConfig.instance.autoDepositAnimation) return;

        // Items appear to leave/arrive at the player around body height.
        Vec3d playerPos = new Vec3d(client.player.posX, client.player.posY + 1.0, client.player.posZ);
        long now = System.currentTimeMillis();

        // Track, per container, when its last item leaves/arrives so we know when to close the lid.
        Map<BlockPos, Long> lastArrival = new HashMap<>();
        int index = 0;
        for (AutoDepositResultPayload.Flight flight : flights) {
            long startTime = now + (long) index * STAGGER_MS;
            BlockPos t = flight.target();
            Vec3d chest = new Vec3d(t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5);
            Vec3d start = reverse ? chest : playerPos;
            Vec3d end = reverse ? playerPos : chest;
            FLIGHTS.add(new FlyingItem(flight.stack(), start, end, startTime));
            lastArrival.merge(flight.target(), startTime + DURATION_MS, Math::max);
            index++;
        }

        // Open every destination container so the player sees where items are headed.
        WorldClient world = client.world;
        if (world != null) {
            for (Map.Entry<BlockPos, Long> entry : lastArrival.entrySet()) {
                openContainer(world, entry.getKey(), entry.getValue() + CHEST_DWELL_MS);
            }
        }
    }

    // Plays the opening animation of whatever container sits at the position: chests and ender chests
    // raise their lid, shulker boxes run their open animation. Containers without an animation (e.g.
    // barrels, or a minecart whose target maps to an empty block) are skipped silently.
    private static void openContainer(WorldClient world, BlockPos pos, long closeAt) {
        TileEntity be = world.getTileEntity(pos);

        if (be instanceof TileEntityChest || be instanceof TileEntityEnderChest) {
            boolean firstOpen = !OPEN_CHESTS.containsKey(pos);
            // TODO(1.12.2 port): the E5 LidAnimatorAccess mixin that force-raises a chest lid client-side is
            // out of scope for this cluster, so the lid does not visibly rise; the open/close SOUND still
            // plays and the dwell tracking still runs, preserving the deposit feedback.
            markOpen(pos, closeAt, true);
            if (firstOpen) playContainerSound(world, pos, openSoundFor(be));

            // TODO(1.12.2 port): double-chest neighbour lid sync depended on ChestBlock.TYPE/FACING block
            // properties (E4+); 1.12.2 chests have no such state, so the second half is not opened here.
        } else if (be instanceof TileEntityShulkerBox) {
            boolean firstOpen = !OPEN_CHESTS.containsKey(pos);
            // TODO(1.12.2 port): the ShulkerAnimationAccessor mixin (mixin.client) is out of scope for this
            // cluster, so the shulker open animation stage is not forced; the open sound still plays.
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
        WorldClient world = Minecraft.getMinecraft().world;
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

    private static void closeContainer(WorldClient world, BlockPos pos, boolean playSound) {
        TileEntity be = world.getTileEntity(pos);
        if (be instanceof TileEntityChest || be instanceof TileEntityEnderChest) {
            // TODO(1.12.2 port): lid lowering (LidAnimatorAccess) is out of scope; only the close sound plays.
            if (playSound) playContainerSound(world, pos, closeSoundFor(be));
        } else if (be instanceof TileEntityShulkerBox) {
            // TODO(1.12.2 port): shulker CLOSING stage (ShulkerAnimationAccessor) is out of scope; only the
            // close sound plays.
            if (playSound) playContainerSound(world, pos, SoundEvents.BLOCK_SHULKER_BOX_CLOSE);
        }
    }

    private static SoundEvent openSoundFor(TileEntity be) {
        return be instanceof TileEntityEnderChest ? SoundEvents.BLOCK_ENDERCHEST_OPEN : SoundEvents.BLOCK_CHEST_OPEN;
    }

    private static SoundEvent closeSoundFor(TileEntity be) {
        return be instanceof TileEntityEnderChest
                ? SoundEvents.BLOCK_ENDERCHEST_CLOSE
                : SoundEvents.BLOCK_CHEST_CLOSE;
    }

    private static BlockPos doubleNeighbor(WorldClient world, BlockPos pos) {
        // TODO(1.12.2 port): double-chest detection used ChestBlock.TYPE/FACING block-state properties that do
        // not exist in 1.12.2 (double chests are two adjacent single-chest blocks, no ChestType state), so the
        // paired-lid sync is dropped. Kept for structural parity; always reports no neighbour.
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.block.BlockChest)) return null;
        return null;
    }

    private static void playContainerSound(WorldClient world, BlockPos pos, SoundEvent sound) {
        world.playSound(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                sound,
                SoundCategory.BLOCKS,
                0.5f,
                world.rand.nextFloat() * 0.1f + 0.9f,
                false);
    }

    /**
     * Draws every in-flight item for the current frame. Called from Forge {@link RenderWorldLastEvent} (E1 has
     * no world-render event and no LevelRenderer pose-stack hook). Renders each stack as a GROUND item model
     * via the legacy immediate-mode {@link RenderItem} path, camera-relative through {@link GlStateManager}.
     */
    public static void renderFlights(float partialTicks) {
        if (FLIGHTS.isEmpty()) return;

        Minecraft client = Minecraft.getMinecraft();
        WorldClient world = client.world;
        if (world == null || client.player == null) {
            FLIGHTS.clear();
            return;
        }

        long now = System.currentTimeMillis();
        // E1 has no Camera object; interpolate the render view entity's eye position for the camera origin.
        net.minecraft.entity.Entity view = client.getRenderViewEntity();
        if (view == null) view = client.player;
        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
        boolean trail = GlobalChestConfig.instance.autoDepositTrail;
        RenderItem itemRenderer = client.getRenderItem();

        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderHelper.enableStandardItemLighting();

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
            Vec3d pos = lerp(flight.start, flight.end, ease).addVector(0.0, Math.sin(Math.PI * t) * ARC_HEIGHT, 0.0);

            if (trail && now - flight.lastParticleMs >= TRAIL_INTERVAL_MS) {
                world.spawnParticle(EnumParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0);
                flight.lastParticleMs = now;
            }

            float spin = (age * 0.18f) % 360.0f;

            GlStateManager.pushMatrix();
            GlStateManager.translate(pos.x - camX, pos.y - camY, pos.z - camZ);
            GlStateManager.rotate(spin, 0.0f, 1.0f, 0.0f);
            GlStateManager.scale(1.25f, 1.25f, 1.25f);
            itemRenderer.renderItem(flight.stack, ItemCameraTransforms.TransformType.GROUND);
            GlStateManager.popMatrix();
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
    }

    private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
        return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}
