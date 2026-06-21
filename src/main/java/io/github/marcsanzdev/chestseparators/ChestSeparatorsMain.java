package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.*;
import io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Common mod initializer. Registers networking payloads, data components,
 * and all server-side packet receivers.
 */
public class ChestSeparatorsMain implements ModInitializer {

    /** Thread-safe map of chests currently locked for editing, keyed by block position. */
    public static final Map<BlockPos, UUID> LOCKED_CHESTS = new ConcurrentHashMap<>();

    private static final Random EXPEL_RANDOM = new Random();

    @Override
    public void onInitialize() {
        GlobalChestConfig.loadConfig();
        ChestSeparatorsComponents.initialize();

        PayloadTypeRegistry.playS2C().register(ShulkerUUIDPayload.ID, ShulkerUUIDPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WhitelistPayload.ID, WhitelistPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WhitelistPayload.ID, WhitelistPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WhitelistRequestPayload.ID, WhitelistRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EditorLockRequestPayload.ID, EditorLockRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EditorLockResponsePayload.ID, EditorLockResponsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AutoDepositRequestPayload.ID, AutoDepositRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AutoDepositResultPayload.ID, AutoDepositResultPayload.CODEC);

        // Release all locks held by a player who disconnects abruptly.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LOCKED_CHESTS.values().removeIf(uuid -> uuid.equals(handler.player.getUuid()));
        });

        // Handles editor lock requests, with support for double chests (two associated positions).
        ServerPlayNetworking.registerGlobalReceiver(EditorLockRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() != null) {
                    UUID playerUuid = context.player().getUuid();
                    BlockPos targetPos = payload.pos();
                    World world = context.player().getEntityWorld();

                    // Resolve all block positions occupied by this container (1 for single, 2 for double).
                    List<BlockPos> associatedPositions = getAssociatedPositions(world, targetPos);

                    if (payload.isLocking()) {
                        boolean canLock = true;

                        // Deny the lock if any part of the container is held by a different player.
                        for (BlockPos p : associatedPositions) {
                            if (LOCKED_CHESTS.containsKey(p)
                                    && !LOCKED_CHESTS.get(p).equals(playerUuid)) {
                                canLock = false;
                                break;
                            }
                        }

                        if (canLock) {
                            // Acquire all positions atomically so no partial lock can slip through.
                            for (BlockPos p : associatedPositions) {
                                LOCKED_CHESTS.put(p, playerUuid);
                            }
                            if (ServerPlayNetworking.canSend(context.player(), EditorLockResponsePayload.ID)) {
                                ServerPlayNetworking.send(
                                        context.player(), new EditorLockResponsePayload(targetPos, true));
                            }
                        } else {
                            if (ServerPlayNetworking.canSend(context.player(), EditorLockResponsePayload.ID)) {
                                ServerPlayNetworking.send(
                                        context.player(), new EditorLockResponsePayload(targetPos, false));
                            }
                        }
                    } else {
                        // Release all associated positions when the player exits the editor.
                        for (BlockPos p : associatedPositions) {
                            if (LOCKED_CHESTS.containsKey(p)
                                    && LOCKED_CHESTS.get(p).equals(playerUuid)) {
                                LOCKED_CHESTS.remove(p);
                            }
                        }
                    }
                }
            });
        });

        // Handles whitelist fetch requests sent by the client when a container is first opened.
        ServerPlayNetworking.registerGlobalReceiver(WhitelistRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() != null) {
                    World world = context.player().getEntityWorld();
                    Inventory targetInventory = getChestInventorySafe(world, payload.pos());

                    if (targetInventory instanceof IWhitelistProvider provider) {
                        if (ServerPlayNetworking.canSend(context.player(), WhitelistPayload.ID)) {
                            ServerPlayNetworking.send(
                                    context.player(), new WhitelistPayload(payload.pos(), provider.getWhitelists()));
                        }
                    }
                }
            });
        });

        // Persists incoming whitelist updates from the client and broadcasts them to co-viewers.
        ServerPlayNetworking.registerGlobalReceiver(WhitelistPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() != null) {
                    World world = context.player().getEntityWorld();
                    BlockState state = world.getBlockState(payload.pos());
                    boolean isEnderChest = state.getBlock() == Blocks.ENDER_CHEST;

                    // Ender Chest filters are local-only (hoppers cannot access them); nothing to do server-side.
                    if (isEnderChest) return;

                    Inventory targetInventory = getChestInventorySafe(world, payload.pos());

                    if (targetInventory instanceof IWhitelistProvider provider) {
                        provider.setWhitelists(payload.whitelists());
                        targetInventory.markDirty();

                        // Broadcast the updated whitelist to all other players currently viewing this container.
                        if (world instanceof ServerWorld serverWorld) {
                            for (ServerPlayerEntity trackingPlayer :
                                    PlayerLookup.tracking(serverWorld, payload.pos())) {
                                if (trackingPlayer != context.player()
                                        && ServerPlayNetworking.canSend(trackingPlayer, WhitelistPayload.ID)) {
                                    ServerPlayNetworking.send(trackingPlayer, payload);
                                }
                            }
                        }

                        // Eject items that violate the new whitelist when all three enforcement rules are enabled.
                        if (GlobalChestConfig.instance.expelInvalidItems) {
                            for (int i = 0; i < targetInventory.size(); i++) {
                                ItemStack stack = targetInventory.getStack(i);
                                if (!stack.isEmpty() && payload.whitelists().containsKey(i)) {
                                    SlotWhitelist wl = payload.whitelists().get(i);

                                    if (wl.allowManual() && wl.allowShift() && wl.allowHopper()) {
                                        String itemId = Registries.ITEM
                                                .getId(stack.getItem())
                                                .toString();

                                        if (!wl.allowedItems().contains(itemId)) {
                                            ItemStack extracted = targetInventory.removeStack(i);

                                            double dropX = payload.pos().getX() + 0.5D;
                                            double dropY = payload.pos().getY() + 0.5D;
                                            double dropZ = payload.pos().getZ() + 0.5D;

                                            ItemEntity itemEntity =
                                                    new ItemEntity(world, dropX, dropY, dropZ, extracted);

                                            double dirX = context.player().getX() - dropX;
                                            double dirY = context.player().getEyeY() - dropY;
                                            double dirZ = context.player().getZ() - dropZ;

                                            double distance = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
                                            dirX /= distance;
                                            dirY /= distance;
                                            dirZ /= distance;

                                            double force = 0.3D;
                                            itemEntity.setVelocity(
                                                    dirX * force + EXPEL_RANDOM.nextGaussian() * 0.05D,
                                                    dirY * force + EXPEL_RANDOM.nextGaussian() * 0.05D + 0.1D,
                                                    dirZ * force + EXPEL_RANDOM.nextGaussian() * 0.05D);

                                            itemEntity.setToDefaultPickupDelay();
                                            world.spawnEntity(itemEntity);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        });

        // Handles the radius auto-deposit trigger: sort the player's inventory into nearby filtered
        // containers and report back what moved so the client can animate it.
        ServerPlayNetworking.registerGlobalReceiver(AutoDepositRequestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() != null) {
                    performAutoDeposit(
                            context.player(),
                            payload.radius(),
                            payload.throughWalls(),
                            payload.enderWhitelists(),
                            payload.entityWhitelists());
                }
            });
        });
    }

    /** Hard cap on the auto-deposit radius, regardless of what the client requests. */
    private static final int MAX_AUTO_DEPOSIT_RADIUS = 32;

    /**
     * Sorts every stack in the player's main inventory into nearby containers whose filters list the
     * item, nearest containers first. Containers blocked by a solid block are skipped unless
     * {@code throughWalls} is set. Sends an {@link AutoDepositResultPayload} describing every transfer.
     */
    private static void performAutoDeposit(
            ServerPlayerEntity player,
            int radius,
            boolean throughWalls,
            Map<Integer, SlotWhitelist> enderWhitelists,
            Map<java.util.UUID, Map<Integer, SlotWhitelist>> entityWhitelists) {
        World world = player.getEntityWorld();
        if (!(world instanceof ServerWorld)) return;

        radius = Math.max(1, Math.min(radius, MAX_AUTO_DEPOSIT_RADIUS));
        Vec3d eye = player.getEyePos();
        BlockPos origin = player.getBlockPos();
        long radiusSq = (long) radius * radius;

        // 1. Collect candidate containers: filtered, within range, with a clear line of sight.
        List<Candidate> candidates = new ArrayList<>();
        int chunkRadius = (radius >> 4) + 1;
        int centerChunkX = origin.getX() >> 4;
        int centerChunkZ = origin.getZ() >> 4;

        // Nearest reachable ender chest block, used as the deposit target for the player's ender
        // inventory (ender filters are client-side, so they arrive in the request payload).
        BlockPos nearestEnder = null;
        double nearestEnderDistSq = Double.MAX_VALUE;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                net.minecraft.world.chunk.WorldChunk chunk = world.getChunk(centerChunkX + dx, centerChunkZ + dz);
                for (Map.Entry<BlockPos, BlockEntity> entry :
                        chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    BlockEntity be = entry.getValue();

                    double cdx = pos.getX() + 0.5D - eye.x;
                    double cdy = pos.getY() + 0.5D - eye.y;
                    double cdz = pos.getZ() + 0.5D - eye.z;
                    double distSq = cdx * cdx + cdy * cdy + cdz * cdz;
                    if (distSq > radiusSq) continue;

                    if (be instanceof net.minecraft.block.entity.EnderChestBlockEntity) {
                        if (distSq < nearestEnderDistSq
                                && (throughWalls || !isBlockObstructed(world, eye, pos, player))) {
                            nearestEnder = pos;
                            nearestEnderDistSq = distSq;
                        }
                        continue;
                    }

                    if (!(be instanceof Inventory inv) || !(be instanceof IWhitelistProvider provider)) continue;
                    Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();
                    if (whitelists == null || whitelists.isEmpty()) continue;

                    if (!throughWalls && isBlockObstructed(world, eye, pos, player)) continue;

                    candidates.add(new Candidate(pos, inv, whitelists, distSq));
                }
            }
        }

        // 1b. Mobile filtered containers: chest minecarts and chest boats are entities, not block
        // entities, and their filters live client-side only, so they arrive in entityWhitelists keyed
        // by UUID.
        net.minecraft.util.math.Box box = player.getBoundingBox().expand(radius);
        for (net.minecraft.entity.Entity entity : world.getOtherEntities(player, box, e -> e instanceof Inventory)) {
            Map<Integer, SlotWhitelist> whitelists = entityWhitelists.get(entity.getUuid());
            if (whitelists == null || whitelists.isEmpty()) continue;

            Vec3d center = entity.getBoundingBox().getCenter();
            double distSq = center.squaredDistanceTo(eye);
            if (distSq > radiusSq) continue;
            if (!throughWalls && blockedToPoint(world, eye, center, player, null)) continue;

            candidates.add(new Candidate(entity.getBlockPos(), (Inventory) entity, whitelists, distSq));
        }

        // 1c. Ender chest: deposit into the player's ender inventory, animated at the nearest reachable
        // ender chest block, using the client-supplied ender filter.
        if (nearestEnder != null && enderWhitelists != null && !enderWhitelists.isEmpty()) {
            candidates.add(
                    new Candidate(nearestEnder, player.getEnderChestInventory(), enderWhitelists, nearestEnderDistSq));
        }

        candidates.sort(java.util.Comparator.comparingDouble(c -> c.distSq));

        // 2. Deposit each stack into matching candidate slots, recording moved amounts per container.
        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();
        // Preserve discovery order so the animation roughly mirrors inventory layout.
        Map<BlockPos, Map<net.minecraft.item.Item, Integer>> moved = new java.util.LinkedHashMap<>();

        for (int i = 0; i < net.minecraft.entity.player.PlayerInventory.MAIN_SIZE; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            net.minecraft.item.Item item = stack.getItem();
            String itemId = Registries.ITEM.getId(item).toString();

            for (Candidate candidate : candidates) {
                if (stack.isEmpty()) break;
                if (!containerListsItem(candidate.whitelists, itemId)) continue;

                int before = stack.getCount();
                insertRespectingFilter(candidate.inv, candidate.whitelists, stack, itemId);
                int delta = before - stack.getCount();
                if (delta > 0) {
                    candidate.inv.markDirty();
                    moved.computeIfAbsent(candidate.pos, k -> new java.util.LinkedHashMap<>())
                            .merge(item, delta, Integer::sum);
                }
            }
            inventory.setStack(i, stack);
        }
        inventory.markDirty();
        player.playerScreenHandler.sendContentUpdates();

        // 3. Build the animation report.
        List<AutoDepositResultPayload.Flight> flights = new ArrayList<>();
        for (Map.Entry<BlockPos, Map<net.minecraft.item.Item, Integer>> chestEntry : moved.entrySet()) {
            for (Map.Entry<net.minecraft.item.Item, Integer> itemEntry :
                    chestEntry.getValue().entrySet()) {
                ItemStack representative = new ItemStack(itemEntry.getKey(), Math.min(itemEntry.getValue(), 999));
                flights.add(new AutoDepositResultPayload.Flight(representative, chestEntry.getKey()));
            }
        }

        if (ServerPlayNetworking.canSend(player, AutoDepositResultPayload.ID)) {
            ServerPlayNetworking.send(player, new AutoDepositResultPayload(flights));
        }
    }

    /** A filtered container eligible to receive items during an auto-deposit. */
    private record Candidate(BlockPos pos, Inventory inv, Map<Integer, SlotWhitelist> whitelists, double distSq) {}

    /** True if any slot whitelist of the container lists the given item id. */
    private static boolean containerListsItem(Map<Integer, SlotWhitelist> whitelists, String itemId) {
        for (SlotWhitelist wl : whitelists.values()) {
            if (wl.allowedItems().contains(itemId)) return true;
        }
        return false;
    }

    /**
     * Inserts as much of {@code stack} as possible into the container, but only into slots whose
     * filter explicitly lists the item: first merging onto matching stacks, then filling empty
     * filtered slots. Mutates {@code stack}'s count in place.
     */
    private static void insertRespectingFilter(
            Inventory inv, Map<Integer, SlotWhitelist> whitelists, ItemStack stack, String itemId) {
        int size = inv.size();

        // Phase 1: top up existing identical stacks in filtered slots.
        for (int slot = 0; slot < size && !stack.isEmpty(); slot++) {
            SlotWhitelist wl = whitelists.get(slot);
            if (wl == null || !wl.allowedItems().contains(itemId)) continue;
            ItemStack dest = inv.getStack(slot);
            if (dest.isEmpty() || !ItemStack.areItemsAndComponentsEqual(dest, stack)) continue;
            int max = Math.min(inv.getMaxCount(dest), dest.getMaxCount());
            int space = max - dest.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, stack.getCount());
            dest.increment(move);
            stack.decrement(move);
        }

        // Phase 2: drop the remainder into empty filtered slots.
        for (int slot = 0; slot < size && !stack.isEmpty(); slot++) {
            SlotWhitelist wl = whitelists.get(slot);
            if (wl == null || !wl.allowedItems().contains(itemId)) continue;
            if (!inv.getStack(slot).isEmpty() || !inv.isValid(slot, stack)) continue;
            int max = Math.min(inv.getMaxCount(stack), stack.getMaxCount());
            int move = Math.min(max, stack.getCount());
            inv.setStack(slot, stack.copyWithCount(move));
            stack.decrement(move);
        }
    }

    /**
     * True if no unobstructed path exists from the player's eye to a free lateral side of the
     * container. Access must come through one of the four horizontal sides (not the top or bottom):
     * the chest is reachable if the eye has a clear line to the center of any open cell directly beside
     * it. A solid block beside the chest blocks that side; the other half of a double chest does not.
     */
    private static boolean isBlockObstructed(
            World world, Vec3d eye, BlockPos chestPos, net.minecraft.entity.Entity player) {
        List<BlockPos> parts = getAssociatedPositions(world, chestPos);
        for (BlockPos part : parts) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighbor = part.offset(dir);
                if (parts.contains(neighbor)) continue;
                // A clear line to this open side cell (ending in air, not blocked by any solid block,
                // including the chest itself) means the chest is reachable from that side.
                if (!blockedToPoint(world, eye, Vec3d.ofCenter(neighbor), player, null)) return false;
            }
        }
        return true;
    }

    /**
     * True if a solid block lies between {@code eye} and {@code target}. Block positions in
     * {@code allowed} (e.g. the target container's own blocks) are not treated as obstructions; pass
     * {@code null} when the target is an empty point such as an entity's center.
     */
    private static boolean blockedToPoint(
            World world,
            Vec3d eye,
            Vec3d target,
            net.minecraft.entity.Entity player,
            java.util.Collection<BlockPos> allowed) {
        net.minecraft.util.hit.BlockHitResult hit = world.raycast(new net.minecraft.world.RaycastContext(
                eye,
                target,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                player));
        if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) return false;
        return allowed == null || !allowed.contains(hit.getBlockPos());
    }

    /**
     * Returns the direction toward the other half of a double chest, or null when the block is not
     * part of a double chest (single chest, shulker box, barrel, or any non-chest container).
     */
    private static Direction getDoubleChestNeighborDirection(BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)) {
            return null;
        }
        ChestType type = state.get(ChestBlock.CHEST_TYPE);
        if (type == ChestType.SINGLE) {
            return null;
        }
        Direction facing = state.get(ChestBlock.FACING);
        return type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
    }

    /**
     * Returns the effective inventory for the given position, merging both halves for double chests.
     */
    private static Inventory getChestInventorySafe(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        BlockEntity be = world.getBlockEntity(pos);

        Direction neighborDir = getDoubleChestNeighborDirection(state);
        if (neighborDir != null) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            BlockEntity neighborBe = world.getBlockEntity(pos.offset(neighborDir));

            if (be instanceof Inventory && neighborBe instanceof Inventory) {
                if (type == ChestType.RIGHT) {
                    return new DoubleInventory((Inventory) be, (Inventory) neighborBe);
                } else {
                    return new DoubleInventory((Inventory) neighborBe, (Inventory) be);
                }
            }
        }
        // Single chest, Shulker Box, or Barrel — return directly.
        if (be instanceof Inventory) {
            return (Inventory) be;
        }
        return null;
    }

    /**
     * Resolves all block positions occupied by a container (2 for double chests, 1 for all others).
     */
    private static List<BlockPos> getAssociatedPositions(World world, BlockPos pos) {
        List<BlockPos> list = new ArrayList<>();
        list.add(pos);

        Direction neighborDir = getDoubleChestNeighborDirection(world.getBlockState(pos));
        if (neighborDir != null) {
            list.add(pos.offset(neighborDir));
        }
        return list;
    }
}
