package io.github.marcsanzdev.chestseparators.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Server/common side of the {@code @SidedProxy} split. Holds only logic that is safe on a dedicated
 * server (networking registration, config, server-authoritative filter enforcement). Client-only work
 * (keybinds, the editor overlay, rendering) lives in {@link ClientProxy}.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {}

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}
}
