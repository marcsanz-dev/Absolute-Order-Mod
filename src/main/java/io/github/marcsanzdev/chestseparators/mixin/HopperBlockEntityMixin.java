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

/**
 * Enforces the Hopper insertion rule on all containers that implement {@link IWhitelistProvider}.
 * When the Hopper rule is enabled for a slot, only whitelisted items may be deposited by automation.
 * When the rule is disabled, the slot accepts any item as if unfiltered.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private static void onCanInsert(Inventory inventory, ItemStack stack, int slot, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (inventory instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();

            if (whitelists != null && whitelists.containsKey(slot)) {
                SlotWhitelist wl = whitelists.get(slot);

                // Hopper rule ON: enforce the whitelist. Rule OFF: let vanilla decide.
                if (wl.allowHopper()) {
                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                    if (!wl.allowedItems().contains(itemId)) {
                        cir.setReturnValue(false);
                    }
                }
            }
        }
    }
}
