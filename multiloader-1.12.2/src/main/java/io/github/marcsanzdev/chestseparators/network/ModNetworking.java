package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.FilterPriority;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.ILockableContainer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * Loader-agnostic server-side networking, ported from the old Fabric {@code ChestSeparatorsMain}. On
 * 1.12.2 the Architectury {@code NetworkManager} is replaced by {@link CsNetwork} (a SimpleNetworkWrapper
 * hub); the enforcement/auto logic (auto-deposit, fill, whitelist persistence, editor locks) is otherwise
 * the same as the modern ports. Receivers registered here already run on the server main thread (they are
 * scheduled by {@link CsServerHandler}), so the modern {@code context.queue(...)} wrapper is dropped.
 */
public final class ModNetworking {

    private ModNetworking() {}

    /** 1.12.2 lacks {@code ItemStack.copyWithCount(int)}; replicate it. */
    private static ItemStack copyWithCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    private static final Random EXPEL_RANDOM = new Random();

    /** Hard cap on the auto-deposit radius, regardless of what the client requests. */
    private static final int MAX_AUTO_DEPOSIT_RADIUS = 32;

    /**
     * Forge event subscriber replacing Architectury's {@code PlayerEvent.PLAYER_QUIT}: releases all locks
     * held by a player who disconnects abruptly and drops their cached inventory filters.
     */
    public static final class ServerEventHandler {
        @SubscribeEvent
        public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.player == null) return;
            UUID uuid = event.player.getUniqueID();
            ChestSeparatorsState.LOCKED_CHESTS.values().removeIf(u -> u.equals(uuid));
            ChestSeparatorsState.INVENTORY_FILTERS.remove(uuid);
        }
    }

    /**
     * Registers every C2S receiver plus the player-quit cleanup handler. Called once from the mod's
     * CommonProxy. Client-side S2C receivers are registered separately in the client module.
     */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ServerEventHandler());

        // Fills the player's inventory from the container they currently have open.
        CsNetwork.registerServer(FillFromChestPayload.ID, (buf, player) -> {
            FillFromChestPayload payload = FillFromChestPayload.read(buf);
            performFillFromOpenContainer(player, payload.animPos(), payload.includeEmpty(), payload.lockHotbar());
        });

        // After a "push into the open chest" deposit, re-sort that container's filtered groups by priority
        // order (the deposit itself is done client-side via slot clicks; this makes higher-priority arrivals
        // move ahead of lower-priority items already stored).
        CsNetwork.registerServer(SortOpenFiltersPayload.ID, (buf, player) -> {
            if (player.openContainer == null) return;
            for (Slot s : player.openContainer.inventorySlots) {
                if (s.inventory instanceof InventoryPlayer) continue;
                if (s.inventory instanceof IWhitelistProvider) {
                    IWhitelistProvider provider = (IWhitelistProvider) s.inventory;
                    reorderFilteredGroups(s.inventory, provider.getWhitelists());
                }
                break; // the container is a single inventory shared by all its slots
            }
            player.openContainer.detectAndSendChanges();
        });

        // Caches the player's inventory filters (synced from the client) for the Pick Up rule.
        CsNetwork.registerServer(InventoryFiltersPayload.ID, (buf, player) -> {
            InventoryFiltersPayload payload = InventoryFiltersPayload.read(buf);
            ChestSeparatorsState.INVENTORY_FILTERS.put(player.getUniqueID(), payload.filters());
        });

        // Persists incoming whitelist updates for ENTITY containers (chest/hopper minecarts). Applying it
        // to the live server-side entity is what makes the Hopper Insert rule actually enforce on minecarts,
        // and the entity's own NBT persistence keeps it across reload.
        CsNetwork.registerServer(EntityWhitelistPayload.ID, (buf, player) -> {
            EntityWhitelistPayload payload = EntityWhitelistPayload.read(buf);
            World world = player.world;
            if (!(world instanceof WorldServer)) return;
            WorldServer serverWorld = (WorldServer) world;
            Entity entity = serverWorld.getEntityFromUuid(payload.entityUuid());
            if (entity instanceof IWhitelistProvider) {
                IWhitelistProvider provider = (IWhitelistProvider) entity;
                provider.setWhitelists(payload.whitelists());
            }
        });

        // Handles editor lock requests, with support for double chests (two associated positions).
        CsNetwork.registerServer(EditorLockRequestPayload.ID, (buf, player) -> {
            EditorLockRequestPayload payload = EditorLockRequestPayload.read(buf);
            UUID playerUuid = player.getUniqueID();
            BlockPos targetPos = payload.pos();
            World world = player.world;

            // Resolve all block positions occupied by this container (1 for single, 2 for double).
            List<BlockPos> associatedPositions = getAssociatedPositions(world, targetPos);

            if (payload.isLocking()) {
                boolean canLock = true;

                // Deny the lock if any part of the container is held by a different player.
                for (BlockPos p : associatedPositions) {
                    if (ChestSeparatorsState.LOCKED_CHESTS.containsKey(p)
                            && !ChestSeparatorsState.LOCKED_CHESTS.get(p).equals(playerUuid)) {
                        canLock = false;
                        break;
                    }
                }

                if (canLock) {
                    // Acquire all positions atomically so no partial lock can slip through.
                    for (BlockPos p : associatedPositions) {
                        ChestSeparatorsState.LOCKED_CHESTS.put(p, playerUuid);
                    }
                }
                if (ModNet.playerCanReceive(player, EditorLockResponsePayload.ID)) {
                    ModNet.sendToPlayer(player, new EditorLockResponsePayload(targetPos, canLock));
                }
            } else {
                // Release all associated positions when the player exits the editor.
                for (BlockPos p : associatedPositions) {
                    if (ChestSeparatorsState.LOCKED_CHESTS.containsKey(p)
                            && ChestSeparatorsState.LOCKED_CHESTS.get(p).equals(playerUuid)) {
                        ChestSeparatorsState.LOCKED_CHESTS.remove(p);
                    }
                }
            }
        });

        // Handles whitelist fetch requests sent by the client when a container is first opened.
        CsNetwork.registerServer(WhitelistRequestPayload.ID, (buf, player) -> {
            WhitelistRequestPayload payload = WhitelistRequestPayload.read(buf);
            World world = player.world;
            IInventory targetInventory = getChestInventorySafe(world, payload.pos());

            if (targetInventory instanceof IWhitelistProvider) {
                IWhitelistProvider provider = (IWhitelistProvider) targetInventory;
                if (ModNet.playerCanReceive(player, WhitelistS2CPayload.ID)) {
                    ModNet.sendToPlayer(player, new WhitelistS2CPayload(payload.pos(), provider.getWhitelists()));
                }
            }
        });

        // Persists incoming whitelist updates from the client and broadcasts them to co-viewers.
        CsNetwork.registerServer(WhitelistPayload.ID, (buf, player) -> {
            WhitelistPayload payload = WhitelistPayload.read(buf);
            World world = player.world;
            IBlockState state = world.getBlockState(payload.pos());
            boolean isEnderChest = state.getBlock() == Blocks.ENDER_CHEST;

            // Ender Chest filters are local-only (hoppers cannot access them); nothing to do here.
            if (isEnderChest) return;

            IInventory targetInventory = getChestInventorySafe(world, payload.pos());

            if (targetInventory instanceof IWhitelistProvider) {
                IWhitelistProvider provider = (IWhitelistProvider) targetInventory;
                provider.setWhitelists(payload.whitelists());
                targetInventory.markDirty();

                // Broadcast the updated whitelist to all other players tracking this container's chunk.
                if (world instanceof WorldServer) {
                    WorldServer serverWorld = (WorldServer) world;
                    ChunkPos chunkPos = new ChunkPos(payload.pos());
                    PlayerChunkMapEntry entry = serverWorld.getPlayerChunkMap().getEntry(chunkPos.x, chunkPos.z);
                    if (entry != null) {
                        for (EntityPlayer viewer : serverWorld.playerEntities) {
                            if (viewer == player || !(viewer instanceof EntityPlayerMP)) continue;
                            EntityPlayerMP trackingPlayer = (EntityPlayerMP) viewer;
                            if (entry.containsPlayer(trackingPlayer)
                                    && ModNet.playerCanReceive(trackingPlayer, WhitelistS2CPayload.ID)) {
                                ModNet.sendToPlayer(
                                        trackingPlayer,
                                        new WhitelistS2CPayload(payload.pos(), payload.whitelists()));
                            }
                        }
                    }
                }

                // Eject items that violate the new whitelist when all three rules are enabled.
                if (GlobalChestConfig.instance.expelInvalidItems) {
                    for (int i = 0; i < targetInventory.getSizeInventory(); i++) {
                        ItemStack stack = targetInventory.getStackInSlot(i);
                        if (!stack.isEmpty() && payload.whitelists().containsKey(i)) {
                            SlotWhitelist wl = payload.whitelists().get(i);

                            if (wl.allowManual() && wl.allowShift() && wl.allowHopper()) {
                                if (!io.github.marcsanzdev.chestseparators.util.ItemKey.matches(
                                        wl.allowedItems(), stack)) {
                                    ItemStack extracted = targetInventory.removeStackFromSlot(i);

                                    double dropX = payload.pos().getX() + 0.5D;
                                    double dropY = payload.pos().getY() + 0.5D;
                                    double dropZ = payload.pos().getZ() + 0.5D;

                                    EntityItem itemEntity = new EntityItem(world, dropX, dropY, dropZ, extracted);

                                    double dirX = player.posX - dropX;
                                    double dirY = (player.posY + player.getEyeHeight()) - dropY;
                                    double dirZ = player.posZ - dropZ;

                                    double distance = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
                                    dirX /= distance;
                                    dirY /= distance;
                                    dirZ /= distance;

                                    double force = 0.3D;
                                    itemEntity.motionX = dirX * force + EXPEL_RANDOM.nextGaussian() * 0.05D;
                                    itemEntity.motionY = dirY * force + EXPEL_RANDOM.nextGaussian() * 0.05D + 0.1D;
                                    itemEntity.motionZ = dirZ * force + EXPEL_RANDOM.nextGaussian() * 0.05D;

                                    itemEntity.setDefaultPickupDelay();
                                    world.spawnEntity(itemEntity);
                                }
                            }
                        }
                    }
                }
            }
        });

        // Handles the radius auto-deposit trigger: sort the player's inventory into nearby filtered
        // containers and report back what moved so the client can animate it.
        CsNetwork.registerServer(AutoDepositRequestPayload.ID, (buf, player) -> {
            AutoDepositRequestPayload payload = AutoDepositRequestPayload.read(buf);
            performAutoDeposit(
                    player,
                    payload.radius(),
                    payload.throughWalls(),
                    payload.enderWhitelists(),
                    payload.entityWhitelists());
        });

        // S2C receivers live in the client module; the server guards every send with ModNet.playerCanReceive.
    }

    // ------------------------------------------------------------------------------------------------
    // Server-side logic (ported from ChestSeparatorsMain, Mojmap -> 1.12.2 MCP).
    // ------------------------------------------------------------------------------------------------

    /** A filtered container eligible to receive items during an auto-deposit. */
    private static final class Candidate {

        private final BlockPos pos;
        private final IInventory inv;
        private final Map<Integer, SlotWhitelist> whitelists;
        private final double distSq;

        private Candidate(BlockPos pos, IInventory inv, Map<Integer, SlotWhitelist> whitelists, double distSq) {
            this.pos = pos;
            this.inv = inv;
            this.whitelists = whitelists;
            this.distSq = distSq;
        }

        public BlockPos pos() {
            return pos;
        }

        public IInventory inv() {
            return inv;
        }

        public Map<Integer, SlotWhitelist> whitelists() {
            return whitelists;
        }

        public double distSq() {
            return distSq;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Candidate other = (Candidate) o;
            return Double.compare(distSq, other.distSq) == 0
                    && java.util.Objects.equals(pos, other.pos)
                    && java.util.Objects.equals(inv, other.inv)
                    && java.util.Objects.equals(whitelists, other.whitelists);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(pos, inv, whitelists, distSq);
        }

        @Override
        public String toString() {
            return "Candidate[pos=" + pos + ", inv=" + inv + ", whitelists=" + whitelists + ", distSq=" + distSq + "]";
        }
    }

    /**
     * Gathers every filtered container in range (block entities, chest minecarts, and the player's ender
     * inventory at the nearest reachable ender chest), sorted nearest first. Containers blocked from every
     * lateral side are skipped unless {@code throughWalls} is set.
     */
    private static List<Candidate> collectCandidates(
            EntityPlayerMP player,
            int radius,
            boolean throughWalls,
            Map<Integer, SlotWhitelist> enderWhitelists,
            Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists) {
        World world = player.world;
        List<Candidate> candidates = new ArrayList<>();
        if (!(world instanceof WorldServer)) return candidates;

        radius = Math.max(1, Math.min(radius, MAX_AUTO_DEPOSIT_RADIUS));
        Vec3d eye = player.getPositionEyes(1.0F);
        BlockPos origin = player.getPosition();
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
                Chunk chunk = world.getChunkFromChunkCoords(centerChunkX + dx, centerChunkZ + dz);
                for (Map.Entry<BlockPos, TileEntity> entry :
                        chunk.getTileEntityMap().entrySet()) {
                    BlockPos pos = entry.getKey();
                    TileEntity be = entry.getValue();

                    double cdx = pos.getX() + 0.5D - eye.x;
                    double cdy = pos.getY() + 0.5D - eye.y;
                    double cdz = pos.getZ() + 0.5D - eye.z;
                    double distSq = cdx * cdx + cdy * cdy + cdz * cdz;
                    if (distSq > radiusSq) continue;

                    if (be instanceof TileEntityEnderChest) {
                        if (distSq < nearestEnderDistSq
                                && (throughWalls || !isBlockObstructed(world, eye, pos, player))) {
                            nearestEnder = pos;
                            nearestEnderDistSq = distSq;
                        }
                        continue;
                    }

                    if (!(be instanceof IInventory) || !(be instanceof IWhitelistProvider)) continue;
                    IInventory inv = (IInventory) be;
                    IWhitelistProvider provider = (IWhitelistProvider) be;
                    Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();
                    if (whitelists == null || whitelists.isEmpty()) continue;

                    if (!throughWalls && isBlockObstructed(world, eye, pos, player)) continue;

                    candidates.add(new Candidate(pos, inv, whitelists, distSq));
                }
            }
        }

        // Mobile filtered containers: chest minecarts are entities, not block entities, and their filters
        // live client-side only, so they arrive in entityWhitelists keyed by UUID.
        AxisAlignedBB box = player.getEntityBoundingBox().grow(radius);
        for (Entity entity : world.getEntitiesInAABBexcluding(player, box, e -> e instanceof IInventory)) {
            Map<Integer, SlotWhitelist> whitelists = entityWhitelists.get(entity.getUniqueID());
            if (whitelists == null || whitelists.isEmpty()) continue;

            Vec3d center = centerOf(entity.getEntityBoundingBox());
            double distSq = center.squareDistanceTo(eye);
            if (distSq > radiusSq) continue;
            if (!throughWalls && blockedToPoint(world, eye, center, player, null)) continue;

            candidates.add(new Candidate(entity.getPosition(), (IInventory) entity, whitelists, distSq));
        }

        // Ender chest: the player's ender inventory, animated at the nearest reachable ender chest
        // block, using the client-supplied ender filter.
        if (nearestEnder != null && enderWhitelists != null && !enderWhitelists.isEmpty()) {
            candidates.add(
                    new Candidate(nearestEnder, player.getInventoryEnderChest(), enderWhitelists, nearestEnderDistSq));
        }

        candidates.sort(java.util.Comparator.comparingDouble(c -> c.distSq));
        return candidates;
    }

    /**
     * Sorts every stack in the player's main inventory into nearby containers whose filters list the
     * item, nearest containers first. Sends an {@link AutoDepositResultPayload} describing every transfer.
     */
    private static void performAutoDeposit(
            EntityPlayerMP player,
            int radius,
            boolean throughWalls,
            Map<Integer, SlotWhitelist> enderWhitelists,
            Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists) {
        World world = player.world;
        if (!(world instanceof WorldServer)) return;

        List<Candidate> candidates = collectCandidates(player, radius, throughWalls, enderWhitelists, entityWhitelists);

        // 2. Deposit each stack into matching candidate slots, recording moved amounts per container.
        InventoryPlayer inventory = player.inventory;
        // Preserve discovery order so the animation roughly mirrors inventory layout.
        Map<BlockPos, Map<Item, Integer>> moved = new java.util.LinkedHashMap<>();
        // Containers that actually received items, so only those get their groups re-sorted afterwards.
        java.util.Set<Candidate> touched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();

            for (Candidate candidate : candidates) {
                if (stack.isEmpty()) break;
                if (!containerListsItem(candidate.whitelists, stack)) continue;

                int before = stack.getCount();
                insertRespectingFilter(candidate.inv, candidate.whitelists, stack);
                int delta = before - stack.getCount();
                if (delta > 0) {
                    candidate.inv.markDirty();
                    touched.add(candidate);
                    moved.computeIfAbsent(candidate.pos, k -> new java.util.LinkedHashMap<>())
                            .merge(item, delta, Integer::sum);
                }
            }
            inventory.setInventorySlotContents(i, stack);
        }

        // Auto-organize each container that received items: sort every filtered group by priority order.
        for (Candidate candidate : touched) {
            reorderFilteredGroups(candidate.inv, candidate.whitelists);
        }

        inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();

        // 3. Build the animation report.
        if (ModNet.playerCanReceive(player, AutoDepositResultPayload.ID)) {
            ModNet.sendToPlayer(player, new AutoDepositResultPayload(buildFlights(moved)));
        }
    }

    /**
     * Fills the player's inventory from the container they currently have open (the editor's "fill
     * inventory" button): pulls every item any inventory filter lists, or — with Shift ({@code includeEmpty})
     * — everything into whatever free space is left. {@code animPos} is only the origin of the animation.
     */
    private static void performFillFromOpenContainer(
            EntityPlayerMP player, BlockPos animPos, boolean includeEmpty, boolean lockHotbar) {
        if (!(player.world instanceof WorldServer)) return;

        // The open container's inventory: the first menu slot not backed by the player.
        IInventory container = null;
        for (Slot s : player.openContainer.inventorySlots) {
            if (!(s.inventory instanceof InventoryPlayer)) {
                container = s.inventory;
                break;
            }
        }
        if (container == null) return;

        Map<Integer, SlotWhitelist> invFilters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUniqueID());
        InventoryPlayer inventory = player.inventory;
        Map<Item, Integer> movedItems = new java.util.LinkedHashMap<>();

        int size = container.getSizeInventory();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = container.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();

            // Pull items the inventory filters list; with Shift, pull everything (into free space).
            if (!includeEmpty && !inventoryListsItem(invFilters, stack)) continue;

            int take = stack.getCount();
            ItemStack portion = copyWithCount(stack, take);
            if (includeEmpty) {
                // Shift: pull everything into whatever free space is left (vanilla spill).
                inventory.addItemStackToInventory(portion);
            } else {
                // No Shift: only top up / fill the slots whose filter lists this item — never overflow into
                // unfiltered slots. Any surplus the filtered slots can't hold stays in the container.
                insertRespectingFilter(inventory, invFilters, portion);
            }
            int delta = take - portion.getCount();
            if (delta > 0) {
                stack.shrink(delta);
                container.setInventorySlotContents(slot, stack);
                movedItems.merge(item, delta, Integer::sum);
            }
        }

        // Auto-organize the inventory's filtered groups after a pull, so items land in priority order.
        // Restricted to the MAIN inventory (0..36): armor and offhand slots are type-specific.
        if (!movedItems.isEmpty() && invFilters != null) {
            // Hotbar is slots 0-8; keep it out of the sort when the option is on so a held item never moves.
            int firstSortable = lockHotbar ? 9 : 0;
            Map<Integer, SlotWhitelist> mainFilters = new java.util.HashMap<>();
            for (Map.Entry<Integer, SlotWhitelist> e : invFilters.entrySet()) {
                if (e.getKey() >= firstSortable && e.getKey() < 36) {
                    mainFilters.put(e.getKey(), e.getValue());
                }
            }
            reorderFilteredGroups(inventory, mainFilters);
        }

        container.markDirty();
        inventory.markDirty();
        player.openContainer.detectAndSendChanges();

        Map<BlockPos, Map<Item, Integer>> moved = new java.util.LinkedHashMap<>();
        if (!movedItems.isEmpty() && animPos != null) moved.put(animPos, movedItems);
        if (ModNet.playerCanReceive(player, AutoDepositResultPayload.ID)) {
            ModNet.sendToPlayer(player, new AutoDepositResultPayload(buildFlights(moved), true));
        }
    }

    /** Builds one animation flight per (item, container) pair from a moved-amount report. */
    private static List<AutoDepositResultPayload.Flight> buildFlights(Map<BlockPos, Map<Item, Integer>> moved) {
        List<AutoDepositResultPayload.Flight> flights = new ArrayList<>();
        for (Map.Entry<BlockPos, Map<Item, Integer>> chestEntry : moved.entrySet()) {
            for (Map.Entry<Item, Integer> itemEntry : chestEntry.getValue().entrySet()) {
                ItemStack representative = new ItemStack(itemEntry.getKey(), Math.min(itemEntry.getValue(), 999));
                flights.add(new AutoDepositResultPayload.Flight(representative, chestEntry.getKey()));
            }
        }
        return flights;
    }

    /** True when any of the player's inventory filters lists the item (so a Fill should pull it in). */
    private static boolean inventoryListsItem(Map<Integer, SlotWhitelist> invFilters, ItemStack stack) {
        if (invFilters == null) return false;
        for (SlotWhitelist wl : invFilters.values()) {
            if (io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack)) return true;
        }
        return false;
    }

    /** True if any slot whitelist of the container lists the given item (variant-aware, with base fallback). */
    private static boolean containerListsItem(Map<Integer, SlotWhitelist> whitelists, ItemStack stack) {
        for (SlotWhitelist wl : whitelists.values()) {
            if (io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack)) return true;
        }
        return false;
    }

    /**
     * Inserts as much of {@code stack} as possible into the container, but only into slots whose
     * filter explicitly lists the item: first merging onto matching stacks, then filling empty
     * filtered slots. Mutates {@code stack}'s count in place.
     */
    private static void insertRespectingFilter(
            IInventory inv, Map<Integer, SlotWhitelist> whitelists, ItemStack stack) {
        int size = inv.getSizeInventory();

        // Phase 1: top up existing identical stacks in filtered slots.
        for (int slot = 0; slot < size && !stack.isEmpty(); slot++) {
            SlotWhitelist wl = whitelists.get(slot);
            if (wl == null || !io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack))
                continue;
            ItemStack dest = inv.getStackInSlot(slot);
            if (dest.isEmpty() || !io.github.marcsanzdev.chestseparators.util.ItemMatch.sameItemSameTags(dest, stack)) continue;
            int max = Math.min(inv.getInventoryStackLimit(), dest.getMaxStackSize());
            int space = max - dest.getCount();
            if (space <= 0) continue;
            int move = Math.min(space, stack.getCount());
            dest.grow(move);
            stack.shrink(move);
        }

        // Phase 2: drop the remainder into empty filtered slots, in the filter's priority order.
        List<Integer> emptySlots = new java.util.ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            SlotWhitelist wl = whitelists.get(slot);
            if (wl == null || !io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack))
                continue;
            if (!inv.getStackInSlot(slot).isEmpty() || !inv.isItemValidForSlot(slot, stack)) continue;
            emptySlots.add(slot);
        }
        emptySlots.sort(java.util.Comparator.comparingInt(
                        (Integer slot) -> FilterPriority.slotPreference(whitelists, slot, stack))
                .thenComparingInt(slot -> slot));
        for (int slot : emptySlots) {
            if (stack.isEmpty()) break;
            int max = Math.min(inv.getInventoryStackLimit(), stack.getMaxStackSize());
            int move = Math.min(max, stack.getCount());
            inv.setInventorySlotContents(slot, copyWithCount(stack, move));
            stack.shrink(move);
        }
    }

    /**
     * Re-lays every filtered group of {@code inv} so its stacks sit in the filter's priority order, packed
     * from the group's first slot. Server-authoritative (direct {@link IInventory#setInventorySlotContents})
     * so nothing can desync or leave ghosts. Items the filter does not list keep their relative place.
     */
    private static void reorderFilteredGroups(IInventory inv, Map<Integer, SlotWhitelist> whitelists) {
        if (inv == null || whitelists == null || whitelists.isEmpty()) return;

        // Collect each group's slot indices in ascending (normal Minecraft) slot order.
        Map<UUID, java.util.List<Integer>> groups = new java.util.LinkedHashMap<>();
        java.util.List<Integer> keys = new ArrayList<>(whitelists.keySet());
        java.util.Collections.sort(keys);
        for (int slot : keys) {
            if (slot < 0 || slot >= inv.getSizeInventory()) continue;
            SlotWhitelist wl = whitelists.get(slot);
            if (wl == null || wl.groupId() == null) continue;
            groups.computeIfAbsent(wl.groupId(), g -> new ArrayList<>()).add(slot);
        }

        for (java.util.List<Integer> slots : groups.values()) {
            if (slots.size() < 2) continue;
            java.util.List<String> order = whitelists.get(slots.get(0)).allowedItems();

            // Snapshot the group's non-empty stacks (references), then sort by the filter's priority order.
            java.util.List<ItemStack> stacks = new ArrayList<>();
            for (int slot : slots) {
                ItemStack st = inv.getStackInSlot(slot);
                if (!st.isEmpty()) stacks.add(st);
            }
            if (stacks.isEmpty()) continue;
            stacks.sort(java.util.Comparator.<ItemStack>comparingInt(st -> {
                        int rank = io.github.marcsanzdev.chestseparators.util.ItemKey.rank(order, st);
                        return rank < 0 ? Integer.MAX_VALUE : rank;
                    })
                    // Tie-break equal-ranked stacks (the same item overflowing several of the group's slots)
                    // by count, fuller first — so a 65-item deposit ends as [64, 1] and not [1, 64].
                    .thenComparing(java.util.Comparator.comparingInt(ItemStack::getCount).reversed()));

            // Re-lay packed from the first slot; any trailing slots become empty.
            for (int i = 0; i < slots.size(); i++) {
                inv.setInventorySlotContents(slots.get(i), i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
            }
        }
        inv.markDirty();
    }

    /**
     * True if no unobstructed path exists from the player's eye to a free lateral side of the
     * container. Access must come through one of the four horizontal sides (not the top or bottom).
     */
    private static boolean isBlockObstructed(World world, Vec3d eye, BlockPos chestPos, Entity player) {
        List<BlockPos> parts = getAssociatedPositions(world, chestPos);
        for (BlockPos part : parts) {
            for (EnumFacing dir : EnumFacing.Plane.HORIZONTAL) {
                BlockPos neighbor = part.offset(dir);
                if (parts.contains(neighbor)) continue;
                // A clear line to this open side cell (ending in air) means the chest is reachable.
                Vec3d neighborCenter =
                        new Vec3d(neighbor.getX() + 0.5D, neighbor.getY() + 0.5D, neighbor.getZ() + 0.5D);
                if (!blockedToPoint(world, eye, neighborCenter, player, null)) return false;
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
            World world, Vec3d eye, Vec3d target, Entity player, java.util.Collection<BlockPos> allowed) {
        RayTraceResult hit = world.rayTraceBlocks(eye, target, false, true, false);
        if (hit == null || hit.typeOfHit == RayTraceResult.Type.MISS) return false;
        return allowed == null || !allowed.contains(hit.getBlockPos());
    }

    /** Center point of an axis-aligned box (1.12.2 {@code AxisAlignedBB} has no {@code getCenter()}). */
    private static Vec3d centerOf(AxisAlignedBB box) {
        return new Vec3d(
                (box.minX + box.maxX) / 2.0D, (box.minY + box.maxY) / 2.0D, (box.minZ + box.maxZ) / 2.0D);
    }

    /**
     * Returns the direction toward the other half of a double chest, or null when the block is not part of
     * a double chest. On 1.12.2 chests carry no {@code ChestType} blockstate, so the pairing is resolved the
     * vanilla way: a horizontally-adjacent chest block of the same {@link BlockChest} type (basic/trap).
     */
    private static EnumFacing getDoubleChestNeighborDirection(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockChest)) {
            return null;
        }
        for (EnumFacing dir : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.offset(dir);
            if (world.getBlockState(neighbor).getBlock() == state.getBlock()) {
                return dir;
            }
        }
        return null;
    }

    /**
     * Returns the effective inventory for the given position, merging both halves for double chests.
     */
    private static IInventory getChestInventorySafe(World world, BlockPos pos) {
        TileEntity be = world.getTileEntity(pos);

        EnumFacing neighborDir = getDoubleChestNeighborDirection(world, pos);
        if (neighborDir != null) {
            TileEntity neighborBe = world.getTileEntity(pos.offset(neighborDir));

            if (be instanceof ILockableContainer && neighborBe instanceof ILockableContainer) {
                // Match vanilla BlockChest#getContainer ordering: a neighbour to the WEST/NORTH is the
                // "upper" (first) inventory, otherwise this block is first.
                if (neighborDir == EnumFacing.WEST || neighborDir == EnumFacing.NORTH) {
                    return new InventoryLargeChest(
                            "container.chestDouble", (ILockableContainer) neighborBe, (ILockableContainer) be);
                } else {
                    return new InventoryLargeChest(
                            "container.chestDouble", (ILockableContainer) be, (ILockableContainer) neighborBe);
                }
            }
        }
        // Single chest, Shulker Box, or Barrel — return directly.
        return be instanceof IInventory ? (IInventory) be : null;
    }

    /**
     * Resolves all block positions occupied by a container (2 for double chests, 1 for all others).
     */
    private static List<BlockPos> getAssociatedPositions(World world, BlockPos pos) {
        List<BlockPos> list = new ArrayList<>();
        list.add(pos);

        EnumFacing neighborDir = getDoubleChestNeighborDirection(world, pos);
        if (neighborDir != null) {
            list.add(pos.offset(neighborDir));
        }
        return list;
    }
}
