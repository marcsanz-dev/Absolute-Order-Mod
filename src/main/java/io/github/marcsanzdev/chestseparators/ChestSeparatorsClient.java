package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.event.KeyInputHandler;
import net.fabricmc.api.ClientModInitializer;

/**
 * Implements the client-side bootstrap logic for the Fabric mod lifecycle.
 * <p>
 * This entry point is strictly isolated from the dedicated server path to ensure a proper
 * separation of concerns regarding rendering and input handling subsystems. This prevents
 * server-side class loading violations (e.g., trying to load UI classes on a headless server).
 */
public class ChestSeparatorsClient implements ClientModInitializer {

    /**
     * Lifecycle hook invoked by the Fabric Loader specifically for the physical client environment.
     * <p>
     * Orchestrates the initialization of client-exclusive modules such as input event registration,
     * key bindings, and client-side network payload receivers.
     */
    @Override
    public void onInitializeClient() {
        ModKeyBindings.register();
        KeyInputHandler.register();

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                io.github.marcsanzdev.chestseparators.network.WhitelistPayload.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        // 1. Synchronize the active data state for UI rendering
                        io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance().setCurrentWhitelists(payload.whitelists());

                        // 2. Inject whitelist data into the client-side physical block entity
                        if (context.player() != null && context.player().getEntityWorld() != null) {
                            net.minecraft.block.entity.BlockEntity be = context.player().getEntityWorld().getBlockEntity(payload.pos());
                            if (be instanceof io.github.marcsanzdev.chestseparators.access.IWhitelistProvider provider) {
                                provider.setWhitelists(payload.whitelists());
                            }
                        }

                        // 3. ANTI-FLICKER FIX: Inject data directly into the client's dummy GUI inventory instance
                        if (context.player() != null && context.player().currentScreenHandler instanceof net.minecraft.screen.GenericContainerScreenHandler genericHandler) {
                            if (genericHandler.getInventory() instanceof io.github.marcsanzdev.chestseparators.access.IWhitelistProvider provider) {
                                provider.setWhitelists(payload.whitelists());
                            }
                        }
                    });
                }
        );
    }
}