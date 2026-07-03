package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ActionIconButtonWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScreenEditFilter extends AbstractEditorScreen {

    // Lista para los botones del popup de guardado
    private final List<CustomWidget> unsavedPopupWidgets = new ArrayList<>();
    private final List<CustomWidget> expelPopupWidgets = new ArrayList<>(); // <--- NUEVO

    public ScreenEditFilter(ChestSeparatorsEditor editor) {
        super(editor);
    }

    @Override
    public void init() {
        super.init();
        // NOTA: Hemos movido el snapshot a la clase ScreenViewGroups
        session.isUnsavedPopupOpen = false;
        session.isExpelPopupOpen = false;

        unsavedPopupWidgets.clear();
        buildUnsavedPopupWidgets();

        expelPopupWidgets.clear(); // Limpiamos y construimos
        buildExpelPopupWidgets();
    }

    private void buildUnsavedPopupWidgets() {
        unsavedPopupWidgets.add(new ActionIconButtonWidget(layout.conflictPopupX + 10, layout.conflictPopupY + 45, 240, 16, "1. " + Text.translatable("button.chestseparators.save_and_exit").getString(), null, 0xFF2D852D, () -> {
            ChestConfigManager.getInstance().saveWhitelistSnapshot();
            editor.saveCurrentGroup();
            session.selectedSlots.clear();
            session.isUnsavedPopupOpen = false;
            editor.toggleState(EditorState.VIEW_GROUPS);
            editor.playClickSound(1.0f);
        }));

        unsavedPopupWidgets.add(new ActionIconButtonWidget(layout.conflictPopupX + 10, layout.conflictPopupY + 63, 240, 16, "2. " + Text.translatable("button.chestseparators.discard_changes").getString(), null, 0xFF852D2D, () -> {
            session.selectedSlots.clear();
            session.isUnsavedPopupOpen = false;
            editor.toggleState(EditorState.VIEW_GROUPS);
            editor.playClickSound(1.0f);
        }));

        unsavedPopupWidgets.add(new ActionIconButtonWidget(layout.conflictPopupX + 10, layout.conflictPopupY + 81, 240, 16, "3. " + Text.translatable("button.chestseparators.cancel").getString(), null, 0xFF444444, () -> {
            session.isUnsavedPopupOpen = false;
            editor.playClickSound(0.8f);
        }));
    }

    // Añade este método justo debajo de buildUnsavedPopupWidgets()
    private void buildExpelPopupWidgets() {
        // Lo bajamos de +45 a +63
        expelPopupWidgets.add(new ActionIconButtonWidget(layout.conflictPopupX + 10, layout.conflictPopupY + 63, 240, 16, "1. " + Text.translatable("button.chestseparators.accept_expel").getString(), null, 0xFF2D852D, () -> {
            ChestConfigManager.getInstance().saveWhitelistSnapshot();
            editor.saveCurrentGroup();
            session.selectedSlots.clear();
            session.isExpelPopupOpen = false;
            editor.toggleState(EditorState.VIEW_GROUPS);
            editor.playClickSound(1.0f);
        }));

        // Lo bajamos de +63 a +81
        expelPopupWidgets.add(new ActionIconButtonWidget(layout.conflictPopupX + 10, layout.conflictPopupY + 81, 240, 16, "2. " + Text.translatable("button.chestseparators.cancel").getString(), null, 0xFF444444, () -> {
            session.isExpelPopupOpen = false;
            editor.playClickSound(0.8f);
        }));
    }

    private boolean hasUnsavedChanges() {
        // 1. Comprobar si los toggles de reglas han cambiado
        if (session.originalRuleManual != session.ruleManual) return true;
        if (session.originalRuleShift != session.ruleShift) return true;
        if (session.originalRuleHopper != session.ruleHopper) return true;

        // 2. Comprobar si la lista de ítems permitidos es exactamente igual (sin importar el orden)
        Set<String> originalSet = new HashSet<>(session.originalItemsSnapshot);
        Set<String> currentSet = new HashSet<>(session.currentAllowedItems);

        return !originalSet.equals(currentSet);
    }

    @Override
    protected void buildWidgets() {
        int btnW = layout.btnW;
        int bH = layout.bH;
        int btnX = layout.rightX;
        int mainY = layout.mainY;

        // Group 1
        WideButtonWidget btnImport = new WideButtonWidget(btnX, mainY, btnW, bH, Text.translatable("button.chestseparators.import_extracted").getString(), ModTextures.ICON_IMPORT, () -> {
            ChestConfigManager.getInstance().saveWhitelistSnapshot();
            boolean isSearching = editor.searchBox != null && !editor.searchBox.getText().isEmpty();

            if (isSearching) {
                int addedCount = 0;
                for (Item item : session.filteredItems) {
                    String id = Registries.ITEM.getId(item).toString();
                    if (!session.currentAllowedItems.contains(id)) {
                        session.currentAllowedItems.add(id);
                        addedCount++;
                    }
                }
                if (addedCount > 0) editor.playClickSound(1.2f);
                else { editor.showStatus(Text.translatable("message.chestseparators.items_already_in_filter"), Formatting.RED); editor.playClickSound(0.8f); }
            } else {
                List<String> extracted = editor.extractItemsFromSelection();
                if (!extracted.isEmpty()) { session.currentAllowedItems.addAll(extracted); editor.playClickSound(1.2f); }
                else { editor.showStatus(Text.translatable("message.chestseparators.no_new_items_found"), Formatting.RED); editor.playClickSound(0.8f); }
            }
            session.forceStopPreview = true; session.isPreviewing = false; session.previewItems.clear();
            editor.updateWhitelistSearchCache();
        });
        btnImport.tooltipText = Text.translatable("tooltip.chestseparators.desc.import_extracted").getString();
        widgets.add(btnImport);

        WideButtonWidget btnAllowAll = new WideButtonWidget(btnX, mainY + 24, btnW, bH, Text.translatable("button.chestseparators.allow_all").getString(), ModTextures.ICON_BACKPACK_FULL, () -> {
            ChestConfigManager.getInstance().saveWhitelistSnapshot();
            session.currentAllowedItems.clear();
            for (Item item : session.allGameItems) session.currentAllowedItems.add(Registries.ITEM.getId(item).toString());
            editor.playClickSound(1.0f);
            session.forceStopPreview = true; session.isPreviewing = false; session.previewItems.clear();
            editor.updateWhitelistSearchCache();
        });
        btnAllowAll.tooltipText = Text.translatable("tooltip.chestseparators.desc.allow_all").getString();
        widgets.add(btnAllowAll);

        WideButtonWidget btnClearAll = new WideButtonWidget(btnX, mainY + 48, btnW, bH, Text.translatable("button.chestseparators.clear_all").getString(), ModTextures.ICON_BACKPACK_EMPTY, () -> {
            ChestConfigManager.getInstance().saveWhitelistSnapshot();
            session.currentAllowedItems.clear(); editor.playClickSound(1.0f);
            session.forceStopPreview = true; session.isPreviewing = false; session.previewItems.clear();
            editor.updateWhitelistSearchCache();
        });
        btnClearAll.tooltipText = Text.translatable("tooltip.chestseparators.desc.clear_all").getString();
        widgets.add(btnClearAll);

        // Group 2
        WideButtonWidget btnManual = new WideButtonWidget(btnX, mainY + 77, btnW, bH, Text.translatable("button.chestseparators.manual_insert").getString(), ModTextures.ICON_CURSOR, () -> {
            session.ruleManual = !session.ruleManual; editor.playClickSound(1.0f);
        });
        btnManual.tooltipText = session.ruleManual ? Text.translatable("tooltip.chestseparators.rule.manual_active").getString() : Text.translatable("tooltip.chestseparators.rule.manual_inactive").getString();
        widgets.add(btnManual);

        WideButtonWidget btnShift = new WideButtonWidget(btnX, mainY + 101, btnW, bH, Text.translatable("button.chestseparators.shift_insert").getString(), ModTextures.ICON_SHIFT, () -> {
            session.ruleShift = !session.ruleShift; editor.playClickSound(1.0f);
        });
        btnShift.tooltipText = session.ruleShift ? Text.translatable("tooltip.chestseparators.rule.shift_active").getString() : Text.translatable("tooltip.chestseparators.rule.shift_inactive").getString();
        widgets.add(btnShift);

        WideButtonWidget btnHopper = new WideButtonWidget(btnX, mainY + 125, btnW, bH, Text.translatable("button.chestseparators.hopper_insert").getString(), ModTextures.ICON_HOPPER, () -> {
            session.ruleHopper = !session.ruleHopper; editor.playClickSound(1.0f);
        });
        if (!session.isEnderChest) btnHopper.tooltipText = session.ruleHopper ? Text.translatable("tooltip.chestseparators.rule.hopper_active").getString() : Text.translatable("tooltip.chestseparators.rule.hopper_inactive").getString();
        widgets.add(btnHopper);

        // Group 3 (Actions)
        ActionIconButtonWidget btnDelete = new ActionIconButtonWidget(btnX, mainY + 154, btnW, bH, Text.translatable("button.chestseparators.delete").getString(), ModTextures.ICON_TRASH, 0xFF212121, () -> {
            ChestConfigManager.getInstance().saveWhitelistSnapshot();
            editor.deleteCurrentGroup();
            session.selectedSlots.clear();
            editor.toggleState(EditorState.VIEW_GROUPS);
            editor.playClickSound(0.8f);
        });
        btnDelete.tooltipText = Text.translatable("tooltip.chestseparators.desc.delete_group").getString();
        widgets.add(btnDelete);

        ActionIconButtonWidget btnSave = new ActionIconButtonWidget(btnX, mainY + 178, (btnW - 4) / 2, bH, Text.translatable("button.chestseparators.save").getString(), ModTextures.ICON_SAVE, 0xFF212121, () -> {
            // --- COMPROBACIÓN INTELIGENTE DE EXPULSIÓN ---
            boolean willExpel = false;

            // 1. ¿Está activa la expulsión global y están las 3 reglas de entrada bloqueadas?
            if (io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.expelInvalidItems &&
                    session.ruleManual && session.ruleShift && session.ruleHopper) {

                // 2. ¿Hay algún ítem en los slots seleccionados que NO esté en la nueva whitelist?
                for (int slotIdx : session.selectedSlots) {
                    if (slotIdx >= 0 && slotIdx < editor.accessor.getHandler().slots.size()) {
                        net.minecraft.item.ItemStack stack = editor.accessor.getHandler().getSlot(slotIdx).getStack();
                        if (!stack.isEmpty()) {
                            String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
                            if (!session.currentAllowedItems.contains(id)) {
                                willExpel = true; // ¡Encontramos un intruso!
                                break;
                            }
                        }
                    }
                }
            }

            if (willExpel) {
                // Si va a expulsar, abrimos el popup de advertencia
                session.isExpelPopupOpen = true;
                editor.playClickSound(1.0f);
            } else {
                // Si está todo limpio (o no están las 3 reglas activas), guardamos normal
                ChestConfigManager.getInstance().saveWhitelistSnapshot();
                editor.saveCurrentGroup();
                session.selectedSlots.clear();
                editor.toggleState(EditorState.VIEW_GROUPS);
                editor.playClickSound(1.0f);
            }
        });
        btnSave.tooltipText = Text.translatable("tooltip.chestseparators.desc.save").getString();
        widgets.add(btnSave);

        ActionIconButtonWidget btnCancel = new ActionIconButtonWidget(btnX + (btnW - 4) / 2 + 4, mainY + 178, (btnW - 4) / 2, bH, Text.translatable("button.chestseparators.cancel").getString(), ModTextures.ICON_CANCEL, 0xFF212121, () -> {
            session.selectedSlots.clear();
            editor.toggleState(EditorState.VIEW_GROUPS);
            editor.playClickSound(1.0f);
        });
        btnCancel.tooltipText = Text.translatable("tooltip.chestseparators.desc.cancel").getString();
        widgets.add(btnCancel);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0x77000000);

        // --- TRUCO MAGISTRAL ---
        // Engañamos a los métodos de fondo si CUALQUIERA de los popups está abierto
        boolean isAnyPopupOpen = session.isUnsavedPopupOpen || session.isExpelPopupOpen;
        int bgMouseX = isAnyPopupOpen ? -1 : mouseX;
        int bgMouseY = isAnyPopupOpen ? -1 : mouseY;

        updateWidgetStates(bgMouseX, bgMouseY);
        handlePreviewLogic(bgMouseX, bgMouseY);

        drawLeftPanel(context, bgMouseX, bgMouseY);
        drawMainPanel(context, bgMouseX, bgMouseY);

        super.render(context, bgMouseX, bgMouseY, delta);

        drawTagDropdown(context, bgMouseX, bgMouseY);

        // --- RENDERIZADO DE LOS POPUPS ---
        if (isAnyPopupOpen) {
            context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0xAA000000);

            // 1. CÁLCULO DE ALTURA DINÁMICA
            // El popup de expulsión solo tiene 2 botones, así que le quitamos unos 20 píxeles de altura
            int currentPopupH = layout.conflictPopupH;
            boolean isDark = GlobalChestConfig.instance.darkMode;

            context.fill(layout.conflictPopupX, layout.conflictPopupY, layout.conflictPopupX + layout.conflictPopupW, layout.conflictPopupY + currentPopupH, 0xFF212121);
            drawDarkBevel(context, layout.conflictPopupX, layout.conflictPopupY, layout.conflictPopupW, currentPopupH, false);

            MinecraftClient client = MinecraftClient.getInstance();
            int maxContentWidth = layout.conflictPopupW - 20; // Margen interno de 10px por lado

            // --- TÍTULO ---
            Text title = session.isExpelPopupOpen ? Text.translatable("gui.chestseparators.expel_items_title") : Text.translatable("gui.chestseparators.unsaved_changes_title");
            int titleColor = session.isExpelPopupOpen ? 0xFFFF5555 : 0xFFFFAA00;

            int titleWidth = client.textRenderer.getWidth(title);
            float titleScale = titleWidth > maxContentWidth ? (float) maxContentWidth / titleWidth : 1.0f;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(layout.conflictPopupX + layout.conflictPopupW / 2.0f, layout.conflictPopupY + 12 + (4 * (1 - titleScale)));
            context.getMatrices().scale(titleScale, titleScale);
            context.drawCenteredTextWithShadow(client.textRenderer, title, 0, 0, titleColor);
            context.getMatrices().popMatrix();

            // --- DESCRIPCIÓN (AHORA MÁS GRANDE Y EN 2-3 LÍNEAS) ---
            Text desc = session.isExpelPopupOpen ? Text.translatable("gui.chestseparators.expel_items_desc") : Text.translatable("gui.chestseparators.unsaved_changes_desc");

            if (session.isExpelPopupOpen) {
                // Para la expulsión, usamos escala 1.0 (grande) y forzamos el salto de línea
                int descY = layout.conflictPopupY + 26; // Bajamos un poco el inicio
                for (net.minecraft.text.OrderedText line : client.textRenderer.wrapLines(desc, maxContentWidth)) {
                    int lineWidth = client.textRenderer.getWidth(line);
                    // Centramos cada línea individualmente
                    context.drawText(client.textRenderer, line, layout.conflictPopupX + (layout.conflictPopupW - lineWidth) / 2, descY, (isDark ? 0xFFDDDDDD : 0xFF333333), false);
                    descY += client.textRenderer.fontHeight + 1; // Espaciado entre líneas
                }
            } else {
                // Mantenemos la lógica de auto-escalado solo para el popup de cambios sin guardar
                int descWidth = client.textRenderer.getWidth(desc);
                float descScale = descWidth > maxContentWidth ? (float) maxContentWidth / descWidth : 1.0f;
                int scaledDescWidth = (int) (descWidth * descScale);
                int descX = layout.conflictPopupX + (layout.conflictPopupW - scaledDescWidth) / 2;

                context.getMatrices().pushMatrix();
                context.getMatrices().translate(descX, layout.conflictPopupY + 30 + (4 * (1 - descScale)));
                context.getMatrices().scale(descScale, descScale);
                context.drawText(client.textRenderer, desc, 0, 0, (isDark ? 0xFFDDDDDD : 0xFF333333), false);
                context.getMatrices().popMatrix();
            }

            // Renderizamos los botones correspondientes
            List<CustomWidget> activeWidgets = session.isExpelPopupOpen ? expelPopupWidgets : unsavedPopupWidgets;
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget w : activeWidgets) {
                w.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void updateWidgetStates(int mouseX, int mouseY) {
        if (widgets.isEmpty()) return;

        boolean isSearching = editor.searchBox != null && !editor.searchBox.getText().isEmpty();
        WideButtonWidget importBtn = (WideButtonWidget) widgets.get(0);
        importBtn.icon = isSearching ? ModTextures.ICON_SEARCH : ModTextures.ICON_IMPORT;
        importBtn.tooltipText = isSearching ? Text.translatable("tooltip.chestseparators.desc.import_search").getString() : Text.translatable("tooltip.chestseparators.desc.import_extracted").getString();

        // ¡ASIGNACIÓN DIRECTA A TU WIDGET CUSTOM!
        String newText = isSearching ? Text.translatable("button.chestseparators.import_search").getString() : Text.translatable("button.chestseparators.import_extracted").getString();
        importBtn.label = newText;

        widgets.get(1).isActive = (session.currentAllowedItems.size() == session.allGameItems.size());
        widgets.get(2).isActive = session.currentAllowedItems.isEmpty();

        widgets.get(3).isActive = session.ruleManual;
        widgets.get(3).tooltipText = session.ruleManual ? Text.translatable("tooltip.chestseparators.rule.manual_active").getString() : Text.translatable("tooltip.chestseparators.rule.manual_inactive").getString();

        widgets.get(4).isActive = session.ruleShift;
        widgets.get(4).tooltipText = session.ruleShift ? Text.translatable("tooltip.chestseparators.rule.shift_active").getString() : Text.translatable("tooltip.chestseparators.rule.shift_inactive").getString();

        widgets.get(5).isActive = session.ruleHopper;
        widgets.get(5).isDisabled = session.isEnderChest;

        if (!session.isEnderChest) {
            widgets.get(5).tooltipText = session.ruleHopper ? Text.translatable("tooltip.chestseparators.rule.hopper_active").getString() : Text.translatable("tooltip.chestseparators.rule.hopper_inactive").getString();
        } else {
            widgets.get(5).tooltipText = null;
        }
    }

    private void handlePreviewLogic(int mouseX, int mouseY) {
        if (widgets.isEmpty()) return;
        boolean hoverImport = widgets.get(0).isHovering(mouseX, mouseY);
        boolean hoverAllow = widgets.get(1).isHovering(mouseX, mouseY);
        boolean hoverClear = widgets.get(2).isHovering(mouseX, mouseY);

        if (hoverImport) session.previewType = 1;
        else if (hoverAllow) session.previewType = 2;
        else if (hoverClear) session.previewType = 3;
        else session.previewType = 0;

        if (session.previewType == 0) session.forceStopPreview = false;

        if (session.previewType != 0 && !session.forceStopPreview) {
            if (!session.isPreviewing) {
                List<String> itemsToPreview = new ArrayList<>();
                boolean isSearching = editor.searchBox != null && !editor.searchBox.getText().isEmpty();

                if (session.previewType == 1) {
                    if (isSearching) {
                        for (Item item : session.filteredItems) itemsToPreview.add(Registries.ITEM.getId(item).toString());
                    } else {
                        itemsToPreview = editor.extractItemsFromSelection();
                    }
                } else if (session.previewType == 2) {
                    for (Item item : session.allGameItems) itemsToPreview.add(Registries.ITEM.getId(item).toString());
                } else if (session.previewType == 3) {
                    itemsToPreview.addAll(session.currentAllowedItems);
                }

                if (!itemsToPreview.isEmpty() || session.previewType == 3) {
                    session.isPreviewing = true;
                    session.previewItems = itemsToPreview;
                    session.previewScrollY = (session.previewType == 3) ? session.listScrollY : 0f;
                    session.previewScrollDirectionDown = true; session.userOverrodePreviewScroll = false;
                    long now = System.currentTimeMillis(); session.lastPreviewTime = now; session.previewStartTime = now;
                } else session.forceStopPreview = true;
            } else {
                long now = System.currentTimeMillis(); float dt = (now - session.lastPreviewTime) / 1000f;
                session.lastPreviewTime = now;
                if (!session.userOverrodePreviewScroll && (now - session.previewStartTime > 1000) && session.previewType != 3) {
                    float maxScroll = Math.max(0, session.previewItems.size() * 18 - (layout.listH - 48));
                    if (maxScroll > 0) {
                        float speed = (session.previewType == 2) ? 80f : 25f;
                        if (session.previewScrollDirectionDown) { session.previewScrollY += speed * dt; if (session.previewScrollY >= maxScroll) { session.previewScrollY = maxScroll; session.previewScrollDirectionDown = false; } }
                        else { session.previewScrollY -= speed * dt; if (session.previewScrollY <= 0) { session.previewScrollY = 0; session.previewScrollDirectionDown = true; } }
                    }
                }
            }
        } else session.isPreviewing = false;
    }

    private void drawLeftPanel(DrawContext context, int mouseX, int mouseY) {
        int listW = layout.listW; int listH = layout.listH;
        int listX = layout.listX; int listY = layout.listY;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        context.fill(listX, listY, listX + listW, listY + listH, isDark ? 0xFF212121 : 0xFFC6C6C6);
        drawDarkBevel(context, listX, listY, listW, listH, false);

        List<String> activeListToRender;
        float activeScrollY;

        if (session.isPreviewing) {
            activeListToRender = session.previewItems;
            activeScrollY = session.previewScrollY;
        } else {
            activeListToRender = session.visibleLeftListItems;
            activeScrollY = session.listScrollY;
        }

        int allowedCount = session.currentAllowedItems.size();
        int blockedCount = session.allGameItems.size() - allowedCount;

        context.getMatrices().pushMatrix();
        float scaleC = 0.8f;
        context.getMatrices().scale(scaleC, scaleC);

        if (session.isPreviewing) {
            if (session.previewType == 1) context.drawText(MinecraftClient.getInstance().textRenderer, Text.translatable("gui.chestseparators.new_detected", activeListToRender.size()), (int)((listX + 8) / scaleC), (int)((listY + 9) / scaleC), 0xFF55FF55, false);
            else if (session.previewType == 2) context.drawText(MinecraftClient.getInstance().textRenderer, Text.translatable("gui.chestseparators.allowing_all", activeListToRender.size()), (int)((listX + 8) / scaleC), (int)((listY + 9) / scaleC), 0xFF55FF55, false);
            else if (session.previewType == 3) context.drawText(MinecraftClient.getInstance().textRenderer, Text.translatable("gui.chestseparators.clearing", activeListToRender.size()), (int)((listX + 8) / scaleC), (int)((listY + 9) / scaleC), 0xFFFF5555, false);
        } else {
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.translatable("gui.chestseparators.allowed", allowedCount), (int)((listX + 8) / scaleC), (int)((listY + 5) / scaleC), 0xFF55FF55, false);
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.translatable("gui.chestseparators.blocked", blockedCount), (int)((listX + 8) / scaleC), (int)((listY + 13) / scaleC), 0xFFFF5555, false);
        }
        context.getMatrices().popMatrix();

        context.fill(listX + 6, listY + 22, listX + listW - 6, listY + 38, isDark ? 0xFF101010 : 0xFFE0E0E0);
        drawDarkBevel(context, listX + 6, listY + 22, listW - 12, 16, true);

        if (editor.whitelistSearchBox != null) {
            editor.whitelistSearchBox.setX(listX + 10);
            editor.whitelistSearchBox.setY(listY + 26);
            if (!session.isPreviewing) editor.whitelistSearchBox.render(context, mouseX, mouseY, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true));
            if (editor.whitelistSearchBox.getText().isEmpty() && !editor.whitelistSearchBox.isFocused() && !session.isPreviewing) {
                context.drawText(MinecraftClient.getInstance().textRenderer, Text.translatable("gui.chestseparators.search"), listX + 12, listY + 26, 0xFF666666, false);
            }
        }

        int listViewY = layout.listViewY;
        int listViewH = layout.listViewH;
        context.fill(listX + 6, listViewY, listX + listW - 6, listViewY + listViewH, isDark ? 0xFF101010 : 0xFFE0E0E0);
        drawDarkBevel(context, listX + 6, listViewY, listW - 12, listViewH, true);

        int totalListItems = activeListToRender.size();
        int totalListHeight = totalListItems * 18;
        float maxListScroll = Math.max(0, totalListHeight - listViewH);

        if (!session.isPreviewing) session.listScrollY = MathHelper.clamp(session.listScrollY, 0, maxListScroll);

        int lsbX = layout.lsbX; int lsbY = layout.lsbY; int lsbH = layout.lsbH;
        context.fill(lsbX, lsbY, lsbX + 6, lsbY + lsbH, isDark ? 0xFF000000 : 0xFFAAAAAA);
        int listThumbH = maxListScroll > 0 ? Math.max(10, (int)((listViewH / (float)Math.max(1, totalListHeight)) * lsbH)) : lsbH;
        int listThumbY = maxListScroll > 0 ? lsbY + (int)((activeScrollY / maxListScroll) * (lsbH - listThumbH)) : lsbY;
        context.fill(lsbX + 1, listThumbY + 1, lsbX + 5, listThumbY + listThumbH - 1, isDark ? 0xFF888888 : 0xFF666666);

        context.enableScissor(listX + 6, listViewY + 1, listX + listW - 14, listViewY + listViewH - 1);
        int startIndex = (int)(activeScrollY / 18);
        int visibleCount = (listViewH / 18) + 2;

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;

            String itemIdStr = activeListToRender.get(idx);
            Item item = Registries.ITEM.get(Identifier.tryParse(itemIdStr));
            int itemY = listViewY + (i * 18) - (int)(activeScrollY % 18) + 2;

            context.drawItem(item.getDefaultStack(), listX + 8, itemY);

            String name = item.getName().getString();
            context.getMatrices().pushMatrix();
            float scale = 0.75f;
            context.getMatrices().scale(scale, scale);
            int maxNameW = (int)((listW - 55) / scale);
            if (MinecraftClient.getInstance().textRenderer.getWidth(name) > maxNameW) name = MinecraftClient.getInstance().textRenderer.trimToWidth(name, maxNameW - 6) + "...";
            int textColor = session.isPreviewing ? ((session.previewType == 3) ? 0x66FFFFFF : 0xAAFFFFFF) : (isDark ? 0xFFFFFFFF : 0xFF222222);
            context.drawText(MinecraftClient.getInstance().textRenderer, name, (int)((listX + 26) / scale), (int)((itemY + 6) / scale), textColor, false);
            context.getMatrices().popMatrix();

            if (!session.isPreviewing || session.previewType == 3) {
                int crossX = listX + listW - 22; int crossY = itemY + 4;
                boolean hoverCross = !session.isPreviewing && editor.isHovering(crossX - 2, crossY - 2, 10, 10, mouseX, mouseY);
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, "x", crossX, crossY, session.isPreviewing ? 0xFFFF5555 : (hoverCross ? 0xFFFF5555 : (isDark ? 0xFFAAAAAA : 0xFF777777)));
            }
        }
        context.disableScissor();
    }

    private void drawMainPanel(DrawContext context, int mouseX, int mouseY) {
        int mainX = layout.mainX; int mainY = layout.mainY;
        int mainW = layout.mainW; int mainH = layout.mainH;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        // --- 1. TABS LOGIC SEPARATION ---
        EditorSessionData.CreativeTabInfo searchTab = null;
        int searchTabIndexGlobal = -1;
        List<EditorSessionData.CreativeTabInfo> normalTabs = new ArrayList<>();
        List<Integer> normalIndices = new ArrayList<>();

        for (int i = 0; i < session.availableTabs.size(); i++) {
            EditorSessionData.CreativeTabInfo tab = session.availableTabs.get(i);
            if (tab == null) continue;
            if (tab.isSearchTab) { searchTab = tab; searchTabIndexGlobal = i; }
            else { normalTabs.add(tab); normalIndices.add(i); }
        }

        // Pagination calculations
        int totalNormalTabs = normalTabs.size();
        int remainingTabs = Math.max(0, totalNormalTabs - 10);
        int normalTabsPerPage = 15;

        int maxPages = 1;
        if (remainingTabs > 0) {
            maxPages = 1 + (int) Math.ceil((double) remainingTabs / normalTabsPerPage);
        }

        if (session.currentTabPage >= maxPages) session.currentTabPage = maxPages - 1;

        // --- 2. DRAW TABS BEHIND PANEL ---
        for (int slot = 0; slot < 16; slot++) {
            EditorSessionData.CreativeTabInfo info = null;
            int actualGlobalIndex = -1;

            if (slot == 7 && searchTab != null) {
                info = searchTab;
                actualGlobalIndex = searchTabIndexGlobal;
            } else {
                int normalListIndex = -1;

                if (session.currentTabPage == 0) {
                    // Custom Layout for Page 0
                    if (slot >= 0 && slot <= 4) normalListIndex = slot;
                    else if (slot >= 8 && slot <= 12) normalListIndex = 5 + (slot - 8);
                } else {
                    // Packed Layout for Page 1+
                    int offsetOnPage = (slot > 7) ? slot - 1 : slot;
                    normalListIndex = 10 + ((session.currentTabPage - 1) * normalTabsPerPage) + offsetOnPage;
                }

                if (normalListIndex >= 0 && normalListIndex < normalTabs.size()) {
                    info = normalTabs.get(normalListIndex);
                    actualGlobalIndex = normalIndices.get(normalListIndex);
                }
            }

            if (info == null) continue;

            boolean isTop = slot < 8;
            int col = slot % 8;
            int tabW = 22; int tabH = 22;
            int tabX = mainX + 3 + (col * 23);
            int tabY = isTop ? mainY - tabH + 2 : mainY + mainH - 2;

            boolean isSelected = (session.currentCreativeTabIndex == actualGlobalIndex);
            boolean hover = editor.isHovering(tabX, tabY, tabW, tabH, mouseX, mouseY);

            int bgColor = isDark ? (isSelected ? 0xFF212121 : (hover ? 0xFF303030 : 0xFF151515)) : (isSelected ? 0xFFC6C6C6 : (hover ? 0xFFA0A0A0 : 0xFF8B8B8B));
            context.fill(tabX, tabY, tabX + tabW, tabY + tabH, bgColor);
            drawDarkBevel(context, tabX, tabY, tabW, tabH, isSelected);
            context.drawItem(info.icon, tabX + 3, tabY + (isTop ? 2 : 4));

            if (hover) context.drawTooltip(MinecraftClient.getInstance().textRenderer, info.name, mouseX, mouseY);
        }

        // --- 3. DRAW MAIN PANEL WINDOW ---
        context.fill(mainX, mainY, mainX + mainW, mainY + mainH, isDark ? 0xFF212121 : 0xFFC6C6C6);
        drawDarkBevel(context, mainX, mainY, mainW, mainH, false);

        // --- 4. ACTIVE TAB SEAMLESS PATCH ---
        int activeSlot = -1;
        if (searchTab != null && session.currentCreativeTabIndex == searchTabIndexGlobal) {
            activeSlot = 7;
        } else {
            int normalListIndex = normalIndices.indexOf(session.currentCreativeTabIndex);
            if (normalListIndex != -1) {
                if (session.currentTabPage == 0) {
                    if (normalListIndex >= 0 && normalListIndex <= 4) activeSlot = normalListIndex;
                    else if (normalListIndex >= 5 && normalListIndex <= 9) activeSlot = 8 + (normalListIndex - 5);
                } else {
                    int pageForIndex = 1 + (normalListIndex - 10) / normalTabsPerPage;
                    if (session.currentTabPage == pageForIndex) {
                        int offset = (normalListIndex - 10) % normalTabsPerPage;
                        activeSlot = (offset >= 7) ? offset + 1 : offset;
                    }
                }
            }
        }

        if (activeSlot != -1) {
            boolean isTopActive = activeSlot < 8;
            int activeCol = activeSlot % 8;
            int tX = mainX + 3 + (activeCol * 23);
            int patchColor = isDark ? 0xFF212121 : 0xFFC6C6C6;

            if (isTopActive) context.fill(tX + 1, mainY, tX + 21, mainY + 2, patchColor);
            else context.fill(tX + 1, mainY + mainH - 2, tX + 21, mainY + mainH, patchColor);
        }

        // --- 5. TITLE & CAROUSEL OR SEARCH BOX ---
        EditorSessionData.CreativeTabInfo activeTabInfo = session.availableTabs.get(session.currentCreativeTabIndex);
        String tabTitle = activeTabInfo != null ? activeTabInfo.name.getString() : Text.translatable("gui.chestseparators.edit_filter_rules").getString();

        if (activeTabInfo != null && activeTabInfo.isSearchTab) {
            int titleWidth = MinecraftClient.getInstance().textRenderer.getWidth(tabTitle);
            int maxTitleWidth = 60; // Límite para que el título no se coma la barra de búsqueda
            float scale = 1.0f;

            if (titleWidth > maxTitleWidth) {
                scale = (float) maxTitleWidth / titleWidth;
            }

            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float)(mainX + 12), (float)(mainY + 10 + (4 * (1 - scale))));
            context.getMatrices().scale(scale, scale);
            context.drawText(MinecraftClient.getInstance().textRenderer, tabTitle, 0, 0, (isDark ? 0xFF909090 : 0xFF555555), false);
            context.getMatrices().popMatrix();

            int boxX = mainX + 12 + (int)(titleWidth * scale) + 12;
            int boxY = mainY + 6;
            int boxW = (mainX + mainW - 12) - boxX;

            context.fill(boxX, boxY, boxX + boxW, boxY + 16, isDark ? 0xFF101010 : 0xFFE0E0E0);
            drawDarkBevel(context, boxX, boxY, boxW, 16, true);
            editor.searchBox.setX(boxX + 4);
            editor.searchBox.setY(boxY + 4);
            editor.searchBox.setWidth(boxW - 8);
            editor.searchBox.render(context, mouseX, mouseY, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true));

            if (editor.searchBox.getText().isEmpty() && !editor.searchBox.isFocused()) {
                context.drawText(MinecraftClient.getInstance().textRenderer, Text.translatable("gui.chestseparators.search"), boxX + 6, boxY + 4, 0xFF666666, false);
            }
        } else {
            // Calcular espacio disponible para el título
            int visibleDots = Math.min(maxPages, 3);
            int carouselWidth = (maxPages > 1) ? ((12 * 2) + (visibleDots * 8)) : 0;
            int maxTextWidth = mainW - 24 - carouselWidth - 5;

            int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(tabTitle);

            // Auto-escalado si el texto es demasiado largo
            if (textWidth > maxTextWidth) {
                float scale = (float) maxTextWidth / textWidth;
                context.getMatrices().pushMatrix();

                // En 1.21.11 las matrices son 2D. Solo pasamos X e Y.
                context.getMatrices().translate((float)(mainX + 12), (float)(mainY + 10 + (4 * (1 - scale))));
                context.getMatrices().scale(scale, scale);

                context.drawText(MinecraftClient.getInstance().textRenderer, tabTitle, 0, 0, (isDark ? 0xFF909090 : 0xFF555555), false);
                context.getMatrices().popMatrix();
            } else {
                context.drawText(MinecraftClient.getInstance().textRenderer, tabTitle, mainX + 12, mainY + 10, (isDark ? 0xFF909090 : 0xFF555555), false);
            }

            drawPaginationCarousel(context, mainX + mainW - 12, mainY + 10, maxPages, session.currentTabPage, mouseX, mouseY);
        }

        // --- 6. DRAW ITEM GRID ---
        int gridX = layout.gridX; int gridViewY = layout.gridViewY; int gridViewH = layout.gridViewH;
        int cols = layout.cols; int itemSize = layout.itemSize;

        context.fill(gridX - 2, gridViewY - 2, gridX + (cols * itemSize) + 2, gridViewY + gridViewH + 2, isDark ? 0xFF101010 : 0xFFE0E0E0);
        drawDarkBevel(context, gridX - 2, gridViewY - 2, (cols * itemSize) + 4, gridViewH + 4, true);

        int totalGridItems = session.filteredItems.size();
        int totalGridHeight = (int)Math.ceil((double)totalGridItems / cols) * itemSize;
        float maxGridScroll = Math.max(0, totalGridHeight - gridViewH);
        session.gridScrollY = MathHelper.clamp(session.gridScrollY, 0, maxGridScroll);

        int msbX = layout.msbX; int msbY = layout.msbY; int msbH = layout.msbH;
        context.fill(msbX, msbY, msbX + 6, msbY + msbH, isDark ? 0xFF000000 : 0xFFAAAAAA);
        int gridThumbH = maxGridScroll > 0 ? Math.max(10, (int)((gridViewH / (float)Math.max(1, totalGridHeight)) * msbH)) : msbH;
        int gridThumbY = maxGridScroll > 0 ? msbY + (int)((session.gridScrollY / maxGridScroll) * (msbH - gridThumbH)) : msbY;
        context.fill(msbX + 1, gridThumbY + 1, msbX + 5, gridThumbY + gridThumbH - 1, isDark ? 0xFF888888 : 0xFF666666);

        context.enableScissor(gridX, gridViewY, gridX + (cols * itemSize), gridViewY + gridViewH);
        int startGridRow = (int)(session.gridScrollY / itemSize);
        int visibleGridRows = (gridViewH / itemSize) + 2;

        for (int r = 0; r < visibleGridRows; r++) {
            int absoluteRow = startGridRow + r;
            int drawY = gridViewY + (r * itemSize) - (int)(session.gridScrollY % itemSize);

            for (int c = 0; c < cols; c++) {
                int itemIndex = (absoluteRow * cols) + c;
                if (itemIndex >= totalGridItems) break;

                int drawX = gridX + (c * itemSize);
                Item item = session.filteredItems.get(itemIndex);
                String itemId = Registries.ITEM.getId(item).toString();
                boolean isAllowed = session.currentAllowedItems.contains(itemId);
                boolean isPreviewed = session.isPreviewing && session.previewItems.contains(itemId);

                context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, isDark ? 0xFF2A2A2A : 0xFF8B8B8B);
                if (session.isPreviewing) {
                    if (session.previewType == 1) { if (isAllowed) context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x66FFFFFF); if (isPreviewed) context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x44FFFFFF); }
                    else if (session.previewType == 2) { if (isAllowed) context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x66FFFFFF); else context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x44FFFFFF); }
                    else if (session.previewType == 3) { if (isAllowed) context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x44000000); }
                } else if (isAllowed) {
                    context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x66FFFFFF);
                }

                context.drawItem(item.getDefaultStack(), drawX + 1, drawY + 1);

                if (session.activeDropdownTags.isEmpty() && editor.isHovering(drawX + 1, drawY + 1, 16, 16, mouseX, mouseY) && mouseY >= gridViewY && mouseY <= gridViewY + gridViewH) {
                    context.drawStrokedRectangle(drawX, drawY, 18, 18, 0xFFFFFFFF);
                    context.drawTooltip(MinecraftClient.getInstance().textRenderer, item.getName(), mouseX, mouseY);
                }
            }
        }
        context.disableScissor();
    }

    private void drawTagDropdown(DrawContext context, int mouseX, int mouseY) {
        if (!session.activeDropdownTags.isEmpty()) {
            boolean isDark = GlobalChestConfig.instance.darkMode;
            int dropW = 120; int dropH = session.activeDropdownTags.size() * 14 + 4;
            int dX = session.dropdownX; int dY = session.dropdownY;
            if (dX + dropW > layout.screenWidth) dX = layout.screenWidth - dropW;
            if (dY + dropH > layout.screenHeight) dY = layout.screenHeight - dropH;
            session.dropdownX = dX; session.dropdownY = dY;

            context.fill(dX, dY, dX + dropW, dY + dropH, isDark ? 0xFF151515 : 0xFFE0E0E0);
            drawDarkBevel(context, dX, dY, dropW, dropH, false);

            for (int i = 0; i < session.activeDropdownTags.size(); i++) {
                String tag = session.activeDropdownTags.get(i);
                int itemY = dY + 2 + (i * 14);
                boolean isHoveringTag = mouseX >= dX && mouseX <= dX + dropW && mouseY >= itemY && mouseY < itemY + 14;
                if (isHoveringTag) context.fill(dX + 2, itemY, dX + dropW - 2, itemY + 14, isDark ? 0x40FFFFFF : 0x40000000);

                context.getMatrices().pushMatrix();
                float scale = 0.85f;
                if (MinecraftClient.getInstance().textRenderer.getWidth("#" + tag) * scale > dropW - 12) scale = (float) (dropW - 12) / MinecraftClient.getInstance().textRenderer.getWidth("#" + tag);
                context.getMatrices().translate((float)(dX + 6), (itemY + (14 - 8 * scale) / 2));
                context.getMatrices().scale(scale, scale);

                int textColor = isHoveringTag ? (isDark ? 0xFF55FF55 : 0xFF00AA00) : (isDark ? 0xFFDDDDDD : 0xFF222222);
                context.drawText(MinecraftClient.getInstance().textRenderer, "#" + tag, 0, 0, textColor, false);
                context.getMatrices().popMatrix();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // --- 0. Interceptar clics si el popup de guardado está abierto ---
        if (session.isUnsavedPopupOpen) {
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget widget : unsavedPopupWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return true; // Bloquea los clics en el fondo
        }

        if (session.isExpelPopupOpen) {
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget widget : expelPopupWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return true; // Bloquea los clics en el fondo
        }

        // --- NUEVO: Actualizar el foco del scroll basado en dónde se hace clic ---
        if (mouseX >= layout.mainX && mouseX <= layout.mainX + layout.mainW && mouseY >= layout.mainY && mouseY <= layout.mainY + layout.mainH) {
            session.lastInteractedWasList = false; // Clic en el panel principal
        } else if (mouseX >= layout.listX && mouseX <= layout.listX + layout.listW && mouseY >= layout.listY && mouseY <= layout.listY + layout.listH) {
            session.lastInteractedWasList = true; // Clic en el panel izquierdo
        }

        // 1. Tag Dropdown logic
        if (!session.activeDropdownTags.isEmpty()) {
            if (button == 0) {
                int dropW = 120; int dropH = session.activeDropdownTags.size() * 14 + 4;
                if (mouseX >= session.dropdownX && mouseX <= session.dropdownX + dropW && mouseY >= session.dropdownY && mouseY <= session.dropdownY + dropH) {
                    int clickedIndex = (int)(mouseY - session.dropdownY - 2) / 14;
                    if (clickedIndex >= 0 && clickedIndex < session.activeDropdownTags.size()) {
                        if (editor.searchBox != null) { editor.searchBox.setText("#" + session.activeDropdownTags.get(clickedIndex)); editor.playClickSound(1.2f); }
                    }
                }
                session.activeDropdownTags.clear();
                return true;
            } else { session.activeDropdownTags.clear(); return true; }
        }

        // Widgets (Botones derechos)
        if (button == 0 && super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 && button != 2) return false; // Solo Left(0) y Middle(2) a partir de aquí

        // Remove from List (The 'X')
        if (button == 0 && mouseX >= layout.listX + 6 && mouseX <= layout.listX + layout.listW - 14 && mouseY >= layout.listViewY && mouseY <= layout.listViewY + layout.listViewH) {
            int clickedIdx = (int) (mouseY - layout.listViewY + session.listScrollY) / 18;

            // We use the cache directly
            List<String> visibleAllowed = session.visibleLeftListItems;

            if (clickedIdx >= 0 && clickedIdx < visibleAllowed.size()) {
                int itemY = layout.listViewY + (clickedIdx * 18) - (int) session.listScrollY;
                if (editor.isHovering(layout.listX + layout.listW - 24, itemY + 2, 12, 12, mouseX, mouseY)) {
                    session.currentAllowedItems.remove(visibleAllowed.get(clickedIdx));
                    editor.updateWhitelistSearchCache(); // Update cache after removing
                    editor.playClickSound(1.2f);
                    return true;
                }
            }
        }

        // Scrollbars
        if (button == 0) {
            if (editor.isHovering(layout.msbX, layout.msbY, 6, layout.msbH, mouseX, mouseY)) {
                session.isDraggingMainScroll = true; return true;
            }
            if (editor.isHovering(layout.lsbX, layout.lsbY, 6, layout.lsbH, mouseX, mouseY)) {
                session.isDraggingListScroll = true; return true;
            }
        }

        // Search Boxes Focus
        EditorSessionData.CreativeTabInfo currentTabInfo = session.availableTabs.get(session.currentCreativeTabIndex);
        if (button == 0 && editor.whitelistSearchBox != null && editor.isHovering(layout.listX + 6, layout.listY + 20, layout.listW - 12, 16, mouseX, mouseY)) {
            editor.whitelistSearchBox.setFocused(true); if (editor.searchBox != null) editor.searchBox.setFocused(false); editor.playClickSound(1.0f); return true;
        } else if (button == 0 && editor.searchBox != null && currentTabInfo != null && currentTabInfo.isSearchTab && editor.searchBox.isMouseOver(mouseX, mouseY)) {
            editor.searchBox.setFocused(true); if (editor.whitelistSearchBox != null) editor.whitelistSearchBox.setFocused(false); editor.playClickSound(1.0f); return true;
        } else if (button == 0) {
            if (editor.searchBox != null) editor.searchBox.setFocused(false);
            if (editor.whitelistSearchBox != null) editor.whitelistSearchBox.setFocused(false);
        }

        // Tabs and Pagination
        if (button == 0) {
            EditorSessionData.CreativeTabInfo searchTab = null;
            int searchTabIndexGlobal = -1;
            List<Integer> normalIndices = new ArrayList<>();

            for (int i = 0; i < session.availableTabs.size(); i++) {
                EditorSessionData.CreativeTabInfo tab = session.availableTabs.get(i);
                if (tab == null) continue;
                if (tab.isSearchTab) { searchTab = tab; searchTabIndexGlobal = i; }
                else { normalIndices.add(i); }
            }

            int totalNormalTabs = normalIndices.size();
            int remainingTabs = Math.max(0, totalNormalTabs - 10);
            int normalTabsPerPage = 15;

            int maxPages = 1;
            if (remainingTabs > 0) {
                maxPages = 1 + (int) Math.ceil((double) remainingTabs / normalTabsPerPage);
            }

            // Click en Pestañas (con soporte de layout híbrido)
            for (int slot = 0; slot < 16; slot++) {
                int actualGlobalIndex = -1;

                if (slot == 7 && searchTab != null) {
                    actualGlobalIndex = searchTabIndexGlobal;
                } else {
                    int normalListIndex = -1;
                    if (session.currentTabPage == 0) {
                        if (slot >= 0 && slot <= 4) normalListIndex = slot;
                        else if (slot >= 8 && slot <= 12) normalListIndex = 5 + (slot - 8);
                    } else {
                        int offsetOnPage = (slot > 7) ? slot - 1 : slot;
                        normalListIndex = 10 + ((session.currentTabPage - 1) * normalTabsPerPage) + offsetOnPage;
                    }

                    if (normalListIndex >= 0 && normalListIndex < normalIndices.size()) {
                        actualGlobalIndex = normalIndices.get(normalListIndex);
                    }
                }

                if (actualGlobalIndex == -1) continue;

                int tabX = layout.mainX + 3 + ((slot % 8) * 23);
                int tabY = (slot < 8) ? layout.mainY - 20 : layout.mainY + layout.mainH - 2;

                if (editor.isHovering(tabX, tabY, 22, 22, mouseX, mouseY)) {
                    editor.switchCreativeTab(actualGlobalIndex);
                    editor.playClickSound(1.0f);
                    return true;
                }
            }

            // Click en el Carrusel
            EditorSessionData.CreativeTabInfo activeTabInfo = session.availableTabs.get(session.currentCreativeTabIndex);
            if (activeTabInfo == null || !activeTabInfo.isSearchTab) {
                if (maxPages > 1) {
                    int visibleDots = Math.min(maxPages, 3);
                    int startPage = Math.max(0, Math.min(session.currentTabPage - 1, maxPages - visibleDots));

                    int dotSpacing = 8;
                    int arrowWidth = 12;
                    int totalWidth = (arrowWidth * 2) + (visibleDots * dotSpacing);
                    int startX = (layout.mainX + layout.mainW - 12) - totalWidth;
                    int currentX = startX;
                    int y = layout.mainY + 10;

                    // Click Flecha Izquierda
                    if (editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY)) {
                        session.currentTabPage = (session.currentTabPage - 1 + maxPages) % maxPages;
                        int newIndex = (session.currentTabPage == 0) ? 0 : 10 + ((session.currentTabPage - 1) * 15);
                        if (newIndex < normalIndices.size()) editor.switchCreativeTab(normalIndices.get(newIndex));
                        editor.playClickSound(0.8f);
                        return true;
                    }
                    currentX += arrowWidth;

                    // Click Puntos de Página
                    for (int i = 0; i < visibleDots; i++) {
                        if (editor.isHovering(currentX, y - 2, dotSpacing, 12, mouseX, mouseY)) {
                            session.currentTabPage = startPage + i;
                            int newIndex = (session.currentTabPage == 0) ? 0 : 10 + ((session.currentTabPage - 1) * 15);
                            if (newIndex < normalIndices.size()) editor.switchCreativeTab(normalIndices.get(newIndex));
                            editor.playClickSound(1.0f);
                            return true;
                        }
                        currentX += dotSpacing;
                    }

                    // Click Flecha Derecha
                    if (editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY)) {
                        session.currentTabPage = (session.currentTabPage + 1) % maxPages;
                        int newIndex = (session.currentTabPage == 0) ? 0 : 10 + ((session.currentTabPage - 1) * 15);
                        if (newIndex < normalIndices.size()) editor.switchCreativeTab(normalIndices.get(newIndex));
                        editor.playClickSound(0.8f);
                        return true;
                    }
                }
            }
        }

        // Grid Click
        if (mouseX >= layout.gridX && mouseX < layout.gridX + (layout.cols * layout.itemSize) && mouseY >= layout.gridViewY && mouseY < layout.gridViewY + layout.gridViewH) {
            int c = (int)(mouseX - layout.gridX) / layout.itemSize;
            int r = (int)(mouseY - layout.gridViewY + session.gridScrollY) / layout.itemSize;
            int itemIndex = (r * layout.cols) + c;
            if (itemIndex >= 0 && itemIndex < session.filteredItems.size()) {
                Item item = session.filteredItems.get(itemIndex);
                if (button == 2) {
                    List<String> tags = new ArrayList<>();
                    item.getRegistryEntry().streamTags().forEach(tagKey -> {
                        String t = tagKey.id().getPath();
                        if (!t.startsWith("mineable/") && !t.startsWith("needs_") && !t.contains("tools") && !t.startsWith("beacon_") && !t.equals("completes_find_tree_tutorial") && !tags.contains(t)) tags.add(t);
                    });
                    if (!tags.isEmpty()) {
                        if (editor.searchBox != null) editor.searchBox.setText("");
                        for (int i = 0; i < session.availableTabs.size(); i++) {
                            if (session.availableTabs.get(i) != null && session.availableTabs.get(i).isSearchTab) {
                                editor.switchCreativeTab(i);
                                int newIndex = session.filteredItems.indexOf(item);
                                if (newIndex != -1) session.gridScrollY = MathHelper.clamp(((float) newIndex / 9 - 4) * 18f, 0, Math.max(0, (int)Math.ceil(session.filteredItems.size() / 9.0) * 18 - (9 * 18)));
                                break;
                            }
                        }
                        session.activeDropdownTags = tags; session.dropdownX = layout.mainX + layout.mainW - 132; session.dropdownY = layout.mainY + 24; editor.playClickSound(1.0f);
                    } else editor.showStatus(Text.translatable("message.chestseparators.no_valid_groups"), Formatting.GRAY);
                } else {
                    String id = Registries.ITEM.getId(item).toString();
                    if (session.currentAllowedItems.contains(id)) { session.currentAllowedItems.remove(id); editor.playClickSound(0.8f); }
                    else { session.currentAllowedItems.add(id); editor.playClickSound(1.0f); }

                    editor.updateWhitelistSearchCache(); // Update cache after modifying
                }
                return true;
            }
        }

        // --- CLICK OUTSIDE TO CLOSE ---
        if (button == 0 && GlobalChestConfig.instance.closeOnClickOutside) {
            boolean isInsideMain = mouseX >= layout.mainX && mouseX <= layout.mainX + layout.mainW && mouseY >= layout.mainY && mouseY <= layout.mainY + layout.mainH;
            boolean isInsideLeft = mouseX >= layout.listX && mouseX <= layout.listX + layout.listW && mouseY >= layout.listY && mouseY <= layout.listY + layout.listH;
            boolean isInsideRight = mouseX >= layout.rightX && mouseX <= layout.rightX + layout.btnW && mouseY >= layout.mainY && mouseY <= layout.mainY + (layout.bH * 8);

            if (!isInsideMain && !isInsideLeft && !isInsideRight && session.activeDropdownTags.isEmpty()) {
                if (hasUnsavedChanges()) {
                    session.isUnsavedPopupOpen = true;
                } else {
                    session.selectedSlots.clear();
                    editor.toggleState(EditorState.VIEW_GROUPS);
                }
                editor.playClickSound(1.0f);
                return true;
            }
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float scrollSpeed = 18f;

        // Si estamos previsualizando un filtro, la rueda siempre mueve la previsualización
        if (session.isPreviewing) {
            float maxScroll = Math.max(0, session.previewItems.size() * layout.itemSize - layout.listViewH);
            session.previewScrollY = net.minecraft.util.math.MathHelper.clamp(session.previewScrollY - (float)(verticalAmount * scrollSpeed), 0, maxScroll);
            session.userOverrodePreviewScroll = true;
            return true;
        }

        if (mouseX >= layout.mainX) {
            session.lastInteractedWasList = false;
            float maxGridScroll = Math.max(0, (int)Math.ceil((double)session.filteredItems.size() / layout.cols) * layout.itemSize - layout.gridViewH);
            session.gridScrollY = net.minecraft.util.math.MathHelper.clamp(session.gridScrollY - (float)(verticalAmount * scrollSpeed), 0, maxGridScroll);
        } else {
            session.lastInteractedWasList = true;
            float maxListScroll = Math.max(0, session.visibleLeftListItems.size() * layout.itemSize - layout.listViewH);
            session.listScrollY = net.minecraft.util.math.MathHelper.clamp(session.listScrollY - (float)(verticalAmount * scrollSpeed), 0, maxListScroll);
        }
        return true;
    }

    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        boolean isSearchFocused = (editor.searchBox != null && editor.searchBox.isFocused());
        boolean isWhitelistSearchFocused = (editor.whitelistSearchBox != null && editor.whitelistSearchBox.isFocused());

        if (isSearchFocused) {
            if (editor.searchBox.keyPressed(input)) return true;
            if (input.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        }
        if (isWhitelistSearchFocused) {
            if (editor.whitelistSearchBox.keyPressed(input)) return true;
            if (input.key() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return true;
        }

        // Scroll con flechas
        if (!isSearchFocused && !isWhitelistSearchFocused) {
            if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                float scrollAmount = 18f * 2;
                int direction = (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) ? -1 : 1;

                if (session.lastInteractedWasList) {
                    float maxListScroll = Math.max(0, session.visibleLeftListItems.size() * 18 - (200 - 48));
                    session.listScrollY = net.minecraft.util.math.MathHelper.clamp(session.listScrollY + (direction * scrollAmount), 0, maxListScroll);
                } else {
                    float maxGridScroll = Math.max(0, (int)Math.ceil(session.filteredItems.size() / 9.0) * 18 - (9 * 18));
                    session.gridScrollY = net.minecraft.util.math.MathHelper.clamp(session.gridScrollY + (direction * scrollAmount), 0, maxGridScroll);
                }
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (editor.searchBox != null && editor.searchBox.isFocused()) return editor.searchBox.charTyped(input);
        if (editor.whitelistSearchBox != null && editor.whitelistSearchBox.isFocused()) return editor.whitelistSearchBox.charTyped(input);
        return false;
    }

    private void drawPaginationCarousel(DrawContext context, int rightBoundX, int y, int maxPages, int currentPage, int mouseX, int mouseY) {
        if (maxPages <= 1) return;

        // --- LÓGICA DE VENTANA DESLIZANTE (MÁXIMO 3 PUNTOS) ---
        int visibleDots = Math.min(maxPages, 3);
        // Calcula qué página representa el primer punto visible
        int startPage = Math.max(0, Math.min(currentPage - 1, maxPages - visibleDots));

        int dotSpacing = 8;
        int arrowWidth = 12;
        int totalWidth = (arrowWidth * 2) + (visibleDots * dotSpacing);
        int startX = rightBoundX - totalWidth;

        int currentX = startX;

        // Flecha Izquierda <
        boolean hoverLeft = editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY);
        context.drawText(MinecraftClient.getInstance().textRenderer, "<", currentX + 2, y, hoverLeft ? 0xFF55FF55 : 0xFF888888, false);
        currentX += arrowWidth;

        // Puntos de página (Solo dibuja los 3 calculados)
        for (int i = 0; i < visibleDots; i++) {
            int pageIndex = startPage + i;
            boolean isActive = (pageIndex == currentPage);
            boolean hoverDot = editor.isHovering(currentX, y - 2, dotSpacing, 12, mouseX, mouseY);

            int color = isActive ? 0xFFFFFFFF : (hoverDot ? 0xFFAAAAAA : 0xFF555555);

            if (isActive) {
                context.fill(currentX + 2, y + 2, currentX + 6, y + 6, color);
            } else {
                context.fill(currentX + 3, y + 3, currentX + 5, y + 5, color);
            }
            currentX += dotSpacing;
        }

        // Flecha Derecha >
        boolean hoverRight = editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY);
        context.drawText(MinecraftClient.getInstance().textRenderer, ">", currentX + 2, y, hoverRight ? 0xFF55FF55 : 0xFF888888, false);
    }
}