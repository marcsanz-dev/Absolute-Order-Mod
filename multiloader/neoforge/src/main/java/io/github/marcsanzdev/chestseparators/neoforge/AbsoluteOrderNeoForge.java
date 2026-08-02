package io.github.marcsanzdev.chestseparators.neoforge;

import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import io.github.marcsanzdev.chestseparators.AbsoluteOrder;
import io.github.marcsanzdev.chestseparators.AbsoluteOrderClient;
import net.neoforged.fml.common.Mod;

/** NeoForge loader entry point — delegates to the common bootstrap (and the client bootstrap on the client). */
@Mod(AbsoluteOrder.MOD_ID)
public final class AbsoluteOrderNeoForge {

    public AbsoluteOrderNeoForge() {
        AbsoluteOrder.init();
        // Client-only bootstrap. The double-lambda defers the class reference so the dedicated server JVM
        // never loads the @Environment(CLIENT) client classes.
        EnvExecutor.runInEnv(Env.CLIENT, () -> AbsoluteOrderClient::init);
    }
}
