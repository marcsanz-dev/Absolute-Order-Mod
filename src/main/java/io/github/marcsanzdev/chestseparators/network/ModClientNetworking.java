package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.AutoDepositAnimator;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Registers all client-side S2C packet handlers for the mod.
 * Each handler runs on the main client thread via {@code context.client().execute()}.
 */
public class ModClientNetworking {

    public static void register() {

        // Receives the Shulker Box UUID from the server when a Shulker is opened.
        // Stored immediately so it is available when the screen initializes.
        ClientPlayNetworking.registerGlobalReceiver(ShulkerUUIDPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
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
        ClientPlayNetworking.registerGlobalReceiver(EditorLockResponsePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
                if (editor != null
                        && editor.session.currentChestPos != null
                        && editor.session.currentChestPos.equals(payload.pos())) {
                    if (payload.granted()) {
                        editor.hasEditorLock = true;
                        editor.toggleState(EditorState.VIEW_GROUPS);
                        editor.playClickSound(1.0f);
                    } else {
                        editor.showStatus(Text.translatable("message.chestseparators.locked"), Formatting.RED);
                        editor.playClickSound(0.5f);
                    }
                }
            });
        });

        // Receives the result of a radius auto-deposit and animates the deposited items flying to chests.
        ClientPlayNetworking.registerGlobalReceiver(AutoDepositResultPayload.ID, (payload, context) -> {
            context.client().execute(() -> AutoDepositAnimator.addFlights(payload.flights()));
        });
    }
}
