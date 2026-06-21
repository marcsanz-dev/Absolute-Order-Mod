package io.github.marcsanzdev.chestseparators.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import net.fabricmc.loader.api.FabricLoader;

public class GlobalChestConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Resolved lazily rather than in a static initializer so that merely reading config values does
    // not touch the Fabric config directory (which is unavailable in unit tests).
    private static File configFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "chestseparators_config.json");
    }

    public static ConfigData instance = new ConfigData();

    /** Shape of the painting/eyedropper magnifier loupe. */
    public enum MagnifierShape {
        CIRCLE,
        SQUARE
    }

    public static class ConfigData {
        // --- CLIENT / VISUALS ---
        public boolean darkMode = true;
        public boolean showLeftPanel = true;
        public boolean enablePreviewAnimation = true;
        public boolean showEditButtons = true;
        public boolean showDepositButton = true;
        public boolean closeOnClickOutside = false;
        // When true, the custom-color column (third palette column) is shared across the Lines,
        // Backgrounds and Combo tabs. When false, each tab keeps its own custom colors.
        public boolean sharedCustomColors = true;
        // Zoom loupe shown while painting separators or using the eyedropper. Off by default; players
        // are hinted that they can enable it (key L or config) if precise editing is tricky.
        public boolean magnifierEnabled = false;
        public MagnifierShape magnifierShape = MagnifierShape.CIRCLE;
        // Loupe size as a percentage of the base size (100 = the minimum/default size).
        public int magnifierSize = 100;
        // Which painting tabs show the loupe (the eyedropper always shows it when enabled). Lines on by
        // default; backgrounds/combo off by default but left for the user to opt into.
        public boolean magnifierTabLines = true;
        public boolean magnifierTabBackgrounds = false;
        public boolean magnifierTabCombo = false;
        public int bgTransparency = 40;
        public int lineTransparency = 100;

        // --- SERVER / MECHANICS ---
        public boolean defaultRuleManual = true;
        public boolean defaultRuleShift = true;
        public boolean defaultRuleHopper = true;
        public boolean expelInvalidItems = false;
    }

    public static boolean isShowEditButton() {
        return instance.showEditButtons;
    }

    public static void loadConfig() {
        File configFile = configFile();
        if (configFile.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                instance = GSON.fromJson(reader, ConfigData.class);
            } catch (Exception e) {
                e.printStackTrace();
                instance = new ConfigData();
            }
        } else {
            saveConfig();
        }
    }

    public static void saveConfig() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
