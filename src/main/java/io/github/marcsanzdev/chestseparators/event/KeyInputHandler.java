package io.github.marcsanzdev.chestseparators.event;

import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class KeyInputHandler {

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

                Text msg = GlobalChestConfig.instance.showEditButtons
                        ? Text.translatable("message.chestseparators.edit_buttons_visible")
                        : Text.translatable("message.chestseparators.edit_buttons_hidden");
                client.player.sendMessage(msg.copy().formatted(Formatting.GRAY), true);
            }

            // --- 2. Toggle Left Preview Panel ---
            while (ModKeyBindings.openEditorKey.wasPressed()) {
                GlobalChestConfig.instance.showLeftPanel = !GlobalChestConfig.instance.showLeftPanel;
                GlobalChestConfig.saveConfig();

                Text msg = GlobalChestConfig.instance.showLeftPanel
                        ? Text.translatable("message.chestseparators.preview_panel_visible")
                        : Text.translatable("message.chestseparators.preview_panel_hidden");
                client.player.sendMessage(msg.copy().formatted(Formatting.GRAY), true);
            }

            // (Deposit Items outside the GUI logic removed, it's now handled by the GenericContainerScreenMixin)
        });
    }

    public static boolean isModifierPressed() {
        if (net.minecraft.client.MinecraftClient.getInstance().getWindow() == null) return false;
        long window =
                net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();

        int currentKeyCode = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(
                        ModKeyBindings.showPanelModifierKey)
                .getCode();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, currentKeyCode) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
}
