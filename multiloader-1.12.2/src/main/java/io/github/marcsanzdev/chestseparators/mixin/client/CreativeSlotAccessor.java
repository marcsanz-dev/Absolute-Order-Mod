package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the wrapped slot inside {@code GuiContainerCreative$CreativeSlot}.
 *
 * <p>On the creative inventory tab every player slot is a {@code CreativeSlot} that wraps the real inventory
 * slot. Its own {@code getSlotIndex()} returns the creative handler's slot ID, NOT the inventory index the
 * server enforces filters by. Reading the wrapped slot instead yields the canonical index, exactly what the
 * survival inventory and chests report, so a filter/layout made in creative lands on the right slot everywhere.
 */
@Mixin(targets = "net.minecraft.client.gui.inventory.GuiContainerCreative$CreativeSlot")
public interface CreativeSlotAccessor {

    @Accessor("slot")
    Slot chestseparators$getWrappedSlot();
}
