package io.github.marcsanzdev.chestseparators.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    /** A boolean toggle carrying the mod's icon on the left. {@code key} is the short config suffix. */
    private static IconBooleanListEntry iconToggle(
            String key, boolean value, boolean def, Consumer<Boolean> save, Identifier icon) {
        return new IconBooleanListEntry(
                Text.translatable("config.chestseparators." + key),
                value,
                def,
                save,
                Text.translatable("config.chestseparators." + key + ".tooltip"),
                icon);
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("title.chestseparators.config"));

            builder.setSavingRunnable(GlobalChestConfig::saveConfig);
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // --- CATEGORY 1: CLIENT / VISUALS & DEFAULTS ---
            ConfigCategory clientCategory =
                    builder.getOrCreateCategory(Text.translatable("config.chestseparators.category.client"));

            // ================= INTERFACE =================
            List<AbstractConfigListEntry> toolbarEntries = new ArrayList<>();
            toolbarEntries.add(iconToggle(
                    "btn_edit_lines",
                    GlobalChestConfig.instance.btnEditLines,
                    true,
                    v -> GlobalChestConfig.instance.btnEditLines = v,
                    ModTextures.ICON_SM_COMBO));
            toolbarEntries.add(iconToggle(
                    "btn_filters",
                    GlobalChestConfig.instance.btnFilters,
                    true,
                    v -> GlobalChestConfig.instance.btnFilters = v,
                    ModTextures.ICON_SM_FILTER));
            toolbarEntries.add(iconToggle(
                    "show_deposit_button",
                    GlobalChestConfig.instance.showDepositButton,
                    true,
                    v -> GlobalChestConfig.instance.showDepositButton = v,
                    ModTextures.ICON_SM_FILL));
            toolbarEntries.add(iconToggle(
                    "btn_fill_from_chest",
                    GlobalChestConfig.instance.btnFillFromChest,
                    true,
                    v -> GlobalChestConfig.instance.btnFillFromChest = v,
                    ModTextures.ICON_SM_DEPOSIT));
            toolbarEntries.add(iconToggle(
                    "btn_chest_presets",
                    GlobalChestConfig.instance.btnChestPresets,
                    true,
                    v -> GlobalChestConfig.instance.btnChestPresets = v,
                    ModTextures.ICON_SM_CHEST_PRESETS));
            toolbarEntries.add(iconToggle(
                    "btn_inventory_presets",
                    GlobalChestConfig.instance.btnInventoryPresets,
                    true,
                    v -> GlobalChestConfig.instance.btnInventoryPresets = v,
                    ModTextures.ICON_SM_INV_PRESETS));

            List<AbstractConfigListEntry> interfaceEntries = new ArrayList<>();
            interfaceEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.show_left_panel"),
                            GlobalChestConfig.instance.showLeftPanel)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.show_left_panel.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.showLeftPanel = newValue)
                    .build());
            interfaceEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.enable_preview_animation"),
                            GlobalChestConfig.instance.enablePreviewAnimation)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.enable_preview_animation.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.enablePreviewAnimation = newValue)
                    .build());
            interfaceEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.show_edit_buttons"),
                            GlobalChestConfig.instance.showEditButtons)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.show_edit_buttons.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.showEditButtons = newValue)
                    .build());
            interfaceEntries.add(entryBuilder
                    .startSubCategory(Text.translatable("config.chestseparators.group.toolbar_buttons"), toolbarEntries)
                    .setExpanded(false)
                    .build());
            interfaceEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.close_on_click_outside"),
                            GlobalChestConfig.instance.closeOnClickOutside)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.close_on_click_outside.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.closeOnClickOutside = newValue)
                    .build());
            clientCategory.addEntry(entryBuilder
                    .startSubCategory(Text.translatable("config.chestseparators.group.interface"), interfaceEntries)
                    .setExpanded(false)
                    .build());

            // ================= LAYOUT =================
            List<AbstractConfigListEntry> layoutEntries = new ArrayList<>();
            layoutEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.shared_custom_colors"),
                            GlobalChestConfig.instance.sharedCustomColors)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.shared_custom_colors.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.sharedCustomColors = newValue)
                    .build());
            layoutEntries.add(new IconIntegerSliderEntry(
                    Text.translatable("config.chestseparators.line_transparency"),
                    0,
                    100,
                    GlobalChestConfig.instance.lineTransparency,
                    100,
                    v -> GlobalChestConfig.instance.lineTransparency = v,
                    Text.translatable("config.chestseparators.line_transparency.tooltip"),
                    ModTextures.ICON_SM_PENCIL));
            layoutEntries.add(new IconIntegerSliderEntry(
                    Text.translatable("config.chestseparators.bg_transparency"),
                    0,
                    100,
                    GlobalChestConfig.instance.bgTransparency,
                    40,
                    v -> GlobalChestConfig.instance.bgTransparency = v,
                    Text.translatable("config.chestseparators.bg_transparency.tooltip"),
                    ModTextures.ICON_SM_BRUSH));
            clientCategory.addEntry(entryBuilder
                    .startSubCategory(Text.translatable("config.chestseparators.group.layout"), layoutEntries)
                    .setExpanded(false)
                    .build());

            // ================= FILTERS =================
            List<AbstractConfigListEntry> filterEntries = new ArrayList<>();
            filterEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.default_rule_manual"),
                            GlobalChestConfig.instance.defaultRuleManual)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.default_rule_manual.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.defaultRuleManual = newValue)
                    .build());
            filterEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.default_rule_shift"),
                            GlobalChestConfig.instance.defaultRuleShift)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.default_rule_shift.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.defaultRuleShift = newValue)
                    .build());
            filterEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.default_rule_hopper"),
                            GlobalChestConfig.instance.defaultRuleHopper)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.default_rule_hopper.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.defaultRuleHopper = newValue)
                    .build());
            clientCategory.addEntry(entryBuilder
                    .startSubCategory(Text.translatable("config.chestseparators.group.filters"), filterEntries)
                    .setExpanded(false)
                    .build());

            // ================= MAGNIFIER =================
            List<AbstractConfigListEntry> magnifierEntries = new ArrayList<>();
            magnifierEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.magnifier_enabled"),
                            GlobalChestConfig.instance.magnifierEnabled)
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("config.chestseparators.magnifier_enabled.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.magnifierEnabled = newValue)
                    .build());
            magnifierEntries.add(entryBuilder
                    .startEnumSelector(
                            Text.translatable("config.chestseparators.magnifier_shape"),
                            GlobalChestConfig.MagnifierShape.class,
                            GlobalChestConfig.instance.magnifierShape)
                    .setDefaultValue(GlobalChestConfig.MagnifierShape.CIRCLE)
                    .setEnumNameProvider(shape -> Text.translatable("config.chestseparators.magnifier_shape."
                            + ((Enum<?>) shape).name().toLowerCase()))
                    .setTooltip(Text.translatable("config.chestseparators.magnifier_shape.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.magnifierShape = newValue)
                    .build());
            magnifierEntries.add(iconToggle(
                    "magnifier_tab_lines",
                    GlobalChestConfig.instance.magnifierTabLines,
                    true,
                    v -> GlobalChestConfig.instance.magnifierTabLines = v,
                    ModTextures.ICON_SM_PENCIL));
            magnifierEntries.add(iconToggle(
                    "magnifier_tab_backgrounds",
                    GlobalChestConfig.instance.magnifierTabBackgrounds,
                    false,
                    v -> GlobalChestConfig.instance.magnifierTabBackgrounds = v,
                    ModTextures.ICON_SM_BRUSH));
            magnifierEntries.add(iconToggle(
                    "magnifier_tab_combo",
                    GlobalChestConfig.instance.magnifierTabCombo,
                    false,
                    v -> GlobalChestConfig.instance.magnifierTabCombo = v,
                    ModTextures.ICON_SM_COMBO));
            clientCategory.addEntry(entryBuilder
                    .startSubCategory(Text.translatable("config.chestseparators.group.magnifier"), magnifierEntries)
                    .setExpanded(false)
                    .build());

            // Auto-deposit options, collapsed into one sub-category.
            List<AbstractConfigListEntry> autoDepositEntries = new ArrayList<>();
            autoDepositEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.lock_hotbar_on_reorder"),
                            GlobalChestConfig.instance.lockHotbarOnReorder)
                    .setDefaultValue(true)
                    // Two-line tooltip: a plain first line, then a dimmed clarification underneath.
                    .setTooltip(
                            Text.translatable("config.chestseparators.lock_hotbar_on_reorder.tooltip"),
                            Text.translatable("config.chestseparators.lock_hotbar_on_reorder.tooltip2")
                                    .formatted(net.minecraft.util.Formatting.DARK_GRAY))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.lockHotbarOnReorder = newValue)
                    .build());
            autoDepositEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.auto_deposit_enabled"),
                            GlobalChestConfig.instance.autoDepositEnabled)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.auto_deposit_enabled.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositEnabled = newValue)
                    .build());
            autoDepositEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.auto_deposit_double_sneak"),
                            GlobalChestConfig.instance.autoDepositDoubleSneak)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.auto_deposit_double_sneak.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositDoubleSneak = newValue)
                    .build());
            autoDepositEntries.add(entryBuilder
                    .startIntSlider(
                            Text.translatable("config.chestseparators.auto_deposit_radius"),
                            GlobalChestConfig.instance.autoDepositRadius,
                            3,
                            32)
                    .setDefaultValue(8)
                    .setTooltip(Text.translatable("config.chestseparators.auto_deposit_radius.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositRadius = newValue)
                    .build());
            autoDepositEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.auto_deposit_through_walls"),
                            GlobalChestConfig.instance.autoDepositThroughWalls)
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("config.chestseparators.auto_deposit_through_walls.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositThroughWalls = newValue)
                    .build());
            autoDepositEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.auto_deposit_animation"),
                            GlobalChestConfig.instance.autoDepositAnimation)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.auto_deposit_animation.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositAnimation = newValue)
                    .build());
            autoDepositEntries.add(entryBuilder
                    .startBooleanToggle(
                            Text.translatable("config.chestseparators.auto_deposit_trail"),
                            GlobalChestConfig.instance.autoDepositTrail)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.auto_deposit_trail.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.autoDepositTrail = newValue)
                    .build());
            clientCategory.addEntry(entryBuilder
                    .startSubCategory(Text.translatable("config.chestseparators.group.autodeposit"), autoDepositEntries)
                    .setExpanded(false)
                    .build());

            // --- CATEGORY 2: SERVER / MECHANICS ---
            // Server mechanics options are only exposed in singleplayer, where the server and client share a JVM.
            if (MinecraftClient.getInstance().isInSingleplayer()) {
                ConfigCategory serverCategory =
                        builder.getOrCreateCategory(Text.translatable("config.chestseparators.category.server"));

                serverCategory.addEntry(entryBuilder
                        .startBooleanToggle(
                                Text.translatable("config.chestseparators.expel_invalid_items"),
                                GlobalChestConfig.instance.expelInvalidItems)
                        .setDefaultValue(false)
                        .setTooltip(Text.translatable("config.chestseparators.expel_invalid_items.tooltip"))
                        .setSaveConsumer(newValue -> GlobalChestConfig.instance.expelInvalidItems = newValue)
                        .build());
            }

            // --- CATEGORY 3: HOTKEYS ---
            ConfigCategory hotkeysCategory =
                    builder.getOrCreateCategory(Text.translatable("config.chestseparators.category.hotkeys"));

            BiConsumer<KeyBinding, String> addKeyEntry = (keyBinding, translationKey) -> {
                hotkeysCategory.addEntry(entryBuilder
                        .startKeyCodeField(
                                Text.translatable(translationKey), KeyBindingHelper.getBoundKeyOf(keyBinding))
                        .setDefaultValue(keyBinding.getDefaultKey())
                        .setTooltip(Text.translatable(translationKey + ".tooltip"))
                        .setKeySaveConsumer(newValue -> {
                            keyBinding.setBoundKey(newValue);
                            MinecraftClient.getInstance().options.write();
                            // Flush Minecraft's key-code cache so the new binding takes effect immediately.
                            KeyBinding.updateKeysByCode();
                        })
                        .build());
            };

            addKeyEntry.accept(ModKeyBindings.toggleMagnifierKey, "config.chestseparators.toggle_magnifier");
            addKeyEntry.accept(ModKeyBindings.autoDepositKey, "config.chestseparators.auto_deposit");
            addKeyEntry.accept(ModKeyBindings.openEditorKey, "config.chestseparators.toggle_preview_panel");
            addKeyEntry.accept(ModKeyBindings.showPanelModifierKey, "config.chestseparators.show_panel_modifier");
            addKeyEntry.accept(ModKeyBindings.pushKey, "config.chestseparators.push");
            addKeyEntry.accept(ModKeyBindings.pullKey, "config.chestseparators.pull");

            return builder.build();
        };
    }
}
