package io.github.marcsanzdev.chestseparators.proxy;

import io.github.marcsanzdev.chestseparators.client.ui.AutoDepositAnimator;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.event.KeyInputHandler;
import io.github.marcsanzdev.chestseparators.network.ModClientNetworking;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Client side of the {@code @SidedProxy} split. Registers keybinds and the editor overlay / rendering
 * (immediate-mode GL, era E1) on top of everything {@link CommonProxy} already sets up.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Keybinds are registered early (preInit) so they show up in the controls screen before init.
        ModKeyBindings.register();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        // super wires the channel + server-authoritative handlers (needed by the integrated server).
        super.init(event);
        // Load our .lang by hand (the coremod jar's resource pack can't serve lang/ — see LangFix).
        io.github.marcsanzdev.chestseparators.client.LangFix.register();
        // Force ChestConfigManager to load NOW, in a safe context. Otherwise its first load can happen inside a
        // HUD render frame during world teardown, where loading a mod class off the coremod jar crashed the
        // client (NoClassDefFoundError). Loading it here means every later getInstance() just returns the cached
        // singleton.
        io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance();
        // Copy the ready-made presets (chest 27/54 + inventory) into the config on first run.
        io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance().seedDefaultPresetsIfNeeded();
        // Client-bound receivers must register AFTER CsNetwork.init() created the channel (done in super).
        ModClientNetworking.init();
        // Chest-closed hotkey polling (preview panel / magnifier / auto-deposit gestures).
        KeyInputHandler.register();
        // World-render hook for the flying-item auto-deposit animation + chest-lid tick.
        AutoDepositAnimator.register();
        // Build the Forge Configuration mirror (backing the mods-list Config screen) from the now-loaded
        // GlobalChestConfig values, and hook OnConfigChangedEvent so edits persist back to the JSON.
        io.github.marcsanzdev.chestseparators.config.ForgeConfigBridge.init();
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
