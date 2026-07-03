package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

// Mixes into the Hopper logic to respect our custom whitelist rules for automated insertion.
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private static void onCanInsert(Inventory inventory, ItemStack stack, int slot, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (inventory instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();

            if (whitelists != null && whitelists.containsKey(slot)) {
                SlotWhitelist wl = whitelists.get(slot);

                // If Hopper rule is ON, enforce the whitelist filter.
                // If it is OFF, we bypass the filter and let the item pass.
                if (wl.allowHopper()) {
                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                    if (!wl.allowedItems().contains(itemId)) {
                        cir.setReturnValue(false); // Item not in whitelist, block it!
                    }
                }
            }
        }
    }
}