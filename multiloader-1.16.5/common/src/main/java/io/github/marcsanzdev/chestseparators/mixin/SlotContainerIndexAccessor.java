package io.github.marcsanzdev.chestseparators.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link Slot}'s private container-relative index. 1.16.5 has no public getter for it
 * ({@code getContainerSlot()} was only added in 1.17); the value lives in the private {@code slot} field.
 * Access it through {@link io.github.marcsanzdev.chestseparators.util.SlotIndex}.
 */
@Mixin(Slot.class)
public interface SlotContainerIndexAccessor {
    @Accessor("slot")
    int chestseparators$getContainerSlot();
}
