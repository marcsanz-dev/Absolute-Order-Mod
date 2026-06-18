package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.client.ui.UiColors;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ActionIconButtonWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

public class ScreenEditFilter extends AbstractEditorScreen {

    private final List<CustomWidget> unsavedPopupWidgets = new ArrayList<>();
    private final List<CustomWidget> expelPopupWidgets = new ArrayList<>();

    private final FilterPanelRenderer panelRenderer;

    public ScreenEditFilter(ChestSeparatorsEditor editor) {
        super(editor);
        this.panelRenderer = new FilterPanelRenderer(this);
    }

    @Override
    public void init() {
        super.init();
        session.isUnsavedPopupOpen = false;
        session.isExpelPopupOpen = false;

        unsavedPopupWidgets.clear();
        buildUnsavedPopupWidgets();

        expelPopupWidgets.clear();
        buildExpelPopupWidgets();
    }

    private void buildUnsavedPopupWidgets() {
        unsavedPopupWidgets.add(new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 45,
                240,
                16,
                "1. "
                        + Text.translatable("button.chestseparators.save_and_exit")
                                .getString(),
                null,
                0xFF2D852D,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    editor.saveCurrentGroup();
                    session.selectedSlots.clear();
                    session.isUnsavedPopupOpen = false;
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(1.0f);
                }));

        unsavedPopupWidgets.add(new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 63,
                240,
                16,
                "2. "
                        + Text.translatable("button.chestseparators.discard_changes")
                                .getString(),
                null,
                0xFF852D2D,
                () -> {
                    session.selectedSlots.clear();
                    session.isUnsavedPopupOpen = false;
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(1.0f);
                }));

        unsavedPopupWidgets.add(new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 81,
                240,
                16,
                "3. " + Text.translatable("button.chestseparators.cancel").getString(),
                null,
                0xFF444444,
                () -> {
                    session.isUnsavedPopupOpen = false;
                    editor.playClickSound(0.8f);
                }));
    }

    private void buildExpelPopupWidgets() {
        expelPopupWidgets.add(new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 63,
                240,
                16,
                "1. " + Text.translatable("button.chestseparators.accept_expel").getString(),
                null,
                0xFF2D852D,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    editor.saveCurrentGroup();
                    session.selectedSlots.clear();
                    session.isExpelPopupOpen = false;
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(1.0f);
                }));

        expelPopupWidgets.add(new ActionIconButtonWidget(
                layout.conflictPopupX + 10,
                layout.conflictPopupY + 81,
                240,
                16,
                "2. " + Text.translatable("button.chestseparators.cancel").getString(),
                null,
                0xFF444444,
                () -> {
                    session.isExpelPopupOpen = false;
                    editor.playClickSound(0.8f);
                }));
    }

    private boolean hasUnsavedChanges() {
        if (session.originalRuleManual != session.ruleManual) return true;
        if (session.originalRuleShift != session.ruleShift) return true;
        if (session.originalRuleHopper != session.ruleHopper) return true;

        // Compare sets so item order differences do not trigger a false positive.
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
        WideButtonWidget btnImport = new WideButtonWidget(
                btnX,
                mainY,
                btnW,
                bH,
                Text.translatable("button.chestseparators.import_extracted").getString(),
                ModTextures.ICON_IMPORT,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    boolean isSearching = editor.searchBox != null
                            && !editor.searchBox.getText().isEmpty();

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
                        else {
                            editor.showStatus(
                                    Text.translatable("message.chestseparators.items_already_in_filter"),
                                    Formatting.RED);
                            editor.playClickSound(0.8f);
                        }
                    } else {
                        List<String> extracted = editor.extractItemsFromSelection();
                        if (!extracted.isEmpty()) {
                            session.currentAllowedItems.addAll(extracted);
                            editor.playClickSound(1.2f);
                        } else {
                            editor.showStatus(
                                    Text.translatable("message.chestseparators.no_new_items_found"), Formatting.RED);
                            editor.playClickSound(0.8f);
                        }
                    }
                    session.forceStopPreview = true;
                    session.isPreviewing = false;
                    session.previewItems.clear();
                    editor.updateWhitelistSearchCache();
                });
        btnImport.tooltipText = Text.translatable("tooltip.chestseparators.desc.import_extracted")
                .getString();
        widgets.add(btnImport);

        WideButtonWidget btnAllowAll = new WideButtonWidget(
                btnX,
                mainY + 24,
                btnW,
                bH,
                Text.translatable("button.chestseparators.allow_all").getString(),
                ModTextures.ICON_BACKPACK_FULL,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    session.currentAllowedItems.clear();
                    for (Item item : session.allGameItems)
                        session.currentAllowedItems.add(
                                Registries.ITEM.getId(item).toString());
                    editor.playClickSound(1.0f);
                    session.forceStopPreview = true;
                    session.isPreviewing = false;
                    session.previewItems.clear();
                    editor.updateWhitelistSearchCache();
                });
        btnAllowAll.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.allow_all").getString();
        widgets.add(btnAllowAll);

        WideButtonWidget btnClearAll = new WideButtonWidget(
                btnX,
                mainY + 48,
                btnW,
                bH,
                Text.translatable("button.chestseparators.clear_all").getString(),
                ModTextures.ICON_BACKPACK_EMPTY,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    session.currentAllowedItems.clear();
                    editor.playClickSound(1.0f);
                    session.forceStopPreview = true;
                    session.isPreviewing = false;
                    session.previewItems.clear();
                    editor.updateWhitelistSearchCache();
                });
        btnClearAll.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.clear_all").getString();
        widgets.add(btnClearAll);

        // Group 2
        WideButtonWidget btnManual = new WideButtonWidget(
                btnX,
                mainY + 77,
                btnW,
                bH,
                Text.translatable("button.chestseparators.manual_insert").getString(),
                ModTextures.ICON_CURSOR,
                () -> {
                    session.ruleManual = !session.ruleManual;
                    editor.playClickSound(1.0f);
                });
        btnManual.tooltipText = session.ruleManual
                ? Text.translatable("tooltip.chestseparators.rule.manual_active")
                        .getString()
                : Text.translatable("tooltip.chestseparators.rule.manual_inactive")
                        .getString();
        widgets.add(btnManual);

        WideButtonWidget btnShift = new WideButtonWidget(
                btnX,
                mainY + 101,
                btnW,
                bH,
                Text.translatable("button.chestseparators.shift_insert").getString(),
                ModTextures.ICON_SHIFT,
                () -> {
                    session.ruleShift = !session.ruleShift;
                    editor.playClickSound(1.0f);
                });
        btnShift.tooltipText = session.ruleShift
                ? Text.translatable("tooltip.chestseparators.rule.shift_active").getString()
                : Text.translatable("tooltip.chestseparators.rule.shift_inactive")
                        .getString();
        widgets.add(btnShift);

        WideButtonWidget btnHopper = new WideButtonWidget(
                btnX,
                mainY + 125,
                btnW,
                bH,
                Text.translatable("button.chestseparators.hopper_insert").getString(),
                ModTextures.ICON_HOPPER,
                () -> {
                    session.ruleHopper = !session.ruleHopper;
                    editor.playClickSound(1.0f);
                });
        if (!session.isEnderChest && !session.isEntityChest)
            btnHopper.tooltipText = session.ruleHopper
                    ? Text.translatable("tooltip.chestseparators.rule.hopper_active")
                            .getString()
                    : Text.translatable("tooltip.chestseparators.rule.hopper_inactive")
                            .getString();
        widgets.add(btnHopper);

        // Group 3 (Actions)
        ActionIconButtonWidget btnDelete = new ActionIconButtonWidget(
                btnX,
                mainY + 154,
                btnW,
                bH,
                Text.translatable("button.chestseparators.delete").getString(),
                ModTextures.ICON_TRASH,
                UiColors.SURFACE_DARK,
                () -> {
                    ChestConfigManager.getInstance().saveWhitelistSnapshot();
                    editor.deleteCurrentGroup();
                    session.selectedSlots.clear();
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(0.8f);
                });
        btnDelete.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.delete_group").getString();
        widgets.add(btnDelete);

        ActionIconButtonWidget btnSave = new ActionIconButtonWidget(
                btnX,
                mainY + 178,
                (btnW - 4) / 2,
                bH,
                Text.translatable("button.chestseparators.save").getString(),
                ModTextures.ICON_SAVE,
                UiColors.SURFACE_DARK,
                () -> {
                    // Check whether saving will cause existing items to be expelled.
                    boolean willExpel = false;

                    // Expulsion can only occur when all three insertion rules are blocked.
                    if (io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.expelInvalidItems
                            && session.ruleManual
                            && session.ruleShift
                            && session.ruleHopper) {

                        for (int slotIdx : session.selectedSlots) {
                            if (slotIdx >= 0
                                    && slotIdx
                                            < editor.accessor.getHandler().slots.size()) {
                                net.minecraft.item.ItemStack stack = editor.accessor
                                        .getHandler()
                                        .getSlot(slotIdx)
                                        .getStack();
                                if (!stack.isEmpty()) {
                                    String id = net.minecraft.registry.Registries.ITEM
                                            .getId(stack.getItem())
                                            .toString();
                                    if (!session.currentAllowedItems.contains(id)) {
                                        willExpel = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if (willExpel) {
                        session.isExpelPopupOpen = true;
                        editor.playClickSound(1.0f);
                    } else {
                        ChestConfigManager.getInstance().saveWhitelistSnapshot();
                        editor.saveCurrentGroup();
                        session.selectedSlots.clear();
                        editor.toggleState(EditorState.VIEW_GROUPS);
                        editor.playClickSound(1.0f);
                    }
                });
        btnSave.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.save").getString();
        widgets.add(btnSave);

        ActionIconButtonWidget btnCancel = new ActionIconButtonWidget(
                btnX + (btnW - 4) / 2 + 4,
                mainY + 178,
                (btnW - 4) / 2,
                bH,
                Text.translatable("button.chestseparators.cancel").getString(),
                ModTextures.ICON_CANCEL,
                UiColors.SURFACE_DARK,
                () -> {
                    session.selectedSlots.clear();
                    editor.toggleState(EditorState.VIEW_GROUPS);
                    editor.playClickSound(1.0f);
                });
        btnCancel.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.cancel").getString();
        widgets.add(btnCancel);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0x77000000);

        // When any popup is active, pass (-1, -1) as mouse coordinates so background
        // methods do not react to hover or draw tooltips.
        boolean isAnyPopupOpen = session.isUnsavedPopupOpen || session.isExpelPopupOpen;
        int bgMouseX = isAnyPopupOpen ? -1 : mouseX;
        int bgMouseY = isAnyPopupOpen ? -1 : mouseY;

        updateWidgetStates(bgMouseX, bgMouseY);
        handlePreviewLogic(bgMouseX, bgMouseY);

        panelRenderer.drawLeftPanel(context, bgMouseX, bgMouseY);
        panelRenderer.drawMainPanel(context, bgMouseX, bgMouseY);

        super.render(context, bgMouseX, bgMouseY, delta);

        panelRenderer.drawTagDropdown(context, bgMouseX, bgMouseY);

        // Popup rendering.
        if (isAnyPopupOpen) {
            context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0xAA000000);

            int currentPopupH = layout.conflictPopupH;
            boolean isDark = GlobalChestConfig.instance.darkMode;

            context.fill(
                    layout.conflictPopupX,
                    layout.conflictPopupY,
                    layout.conflictPopupX + layout.conflictPopupW,
                    layout.conflictPopupY + currentPopupH,
                    UiColors.SURFACE_DARK);
            drawDarkBevel(
                    context, layout.conflictPopupX, layout.conflictPopupY, layout.conflictPopupW, currentPopupH, false);

            MinecraftClient client = MinecraftClient.getInstance();
            int maxContentWidth = layout.conflictPopupW - 20; // Margen interno de 10px por lado

            Text title = session.isExpelPopupOpen
                    ? Text.translatable("gui.chestseparators.expel_items_title")
                    : Text.translatable("gui.chestseparators.unsaved_changes_title");
            int titleColor = session.isExpelPopupOpen ? 0xFFFF5555 : 0xFFFFAA00;

            int titleWidth = client.textRenderer.getWidth(title);
            float titleScale = titleWidth > maxContentWidth ? (float) maxContentWidth / titleWidth : 1.0f;

            context.getMatrices().pushMatrix();
            context.getMatrices()
                    .translate(
                            layout.conflictPopupX + layout.conflictPopupW / 2.0f,
                            layout.conflictPopupY + 12 + (4 * (1 - titleScale)));
            context.getMatrices().scale(titleScale, titleScale);
            context.drawCenteredTextWithShadow(client.textRenderer, title, 0, 0, titleColor);
            context.getMatrices().popMatrix();

            Text desc = session.isExpelPopupOpen
                    ? Text.translatable("gui.chestseparators.expel_items_desc")
                    : Text.translatable("gui.chestseparators.unsaved_changes_desc");

            if (session.isExpelPopupOpen) {
                int descY = layout.conflictPopupY + 26;
                for (net.minecraft.text.OrderedText line : client.textRenderer.wrapLines(desc, maxContentWidth)) {
                    int lineWidth = client.textRenderer.getWidth(line);
                    context.drawText(
                            client.textRenderer,
                            line,
                            layout.conflictPopupX + (layout.conflictPopupW - lineWidth) / 2,
                            descY,
                            (isDark ? 0xFFDDDDDD : 0xFF333333),
                            false);
                    descY += client.textRenderer.fontHeight + 1;
                }
            } else {
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

        boolean isSearching =
                editor.searchBox != null && !editor.searchBox.getText().isEmpty();
        WideButtonWidget importBtn = (WideButtonWidget) widgets.get(0);
        importBtn.icon = isSearching ? ModTextures.ICON_SEARCH : ModTextures.ICON_IMPORT;
        importBtn.tooltipText = isSearching
                ? Text.translatable("tooltip.chestseparators.desc.import_search")
                        .getString()
                : Text.translatable("tooltip.chestseparators.desc.import_extracted")
                        .getString();

        String newText = isSearching
                ? Text.translatable("button.chestseparators.import_search").getString()
                : Text.translatable("button.chestseparators.import_extracted").getString();
        importBtn.label = newText;

        widgets.get(1).isActive = (session.currentAllowedItems.size() == session.allGameItems.size());
        widgets.get(2).isActive = session.currentAllowedItems.isEmpty();

        widgets.get(3).isActive = session.ruleManual;
        widgets.get(3).tooltipText = session.ruleManual
                ? Text.translatable("tooltip.chestseparators.rule.manual_active")
                        .getString()
                : Text.translatable("tooltip.chestseparators.rule.manual_inactive")
                        .getString();

        widgets.get(4).isActive = session.ruleShift;
        widgets.get(4).tooltipText = session.ruleShift
                ? Text.translatable("tooltip.chestseparators.rule.shift_active").getString()
                : Text.translatable("tooltip.chestseparators.rule.shift_inactive")
                        .getString();

        widgets.get(5).isActive = session.ruleHopper;
        widgets.get(5).isDisabled = session.isEnderChest || session.isEntityChest;

        if (!session.isEnderChest && !session.isEntityChest) {
            widgets.get(5).tooltipText = session.ruleHopper
                    ? Text.translatable("tooltip.chestseparators.rule.hopper_active")
                            .getString()
                    : Text.translatable("tooltip.chestseparators.rule.hopper_inactive")
                            .getString();
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
                boolean isSearching =
                        editor.searchBox != null && !editor.searchBox.getText().isEmpty();

                if (session.previewType == 1) {
                    if (isSearching) {
                        for (Item item : session.filteredItems)
                            itemsToPreview.add(Registries.ITEM.getId(item).toString());
                    } else {
                        itemsToPreview = editor.extractItemsFromSelection();
                    }
                } else if (session.previewType == 2) {
                    for (Item item : session.allGameItems)
                        itemsToPreview.add(Registries.ITEM.getId(item).toString());
                } else if (session.previewType == 3) {
                    itemsToPreview.addAll(session.currentAllowedItems);
                }

                if (!itemsToPreview.isEmpty() || session.previewType == 3) {
                    session.isPreviewing = true;
                    session.previewItems = itemsToPreview;
                    session.previewScrollY = (session.previewType == 3) ? session.listScrollY : 0f;
                    session.previewScrollDirectionDown = true;
                    session.userOverrodePreviewScroll = false;
                    long now = System.currentTimeMillis();
                    session.lastPreviewTime = now;
                    session.previewStartTime = now;
                } else session.forceStopPreview = true;
            } else {
                long now = System.currentTimeMillis();
                float dt = (now - session.lastPreviewTime) / 1000f;
                session.lastPreviewTime = now;
                if (!session.userOverrodePreviewScroll
                        && (now - session.previewStartTime > 1000)
                        && session.previewType != 3) {
                    float maxScroll = Math.max(0, session.previewItems.size() * 18 - (layout.listH - 48));
                    if (maxScroll > 0) {
                        float speed = (session.previewType == 2) ? 80f : 25f;
                        if (session.previewScrollDirectionDown) {
                            session.previewScrollY += speed * dt;
                            if (session.previewScrollY >= maxScroll) {
                                session.previewScrollY = maxScroll;
                                session.previewScrollDirectionDown = false;
                            }
                        } else {
                            session.previewScrollY -= speed * dt;
                            if (session.previewScrollY <= 0) {
                                session.previewScrollY = 0;
                                session.previewScrollDirectionDown = true;
                            }
                        }
                    }
                }
            }
        } else session.isPreviewing = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Forward clicks to the active popup and block all background interaction.
        if (session.isUnsavedPopupOpen) {
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget widget : unsavedPopupWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return true;
        }

        if (session.isExpelPopupOpen) {
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget widget : expelPopupWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return true;
        }

        // Track which panel received the last click to route keyboard scroll correctly.
        if (mouseX >= layout.mainX
                && mouseX <= layout.mainX + layout.mainW
                && mouseY >= layout.mainY
                && mouseY <= layout.mainY + layout.mainH) {
            session.lastInteractedWasList = false;
        } else if (mouseX >= layout.listX
                && mouseX <= layout.listX + layout.listW
                && mouseY >= layout.listY
                && mouseY <= layout.listY + layout.listH) {
            session.lastInteractedWasList = true;
        }

        // 1. Tag Dropdown logic
        if (!session.activeDropdownTags.isEmpty()) {
            if (button == 0) {
                int dropW = 120;
                int dropH = session.activeDropdownTags.size() * 14 + 4;
                if (mouseX >= session.dropdownX
                        && mouseX <= session.dropdownX + dropW
                        && mouseY >= session.dropdownY
                        && mouseY <= session.dropdownY + dropH) {
                    int clickedIndex = (int) (mouseY - session.dropdownY - 2) / 14;
                    if (clickedIndex >= 0 && clickedIndex < session.activeDropdownTags.size()) {
                        if (editor.searchBox != null) {
                            editor.searchBox.setText("#" + session.activeDropdownTags.get(clickedIndex));
                            editor.playClickSound(1.2f);
                        }
                    }
                }
                session.activeDropdownTags.clear();
                return true;
            } else {
                session.activeDropdownTags.clear();
                return true;
            }
        }

        if (button == 0 && super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 && button != 2) return false;

        // Remove from List (The 'X')
        if (button == 0
                && mouseX >= layout.listX + 6
                && mouseX <= layout.listX + layout.listW - 14
                && mouseY >= layout.listViewY
                && mouseY <= layout.listViewY + layout.listViewH) {
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
                session.isDraggingMainScroll = true;
                return true;
            }
            if (editor.isHovering(layout.lsbX, layout.lsbY, 6, layout.lsbH, mouseX, mouseY)) {
                session.isDraggingListScroll = true;
                return true;
            }
        }

        // Search Boxes Focus
        EditorSessionData.CreativeTabInfo currentTabInfo = session.availableTabs.get(session.currentCreativeTabIndex);
        if (button == 0
                && editor.whitelistSearchBox != null
                && editor.isHovering(layout.listX + 6, layout.listY + 20, layout.listW - 12, 16, mouseX, mouseY)) {
            editor.whitelistSearchBox.setFocused(true);
            if (editor.searchBox != null) editor.searchBox.setFocused(false);
            editor.playClickSound(1.0f);
            return true;
        } else if (button == 0
                && editor.searchBox != null
                && currentTabInfo != null
                && currentTabInfo.isSearchTab
                && editor.searchBox.isMouseOver(mouseX, mouseY)) {
            editor.searchBox.setFocused(true);
            if (editor.whitelistSearchBox != null) editor.whitelistSearchBox.setFocused(false);
            editor.playClickSound(1.0f);
            return true;
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
                if (tab.isSearchTab) {
                    searchTab = tab;
                    searchTabIndexGlobal = i;
                } else {
                    normalIndices.add(i);
                }
            }

            int totalNormalTabs = normalIndices.size();
            int remainingTabs = Math.max(0, totalNormalTabs - 10);
            int normalTabsPerPage = 15;

            int maxPages = 1;
            if (remainingTabs > 0) {
                maxPages = 1 + (int) Math.ceil((double) remainingTabs / normalTabsPerPage);
            }

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
            EditorSessionData.CreativeTabInfo activeTabInfo =
                    session.availableTabs.get(session.currentCreativeTabIndex);
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

                    if (editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY)) {
                        session.currentTabPage = (session.currentTabPage - 1 + maxPages) % maxPages;
                        int newIndex = (session.currentTabPage == 0) ? 0 : 10 + ((session.currentTabPage - 1) * 15);
                        if (newIndex < normalIndices.size()) editor.switchCreativeTab(normalIndices.get(newIndex));
                        editor.playClickSound(0.8f);
                        return true;
                    }
                    currentX += arrowWidth;

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
        if (mouseX >= layout.gridX
                && mouseX < layout.gridX + (layout.cols * layout.itemSize)
                && mouseY >= layout.gridViewY
                && mouseY < layout.gridViewY + layout.gridViewH) {
            int c = (int) (mouseX - layout.gridX) / layout.itemSize;
            int r = (int) (mouseY - layout.gridViewY + session.gridScrollY) / layout.itemSize;
            int itemIndex = (r * layout.cols) + c;
            if (itemIndex >= 0 && itemIndex < session.filteredItems.size()) {
                Item item = session.filteredItems.get(itemIndex);
                if (button == 2) {
                    List<String> tags = new ArrayList<>();
                    item.getRegistryEntry().streamTags().forEach(tagKey -> {
                        String t = tagKey.id().getPath();
                        if (!t.startsWith("mineable/")
                                && !t.startsWith("needs_")
                                && !t.contains("tools")
                                && !t.startsWith("beacon_")
                                && !t.equals("completes_find_tree_tutorial")
                                && !tags.contains(t)) tags.add(t);
                    });
                    if (!tags.isEmpty()) {
                        if (editor.searchBox != null) editor.searchBox.setText("");
                        for (int i = 0; i < session.availableTabs.size(); i++) {
                            if (session.availableTabs.get(i) != null && session.availableTabs.get(i).isSearchTab) {
                                editor.switchCreativeTab(i);
                                int newIndex = session.filteredItems.indexOf(item);
                                if (newIndex != -1)
                                    session.gridScrollY = MathHelper.clamp(
                                            ((float) newIndex / 9 - 4) * 18f,
                                            0,
                                            Math.max(
                                                    0,
                                                    (int) Math.ceil(session.filteredItems.size() / 9.0) * 18
                                                            - (9 * 18)));
                                break;
                            }
                        }
                        session.activeDropdownTags = tags;
                        session.dropdownX = layout.mainX + layout.mainW - 132;
                        session.dropdownY = layout.mainY + 24;
                        editor.playClickSound(1.0f);
                    } else
                        editor.showStatus(
                                Text.translatable("message.chestseparators.no_valid_groups"), Formatting.GRAY);
                } else {
                    String id = Registries.ITEM.getId(item).toString();
                    if (session.currentAllowedItems.contains(id)) {
                        session.currentAllowedItems.remove(id);
                        editor.playClickSound(0.8f);
                    } else {
                        session.currentAllowedItems.add(id);
                        editor.playClickSound(1.0f);
                    }

                    editor.updateWhitelistSearchCache(); // Update cache after modifying
                }
                return true;
            }
        }

        // --- CLICK OUTSIDE TO CLOSE ---
        if (button == 0 && GlobalChestConfig.instance.closeOnClickOutside) {
            boolean isInsideMain = mouseX >= layout.mainX
                    && mouseX <= layout.mainX + layout.mainW
                    && mouseY >= layout.mainY
                    && mouseY <= layout.mainY + layout.mainH;
            boolean isInsideLeft = mouseX >= layout.listX
                    && mouseX <= layout.listX + layout.listW
                    && mouseY >= layout.listY
                    && mouseY <= layout.listY + layout.listH;
            boolean isInsideRight = mouseX >= layout.rightX
                    && mouseX <= layout.rightX + layout.btnW
                    && mouseY >= layout.mainY
                    && mouseY <= layout.mainY + (layout.bH * 8);

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

        // While previewing, the scroll wheel always controls the preview panel.
        if (session.isPreviewing) {
            float maxScroll = Math.max(0, session.previewItems.size() * layout.itemSize - layout.listViewH);
            session.previewScrollY = net.minecraft.util.math.MathHelper.clamp(
                    session.previewScrollY - (float) (verticalAmount * scrollSpeed), 0, maxScroll);
            session.userOverrodePreviewScroll = true;
            return true;
        }

        if (mouseX >= layout.mainX) {
            session.lastInteractedWasList = false;
            float maxGridScroll = Math.max(
                    0,
                    (int) Math.ceil((double) session.filteredItems.size() / layout.cols) * layout.itemSize
                            - layout.gridViewH);
            session.gridScrollY = net.minecraft.util.math.MathHelper.clamp(
                    session.gridScrollY - (float) (verticalAmount * scrollSpeed), 0, maxGridScroll);
        } else {
            session.lastInteractedWasList = true;
            float maxListScroll = Math.max(0, session.visibleLeftListItems.size() * layout.itemSize - layout.listViewH);
            session.listScrollY = net.minecraft.util.math.MathHelper.clamp(
                    session.listScrollY - (float) (verticalAmount * scrollSpeed), 0, maxListScroll);
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

        if (!isSearchFocused && !isWhitelistSearchFocused) {
            if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                float scrollAmount = 18f * 2;
                int direction = (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) ? -1 : 1;

                if (session.lastInteractedWasList) {
                    float maxListScroll = Math.max(0, session.visibleLeftListItems.size() * 18 - (200 - 48));
                    session.listScrollY = net.minecraft.util.math.MathHelper.clamp(
                            session.listScrollY + (direction * scrollAmount), 0, maxListScroll);
                } else {
                    float maxGridScroll =
                            Math.max(0, (int) Math.ceil(session.filteredItems.size() / 9.0) * 18 - (9 * 18));
                    session.gridScrollY = net.minecraft.util.math.MathHelper.clamp(
                            session.gridScrollY + (direction * scrollAmount), 0, maxGridScroll);
                }
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (editor.searchBox != null && editor.searchBox.isFocused()) return editor.searchBox.charTyped(input);
        if (editor.whitelistSearchBox != null && editor.whitelistSearchBox.isFocused())
            return editor.whitelistSearchBox.charTyped(input);
        return false;
    }
}
