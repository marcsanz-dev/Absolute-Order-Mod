package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static KeyBinding openEditorKey;
    public static KeyBinding showPanelModifierKey;
    public static KeyBinding toggleButtonKey;
    public static KeyBinding depositFilterKey;
    public static KeyBinding depositAllKey;

    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("chestseparators", "keys"));

    public static void register() {
        // Al usar GLFW.GLFW_KEY_... directamente en el constructor,
        // Minecraft establece ese valor como el "Reset" por defecto.

        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.toggle_preview_panel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O, // Valor hardcoded para el Reset
                CATEGORY
        ));

        showPanelModifierKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.show_panel_modifier",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT, // Valor hardcoded para el Reset
                CATEGORY
        ));

        toggleButtonKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.toggle_edit_buttons",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H, // Valor hardcoded para el Reset
                CATEGORY
        ));

        depositFilterKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.deposit_filter",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_S,
                CATEGORY
        ));

        depositAllKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chestseparators.deposit_all",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_D,
                CATEGORY
        ));
    }

}