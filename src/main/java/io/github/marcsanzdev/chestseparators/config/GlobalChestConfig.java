package io.github.marcsanzdev.chestseparators.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Manages the global configuration for the mod.
 * <p>
 * This class handles the serialization and deserialization of the mod's settings
 * using Gson, storing the data in a JSON file within the Fabric configuration directory.
 */
public class GlobalChestConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "chestseparators_config.json");

    /**
     * The active instance of the configuration data held in memory.
     */
    public static ConfigData instance = new ConfigData();

    /**
     * Data structure representing the configurable properties of the mod.
     * Contains both client-side visual preferences and server-side mechanical rules.
     */
    public static class ConfigData {
        // --- CLIENT / VISUALS ---

        /** Enables the dark mode theme for the custom UI. */
        public boolean darkMode = true;

        /** Toggles the visibility of the left-side preview panel in the editor. */
        public boolean showLeftPanel = true;

        /** Enables animations when previewing item insertions. */
        public boolean enablePreviewAnimation = true;

        /** Toggles the visibility of the overlay edit buttons on containers. */
        public boolean showEditButtons = true;

        /** Toggles the visibility of the quick-deposit button. */
        public boolean showDepositButton = true;

        /** Determines if the editor GUI should close when clicking outside of it. */
        public boolean closeOnClickOutside = false;

        /** Opacity level for the background painted areas (0-100). */
        public int bgTransparency = 40;

        /** Opacity level for the drawn separator lines (0-100). */
        public int lineTransparency = 100;

        // --- SERVER / MECHANICS ---

        /** Enforces whitelist rules for manual item insertion by players. */
        public boolean defaultRuleManual = true;

        /** Enforces whitelist rules for shift-clicking items into the container. */
        public boolean defaultRuleShift = true;

        /** Enforces whitelist rules for automated insertion via Hoppers or pipes. */
        public boolean defaultRuleHopper = true;

        /** Actively expels items from the container if they violate the current whitelist. */
        public boolean expelInvalidItems = false;
    }

    /**
     * Convenience method to check if edit buttons should be rendered.
     *
     * @return True if edit buttons are enabled in the configuration, false otherwise.
     */
    public static boolean isShowEditButton() {
        return instance.showEditButtons;
    }

    /**
     * Loads the configuration from the JSON file into memory.
     * <p>
     * If the configuration file does not exist, it instantiates the default settings
     * and creates a new file. If an error occurs during reading, it defaults to a fresh instance.
     */
    public static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
                instance = GSON.fromJson(reader, ConfigData.class);
            } catch (Exception e) {
                e.printStackTrace();
                instance = new ConfigData();
            }
        } else {
            saveConfig();
        }
    }

    /**
     * Saves the current configuration instance from memory to the JSON file.
     */
    public static void saveConfig() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}