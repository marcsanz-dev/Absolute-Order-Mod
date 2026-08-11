package io.github.marcsanzdev.chestseparators.core;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/**
 * Minimal coremod whose only purpose is to make the mod jar a <em>coremod</em>, so it is placed on the launch
 * classloader during the early PREINIT phase.
 *
 * <p>MixinBooter reads each jar's {@code MixinConfigs} manifest attribute during its own coremod phase and
 * loads the referenced config through the launch classloader. A plain (non-coremod) mod jar is not on that
 * classloader yet at that moment, so the config resource reads back as null and Mixin aborts with
 * "The specified resource ... was invalid or could not be read". Being a coremod fixes the timing: the jar is
 * on the classloader, so {@code MixinConfigs: mixins.absoluteorder.json} in the manifest is readable.
 *
 * <p>This class deliberately implements ONLY {@link IFMLLoadingPlugin} and references nothing beyond FML —
 * in particular NOT MixinBooter's {@code IEarlyMixinLoader}, because a coremod class is defined before
 * MixinBooter's jar is guaranteed to be on the classloader, and referencing it here throws
 * {@code NoClassDefFoundError} at coremod-load time. It must not touch any Minecraft class either (loaded
 * before deobfuscation/transformation).
 */
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("AbsoluteOrder Core")
@IFMLLoadingPlugin.SortingIndex(1001)
public class AbsoluteOrderCore implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // no-op
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
