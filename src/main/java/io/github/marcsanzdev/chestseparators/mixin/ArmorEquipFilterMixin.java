package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsMain;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks right-click armor equip when the target armor slot has an active Shift filter that
 * does not include the item. Vanilla's ArmorItem.use() bypasses canInsert entirely, so this
 * intercept at the network-handler level is the only reliable block point.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ArmorEquipFilterMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
    private void enforceFilterOnArmorEquip(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        ItemStack stack = player.getStackInHand(packet.getHand());
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
            ci.cancel();
            player.currentScreenHandler.syncState();
        }
    }
}
