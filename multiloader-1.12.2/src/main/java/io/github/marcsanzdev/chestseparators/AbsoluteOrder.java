package io.github.marcsanzdev.chestseparators;

import io.github.marcsanzdev.chestseparators.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

/**
 * Mod entry point for the 1.12.2 (Phase 4, mythic / Forge legacy) build of Absolute Order.
 *
 * <p>Unlike the modern Architectury ports, 1.12.2 uses the classic FML lifecycle ({@code @Mod} +
 * {@code @SidedProxy} + pre/init/post events) and MCP mappings. Rendering is immediate-mode GL (era E1).
 * This class stays deliberately thin — all wiring happens through the side proxies so the dedicated
 * server never touches client-only classes.
 */
@Mod(
        modid = AbsoluteOrder.MODID,
        name = AbsoluteOrder.NAME,
        version = AbsoluteOrder.VERSION,
        acceptedMinecraftVersions = "[1.12.2]")
public class AbsoluteOrder {

    public static final String MODID = "chestseparators";
    public static final String NAME = "Absolute Order";
    public static final String VERSION = "2.0.0";

    @Mod.Instance(MODID)
    public static AbsoluteOrder instance;

    @SidedProxy(
            clientSide = "io.github.marcsanzdev.chestseparators.proxy.ClientProxy",
            serverSide = "io.github.marcsanzdev.chestseparators.proxy.CommonProxy")
    public static CommonProxy proxy;

    public static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }
}
