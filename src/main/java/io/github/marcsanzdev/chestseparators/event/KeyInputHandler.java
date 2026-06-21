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

            while (ModKeyBindings.toggleButtonKey.wasPressed()) toggleEditButtons(client);
            while (ModKeyBindings.openEditorKey.wasPressed()) togglePreviewPanel(client);
            while (ModKeyBindings.toggleDepositButtonKey.wasPressed()) toggleDepositButton(client);
        });
    }

    public static void toggleEditButtons(MinecraftClient client) {
        GlobalChestConfig.instance.showEditButtons = !GlobalChestConfig.instance.showEditButtons;
        GlobalChestConfig.saveConfig();
        sendToggleMessage(
                client,
                GlobalChestConfig.instance.showEditButtons,
                "message.chestseparators.edit_buttons_visible",
                "message.chestseparators.edit_buttons_hidden");
    }

    public static void togglePreviewPanel(MinecraftClient client) {
        GlobalChestConfig.instance.showLeftPanel = !GlobalChestConfig.instance.showLeftPanel;
        GlobalChestConfig.saveConfig();
        sendToggleMessage(
                client,
                GlobalChestConfig.instance.showLeftPanel,
                "message.chestseparators.preview_panel_visible",
                "message.chestseparators.preview_panel_hidden");
    }

    public static void toggleDepositButton(MinecraftClient client) {
        GlobalChestConfig.instance.showDepositButton = !GlobalChestConfig.instance.showDepositButton;
        GlobalChestConfig.saveConfig();
        sendToggleMessage(
                client,
                GlobalChestConfig.instance.showDepositButton,
                "message.chestseparators.deposit_button_visible",
                "message.chestseparators.deposit_button_hidden");
    }

    private static void sendToggleMessage(MinecraftClient client, boolean on, String onKey, String offKey) {
        if (client.player == null) return;
        client.player.sendMessage(Text.translatable(on ? onKey : offKey).copy().formatted(Formatting.GRAY), true);
    }

    public static boolean isModifierPressed() {
        if (MinecraftClient.getInstance().getWindow() == null) return false;
        long window = MinecraftClient.getInstance().getWindow().getHandle();

        int currentKeyCode = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.showPanelModifierKey)
                .getCode();
        return GLFW.glfwGetKey(window, currentKeyCode) == GLFW.GLFW_PRESS;
    }
}
