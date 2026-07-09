package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsMain;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the client-side equip prediction (animation flicker) when right-clicking armor
 * whose target slot has a Shift filter that blocks the item. Without this, the server cancels
 * the equip one tick later, causing a visible flash of the armor being worn momentarily.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ArmorEquipClientMixin {

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void blockArmorEquipIfFiltered(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty()) return;

        var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
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

        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
        if (!wl.allowedItems().contains(itemId)) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
