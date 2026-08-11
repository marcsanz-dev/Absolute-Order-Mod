package io.github.marcsanzdev.chestseparators.config;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.ConfigData;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Bridges Forge's classic {@link Configuration} GUI framework to our JSON-backed {@link GlobalChestConfig}.
 *
 * <p>At runtime {@link GlobalChestConfig#instance} stays the single source of truth (persisted as
 * {@code chestseparators_config.json}). This {@link Configuration} is only a mirror so Forge's built-in
 * {@code GuiConfig} screen has something to render/edit. When the player presses Done, Forge fires
 * {@link ConfigChangedEvent.OnConfigChangedEvent}; we copy the mirror's values back into
 * {@code GlobalChestConfig.instance} and save the JSON.
 */
public class ForgeConfigBridge {

    // Category keys.
    private static final String CAT_INTERFACE = "interface";
    private static final String CAT_BUTTONS = "buttons";
    private static final String CAT_MAGNIFIER = "magnifier";
    private static final String CAT_BEHAVIOR = "behavior";
    private static final String CAT_AUTODEPOSIT = "autodeposit";
    private static final String CAT_RULES = "rules";

    private static Configuration config;
    /** fieldName -> mirror property, kept so syncToGlobal never has to re-pass defaults. */
    private static final Map<String, Property> props = new LinkedHashMap<String, Property>();

    private ForgeConfigBridge() {}

    public static void init() {
        if (config != null) {
            return;
        }
        config = new Configuration(new File(Loader.instance().getConfigDir(), "chestseparators.cfg"));
        defineAndMirror();
        if (config.hasChanged()) {
            config.save();
        }
        // Static @SubscribeEvent handlers are registered by passing the class itself.
        MinecraftForge.EVENT_BUS.register(ForgeConfigBridge.class);
    }

    public static Configuration getConfig() {
        return config;
    }

    private static void defineAndMirror() {
        ConfigData g = GlobalChestConfig.instance;

        // --- interface ---
        defBool(CAT_INTERFACE, "darkMode", true, g.darkMode, "Use the dark UI theme.");
        defBool(CAT_INTERFACE, "showLeftPanel", true, g.showLeftPanel, "Show the left preview panel.");
        defBool(CAT_INTERFACE, "enablePreviewAnimation", true, g.enablePreviewAnimation,
                "Animate the preview panel.");
        defBool(CAT_INTERFACE, "sharedCustomColors", true, g.sharedCustomColors,
                "Share the custom-color column across the Lines, Backgrounds and Combo tabs.");
        defBool(CAT_INTERFACE, "closeOnClickOutside", true, g.closeOnClickOutside,
                "Close popups when clicking outside them.");
        defInt(CAT_INTERFACE, "bgTransparency", 40, g.bgTransparency,
                "Background transparency (0-100).", 0, 100);
        defInt(CAT_INTERFACE, "lineTransparency", 100, g.lineTransparency,
                "Separator line transparency (0-100).", 0, 100);
        config.setCategoryPropertyOrder(CAT_INTERFACE, Arrays.asList(
                "darkMode", "showLeftPanel", "enablePreviewAnimation", "sharedCustomColors",
                "closeOnClickOutside", "bgTransparency", "lineTransparency"));

        // --- buttons ---
        defBool(CAT_BUTTONS, "showEditButtons", true, g.showEditButtons,
                "Master toggle for the editor toolbar.");
        defBool(CAT_BUTTONS, "showDepositButton", true, g.showDepositButton, "Show the deposit button.");
        defBool(CAT_BUTTONS, "btnEditLines", true, g.btnEditLines, "Show the Edit Lines button.");
        defBool(CAT_BUTTONS, "btnFilters", true, g.btnFilters, "Show the Filters button.");
        defBool(CAT_BUTTONS, "btnFillFromChest", true, g.btnFillFromChest, "Show the Fill From Chest button.");
        defBool(CAT_BUTTONS, "btnInventoryPresets", true, g.btnInventoryPresets,
                "Show the Inventory Presets button.");
        defBool(CAT_BUTTONS, "btnChestPresets", true, g.btnChestPresets, "Show the Chest Presets button.");
        config.setCategoryPropertyOrder(CAT_BUTTONS, Arrays.asList(
                "showEditButtons", "showDepositButton", "btnEditLines", "btnFilters", "btnFillFromChest",
                "btnInventoryPresets", "btnChestPresets"));

        // --- magnifier ---
        defBool(CAT_MAGNIFIER, "magnifierEnabled", false, g.magnifierEnabled, "Enable the zoom loupe.");
        defBool(CAT_MAGNIFIER, "magnifierTabLines", true, g.magnifierTabLines,
                "Show the loupe on the Lines tab.");
        defBool(CAT_MAGNIFIER, "magnifierTabBackgrounds", false, g.magnifierTabBackgrounds,
                "Show the loupe on the Backgrounds tab.");
        defBool(CAT_MAGNIFIER, "magnifierTabCombo", false, g.magnifierTabCombo,
                "Show the loupe on the Combo tab.");
        config.setCategoryPropertyOrder(CAT_MAGNIFIER, Arrays.asList(
                "magnifierEnabled", "magnifierTabLines", "magnifierTabBackgrounds", "magnifierTabCombo"));

        // --- behavior ---
        defBool(CAT_BEHAVIOR, "lockHotbarOnReorder", true, g.lockHotbarOnReorder,
                "Keep the hotbar untouched when auto-reordering the inventory.");
        defInt(CAT_BEHAVIOR, "inventoryPresetCount", 9, g.inventoryPresetCount,
                "How many inventory preset slots are available (1-9).", 1, 9);
        config.setCategoryPropertyOrder(CAT_BEHAVIOR, Arrays.asList(
                "lockHotbarOnReorder", "inventoryPresetCount"));

        // --- autodeposit ---
        defBool(CAT_AUTODEPOSIT, "autoDepositEnabled", true, g.autoDepositEnabled,
                "Master switch for radius auto-deposit.");
        defBool(CAT_AUTODEPOSIT, "autoDepositDoubleSneak", true, g.autoDepositDoubleSneak,
                "Trigger auto-deposit with a double-tap-sneak gesture.");
        defInt(CAT_AUTODEPOSIT, "autoDepositRadius", 8, g.autoDepositRadius,
                "Search radius in blocks (1-32).", 1, 32);
        defBool(CAT_AUTODEPOSIT, "autoDepositThroughWalls", false, g.autoDepositThroughWalls,
                "Allow depositing through walls.");
        defBool(CAT_AUTODEPOSIT, "autoDepositAnimation", true, g.autoDepositAnimation,
                "Animate items flying to chests.");
        defBool(CAT_AUTODEPOSIT, "autoDepositTrail", true, g.autoDepositTrail,
                "Leave a particle trail behind flying items.");
        config.setCategoryPropertyOrder(CAT_AUTODEPOSIT, Arrays.asList(
                "autoDepositEnabled", "autoDepositDoubleSneak", "autoDepositRadius",
                "autoDepositThroughWalls", "autoDepositAnimation", "autoDepositTrail"));

        // --- rules ---
        defBool(CAT_RULES, "defaultRuleManual", true, g.defaultRuleManual, "Default Manual insertion rule.");
        defBool(CAT_RULES, "defaultRuleShift", true, g.defaultRuleShift, "Default Shift insertion rule.");
        defBool(CAT_RULES, "defaultRuleHopper", true, g.defaultRuleHopper, "Default Hopper insertion rule.");
        defBool(CAT_RULES, "defaultRulePickup", true, g.defaultRulePickup, "Default Pick Up insertion rule.");
        defBool(CAT_RULES, "expelInvalidItems", false, g.expelInvalidItems,
                "Expel items that do not match any filter.");
        config.setCategoryPropertyOrder(CAT_RULES, Arrays.asList(
                "defaultRuleManual", "defaultRuleShift", "defaultRuleHopper", "defaultRulePickup",
                "expelInvalidItems"));
    }

    private static void defBool(String cat, String key, boolean def, boolean cur, String comment) {
        Property p = config.get(cat, key, def, comment);
        p.set(cur);
        p.setRequiresMcRestart(false);
        props.put(key, p);
    }

    private static void defInt(String cat, String key, int def, int cur, String comment, int min, int max) {
        Property p = config.get(cat, key, def, comment, min, max);
        p.set(cur);
        p.setRequiresMcRestart(false);
        props.put(key, p);
    }

    private static boolean b(String key) {
        return props.get(key).getBoolean();
    }

    private static int i(String key) {
        return props.get(key).getInt();
    }

    /** Copy every mirror value into {@link GlobalChestConfig#instance} and persist the JSON. */
    public static void syncToGlobal() {
        if (config == null) {
            return;
        }
        ConfigData g = GlobalChestConfig.instance;

        g.darkMode = b("darkMode");
        g.showLeftPanel = b("showLeftPanel");
        g.enablePreviewAnimation = b("enablePreviewAnimation");
        g.sharedCustomColors = b("sharedCustomColors");
        g.closeOnClickOutside = b("closeOnClickOutside");
        g.bgTransparency = i("bgTransparency");
        g.lineTransparency = i("lineTransparency");

        g.showEditButtons = b("showEditButtons");
        g.showDepositButton = b("showDepositButton");
        g.btnEditLines = b("btnEditLines");
        g.btnFilters = b("btnFilters");
        g.btnFillFromChest = b("btnFillFromChest");
        g.btnInventoryPresets = b("btnInventoryPresets");
        g.btnChestPresets = b("btnChestPresets");

        g.magnifierEnabled = b("magnifierEnabled");
        g.magnifierTabLines = b("magnifierTabLines");
        g.magnifierTabBackgrounds = b("magnifierTabBackgrounds");
        g.magnifierTabCombo = b("magnifierTabCombo");

        g.lockHotbarOnReorder = b("lockHotbarOnReorder");
        g.inventoryPresetCount = i("inventoryPresetCount");

        g.autoDepositEnabled = b("autoDepositEnabled");
        g.autoDepositDoubleSneak = b("autoDepositDoubleSneak");
        g.autoDepositRadius = i("autoDepositRadius");
        g.autoDepositThroughWalls = b("autoDepositThroughWalls");
        g.autoDepositAnimation = b("autoDepositAnimation");
        g.autoDepositTrail = b("autoDepositTrail");

        g.defaultRuleManual = b("defaultRuleManual");
        g.defaultRuleShift = b("defaultRuleShift");
        g.defaultRuleHopper = b("defaultRuleHopper");
        g.defaultRulePickup = b("defaultRulePickup");
        g.expelInvalidItems = b("expelInvalidItems");

        GlobalChestConfig.saveConfig();
        if (config.hasChanged()) {
            config.save();
        }
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent e) {
        if ("chestseparators".equals(e.getModID())) {
            syncToGlobal();
        }
    }
}
