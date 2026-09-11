package io.github.marcsanzdev.chestseparators.client;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.Locale;
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
 * work for the coremod jar — and inject them into BOTH translation maps. The vanilla client overwrites them on
 * every resource reload, so we re-inject on each reload, registering our listener after the vanilla
 * LanguageManager so ours runs last and wins.
 *
 * <p>Two maps, because 1.12.2 resolves translations through two independent tables:
 * <ul>
 *   <li>the common {@link LanguageMap} — used by {@code TextComponentTranslation} (GUI text, tooltips, item
 *       names). {@link LanguageMap#inject} handles this one.</li>
 *   <li>the CLIENT {@link Locale} held by the {@code LanguageManager} — used by
 *       {@code net.minecraft.client.resources.I18n}, which is what the vanilla Controls screen calls to render
 *       a {@code KeyBinding}'s category and label. LangFix must push into this one too, or every
 *       {@code key.chestseparators.*} / {@code key.categories.chestseparators} shows raw in Controls.</li>
 * </ul>
 * The client-Locale poke uses reflection BY FIELD TYPE (the single {@link Locale} field on the manager, the
 * single {@link Map} field on the Locale) so it survives MCP→SRG reobf without hard-coding obfuscated names.
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
        String path = "/assets/chestseparators/lang/" + code + ".lang";
        // 1) common map (TextComponentTranslation) — GUI text, tooltips, item names.
        try (InputStream in = LangFix.class.getResourceAsStream(path)) {
            if (in != null) {
                LanguageMap.inject(in);
            }
        } catch (Exception ignored) {
            // a missing per-language file just falls back to en_us
        }
        // 2) client Locale (net.minecraft.client.resources.I18n) — keybind category + labels in Controls.
        try (InputStream in = LangFix.class.getResourceAsStream(path)) {
            if (in != null) {
                Map<String, String> clientProps = clientLocaleProperties();
                if (clientProps != null) {
                    parseLangInto(in, clientProps);
                }
            }
        } catch (Exception ignored) {
            // client Locale not ready or reflection failed — TextComponentTranslation still works via (1).
        }
    }

    /**
     * The backing map of the {@link Locale} that {@code net.minecraft.client.resources.I18n} translates through
     * (the same table the vanilla Controls screen queries for keybind labels/category). Resolved by field TYPE
     * so it survives MCP→SRG reobf; the I18n locale field is {@code static} — hence {@code f.get(null)}. In
     * 1.12.2 this Locale is the same object {@code LanguageManager} loads into, but I18n is the authoritative
     * holder for client-side translation.
     */
    private static Map<String, String> clientLocaleProperties() {
        try {
            Locale locale = null;
            for (Field f : I18n.class.getDeclaredFields()) {
                if (Locale.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    locale = (Locale) f.get(null); // static field
                    break;
                }
            }
            if (locale == null) return null;
            for (Field f : locale.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<String, String> props = (Map<String, String>) f.get(locale);
                    return props;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return null;
    }

    /** Minimal {@code .lang} parser: {@code key=value} lines, {@code #} comments and blanks skipped. */
    private static void parseLangInto(InputStream in, Map<String, String> out) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            out.put(line.substring(0, eq), line.substring(eq + 1));
        }
    }
}
