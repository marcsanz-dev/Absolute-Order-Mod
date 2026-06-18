package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.event.KeyInputHandler;
import io.github.marcsanzdev.chestseparators.network.ModClientNetworking;
import io.github.marcsanzdev.chestseparators.network.WhitelistPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

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

        ClientPlayNetworking.registerGlobalReceiver(WhitelistPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (context.player() == null || context.player().getEntityWorld() == null) return;

                // Detect Ender Chest by inspecting the open screen handler's inventory slots,
                // not the block position — the payload's pos points to the block the server used,
                // which may differ from the Ender Chest position in edge cases.
                boolean isEnderChest = false;
                if (context.player().currentScreenHandler != null) {
                    for (net.minecraft.screen.slot.Slot slot : context.player().currentScreenHandler.slots) {
                        if (slot.inventory instanceof net.minecraft.inventory.EnderChestInventory) {
                            isEnderChest = true;
                            break;
                        }
                    }
                }

                if (isEnderChest) {
                    return;
                }

                // 1. Store data for UI rendering
                io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                        .setCurrentWhitelists(payload.whitelists());

                // 2. Inject into the client-side physical block
                net.minecraft.block.entity.BlockEntity be =
                        context.player().getEntityWorld().getBlockEntity(payload.pos());
                if (be instanceof io.github.marcsanzdev.chestseparators.access.IWhitelistProvider provider) {
                    provider.setWhitelists(payload.whitelists());
                }

                // 3. THE FLICKER KILLER: Inject into the client's dummy GUI inventory
                if (context.player().currentScreenHandler
                        instanceof net.minecraft.screen.GenericContainerScreenHandler genericHandler) {
                    if (genericHandler.getInventory()
                            instanceof io.github.marcsanzdev.chestseparators.access.IWhitelistProvider provider) {
                        provider.setWhitelists(payload.whitelists());
                    }
                }
            });
        });
    }
}
