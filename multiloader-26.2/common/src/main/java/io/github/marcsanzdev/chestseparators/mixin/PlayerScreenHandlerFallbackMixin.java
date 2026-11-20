package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsState;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import io.github.marcsanzdev.chestseparators.util.InventorySlotRefill;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When shift-clicking armor whose target armor slot is blocked by a Shift filter, vanilla's
 * {@code quickMoveStack} returns without a fallback — the item stays put. This adds a fallback that
 * inserts the armor into the main inventory or hotbar instead (like shift-clicking any non-armor item).
 *
 * <p>InventoryMenu slot layout: 0=craft output, 1-4=craft grid, 5-8=armor, 9-35=main, 36-44=hotbar,
 * 45=offhand. {@code moveItemStackTo} is reached via {@link AbstractContainerMenuAccessor}.
 */
@Mixin(InventoryMenu.class)
public abstract class PlayerScreenHandlerFallbackMixin {

    /**
     * Vanilla InventoryMenu.quickMoveStack sends main-inventory items straight to the hotbar,
     * never consulting dedicated MAIN filters. Give matching filtered main slots first refusal.
     * Run on BOTH logical sides to keep predicted clicks aligned with the server.
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void chestseparators$preferFilteredMainOnShift(
            Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (player == null) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;
        Slot sourceSlot = menu.slots.get(slotIndex);
        if (!(sourceSlot.container instanceof Inventory)) return;
        int raw = sourceSlot.getContainerSlot();
        if (raw < 0 || raw >= Inventory.INVENTORY_SIZE || !sourceSlot.hasItem()) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUUID());
        if (filters == null || filters.isEmpty()) return;
        if (raw < 9) {
            // Manual placing INTO the hotbar stays vanilla. An explicit shift-click OUT of it
            // should, however, offer the stack to matching MAIN filter slots before vanilla
            // can merge it into an unrelated main slot.
            chestseparators$shiftHotbarIntoFilteredMain(player, menu, sourceSlot, filters, cir);
            return;
        }
        if (filters.containsKey(raw)) return;
        ItemStack source = sourceSlot.getItem();
        String id = BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
        boolean dedicated = filters.entrySet().stream().anyMatch(e ->
                e.getKey() >= 9 && e.getKey() < Inventory.INVENTORY_SIZE
                        && e.getValue().allowedItems().contains(id));
        if (!dedicated) return;

        ItemStack original = source.copy();
        boolean changed = InventorySlotRefill.refill(player.getInventory(), filters);
        if (!changed) return;
        if (!player.level().isClientSide()) menu.broadcastChanges();
        // If all items moved into dedicated main slots, suppress vanilla's hotbar fallback.
        // Otherwise vanilla can shift-move the surplus in the source slot as usual.
        if (player.getInventory().getItem(raw).isEmpty()) cir.setReturnValue(original);
    }

    /**
     * InventoryMenu's vanilla hotbar -> main quick-move merges into the first identical main
     * stack, even when a later main slot is dedicated to that item. Intercept ONLY an explicit
     * shift-click from the hotbar, using vanilla's own moveItemStackTo for each destination.
     *
     * Filling matching home stacks first makes [1 in home + 64 in hotbar] become
     * [64 in home + 1 in hotbar]. The leftover stays in the hotbar for this click, rather
     * than silently going into an unrelated main slot. If no home has room, vanilla proceeds.
     * This runs on BOTH logical sides for identical client prediction and server results.
     */
    @Unique
    private void chestseparators$shiftHotbarIntoFilteredMain(
            Player player, AbstractContainerMenu menu, Slot sourceSlot,
            Map<Integer, SlotWhitelist> filters, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = sourceSlot.getItem();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        List<Integer> homeSlots = new ArrayList<>();
        for (int menuIndex = 0; menuIndex < menu.slots.size(); menuIndex++) {
            Slot candidate = menu.slots.get(menuIndex);
            if (!(candidate.container instanceof Inventory)) continue;
            int rawTarget = candidate.getContainerSlot();
            if (rawTarget < 9 || rawTarget >= Inventory.INVENTORY_SIZE) continue;
            SlotWhitelist filter = filters.get(rawTarget);
            if (filter != null && filter.allowedItems().contains(itemId)) homeSlots.add(menuIndex);
        }
        if (homeSlots.isEmpty()) return;
        // Stable filter priority and raw-slot ordering; existing matching stacks are filled first.
        homeSlots.sort(Comparator.comparingInt((Integer i) ->
                io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(
                        filters, menu.slots.get(i).getContainerSlot(), itemId))
                .thenComparingInt(i -> menu.slots.get(i).getContainerSlot()));
        AbstractContainerMenuAccessor mover = (AbstractContainerMenuAccessor) (Object) this;
        ItemStack original = stack.copy();
        boolean moved = false;
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int index : homeSlots) {
                if (stack.isEmpty()) break;
                Slot home = menu.slots.get(index);
                ItemStack existing = home.getItem();
                if (pass == 0) {
                    if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) continue;
                    if (existing.getCount() >= Math.min(existing.getMaxStackSize(), home.getMaxStackSize(stack))) continue;
                } else if (!existing.isEmpty()) {
                    continue;
                }
                moved |= mover.chestseparators$moveItemStackTo(stack, index, index + 1, false);
            }
        }
        if (!moved) return;
        if (stack.isEmpty()) sourceSlot.set(ItemStack.EMPTY);
        sourceSlot.setChanged();
        if (!player.level().isClientSide()) menu.broadcastChanges();
        // A successful partial move must not fall through into vanilla's unrelated main slots.
        cir.setReturnValue(original);
    }

    @Inject(method = "quickMoveStack", at = @At("RETURN"), cancellable = true)
    private void chestseparators$armorFallback(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        if (!cir.getReturnValue().isEmpty()) return;

        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        if (slotIndex < 0 || slotIndex >= self.slots.size()) return;
        Slot slot = self.slots.get(slotIndex);
        ItemStack slotStack = slot.getItem();
        if (slotStack.isEmpty()) return;

        var equippable = slotStack.get(DataComponents.EQUIPPABLE);
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

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsState.INVENTORY_FILTERS.get(player.getUUID());
        if (filters == null) return;
        SlotWhitelist wl = filters.get(rawIndex);
        if (wl == null || !wl.allowShift()) return;
        String itemId = BuiltInRegistries.ITEM.getKey(slotStack.getItem()).toString();
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
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            slot.setChanged();
            cir.setReturnValue(copy);
        }
    }
}
