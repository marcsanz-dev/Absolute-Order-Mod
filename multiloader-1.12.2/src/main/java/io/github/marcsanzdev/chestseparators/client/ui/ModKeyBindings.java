package io.github.marcsanzdev.chestseparators.client.ui;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

/**
 * 1.12.2 (E1) port of the mod's key bindings. The modern ports build {@code net.minecraft.client.KeyMapping}
 * against GLFW key codes and register through Architectury; here the vanilla {@link KeyBinding} is built
 * against LWJGL2 {@link Keyboard} key codes and registered through Forge's {@link ClientRegistry}.
 *
 * <p>The editor's already-ported input pipeline compares keys with {@code org.lwjgl.input.Keyboard.KEY_*}, so
 * these bindings carry the same LWJGL2 codes and {@link #matches(KeyBinding, int)} compares the raw keyCode
 * that {@code keyTyped(char, int)} delivers directly (no GLFW conversion).
 */
public class ModKeyBindings {

    public static KeyBinding openEditorKey;
    public static KeyBinding showPanelModifierKey;
    public static KeyBinding toggleMagnifierKey;
    public static KeyBinding pushKey;
    public static KeyBinding pullKey;
    public static KeyBinding autoDepositKey;

    private static final String CATEGORY = "key.categories.chestseparators";

    public static void register() {
        openEditorKey = new KeyBinding("key.chestseparators.toggle_preview_panel", Keyboard.KEY_O, CATEGORY);
        ClientRegistry.registerKeyBinding(openEditorKey);

        // Default Left Alt: the "show panel" modifier, held to reveal the preview panel outside edit mode.
        showPanelModifierKey = new KeyBinding("key.chestseparators.show_panel_modifier", Keyboard.KEY_LMENU, CATEGORY);
        ClientRegistry.registerKeyBinding(showPanelModifierKey);

        toggleMagnifierKey = new KeyBinding("key.chestseparators.toggle_magnifier", Keyboard.KEY_M, CATEGORY);
        ClientRegistry.registerKeyBinding(toggleMagnifierKey);

        // Push inventory items INTO the open chest's filters. Hold Shift to also push into empty slots.
        pushKey = new KeyBinding("key.chestseparators.push", Keyboard.KEY_S, CATEGORY);
        ClientRegistry.registerKeyBinding(pushKey);

        // Pull the open chest's items INTO the inventory's filters. Hold Shift to also pull unfiltered items.
        pullKey = new KeyBinding("key.chestseparators.pull", Keyboard.KEY_D, CATEGORY);
        ClientRegistry.registerKeyBinding(pullKey);

        // Unbound by default (Keyboard.KEY_NONE == 0): the primary trigger is the double-tap-sneak gesture,
        // so this hotkey is an optional alternative the player may bind to avoid any movement-key conflict.
        autoDepositKey = new KeyBinding("key.chestseparators.auto_deposit", Keyboard.KEY_NONE, CATEGORY);
        ClientRegistry.registerKeyBinding(autoDepositKey);
    }

    /**
     * Whether {@code keyCode} (the LWJGL2 code delivered by {@code keyTyped(char, int)}) matches this
     * binding. An unbound binding (code 0) never matches, so the "no key" default triggers nothing.
     */
    public static boolean matches(KeyBinding kb, int keyCode) {
        return keyCode != 0 && kb.getKeyCode() == keyCode;
    }
}
