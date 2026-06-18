package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * Handles mouse-click routing for the filter editor: popups, the action buttons, the search/list
 * panels, tag tabs, and the item grid. Extracted verbatim from ScreenEditFilter.
 */
final class FilterClickHandler {

    private final ScreenEditFilter screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;

    FilterClickHandler(ScreenEditFilter screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
    }

    boolean onMouseClicked(double mouseX, double mouseY, int button) {
        // Forward clicks to the active popup and block all background interaction.
        if (session.isUnsavedPopupOpen) {
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget widget :
                    screen.unsavedPopupWidgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return true;
        }

        if (session.isExpelPopupOpen) {
            for (io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget widget :
                    screen.expelPopupWidgets) {
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

        if (button == 0 && screen.clickWidgets(mouseX, mouseY, button)) return true;
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
                if (screen.hasUnsavedChanges()) {
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
}
