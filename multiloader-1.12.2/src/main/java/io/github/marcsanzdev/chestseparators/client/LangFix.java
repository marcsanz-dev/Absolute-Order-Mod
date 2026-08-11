package io.github.marcsanzdev.chestseparators.client;

import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.text.translation.LanguageMap;

/**
 * Loads the mod's {@code .lang} files by hand, working around a 1.12.2 quirk where our coremod jar's
 * {@link net.minecraftforge.fml.client.FMLFileResourcePack} cannot serve {@code assets/chestseparators/lang/*}
 * through the resource manager (both {@code getResource} and {@code getAllResources} throw
 * {@code FileNotFoundException} for the lang path, even though the very same pack serves our textures fine and
 * a direct {@code ZipFile.getEntry} on the jar finds the entry — a resource-pack/ZipFile-handle anomaly seen
 * on coremod jars). Because the lang never reaches the client {@code Locale}, {@code TextComponentTranslation}
 * (which resolves through the common {@link LanguageMap}) shows raw keys.
 *
 * <p>The fix: read the lang files straight off the classpath via {@link Class#getResourceAsStream} — which DOES
 * work for the coremod jar — and {@link LanguageMap#inject} them into the common map. The vanilla client
 * overwrites that map (via {@code LanguageMap.replaceWith}) on every resource reload, so we re-inject on each
 * reload, registering our listener after the vanilla LanguageManager so ours runs last and wins.
 */
public final class LangFix {

    private LangFix() {}

    public static void register() {
        IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
        if (rm instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) rm)
                    .registerReloadListener((IResourceManagerReloadListener) manager -> injectAll());
        }
        // Cover the state already loaded before this listener was registered.
        injectAll();
    }

    private static void injectAll() {
        // en_us first as the base, then the selected language on top so its overrides win.
        injectLang("en_us");
        try {
            String current = Minecraft.getMinecraft()
                    .getLanguageManager()
                    .getCurrentLanguage()
                    .getLanguageCode();
            if (current != null && !"en_us".equals(current)) {
                injectLang(current);
            }
        } catch (Exception ignored) {
            // language manager not ready yet — the reload pass will catch it
        }
    }

    private static void injectLang(String code) {
        try (InputStream in = LangFix.class.getResourceAsStream("/assets/chestseparators/lang/" + code + ".lang")) {
            if (in != null) {
                LanguageMap.inject(in);
            }
        } catch (Exception ignored) {
            // a missing per-language file just falls back to en_us
        }
    }
}
