package io.github.marcsanzdev.chestseparators.event;

import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // These fire only with no screen open (chest closed). The same toggles are wired into the
            // chest screen via GenericContainerScreenMixin so they also work with the chest open.
            if (client.currentScreen != null) {
                return;
            }

            if (client.player == null) return;
            while (ModKeyBindings.toggleButtonKey.wasPressed()) actionBar(client, toggleEditButtons());
            while (ModKeyBindings.openEditorKey.wasPressed()) actionBar(client, togglePreviewPanel());
            while (ModKeyBindings.toggleDepositButtonKey.wasPressed()) actionBar(client, toggleDepositButton());
            while (ModKeyBindings.toggleMagnifierKey.wasPressed()) actionBar(client, toggleMagnifier());
        });
    }

    // Each toggle flips its config flag and returns the feedback text, leaving the caller to display
    // it: the action bar when no screen is open, or the editor's status overlay when a chest is open
    // (the action bar would otherwise be hidden behind the chest GUI).

    public static Text toggleMagnifier() {
        GlobalChestConfig.instance.magnifierEnabled = !GlobalChestConfig.instance.magnifierEnabled;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.magnifierEnabled,
                "message.chestseparators.magnifier_on",
                "message.chestseparators.magnifier_off");
    }

    public static Text toggleEditButtons() {
        GlobalChestConfig.instance.showEditButtons = !GlobalChestConfig.instance.showEditButtons;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.showEditButtons,
                "message.chestseparators.edit_buttons_visible",
                "message.chestseparators.edit_buttons_hidden");
    }

    public static Text togglePreviewPanel() {
        GlobalChestConfig.instance.showLeftPanel = !GlobalChestConfig.instance.showLeftPanel;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.showLeftPanel,
                "message.chestseparators.preview_panel_visible",
                "message.chestseparators.preview_panel_hidden");
    }

    public static Text toggleDepositButton() {
        GlobalChestConfig.instance.showDepositButton = !GlobalChestConfig.instance.showDepositButton;
        GlobalChestConfig.saveConfig();
        return label(
                GlobalChestConfig.instance.showDepositButton,
                "message.chestseparators.deposit_button_visible",
                "message.chestseparators.deposit_button_hidden");
    }

    private static Text label(boolean on, String onKey, String offKey) {
        return Text.translatable(on ? onKey : offKey);
    }

    private static void actionBar(MinecraftClient client, Text message) {
        if (client.player != null) client.player.sendMessage(message.copy().formatted(Formatting.GRAY), true);
    }

    public static boolean isModifierPressed() {
        if (MinecraftClient.getInstance().getWindow() == null) return false;
        long window = MinecraftClient.getInstance().getWindow().getHandle();

        int currentKeyCode = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.showPanelModifierKey)
                .getCode();
        return GLFW.glfwGetKey(window, currentKeyCode) == GLFW.GLFW_PRESS;
    }
}
