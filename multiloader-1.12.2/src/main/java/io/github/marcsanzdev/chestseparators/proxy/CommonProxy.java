package io.github.marcsanzdev.chestseparators.proxy;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.network.CsNetwork;
import io.github.marcsanzdev.chestseparators.network.ModNetworking;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Server/common side of the {@code @SidedProxy} split. Holds only logic that is safe on a dedicated
 * server (networking registration, config, server-authoritative filter enforcement). Client-only work
 * (keybinds, the editor overlay, rendering) lives in {@link ClientProxy}.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // Config is needed on both sides (the server reads enforcement flags too) and the config dir is
        // available from preInit onward.
        GlobalChestConfig.loadConfig();
    }

    public void init(FMLInitializationEvent event) {
        // The channel MUST be created before any receiver is registered against it, so CsNetwork.init()
        // runs first, then the server-authoritative handlers. On the integrated (single-player) server the
        // client runs this too via ClientProxy#init -> super, which is exactly what enforcement needs.
        CsNetwork.init();
        ModNetworking.init();
    }

    public void postInit(FMLPostInitializationEvent event) {}
}
