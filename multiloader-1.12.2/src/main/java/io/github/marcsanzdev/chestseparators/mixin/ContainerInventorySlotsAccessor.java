package io.github.marcsanzdev.chestseparators.mixin;

import java.util.List;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link Container}'s {@code inventorySlots} list ({@code List<Slot>}) via an {@code @Accessor} mixin,
 * replacing {@code @Shadow} declarations that the manual montage's annotation processor does not emit to the
 * refmap (and so fail to bind under SRG/obf in production). Shared by the swap and whitelist container mixins.
 */
@Mixin(Container.class)
public interface ContainerInventorySlotsAccessor {

    @Accessor("inventorySlots")
    List<Slot> chestseparators$getInventorySlots();
}
