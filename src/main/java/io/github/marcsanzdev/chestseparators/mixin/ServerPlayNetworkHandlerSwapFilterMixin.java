package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.ChestSeparatorsMain;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces inventory filters on the F-key offhand swap when no screen is open. In that context,
 * the client sends {@code PlayerActionC2SPacket} with action {@code SWAP_ITEM_WITH_OFFHAND} rather
 * than a slot-click packet, so {@link ScreenHandlerSwapFilterMixin} on {@code ScreenHandler} does
 * not intercept it. Here we check both the offhand and the selected hotbar slot against any active
 * Shift-rule filter and cancel the swap if either side is blocked.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerSwapFilterMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void enforceFilterOnOffhandSwap(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (packet.getAction() != PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) return;

        Map<Integer, SlotWhitelist> filters = ChestSeparatorsMain.INVENTORY_FILTERS.get(player.getUuid());
        if (filters == null || filters.isEmpty()) return;

        PlayerInventory inv = player.getInventory();
        // getStack(40) returns the offhand item; getMainHandStack() returns the selected hotbar item.
        ItemStack mainHandItem = inv.getStack(inv.selectedSlot); // moves INTO the offhand slot
        ItemStack offhandItem = inv.getStack(40); // moves INTO the selected hotbar slot

        // Check offhand filter (raw index 40): does the main-hand item pass the Shift rule?
        SlotWhitelist offhandFilter = filters.get(40);
        if (offhandFilter != null && offhandFilter.allowShift() && !mainHandItem.isEmpty()) {
            String id = Registries.ITEM.getId(mainHandItem.getItem()).toString();
            if (!offhandFilter.allowedItems().contains(id)) {
                ci.cancel();
                player.currentScreenHandler.syncState();
                return;
            }
        }

        // Check selected hotbar slot filter: does the offhand item pass the Shift rule?
        int hotbarIndex = inv.selectedSlot;
        SlotWhitelist hotbarFilter = filters.get(hotbarIndex);
        if (hotbarFilter != null && hotbarFilter.allowShift() && !offhandItem.isEmpty()) {
            String id = Registries.ITEM.getId(offhandItem.getItem()).toString();
            if (!hotbarFilter.allowedItems().contains(id)) {
                ci.cancel();
                player.currentScreenHandler.syncState();
            }
        }
    }
}
