package io.github.marcsanzdev.chestseparators.neoforge;

import io.github.marcsanzdev.chestseparators.AbsoluteOrder;
import net.neoforged.fml.common.Mod;

/** NeoForge loader entry point — delegates to the common bootstrap. */
@Mod(AbsoluteOrder.MOD_ID)
public final class AbsoluteOrderNeoForge {

    public AbsoluteOrderNeoForge() {
        AbsoluteOrder.init();
    }
}
