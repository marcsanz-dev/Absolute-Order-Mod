package io.github.marcsanzdev.chestseparators.util;

import net.minecraft.inventory.Slot;

/**
 * Container-relative slot index. On 1.12.2 the vanilla {@link Slot#getSlotIndex()} already exposes the
 * index within the backing inventory (the modern {@code getContainerSlot()} / private-field-accessor shim
 * the E2 backports needed is unnecessary here), so this is a thin, uniform wrapper for the call sites.
 */
public final class SlotIndex {
    private SlotIndex() {}

    public static int of(Slot slot) {
        return slot.getSlotIndex();
    }
}
