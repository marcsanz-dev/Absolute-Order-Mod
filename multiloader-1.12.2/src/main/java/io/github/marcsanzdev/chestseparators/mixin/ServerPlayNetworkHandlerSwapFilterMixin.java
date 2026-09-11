package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces inventory filters on the F-key offhand swap when no screen is open. There the client sends a
 * {@code CPacketPlayerDigging} with {@code SWAP_HELD_ITEMS} rather than a slot click, so
 * {@link ScreenHandlerSwapFilterMixin} does not intercept it. Both the offhand and the selected hotbar
 * slot are checked against any active Shift-rule filter and the swap is cancelled if either side blocks.
 *
 * <p>1.12.2 deltas: {@code ServerGamePacketListenerImpl}→{@link NetHandlerPlayServer};
 * {@code handlePlayerAction(ServerboundPlayerActionPacket)}→{@code processPlayerDigging(CPacketPlayerDigging)};
 * {@code Action.SWAP_ITEM_WITH_OFFHAND}→{@code Action.SWAP_HELD_ITEMS}; {@code Inventory#selected}→
 * {@code InventoryPlayer#currentItem}; {@code Inventory#getItem}→{@code getStackInSlot};
 * {@code containerMenu.broadcastChanges}→{@code openContainer.detectAndSendChanges}.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class ServerPlayNetworkHandlerSwapFilterMixin {

    @Inject(method = "processPlayerDigging", at = @At("HEAD"), cancellable = true)
    private void enforceFilterOnOffhandSwap(CPacketPlayerDigging packet, CallbackInfo ci) {
        if (packet.getAction() != CPacketPlayerDigging.Action.SWAP_HELD_ITEMS) return;

        EntityPlayerMP player = ((NetHandlerPlayServerAccessor) (Object) this).chestseparators$getPlayer();
        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUniqueID());
        if (filters == null || filters.isEmpty()) return;

        InventoryPlayer inv = player.inventory;
        int hotbarIndex = inv.currentItem;
        ItemStack mainHandItem = inv.getStackInSlot(hotbarIndex); // moves INTO the offhand slot
        ItemStack offhandItem = inv.getStackInSlot(40); // moves INTO the selected hotbar slot

        // Offhand filter (raw index 40): does the main-hand item pass the Shift rule?
        SlotWhitelist offhandFilter = filters.get(40);
        if (offhandFilter != null && offhandFilter.allowShift() && !mainHandItem.isEmpty()) {
            if (!io.github.marcsanzdev.chestseparators.util.ItemKey.matches(offhandFilter.allowedItems(), mainHandItem)) {
                ci.cancel();
                player.openContainer.detectAndSendChanges();
                return;
            }
        }

        // Selected hotbar slot filter: does the offhand item pass the Shift rule?
        SlotWhitelist hotbarFilter = filters.get(hotbarIndex);
        if (hotbarFilter != null && hotbarFilter.allowShift() && !offhandItem.isEmpty()) {
            if (!io.github.marcsanzdev.chestseparators.util.ItemKey.matches(hotbarFilter.allowedItems(), offhandItem)) {
                ci.cancel();
                player.openContainer.detectAndSendChanges();
            }
        }
    }
}
