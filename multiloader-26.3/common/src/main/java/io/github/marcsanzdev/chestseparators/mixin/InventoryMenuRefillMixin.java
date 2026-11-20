package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.util.InventorySlotRefill;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reconciles filtered MAIN inventory slots after each completed player-inventory interaction.
 *
 * <p>The old origin-tracking hook only reacted when a stack was picked up from a filtered slot
 * and subsequently placed into an unfiltered MAIN slot. Shift-clicking, hotbar swaps, quick
 * crafting and rearranging stacks that were already in ordinary slots could bypass that hook,
 * leaving dedicated slots partially filled until an unrelated click triggered the refill.
 *
 * <p>This is deliberately limited to InventoryMenu. Open-container Push uses several synthetic
 * clicks on a different menu and has its own pre-deposit refill; running after each of those
 * clicks would interrupt the cursor transfer. InventorySlotRefill never sources the hotbar,
 * so intentionally placed hotbar items remain untouched.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class InventoryMenuRefillMixin {
    @Inject(method = "clicked", at = @At("RETURN"))
    private void chestseparators$reconcilePlayerInventoryAfterClick(
            int slotIndex, int button, ContainerInput actionType, Player player, CallbackInfo ci) {
        if (player == null || !((Object) this instanceof InventoryMenu)) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUUID());
        if (filters == null || filters.isEmpty()) return;

        if (InventorySlotRefill.refill(player.getInventory(), filters) && !player.level().isClientSide()) {
            ((AbstractContainerMenu) (Object) this).broadcastChanges();
        }
    }
}
