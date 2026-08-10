package io.github.marcsanzdev.chestseparators.util;

import io.github.marcsanzdev.chestseparators.mixin.SlotContainerIndexAccessor;
import net.minecraft.world.inventory.Slot;

/**
 * 1.16.5 shim for {@code Slot.getContainerSlot()} (added in 1.17). Reads the private container-relative index
 * via {@link SlotContainerIndexAccessor}.
 */
public final class SlotIndex {
    private SlotIndex() {}

    public static int of(Slot slot) {
        return ((SlotContainerIndexAccessor) (Object) slot).chestseparators$getContainerSlot();
    }
}
