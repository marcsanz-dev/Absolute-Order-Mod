package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the wrapped slot inside {@code CreativeInventoryScreen$CreativeSlot}.
 *
 * <p>On the creative inventory tab every player slot is a {@code CreativeSlot} that wraps the real
 * {@code PlayerScreenHandler} slot. Its own {@code getIndex()} returns the wrapper's position in the
 * creative handler (armor 5-8, hotbar 36-44, offhand 45) — the handler slot IDs, NOT the inventory
 * indices the server enforces filters by (hotbar 0-8, armor 36-39, offhand 40). Reading the wrapped
 * slot's {@code getIndex()} instead yields the canonical index, exactly what the survival inventory and
 * chests report, so a filter/layout made in creative lands on the right slot everywhere.
 */
@Mixin(targets = "net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen$CreativeSlot")
public interface CreativeSlotAccessor {

    @Accessor("slot")
    Slot chestseparators$getWrappedSlot();
}
