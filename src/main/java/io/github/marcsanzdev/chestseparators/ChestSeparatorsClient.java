package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.client.ui.AutoDepositAnimator;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.event.KeyInputHandler;
import io.github.marcsanzdev.chestseparators.network.ModClientNetworking;
import io.github.marcsanzdev.chestseparators.network.WhitelistPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

// Implements the client-side bootstrap logic for the Fabric mod lifecycle.
// This entry point is isolated from the dedicated server path to ensure strict separation of concerns
// regarding rendering and input handling subsystems, preventing server-side class loading violations.
public class ChestSeparatorsClient implements ClientModInitializer {

    // Lifecycle hook invoked by the Fabric Loader specifically for the physical client environment.
    // Orchestrates the initialization of client-exclusive modules such as configuration IO
    // and input event registration.
    @Override
    public void onInitializeClient() {
        ModKeyBindings.register();
        KeyInputHandler.register();
        ModClientNetworking.register();
        AutoDepositAnimator.register();

        // On joining a world, load the inventory profile into the render cache and push the inventory
        // filters to the server so the Pick Up rule works immediately (before the editor is opened).
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> client.execute(() -> {
                    ChestConfigManager.getInstance().loadInventoryRenderCache();
                    ModClientNetworking.sendInventoryFilters();
                }));

        ClientPlayNetworking.registerGlobalReceiver(WhitelistPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.player() == null || context.player().getEntityWorld() == null) return;

                // Detect Ender Chest by inspecting the open screen handler's inventory slots,
                // not the block position — the payload's pos points to the block the server used,
                // which may differ from the Ender Chest position in edge cases.
                boolean isEnderChest = false;
                if (context.player().currentScreenHandler != null) {
                    for (Slot slot : context.player().currentScreenHandler.slots) {
                        if (slot.inventory instanceof EnderChestInventory) {
                            isEnderChest = true;
                            break;
                        }
                    }
                }

                if (isEnderChest) {
                    return;
                }

                // 1. Store data for UI rendering
                ChestConfigManager.getInstance().setCurrentWhitelists(payload.whitelists());

                // 2. Inject into the client-side physical block
                BlockEntity be = context.player().getEntityWorld().getBlockEntity(payload.pos());
                if (be instanceof IWhitelistProvider provider) {
                    provider.setWhitelists(payload.whitelists());
                }

                // 3. THE FLICKER KILLER: Inject into the client's dummy GUI inventory
                if (context.player().currentScreenHandler instanceof GenericContainerScreenHandler genericHandler) {
                    if (genericHandler.getInventory() instanceof IWhitelistProvider provider) {
                        provider.setWhitelists(payload.whitelists());
                    }
                }
            });
        });
    }
}
