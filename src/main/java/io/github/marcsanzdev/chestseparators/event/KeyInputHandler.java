package io.github.marcsanzdev.chestseparators.event;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.AutoDepositRequestPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {

    // Double-tap-sneak gesture state for the radius auto-deposit trigger.
    private static boolean wasSneakDown = false;
    private static long lastSneakTapTime = 0L;
    private static final long DOUBLE_TAP_WINDOW_MS = 350L;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // These fire only with no screen open (chest closed). The same toggles are wired into the
            // chest screen via GenericContainerScreenMixin so they also work with the chest open.
            if (client.currentScreen != null) {
                return;
            }

            if (client.player == null) return;
            while (ModKeyBindings.openEditorKey.wasPressed()) actionBar(client, togglePreviewPanel());
            while (ModKeyBindings.toggleMagnifierKey.wasPressed()) actionBar(client, toggleMagnifier());

            handleAutoDepositTriggers(client);
        });
    }

    // Fires the radius auto-deposit from either the dedicated (optional) hotkey or a quick
    // double-tap of the sneak key. The server performs the move and replies with the animation data.
    private static void handleAutoDepositTriggers(MinecraftClient client) {
        boolean sneakDown = client.options.sneakKey.isPressed();

        if (!GlobalChestConfig.instance.autoDepositEnabled) {
            wasSneakDown = sneakDown;
            return;
        }

        while (ModKeyBindings.autoDepositKey.wasPressed())
            triggerAutoDeposit(client, AutoDepositRequestPayload.ACTION_DEPOSIT_ALL);

        if (GlobalChestConfig.instance.autoDepositDoubleSneak && sneakDown && !wasSneakDown) {
            long now = System.currentTimeMillis();
            if (now - lastSneakTapTime <= DOUBLE_TAP_WINDOW_MS) {
                triggerAutoDeposit(client, AutoDepositRequestPayload.ACTION_DEPOSIT_ALL);
                lastSneakTapTime = 0L;
            } else {
                lastSneakTapTime = now;
            }
        }
        wasSneakDown = sneakDown;
    }

    private static void triggerAutoDeposit(MinecraftClient client, int action) {
        if (client.player == null || !ClientPlayNetworking.canSend(AutoDepositRequestPayload.ID)) return;

        int radius = GlobalChestConfig.instance.autoDepositRadius;

        // Entity (chest minecart / boat) filters are client-side only, so gather the ones near the
        // player and forward them keyed by UUID; the server can't read these on its own.
        Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists = new HashMap<>();
        if (client.world != null) {
            Box box = client.player.getBoundingBox().expand(radius);
            for (Entity entity :
                    client.world.getOtherEntities(client.player, box, e -> e instanceof IWhitelistProvider)) {
                Map<Integer, SlotWhitelist> wl =
                        ChestConfigManager.getInstance().readEntityWhitelists(entity.getUuid());
                if (!wl.isEmpty()) entityWhitelists.put(entity.getUuid(), wl);
            }
        }

        ClientPlayNetworking.send(new AutoDepositRequestPayload(
                radius,
                GlobalChestConfig.instance.autoDepositThroughWalls,
                action,
                ChestConfigManager.getInstance().readEnderWhitelists(),
                entityWhitelists));
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
