package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsMain;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
 */
@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerFallbackMixin {

    @Shadow
    @Final
    public DefaultedList<Slot> slots;

    @Shadow
    protected abstract boolean insertItem(ItemStack stack, int startIndex, int endIndex, boolean fromLast);

    @Inject(method = "quickMove", at = @At("RETURN"), cancellable = true)
    private void chestseparators$armorFallback(
            PlayerEntity player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        // Non-empty return value means vanilla already moved the item.
        if (!cir.getReturnValue().isEmpty()) return;

        if (slotIndex < 0 || slotIndex >= this.slots.size()) return;
        Slot slot = this.slots.get(slotIndex);
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
        if (wl.allowedItems().contains(itemId)) return; // Allowed — no fallback needed.

        // Armor is blocked by filter. Redirect to main inventory or hotbar like a regular item.
        ItemStack copy = slotStack.copy();
        boolean fromHotbar = slotIndex >= 36 && slotIndex < 45;
        if (fromHotbar) {
            // Item is in hotbar → try main inventory
            this.insertItem(slotStack, 9, 36, false);
        } else {
            // Item is in main inventory or elsewhere → try hotbar first, then main inventory
            if (!this.insertItem(slotStack, 36, 45, false)) {
                this.insertItem(slotStack, 9, 36, false);
            }
        }

        if (slotStack.getCount() != copy.getCount()) {
            if (slotStack.isEmpty()) slot.setStack(ItemStack.EMPTY);
            slot.markDirty();
            cir.setReturnValue(copy);
        }
    }
}
