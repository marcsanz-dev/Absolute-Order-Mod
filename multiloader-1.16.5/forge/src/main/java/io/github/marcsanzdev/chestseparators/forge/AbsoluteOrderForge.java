package io.github.marcsanzdev.chestseparators.forge;

import me.shedaniel.architectury.utils.Env;
import me.shedaniel.architectury.utils.EnvExecutor;
import io.github.marcsanzdev.chestseparators.AbsoluteOrder;
import net.minecraftforge.fml.common.Mod;

/** Forge loader entry point — delegates to the common bootstrap (and the client bootstrap on the client). */
@Mod(AbsoluteOrder.MOD_ID)
public final class AbsoluteOrderForge {

    public AbsoluteOrderForge() {
        AbsoluteOrder.init();
        // Client-only bootstrap (common client init + the Cloth Config screen registration). The double-lambda
        // defers the class references so the dedicated server JVM never loads the client-only classes.
        EnvExecutor.runInEnv(Env.CLIENT, () -> ForgeClientBootstrap::init);
    }
}
