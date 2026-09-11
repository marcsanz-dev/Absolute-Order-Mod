package io.github.marcsanzdev.chestseparators.forge;

import io.github.marcsanzdev.chestseparators.AbsoluteOrderClient;
import io.github.marcsanzdev.chestseparators.client.config.AbsoluteOrderConfigScreen;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Forge client-only bootstrap, reached only from inside the {@code EnvExecutor.runInEnv(Env.CLIENT, …)} block
 * so the dedicated-server JVM never loads its client class references. Runs the common client init and
 * registers the Cloth Config screen as the mod's config screen (Forge's ModMenu equivalent).
 *
 * <p>1.16.5 registers the config GUI via {@code ExtensionPoint.CONFIGGUIFACTORY} — a
 * {@code BiFunction<Minecraft, Screen, Screen>} — which predates the {@code ConfigGuiHandler}/
 * {@code ConfigScreenHandler} wrappers used by 1.18.2 / 1.19+.
 */
final class ForgeClientBootstrap {

    private ForgeClientBootstrap() {}

    static void init() {
        AbsoluteOrderClient.init();
        ModLoadingContext.get()
                .registerExtensionPoint(
                        ExtensionPoint.CONFIGGUIFACTORY,
                        () -> (BiFunction<Minecraft, Screen, Screen>)
                                (minecraft, parent) -> AbsoluteOrderConfigScreen.buildScreen(parent));
    }
}
