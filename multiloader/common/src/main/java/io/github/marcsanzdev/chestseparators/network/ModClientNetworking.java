package io.github.marcsanzdev.chestseparators.network;

import dev.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.AutoDepositAnimator;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Registers all client-side S2C packet handlers for the mod. Each handler runs on the main client thread
 * via {@code context.queue(...)}. The matching S2C payload types are registered on the dedicated server in
 * {@link ModNetworking#init()} (so {@code canPlayerReceive} is accurate before any send).
 */
@Environment(EnvType.CLIENT)
public class ModClientNetworking {

    /** Sends the player's current inventory filters to the server (for the Pick Up rule enforcement). */
    public static void sendInventoryFilters() {
        if (NetworkManager.canServerReceive(InventoryFiltersPayload.TYPE)) {
            NetworkManager.sendToServer(new InventoryFiltersPayload(
                    new java.util.HashMap<>(ChestConfigManager.getInstance().getPlayerInventoryFilters())));
        }
    }

    public static void register() {

        // Receives the Shulker Box UUID from the server when a Shulker is opened.
        // Stored immediately so it is available when the screen initializes.
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C, ShulkerUUIDPayload.TYPE, ShulkerUUIDPayload.CODEC, (payload, context) -> {
                    context.queue(() -> {
                        ChestPosStorage.lastOpenedShulkerUUID = payload.shulkerUUID();

                        // Hot-update the editor if the screen is already open (rare but possible).
                        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
                        if (editor != null && editor.session.isShulkerBox) {
                            editor.session.currentShulkerUUID = payload.shulkerUUID();
                            ChestConfigManager.getInstance().loadShulkerConfig(payload.shulkerUUID());
                        }
                    });
                });

        // Receives the server's response to an editor lock request.
        // On success, transitions the editor to VIEW_GROUPS mode.
        // On failure, shows an error status message and plays a rejection sound.
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                EditorLockResponsePayload.TYPE,
                EditorLockResponsePayload.CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
                        if (editor != null
                                && editor.session.currentChestPos != null
                                && editor.session.currentChestPos.equals(payload.pos())) {
                            if (payload.granted()) {
                                editor.hasEditorLock = true;
                                editor.toggleState(EditorState.VIEW_GROUPS);
                                editor.playClickSound(1.0f);
                            } else {
                                editor.showStatus(
                                        Component.translatable("message.chestseparators.locked"), ChatFormatting.RED);
                                editor.playClickSound(0.5f);
                            }
                        }
                    });
                });

        // Receives the result of a radius transfer and animates the items flying (to chests for a
        // deposit, or back to the player for a grab when reverse is set).
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                AutoDepositResultPayload.TYPE,
                AutoDepositResultPayload.CODEC,
                (payload, context) -> context.queue(
                        () -> AutoDepositAnimator.addFlights(payload.flights(), payload.reverse())));
    }
}
