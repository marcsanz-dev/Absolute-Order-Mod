package io.github.marcsanzdev.chestseparators.util;

/** Pure quantity arithmetic shared by client previews and server transfers. */
public final class InventoryTransferPolicy {
    private InventoryTransferPolicy() {}

    /**
     * How many items may leave one matching, filtered stack.
     * A target of zero is the legacy behaviour: keep all items in the filtered slot.
     * Positive targets are retained across the group's earlier slots, not once per slot.
     */
    public static int movableCount(int stackCount, int targetCount, int earlierMatchingCount) {
        if (stackCount <= 0) return 0;
        if (targetCount <= 0) return 0;
        int neededHere = Math.max(0, targetCount - Math.max(0, earlierMatchingCount));
        return Math.max(0, stackCount - neededHere);
    }

    /** Remaining room under a filter target; zero means there is no quantity cap on inbound pulls. */
    public static int inboundRoom(int targetCount, int currentlyHeld) {
        return targetCount <= 0 ? Integer.MAX_VALUE : Math.max(0, targetCount - Math.max(0, currentlyHeld));
    }
}
