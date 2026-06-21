package io.github.marcsanzdev.chestseparators.client.ui;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static KeyBinding openEditorKey;
    public static KeyBinding showPanelModifierKey;
    public static KeyBinding toggleButtonKey;
    public static KeyBinding toggleDepositButtonKey;
    public static KeyBinding toggleMagnifierKey;
    public static KeyBinding depositFilterKey;
    public static KeyBinding depositAllKey;
    public static KeyBinding autoDepositKey;

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("chestseparators", "keys"));

    public static void register() {
        // Passing GLFW.GLFW_KEY_... directly to the constructor sets that value as the "Reset" default.

        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.toggle_preview_panel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY));

        showPanelModifierKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.show_panel_modifier", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY));

        toggleButtonKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.toggle_edit_buttons", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));

        toggleDepositButtonKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.toggle_deposit_button", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));

        toggleMagnifierKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.toggle_magnifier", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, CATEGORY));

        depositFilterKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.chestseparators.deposit_filter", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_S, CATEGORY));

        depositAllKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.chestseparators.deposit_all", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_D, CATEGORY));

        // Unbound by default: the primary trigger is the double-tap-sneak gesture, so this hotkey is an
        // optional alternative the player may bind to avoid any conflict with other movement keys.
        autoDepositKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.auto_deposit", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
    }
}
