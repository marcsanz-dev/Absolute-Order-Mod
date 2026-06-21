package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.*;
import io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents;
import java.util.ArrayList;
import java.util.HashMap;
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

    /**
     * Per-player inventory filters, synced from the client (where they live), used to enforce the
     * "Pick Up" rule server-side: filtered inventory slots only accept their item on pickup. Keyed by
     * player UUID, then by PlayerInventory slot index.
     */
    public static final Map<UUID, Map<Integer, SlotWhitelist>> INVENTORY_FILTERS = new ConcurrentHashMap<>();

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
        PayloadTypeRegistry.playC2S().register(InventoryFiltersPayload.ID, InventoryFiltersPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FillFromChestPayload.ID, FillFromChestPayload.CODEC);

        // Release all locks held by a player who disconnects abruptly, and drop their cached filters.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LOCKED_CHESTS.values().removeIf(uuid -> uuid.equals(handler.player.getUuid()));
            INVENTORY_FILTERS.remove(handler.player.getUuid());
        });

        // Fills the player's inventory from the container they currently have open.
        ServerPlayNetworking.registerGlobalReceiver(FillFromChestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() != null) {
                    performFillFromOpenContainer(context.player(), payload.animPos());
                }
            });
        });

        // Caches the player's inventory filters (synced from the client) for the Pick Up rule.
        ServerPlayNetworking.registerGlobalReceiver(InventoryFiltersPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() != null) {
                    INVENTORY_FILTERS.put(context.player().getUuid(), payload.filters());
                }
            });
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
                ServerPlayerEntity p = context.player();
                if (p == null) return;
                switch (payload.action()) {
                    case AutoDepositRequestPayload.ACTION_DEPOSIT_JUNK -> performDepositJunk(
                            p,
                            payload.radius(),
                            payload.throughWalls(),
                            payload.enderWhitelists(),
                            payload.entityWhitelists());
                    case AutoDepositRequestPayload.ACTION_GRAB -> performGrab(
                            p,
                            payload.radius(),
                            payload.throughWalls(),
                            payload.enderWhitelists(),
                            payload.entityWhitelists());
                    default -> performAutoDeposit(
                            p,
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
    /**
     * Gathers every filtered container in range (block entities, chest minecarts/boats, and the
     * player's ender inventory at the nearest reachable ender chest), sorted nearest first. Containers
     * blocked from every lateral side are skipped unless {@code throughWalls} is set.
     */
    private static List<Candidate> collectCandidates(
            ServerPlayerEntity player,
            int radius,
            boolean throughWalls,
            Map<Integer, SlotWhitelist> enderWhitelists,
            Map<java.util.UUID, Map<Integer, SlotWhitelist>> entityWhitelists) {
        World world = player.getEntityWorld();
        List<Candidate> candidates = new ArrayList<>();
        if (!(world instanceof ServerWorld)) return candidates;

        radius = Math.max(1, Math.min(radius, MAX_AUTO_DEPOSIT_RADIUS));
        Vec3d eye = player.getEyePos();
        BlockPos origin = player.getBlockPos();
        long radiusSq = (long) radius * radius;

        int chunkRadius = (radius >> 4) + 1;
        int centerChunkX = origin.getX() >> 4;
        int centerChunkZ = origin.getZ() >> 4;

        // Nearest reachable ender chest block, used as the target for the player's ender inventory
        // (ender filters are client-side, so they arrive in the request payload).
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

        // Mobile filtered containers: chest minecarts and chest boats are entities, not block entities,
        // and their filters live client-side only, so they arrive in entityWhitelists keyed by UUID.
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

        // Ender chest: the player's ender inventory, animated at the nearest reachable ender chest
        // block, using the client-supplied ender filter.
        if (nearestEnder != null && enderWhitelists != null && !enderWhitelists.isEmpty()) {
            candidates.add(
                    new Candidate(nearestEnder, player.getEnderChestInventory(), enderWhitelists, nearestEnderDistSq));
        }

        candidates.sort(java.util.Comparator.comparingDouble(c -> c.distSq));
        return candidates;
    }

    private static void performAutoDeposit(
            ServerPlayerEntity player,
            int radius,
            boolean throughWalls,
            Map<Integer, SlotWhitelist> enderWhitelists,
            Map<java.util.UUID, Map<Integer, SlotWhitelist>> entityWhitelists) {
        World world = player.getEntityWorld();
        if (!(world instanceof ServerWorld)) return;

        List<Candidate> candidates = collectCandidates(player, radius, throughWalls, enderWhitelists, entityWhitelists);

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
        if (ServerPlayNetworking.canSend(player, AutoDepositResultPayload.ID)) {
            ServerPlayNetworking.send(player, new AutoDepositResultPayload(buildFlights(moved)));
        }
    }

    /**
     * Deposits the items the player's inventory filters do NOT want to keep (the "drop" hotkey): items
     * no inventory filter lists, plus the excess of items kept beyond their target count. Only into
     * nearby chests whose filters list the item; whatever has no home stays in the inventory.
     */
    private static void performDepositJunk(
            ServerPlayerEntity player,
            int radius,
            boolean throughWalls,
            Map<Integer, SlotWhitelist> enderWhitelists,
            Map<java.util.UUID, Map<Integer, SlotWhitelist>> entityWhitelists) {
        if (!(player.getEntityWorld() instanceof ServerWorld)) return;

        Map<Integer, SlotWhitelist> invFilters = INVENTORY_FILTERS.get(player.getUuid());
        List<Candidate> candidates = collectCandidates(player, radius, throughWalls, enderWhitelists, entityWhitelists);
        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();

        // Per-item depositable budget = current amount minus what we keep (target; 0 target = keep all).
        Map<String, Integer> budget = new HashMap<>();
        for (Map.Entry<String, Integer> e : mainInventoryCounts(player).entrySet()) {
            int keep = inventoryKeepLimit(invFilters, e.getKey());
            int dep = keep == Integer.MAX_VALUE ? 0 : Math.max(0, e.getValue() - keep);
            if (dep > 0) budget.put(e.getKey(), dep);
        }

        Map<BlockPos, Map<net.minecraft.item.Item, Integer>> moved = new java.util.LinkedHashMap<>();
        for (int i = 0; i < net.minecraft.entity.player.PlayerInventory.MAIN_SIZE; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            net.minecraft.item.Item item = stack.getItem();
            String itemId = Registries.ITEM.getId(item).toString();
            Integer b = budget.get(itemId);
            if (b == null || b <= 0) continue;

            for (Candidate candidate : candidates) {
                if (stack.isEmpty() || b <= 0) break;
                if (!containerListsItem(candidate.whitelists, itemId)) continue;

                ItemStack portion = stack.copyWithCount(Math.min(b, stack.getCount()));
                int before = portion.getCount();
                insertRespectingFilter(candidate.inv, candidate.whitelists, portion, itemId);
                int delta = before - portion.getCount();
                if (delta > 0) {
                    stack.decrement(delta);
                    b -= delta;
                    candidate.inv.markDirty();
                    moved.computeIfAbsent(candidate.pos, k -> new java.util.LinkedHashMap<>())
                            .merge(item, delta, Integer::sum);
                }
            }
            budget.put(itemId, b);
            inventory.setStack(i, stack);
        }
        inventory.markDirty();
        player.playerScreenHandler.sendContentUpdates();

        if (ServerPlayNetworking.canSend(player, AutoDepositResultPayload.ID)) {
            ServerPlayNetworking.send(player, new AutoDepositResultPayload(buildFlights(moved)));
        }
    }

    /**
     * Pulls items the player's inventory filters want (the "grab" hotkey) from nearby filtered chests
     * into the inventory, only up to each filter's target count (0 target = grab as much as fits).
     * Items flow only out of chest slots whose filter lists the item.
     */
    private static void performGrab(
            ServerPlayerEntity player,
            int radius,
            boolean throughWalls,
            Map<Integer, SlotWhitelist> enderWhitelists,
            Map<java.util.UUID, Map<Integer, SlotWhitelist>> entityWhitelists) {
        if (!(player.getEntityWorld() instanceof ServerWorld)) return;

        Map<Integer, SlotWhitelist> invFilters = INVENTORY_FILTERS.get(player.getUuid());
        List<Candidate> candidates = collectCandidates(player, radius, throughWalls, enderWhitelists, entityWhitelists);
        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();

        Map<String, Integer> startHave = mainInventoryCounts(player);
        Map<String, Integer> grabbed = new HashMap<>();
        Map<String, Integer> keepCache = new HashMap<>();
        Map<BlockPos, Map<net.minecraft.item.Item, Integer>> moved = new java.util.LinkedHashMap<>();

        for (Candidate candidate : candidates) {
            int size = candidate.inv.size();
            for (int slot = 0; slot < size; slot++) {
                ItemStack stack = candidate.inv.getStack(slot);
                if (stack.isEmpty()) continue;
                net.minecraft.item.Item item = stack.getItem();
                String itemId = Registries.ITEM.getId(item).toString();

                SlotWhitelist wl = candidate.whitelists.get(slot);
                if (wl == null || !wl.allowedItems().contains(itemId)) continue;

                int keep = keepCache.computeIfAbsent(itemId, k -> inventoryKeepLimit(invFilters, k));
                if (keep <= 0) continue;
                long need = (long) keep - startHave.getOrDefault(itemId, 0) - grabbed.getOrDefault(itemId, 0);
                if (need <= 0) continue;

                int take = (int) Math.min(need, stack.getCount());
                if (take <= 0) continue;

                ItemStack portion = stack.copyWithCount(take);
                inventory.insertStack(portion);
                int delta = take - portion.getCount();
                if (delta > 0) {
                    stack.decrement(delta);
                    candidate.inv.setStack(slot, stack);
                    candidate.inv.markDirty();
                    grabbed.merge(itemId, delta, Integer::sum);
                    moved.computeIfAbsent(candidate.pos, k -> new java.util.LinkedHashMap<>())
                            .merge(item, delta, Integer::sum);
                }
            }
        }
        inventory.markDirty();
        player.playerScreenHandler.sendContentUpdates();

        if (ServerPlayNetworking.canSend(player, AutoDepositResultPayload.ID)) {
            ServerPlayNetworking.send(player, new AutoDepositResultPayload(buildFlights(moved), true));
        }
    }

    /**
     * Fills the player's inventory from the container they currently have open (the editor's "fill
     * inventory" button): pulls items the player's inventory filters want, up to each target count.
     * Pulls from every container slot, not only the chest's own filtered slots, since it is a
     * deliberate per-chest action. {@code animPos} is only the origin of the fly-back animation.
     */
    private static void performFillFromOpenContainer(ServerPlayerEntity player, BlockPos animPos) {
        if (!(player.getEntityWorld() instanceof ServerWorld)) return;

        // The open container's inventory: the first screen-handler slot not backed by the player.
        Inventory container = null;
        for (net.minecraft.screen.slot.Slot s : player.currentScreenHandler.slots) {
            if (!(s.inventory instanceof net.minecraft.entity.player.PlayerInventory)) {
                container = s.inventory;
                break;
            }
        }
        if (container == null) return;

        Map<Integer, SlotWhitelist> invFilters = INVENTORY_FILTERS.get(player.getUuid());
        net.minecraft.entity.player.PlayerInventory inventory = player.getInventory();
        Map<String, Integer> startHave = mainInventoryCounts(player);
        Map<String, Integer> grabbed = new HashMap<>();
        Map<String, Integer> keepCache = new HashMap<>();
        Map<net.minecraft.item.Item, Integer> movedItems = new java.util.LinkedHashMap<>();

        int size = container.size();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = container.getStack(slot);
            if (stack.isEmpty()) continue;
            net.minecraft.item.Item item = stack.getItem();
            String itemId = Registries.ITEM.getId(item).toString();

            int keep = keepCache.computeIfAbsent(itemId, k -> inventoryKeepLimit(invFilters, k));
            if (keep <= 0) continue;
            long need = (long) keep - startHave.getOrDefault(itemId, 0) - grabbed.getOrDefault(itemId, 0);
            if (need <= 0) continue;

            int take = (int) Math.min(need, stack.getCount());
            if (take <= 0) continue;

            ItemStack portion = stack.copyWithCount(take);
            inventory.insertStack(portion);
            int delta = take - portion.getCount();
            if (delta > 0) {
                stack.decrement(delta);
                container.setStack(slot, stack);
                grabbed.merge(itemId, delta, Integer::sum);
                movedItems.merge(item, delta, Integer::sum);
            }
        }
        container.markDirty();
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();

        Map<BlockPos, Map<net.minecraft.item.Item, Integer>> moved = new java.util.LinkedHashMap<>();
        if (!movedItems.isEmpty() && animPos != null) moved.put(animPos, movedItems);
        if (ServerPlayNetworking.canSend(player, AutoDepositResultPayload.ID)) {
            ServerPlayNetworking.send(player, new AutoDepositResultPayload(buildFlights(moved), true));
        }
    }

    /** Builds one animation flight per (item, container) pair from a moved-amount report. */
    private static List<AutoDepositResultPayload.Flight> buildFlights(
            Map<BlockPos, Map<net.minecraft.item.Item, Integer>> moved) {
        List<AutoDepositResultPayload.Flight> flights = new ArrayList<>();
        for (Map.Entry<BlockPos, Map<net.minecraft.item.Item, Integer>> chestEntry : moved.entrySet()) {
            for (Map.Entry<net.minecraft.item.Item, Integer> itemEntry :
                    chestEntry.getValue().entrySet()) {
                ItemStack representative = new ItemStack(itemEntry.getKey(), Math.min(itemEntry.getValue(), 999));
                flights.add(new AutoDepositResultPayload.Flight(representative, chestEntry.getKey()));
            }
        }
        return flights;
    }

    /** Total count of each item id currently in the player's main inventory. */
    private static Map<String, Integer> mainInventoryCounts(ServerPlayerEntity player) {
        Map<String, Integer> counts = new HashMap<>();
        net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < net.minecraft.entity.player.PlayerInventory.MAIN_SIZE; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            counts.merge(Registries.ITEM.getId(s.getItem()).toString(), s.getCount(), Integer::sum);
        }
        return counts;
    }

    /**
     * How many of an item the player's inventory filters want to keep: 0 if no filter lists it (junk),
     * {@link Integer#MAX_VALUE} if any listing filter has no target (keep all), otherwise the sum of
     * the target counts of the distinct filter groups listing it.
     */
    private static int inventoryKeepLimit(Map<Integer, SlotWhitelist> invFilters, String itemId) {
        if (invFilters == null) return 0;
        int sum = 0;
        boolean listed = false;
        java.util.Set<java.util.UUID> seenGroups = new java.util.HashSet<>();
        for (SlotWhitelist wl : invFilters.values()) {
            if (!wl.allowedItems().contains(itemId)) continue;
            if (!seenGroups.add(wl.groupId())) continue;
            listed = true;
            if (wl.targetCount() <= 0) return Integer.MAX_VALUE;
            sum += wl.targetCount();
        }
        return listed ? sum : 0;
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
