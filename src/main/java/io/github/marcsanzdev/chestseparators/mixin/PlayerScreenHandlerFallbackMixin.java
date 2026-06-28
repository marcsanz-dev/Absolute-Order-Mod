package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsMain;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When shift-clicking armor whose target armor slot is blocked by a Shift filter,
 * vanilla's PlayerScreenHandler.quickMove returns without a fallback — the item stays
 * in place. This mixin adds a fallback that inserts the armor into the main inventory
 * or hotbar instead (the same behaviour as shift-clicking any non-armor item).
 *
 * <p>PlayerScreenHandler slot layout: 0=craft output, 1-4=craft grid, 5-8=armor,
 * 9-35=main inventory, 36-44=hotbar, 45=offhand.
 *
 * <p>@Shadow cannot resolve insertItem/slots from parent ScreenHandler when targeting
 * PlayerScreenHandler (no refMap). Instead we cast to ScreenHandler directly;
 * insertItem is exposed as public via the access widener.
 */
@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerFallbackMixin {

    @Inject(method = "quickMove", at = @At("RETURN"), cancellable = true)
    private void chestseparators$armorFallback(
            PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (!cir.getReturnValue().isEmpty()) return;

        ScreenHandler self = (ScreenHandler) (Object) this;
        if (slotIndex < 0 || slotIndex >= self.slots.size()) return;
        Slot slot = self.slots.get(slotIndex);
        ItemStack slotStack = slot.getStack();
        if (slotStack.isEmpty()) return;

        var equippable = slotStack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null) return;

        int rawIndex =
                switch (equippable.slot()) {
                    case FEET -> 36;
                    case LEGS -> 37;
                    case CHEST -> 38;
                    case HEAD -> 39;
                    default -> -1;
                };
        if (rawIndex == -1) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsMain.INVENTORY_FILTERS.get(player.getUuid());
        if (filters == null) return;
        SlotWhitelist wl = filters.get(rawIndex);
        if (wl == null || !wl.allowShift()) return;
        String itemId = Registries.ITEM.getId(slotStack.getItem()).toString();
        if (wl.allowedItems().contains(itemId)) return;

        ItemStack copy = slotStack.copy();
        boolean fromHotbar = slotIndex >= 36 && slotIndex < 45;
        if (fromHotbar) {
            self.insertItem(slotStack, 9, 36, false);
        } else {
            if (!self.insertItem(slotStack, 36, 45, false)) {
                self.insertItem(slotStack, 9, 36, false);
            }
        }

        if (slotStack.getCount() != copy.getCount()) {
            if (slotStack.isEmpty()) slot.setStack(ItemStack.EMPTY);
            slot.markDirty();
            cir.setReturnValue(copy);
        }
    }
}
