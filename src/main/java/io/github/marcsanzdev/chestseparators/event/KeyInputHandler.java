package io.github.marcsanzdev.chestseparators.event;

import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * Handles global key input events during the client tick lifecycle.
 * <p>
 * This class registers listeners that evaluate user input outside of standard GUI screens,
 * allowing players to toggle configuration settings on the fly.
 */
public class KeyInputHandler {

    /**
     * Registers the client tick event listener.
     * <p>
     * Evaluates registered key bindings at the end of each client tick and performs
     * the corresponding state updates, such as toggling UI elements and notifying the player.
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // We keep the screen check to prevent hotkeys from triggering while typing in GUIs
            if (client.currentScreen != null) {
                return;
            }

            // --- 1. Toggle Edit Buttons ---
            while (ModKeyBindings.toggleButtonKey.wasPressed()) {
                GlobalChestConfig.instance.showEditButtons = !GlobalChestConfig.instance.showEditButtons;
                GlobalChestConfig.saveConfig();

                Text msg = GlobalChestConfig.instance.showEditButtons ?
                        Text.translatable("message.chestseparators.edit_buttons_visible") :
                        Text.translatable("message.chestseparators.edit_buttons_hidden");
                client.player.sendMessage(msg.copy().formatted(Formatting.GRAY), true);
            }

            // --- 2. Toggle Left Preview Panel ---
            while (ModKeyBindings.openEditorKey.wasPressed()) {
                GlobalChestConfig.instance.showLeftPanel = !GlobalChestConfig.instance.showLeftPanel;
                GlobalChestConfig.saveConfig();

                Text msg = GlobalChestConfig.instance.showLeftPanel ?
                        Text.translatable("message.chestseparators.preview_panel_visible") :
                        Text.translatable("message.chestseparators.preview_panel_hidden");
                client.player.sendMessage(msg.copy().formatted(Formatting.GRAY), true);
            }

            // (Deposit Items outside the GUI logic removed, it's now handled by the GenericContainerScreenMixin)
        });
    }

    /**
     * Determines if the configured modifier key is currently being held down.
     * <p>
     * Interfaces directly with GLFW to capture raw keyboard state, bypassing standard
     * event consumption rules to allow continuous polling during UI interactions.
     *
     * @return {@code true} if the modifier key is pressed, {@code false} otherwise.
     */
    public static boolean isModifierPressed() {
        if (MinecraftClient.getInstance().getWindow() == null) return false;
        long window = MinecraftClient.getInstance().getWindow().getHandle();

        // Read the current key code from the native Minecraft KeyBinding and pass it to GLFW
        int currentKeyCode = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.showPanelModifierKey).getCode();
        return GLFW.glfwGetKey(window, currentKeyCode) == GLFW.GLFW_PRESS;
    }
}