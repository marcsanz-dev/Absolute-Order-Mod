package io.github.marcsanzdev.chestseparators.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class GlobalChestConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "chestseparators_config.json");

    public static ConfigData instance = new ConfigData();

    public static class ConfigData {
        // --- CLIENT / VISUALS ---
        public boolean darkMode = true;
        public boolean showLeftPanel = true;
        public boolean enablePreviewAnimation = true;
        public boolean showEditButtons = true;
        public boolean showDepositButton = true;
        public boolean closeOnClickOutside = false;
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

    public static void saveConfig() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8)) {
            GSON.toJson(instance, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}