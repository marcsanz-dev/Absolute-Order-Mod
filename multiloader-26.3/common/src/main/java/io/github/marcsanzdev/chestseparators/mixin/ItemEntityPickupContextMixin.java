package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.util.InventoryPickupContext;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ground-pickup-only scope for the inventory's automatic filter slot selection. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupContextMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void chestseparators$beginGroundPickup(Player player, CallbackInfo ci) {
        InventoryPickupContext.begin(player.getUUID());
    }

    @Inject(method = "playerTouch", at = @At("RETURN"))
    private void chestseparators$finishGroundPickup(Player player, CallbackInfo ci) {
        InventoryPickupContext.end();
    }
}
