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
        PayloadTypeRegistry.playC2S().register(SortOpenFiltersPayload.ID, SortOpenFiltersPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EntityWhitelistPayload.ID, EntityWhitelistPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EntityWhitelistPayload.ID, EntityWhitelistPayload.CODEC);

        // Release all locks held by a player who disconnects abruptly, and drop their cached filters.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LOCKED_CHESTS.values().removeIf(uuid -> uuid.equals(handler.player.getUuid()));
            INVENTORY_FILTERS.remove(handler.player.getUuid());
        });

        // Fills the player's inventory from the container they currently have open.
        ServerPlayNetworking.registerGlobalReceiver(FillFromChestPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() != null) {
                    performFillFromOpenContainer(
                            context.player(), payload.animPos(), payload.includeEmpty(), payload.lockHotbar());
                }
            });
        });

        // After a "push into the open chest" deposit, re-sort that container's filtered groups by priority
        // order (the deposit itself is done client-side via slot clicks; this makes higher-priority arrivals
        // move ahead of lower-priority items already stored).
        ServerPlayNetworking.registerGlobalReceiver(SortOpenFiltersPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                net.minecraft.server.network.ServerPlayerEntity p = context.player();
                if (p == null || p.currentScreenHandler == null) return;
                for (net.minecraft.screen.slot.Slot s : p.currentScreenHandler.slots) {
                    if (s.inventory instanceof net.minecraft.entity.player.PlayerInventory) continue;
                    if (s.inventory instanceof IWhitelistProvider provider) {
                        reorderFilteredGroups(s.inventory, provider.getWhitelists());
                    }
                    break; // the container is a single inventory shared by all its slots
                }
                p.currentScreenHandler.sendContentUpdates();
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

        // Persists incoming whitelist updates for ENTITY containers (chest/hopper minecarts). Applying it
        // to the live server-side entity is what makes the Hopper Insert rule actually enforce on minecarts
        // (see HopperBlockEntityMixin), and the entity's own NBT persistence keeps it across reload.
        ServerPlayNetworking.registerGlobalReceiver(EntityWhitelistPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player() == null) return;
                World world = context.player().getEntityWorld();
                if (!(world instanceof ServerWorld serverWorld)) return;

                net.minecraft.entity.Entity entity = serverWorld.getEntity(payload.entityUuid());
                if (entity instanceof IWhitelistProvider provider) {
                    provider.setWhitelists(payload.whitelists());
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
                        }
                        if (ServerPlayNetworking.canSend(context.player(), EditorLockResponsePayload.ID)) {
                            ServerPlayNetworking.send(
                                    context.player(), new EditorLockResponsePayload(targetPos, canLock));
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
                performAutoDeposit(
                        p,
                        payload.radius(),
                        payload.throughWalls(),
                        payload.enderWhitelists(),
                        payload.entityWhitelists());
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
        // Containers that actually received items, so only those get their groups re-sorted afterwards
        // (nearby chests nothing was deposited into are left exactly as the player arranged them).
        java.util.Set<Candidate> touched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

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
                    touched.add(candidate);
                    moved.computeIfAbsent(candidate.pos, k -> new java.util.LinkedHashMap<>())
                            .merge(item, delta, Integer::sum);
                }
            }
            inventory.setStack(i, stack);
        }

        // Auto-organize each container that received items: sort every filtered group by priority order.
        for (Candidate candidate : touched) {
            reorderFilteredGroups(candidate.inv, candidate.whitelists);
        }

        inventory.markDirty();
        player.playerScreenHandler.sendContentUpdates();

        // 3. Build the animation report.
        if (ServerPlayNetworking.canSend(player, AutoDepositResultPayload.ID)) {
            ServerPlayNetworking.send(player, new AutoDepositResultPayload(buildFlights(moved)));
        }
    }

    /**
     * Fills the player's inventory from the container they currently have open (the editor's "fill
     * inventory" button): pulls every item any inventory filter lists, or — with Shift ({@code includeEmpty})
     * — everything into whatever free space is left. Pulls from every container slot, since it is a
     * deliberate per-chest action. {@code animPos} is only the origin of the fly-back animation.
     */
    private static void performFillFromOpenContainer(
            ServerPlayerEntity player, BlockPos animPos, boolean includeEmpty, boolean lockHotbar) {
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
        Map<net.minecraft.item.Item, Integer> movedItems = new java.util.LinkedHashMap<>();

        int size = container.size();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = container.getStack(slot);
            if (stack.isEmpty()) continue;
            net.minecraft.item.Item item = stack.getItem();
            String itemId = Registries.ITEM.getId(item).toString();

            // Pull items the inventory filters list; with Shift, pull everything (into free space).
            if (!includeEmpty && !inventoryListsItem(invFilters, itemId)) continue;

            int take = stack.getCount();
            ItemStack portion = stack.copyWithCount(take);
            inventory.insertStack(portion);
            int delta = take - portion.getCount();
            if (delta > 0) {
                stack.decrement(delta);
                container.setStack(slot, stack);
                movedItems.merge(item, delta, Integer::sum);
            }
        }

        // Auto-organize the inventory's filtered groups after a pull, so items land in priority order and
        // higher-priority arrivals sit ahead of lower-priority ones already stored. Restricted to the MAIN
        // inventory (0..MAIN_SIZE): armor and offhand slots are type-specific, so re-laying stacks there by
        // priority (which bypasses slot validation) could drop the wrong piece into an equipment slot.
        if (!movedItems.isEmpty() && invFilters != null) {
            // Hotbar is slots 0-8; keep it out of the sort when the option is on so a held item never moves.
            // The flag is forwarded from the client (GlobalChestConfig is client-only — never read it here).
            int firstSortable = lockHotbar ? 9 : 0;
            Map<Integer, SlotWhitelist> mainFilters = new java.util.HashMap<>();
            for (Map.Entry<Integer, SlotWhitelist> e : invFilters.entrySet()) {
                if (e.getKey() >= firstSortable && e.getKey() < net.minecraft.entity.player.PlayerInventory.MAIN_SIZE) {
                    mainFilters.put(e.getKey(), e.getValue());
                }
            }
            reorderFilteredGroups(inventory, mainFilters);
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

    /** True when any of the player's inventory filters lists the item (so a Fill should pull it in). */
    private static boolean inventoryListsItem(Map<Integer, SlotWhitelist> invFilters, String itemId) {
        if (invFilters == null) return false;
        for (SlotWhitelist wl : invFilters.values()) {
            if (wl.allowedItems().contains(itemId)) return true;
        }
        return false;
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

        // Phase 2: drop the remainder into empty filtered slots, in the filter's priority order — the
        // item ranked first in the list heads for the group's first slot, and so on — so a deposit lays
        // items out in the same order the player arranged by dragging.
        List<Integer> emptySlots = new java.util.ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            SlotWhitelist wl = whitelists.get(slot);
            if (wl == null || !wl.allowedItems().contains(itemId)) continue;
            if (!inv.getStack(slot).isEmpty() || !inv.isValid(slot, stack)) continue;
            emptySlots.add(slot);
        }
        emptySlots.sort(java.util.Comparator.comparingInt(
                        (Integer slot) -> io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(
                                whitelists, slot, itemId))
                .thenComparingInt(slot -> slot));
        for (int slot : emptySlots) {
            if (stack.isEmpty()) break;
            int max = Math.min(inv.getMaxCount(stack), stack.getMaxCount());
            int move = Math.min(max, stack.getCount());
            inv.setStack(slot, stack.copyWithCount(move));
            stack.decrement(move);
        }
    }

    /**
     * Re-lays every filtered group of {@code inv} so its stacks sit in the filter's priority order, packed
     * from the group's first slot. This is the push/pull "auto-organize" step: after a deposit or fill, a
     * higher-priority item that arrived into a later slot is moved ahead of the lower-priority items already
     * stored, and no gap is left reserved for an item that is not present. Server-authoritative (direct
     * {@link Inventory#setStack}) so nothing can desync or leave ghosts. Items the filter does not list keep
     * their relative place at the end of the group, so a manually stored foreign item is preserved.
     */
    private static void reorderFilteredGroups(Inventory inv, Map<Integer, SlotWhitelist> whitelists) {
        if (inv == null || whitelists == null || whitelists.isEmpty()) return;

        // Collect each group's slot indices in ascending (normal Minecraft) slot order.
        Map<java.util.UUID, java.util.List<Integer>> groups = new java.util.LinkedHashMap<>();
        java.util.List<Integer> keys = new ArrayList<>(whitelists.keySet());
        java.util.Collections.sort(keys);
        for (int slot : keys) {
            if (slot < 0 || slot >= inv.size()) continue;
            SlotWhitelist wl = whitelists.get(slot);
            if (wl == null || wl.groupId() == null) continue;
            groups.computeIfAbsent(wl.groupId(), g -> new ArrayList<>()).add(slot);
        }

        for (java.util.List<Integer> slots : groups.values()) {
            if (slots.size() < 2) continue;
            java.util.List<String> order = whitelists.get(slots.get(0)).allowedItems();

            // Snapshot the group's non-empty stacks (references), then sort by the filter's priority order.
            // The sort is stable, so equal-ranked and unlisted items keep their relative order.
            java.util.List<ItemStack> stacks = new ArrayList<>();
            for (int slot : slots) {
                ItemStack st = inv.getStack(slot);
                if (!st.isEmpty()) stacks.add(st);
            }
            if (stacks.isEmpty()) continue;
            stacks.sort(java.util.Comparator.<ItemStack>comparingInt(st -> {
                        int rank = order.indexOf(Registries.ITEM.getId(st.getItem()).toString());
                        return rank < 0 ? Integer.MAX_VALUE : rank;
                    })
                    // Tie-break equal-ranked stacks (the same item overflowing several of the group's slots)
                    // by count, fuller first — so a 65-item deposit ends as [64, 1] and not [1, 64].
                    .thenComparing(java.util.Comparator.comparingInt(ItemStack::getCount).reversed()));

            // Re-lay packed from the first slot; any trailing slots become empty. Every stack came from this
            // same group, so nothing can be lost (stacks.size() <= slots.size()).
            for (int i = 0; i < slots.size(); i++) {
                inv.setStack(slots.get(i), i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
            }
        }
        inv.markDirty();
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
        return be instanceof Inventory inv ? inv : null;
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
