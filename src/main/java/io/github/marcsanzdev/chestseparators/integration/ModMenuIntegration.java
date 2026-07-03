package io.github.marcsanzdev.chestseparators.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.marcsanzdev.chestseparators.client.ui.ModKeyBindings;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("title.chestseparators.config"));

            builder.setSavingRunnable(GlobalChestConfig::saveConfig);
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // --- CATEGORY 1: CLIENT / VISUALS & DEFAULTS ---
            ConfigCategory clientCategory = builder.getOrCreateCategory(Text.translatable("config.chestseparators.category.client"));

            // --- NUEVO: BOTÓN DE MODO OSCURO ---
            clientCategory.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.chestseparators.dark_mode"), GlobalChestConfig.instance.darkMode)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.dark_mode.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.darkMode = newValue)
                    .build());

            clientCategory.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.chestseparators.show_left_panel"), GlobalChestConfig.instance.showLeftPanel)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.show_left_panel.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.showLeftPanel = newValue)
                    .build());

            clientCategory.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.chestseparators.enable_preview_animation"), GlobalChestConfig.instance.enablePreviewAnimation)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.enable_preview_animation.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.enablePreviewAnimation = newValue)
                    .build());

            clientCategory.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.chestseparators.show_edit_buttons"), GlobalChestConfig.instance.showEditButtons)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.show_edit_buttons.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.showEditButtons = newValue)
                    .build());

            clientCategory.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.chestseparators.show_deposit_button"), GlobalChestConfig.instance.showDepositButton)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.show_deposit_button.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.showDepositButton = newValue)
                    .build());

            // Puedes ponerlo justo debajo del botón de "show_deposit_button"
            clientCategory.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.chestseparators.close_on_click_outside"), GlobalChestConfig.instance.closeOnClickOutside)
                    .setDefaultValue(false)
                    .setTooltip(Text.translatable("config.chestseparators.close_on_click_outside.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.closeOnClickOutside = newValue)
                    .build());

            clientCategory.addEntry(entryBuilder.startIntSlider(Text.translatable("config.chestseparators.bg_transparency"), GlobalChestConfig.instance.bgTransparency, 0, 100)
                    .setDefaultValue(40)
                    .setTooltip(Text.translatable("config.chestseparators.bg_transparency.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.bgTransparency = newValue)
                    .build());

            clientCategory.addEntry(entryBuilder.startIntSlider(Text.translatable("config.chestseparators.line_transparency"), GlobalChestConfig.instance.lineTransparency, 0, 100)
                    .setDefaultValue(100)
                    .setTooltip(Text.translatable("config.chestseparators.line_transparency.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.lineTransparency = newValue)
                    .build());

            clientCategory.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.chestseparators.default_rule_manual"), GlobalChestConfig.instance.defaultRuleManual)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.default_rule_manual.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.defaultRuleManual = newValue)
                    .build());

            clientCategory.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.chestseparators.default_rule_shift"), GlobalChestConfig.instance.defaultRuleShift)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.default_rule_shift.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.defaultRuleShift = newValue)
                    .build());

            clientCategory.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.chestseparators.default_rule_hopper"), GlobalChestConfig.instance.defaultRuleHopper)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.chestseparators.default_rule_hopper.tooltip"))
                    .setSaveConsumer(newValue -> GlobalChestConfig.instance.defaultRuleHopper = newValue)
                    .build());


            // --- CATEGORY 2: SERVER / MECHANICS ---
            // ¡EL GRAN TRUCO! Solo añadimos esta pestaña si estamos en Singleplayer (jugando localmente)
            if (MinecraftClient.getInstance().isInSingleplayer()) {
                ConfigCategory serverCategory = builder.getOrCreateCategory(Text.translatable("config.chestseparators.category.server"));

                serverCategory.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.chestseparators.expel_invalid_items"), GlobalChestConfig.instance.expelInvalidItems)
                        .setDefaultValue(false)
                        .setTooltip(Text.translatable("config.chestseparators.expel_invalid_items.tooltip"))
                        .setSaveConsumer(newValue -> GlobalChestConfig.instance.expelInvalidItems = newValue)
                        .build());
            }

            // --- CATEGORY 3: HOTKEYS ---
            ConfigCategory hotkeysCategory = builder.getOrCreateCategory(Text.translatable("config.chestseparators.category.hotkeys"));

            // Función auxiliar interna (modificada para actualizar el caché de teclas)
            java.util.function.BiConsumer<net.minecraft.client.option.KeyBinding, String> addKeyEntry = (keyBinding, translationKey) -> {
                hotkeysCategory.addEntry(entryBuilder.startKeyCodeField(
                                Text.translatable(translationKey),
                                net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(keyBinding) // LEE LA TECLA ACTUAL
                        )
                        .setDefaultValue(keyBinding.getDefaultKey()) // TECLA DE RESET
                        .setKeySaveConsumer(newValue -> {
                            keyBinding.setBoundKey(newValue);
                            MinecraftClient.getInstance().options.write(); // Guarda en options.txt

                            // --- LA LÍNEA MÁGICA: Refresca el caché del teclado de Minecraft ---
                            net.minecraft.client.option.KeyBinding.updateKeysByCode();
                        })
                        .build());
            };

            // Registramos todas las teclas pasándole el KeyBinding y su texto correspondiente
            addKeyEntry.accept(ModKeyBindings.toggleButtonKey, "config.chestseparators.toggle_edit_buttons");
            addKeyEntry.accept(ModKeyBindings.openEditorKey, "config.chestseparators.toggle_preview_panel");
            addKeyEntry.accept(ModKeyBindings.showPanelModifierKey, "config.chestseparators.show_panel_modifier");
            addKeyEntry.accept(ModKeyBindings.depositFilterKey, "config.chestseparators.deposit_filter");
            addKeyEntry.accept(ModKeyBindings.depositAllKey, "config.chestseparators.deposit_all");

            return builder.build();
        };
    }
}