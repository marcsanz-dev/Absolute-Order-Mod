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
                    // Create the ready-made presets on first run (no-op afterwards). Done in a context where
                    // the client run directory is guaranteed available.
                    ChestConfigManager.getInstance().seedDefaultPresetsIfNeeded();
                    ChestConfigManager.getInstance().loadInventoryProfile();
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

                // 1. Store data for UI rendering. setCurrentWhitelists replaces the whole working map with
                // only the container's filters, dropping the player-inventory filters the editor mirrors in
                // at offset keys. Re-mirror them so the preview panel can show an inventory group's filter
                // even while a chest is open — same fix the minecart handler already applies.
                ChestConfigManager.getInstance().setCurrentWhitelists(payload.whitelists());
                io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor editorForMirror =
                        io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.getInstance();
                if (editorForMirror != null && !editorForMirror.session.isInventoryScreenContext) {
                    ChestConfigManager.getInstance().mirrorInventoryIntoCurrent();
                }

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

        // Server-authoritative whitelist for ENTITY containers (chest minecarts), pushed when the GUI opens
        // and after a save. Mirrors the block-chest handler above but keyed by entity UUID.
        ClientPlayNetworking.registerGlobalReceiver(
                io.github.marcsanzdev.chestseparators.network.EntityWhitelistPayload.ID, (payload, context) -> {
                    context.client().execute(() -> {
                        if (context.player() == null) return;

                        // An empty payload means the server entity has no stored filter yet. This is exactly
                        // the migration case for minecarts filtered before this version (their filter lives
                        // only in the local .dat, never synced): ignore it so the local filter is preserved
                        // and displayed, and it will sync up to the server on the next save.
                        if (payload.whitelists().isEmpty()) return;

                        // Inject into the client's open container inventory (the minecart) to keep client-side
                        // prediction consistent and avoid a flicker.
                        if (context.player().currentScreenHandler
                                instanceof GenericContainerScreenHandler genericHandler) {
                            if (genericHandler.getInventory() instanceof IWhitelistProvider provider) {
                                provider.setWhitelists(payload.whitelists());
                            }
                        }

                        // If the editor is open on exactly this minecart, make its displayed filters match the
                        // server. setCurrentWhitelists replaces the container portion, so re-mirror the player
                        // inventory (offset keys) afterwards to avoid dropping the inventory filters.
                        io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor editor =
                                io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor.getInstance();
                        if (editor != null
                                && editor.session.isMinecartChest
                                && payload.entityUuid().equals(editor.session.currentEntityUUID)) {
                            ChestConfigManager.getInstance().setCurrentWhitelists(payload.whitelists());
                            ChestConfigManager.getInstance().mirrorInventoryIntoCurrent();
                        }
                    });
                });
    }
}
