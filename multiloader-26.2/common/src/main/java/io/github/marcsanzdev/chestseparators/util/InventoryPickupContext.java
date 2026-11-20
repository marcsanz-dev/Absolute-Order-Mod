package io.github.marcsanzdev.chestseparators.util;

import java.util.UUID;

/** Distinguishes an ItemEntity ground pickup from a deliberate inventory operation on this thread. */
public final class InventoryPickupContext {
    private static final ThreadLocal<UUID> PICKING_UP = new ThreadLocal<>();

    private InventoryPickupContext() {}

    public static void begin(UUID playerId) { PICKING_UP.set(playerId); }
    public static void end() { PICKING_UP.remove(); }
    public static boolean isGroundPickup(UUID playerId) { return playerId.equals(PICKING_UP.get()); }
}
