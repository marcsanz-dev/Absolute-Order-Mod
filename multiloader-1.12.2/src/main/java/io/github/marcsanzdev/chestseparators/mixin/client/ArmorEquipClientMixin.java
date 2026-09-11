package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the client-side equip prediction (animation flicker) when right-clicking armor whose target slot
 * has a Shift filter that blocks the item. Without this, the server cancels the equip one tick later, causing
 * a visible flash of the armor being worn momentarily.
 *
 * <p>1.12.2 (E1): {@code MultiPlayerGameMode#useItem}→{@code PlayerControllerMP#processRightClick(EntityPlayer,
 * World, EnumHand)}; {@code Mob.getEquipmentSlotForItem}→{@code EntityLiving.getSlotForItemStack};
 * {@code Registry.ITEM.getKey}→{@code Item#getRegistryName}.
 */
@Mixin(PlayerControllerMP.class)
public abstract class ArmorEquipClientMixin {

    @Inject(method = "processRightClick", at = @At("HEAD"), cancellable = true)
    private void chestseparators$blockArmorEquipIfFiltered(
            EntityPlayer player, World world, EnumHand hand, CallbackInfoReturnable<EnumActionResult> cir) {
        ItemStack stack = player.getHeldItem(hand);
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

        if (!io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack)) {
            cir.setReturnValue(EnumActionResult.FAIL);
        }
    }
}
