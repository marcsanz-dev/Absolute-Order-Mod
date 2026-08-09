package io.github.marcsanzdev.chestseparators.forge;

import io.github.marcsanzdev.chestseparators.AbsoluteOrderClient;
import io.github.marcsanzdev.chestseparators.client.config.AbsoluteOrderConfigScreen;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Forge client-only bootstrap, reached only from inside the {@code EnvExecutor.runInEnv(Env.CLIENT, …)} block
 * so the dedicated-server JVM never loads its client class references. Runs the common client init and
 * registers the Cloth Config screen as the mod's config screen (Forge's ModMenu equivalent).
 */
final class ForgeClientBootstrap {

    private ForgeClientBootstrap() {}

    static void init() {
        AbsoluteOrderClient.init();
        ModLoadingContext.get().registerExtensionPoint(
                ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory(
                        (minecraft, parent) -> AbsoluteOrderConfigScreen.buildScreen(parent)));
    }
}
