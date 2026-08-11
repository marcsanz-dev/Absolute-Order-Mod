package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks right-click armor equip when the target armor slot has an active Shift filter that does not
 * include the item. Vanilla's equip path bypasses {@code isItemValid} entirely, so intercepting at the
 * network-handler level is the only reliable block point.
 *
 * <p>1.12.2 deltas: {@code ServerGamePacketListenerImpl}→{@link NetHandlerPlayServer};
 * {@code handleUseItem(ServerboundUseItemPacket)}→{@code processTryUseItem(CPacketPlayerTryUseItem)};
 * {@code getItemInHand}→{@code getHeldItem}; {@code Mob.getEquipmentSlotForItem}→
 * {@code EntityLiving.getSlotForItemStack}; {@code containerMenu.broadcastChanges}→
 * {@code openContainer.detectAndSendChanges}.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class ArmorEquipFilterMixin {

    @Inject(method = "processTryUseItem", at = @At("HEAD"), cancellable = true)
    private void enforceFilterOnArmorEquip(CPacketPlayerTryUseItem packet, CallbackInfo ci) {
        EntityPlayerMP player = ((NetHandlerPlayServerAccessor) (Object) this).chestseparators$getPlayer();
        ItemStack stack = player.getHeldItem(packet.getHand());
        if (stack.isEmpty()) return;

        EntityEquipmentSlot slot = EntityLiving.getSlotForItemStack(stack);

        int rawIndex;
        switch (slot) {
            case FEET:
                rawIndex = 36;
                break;
            case LEGS:
                rawIndex = 37;
                break;
            case CHEST:
                rawIndex = 38;
                break;
            case HEAD:
                rawIndex = 39;
                break;
            default:
                rawIndex = -1;
                break;
        }
        if (rawIndex == -1) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUniqueID());
        if (filters == null) return;

        SlotWhitelist wl = filters.get(rawIndex);
        if (wl == null || !wl.allowShift()) return;

        String itemId = stack.getItem().getRegistryName().toString();
        if (!wl.allowedItems().contains(itemId)) {
            ci.cancel();
            player.openContainer.detectAndSendChanges();
        }
    }
}
