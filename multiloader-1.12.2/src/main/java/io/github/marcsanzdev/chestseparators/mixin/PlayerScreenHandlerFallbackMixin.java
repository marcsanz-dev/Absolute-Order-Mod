package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When shift-clicking armor whose target armor slot is blocked by a Shift filter, vanilla's
 * {@code transferStackInSlot} (Mojmap {@code quickMoveStack}) returns without a fallback — the item stays
 * put. This adds a fallback that inserts the armor into the main inventory or hotbar instead (like
 * shift-clicking any non-armor item).
 *
 * <p>ContainerPlayer slot layout: 0=craft output, 1-4=craft grid, 5-8=armor, 9-35=main, 36-44=hotbar,
 * 45=offhand. {@code mergeItemStack} is reached via {@link AbstractContainerMenuAccessor}.
 */
@Mixin(ContainerPlayer.class)
public abstract class PlayerScreenHandlerFallbackMixin {

    @Inject(method = "transferStackInSlot", at = @At("RETURN"), cancellable = true)
    private void chestseparators$armorFallback(EntityPlayer player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (!cir.getReturnValue().isEmpty()) return;

        Container self = (Container) (Object) this;
        if (slotIndex < 0 || slotIndex >= self.inventorySlots.size()) return;
        Slot slot = self.inventorySlots.get(slotIndex);
        ItemStack slotStack = slot.getStack();
        if (slotStack.isEmpty()) return;

        EntityEquipmentSlot armorSlot = EntityLiving.getSlotForItemStack(slotStack);

        int rawIndex;
        switch (armorSlot) {
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
        String itemId = slotStack.getItem().getRegistryName().toString();
        if (wl.allowedItems().contains(itemId)) return;

        ItemStack copy = slotStack.copy();
        boolean fromHotbar = slotIndex >= 36 && slotIndex < 45;
        AbstractContainerMenuAccessor acc = (AbstractContainerMenuAccessor) (Object) this;
        if (fromHotbar) {
            acc.chestseparators$moveItemStackTo(slotStack, 9, 36, false);
        } else {
            if (!acc.chestseparators$moveItemStackTo(slotStack, 36, 45, false)) {
                acc.chestseparators$moveItemStackTo(slotStack, 9, 36, false);
            }
        }

        if (slotStack.getCount() != copy.getCount()) {
            if (slotStack.isEmpty()) slot.putStack(ItemStack.EMPTY);
            slot.onSlotChanged();
            cir.setReturnValue(copy);
        }
    }
}
