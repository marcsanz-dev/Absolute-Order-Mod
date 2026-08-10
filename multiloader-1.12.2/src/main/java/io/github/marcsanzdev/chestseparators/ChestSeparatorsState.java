package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.math.BlockPos;

/**
 * Loader-agnostic server-side runtime state. Both maps are pure runtime state (never persisted): they are
 * rebuilt from client syncs and lock requests, and cleared when a player disconnects.
 */
public final class ChestSeparatorsState {

    /** Thread-safe map of chests currently locked for editing, keyed by block position. */
    public static final Map<BlockPos, UUID> LOCKED_CHESTS = new ConcurrentHashMap<>();

    /**
     * Per-player inventory filters, synced from the client (where they live), used to enforce the
     * "Pick Up" rule server-side: filtered inventory slots only accept their item on pickup. Keyed by
     * player UUID, then by player-inventory slot index.
     */
    public static final Map<UUID, Map<Integer, SlotWhitelist>> INVENTORY_FILTERS = new ConcurrentHashMap<>();

    private ChestSeparatorsState() {}
}
