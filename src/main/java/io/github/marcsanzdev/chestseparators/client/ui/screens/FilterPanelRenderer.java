package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.client.ui.UiColors;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Renders the three panels of the filter editor: the left item list, the main whitelist panel,
 * and the tag dropdown. Pure rendering, extracted verbatim from ScreenEditFilter.
 */
final class FilterPanelRenderer {

    private final ScreenEditFilter screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;

    FilterPanelRenderer(ScreenEditFilter screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
    }

    void drawLeftPanel(DrawContext context, int mouseX, int mouseY) {
        int listW = layout.listW;
        int listH = layout.listH;
        int listX = layout.listX;
        int listY = layout.listY;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(context, listX, listY, listW, listH);

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
            if (session.previewType == 1)
                context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        Text.translatable("gui.chestseparators.new_detected", activeListToRender.size()),
                        (int) ((listX + 8) / scaleC),
                        (int) ((listY + 9) / scaleC),
                        0xFF55FF55,
                        false);
            else if (session.previewType == 2)
                context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        Text.translatable("gui.chestseparators.allowing_all", activeListToRender.size()),
                        (int) ((listX + 8) / scaleC),
                        (int) ((listY + 9) / scaleC),
                        0xFF55FF55,
                        false);
            else if (session.previewType == 3)
                context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        Text.translatable("gui.chestseparators.clearing", activeListToRender.size()),
                        (int) ((listX + 8) / scaleC),
                        (int) ((listY + 9) / scaleC),
                        0xFFFF5555,
                        false);
        } else {
            context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    Text.translatable("gui.chestseparators.allowed", allowedCount),
                    (int) ((listX + 8) / scaleC),
                    (int) ((listY + 5) / scaleC),
                    0xFF55FF55,
                    false);
            context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    Text.translatable("gui.chestseparators.blocked", blockedCount),
                    (int) ((listX + 8) / scaleC),
                    (int) ((listY + 13) / scaleC),
                    0xFFFF5555,
                    false);
        }
        context.getMatrices().popMatrix();

        context.fill(listX + 6, listY + 22, listX + listW - 6, listY + 38, isDark ? 0xFF101010 : 0xFFE0E0E0);
        screen.drawDarkBevel(context, listX + 6, listY + 22, listW - 12, 16, true);

        if (editor.whitelistSearchBox != null) {
            editor.whitelistSearchBox.setX(listX + 10);
            editor.whitelistSearchBox.setY(listY + 26);
            if (!session.isPreviewing)
                editor.whitelistSearchBox.render(
                        context,
                        mouseX,
                        mouseY,
                        MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true));
            if (editor.whitelistSearchBox.getText().isEmpty()
                    && !editor.whitelistSearchBox.isFocused()
                    && !session.isPreviewing) {
                context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        Text.translatable("gui.chestseparators.search"),
                        listX + 12,
                        listY + 26,
                        0xFF666666,
                        false);
            }
        }

        int listViewY = layout.listViewY;
        int listViewH = layout.listViewH;
        context.fill(listX + 6, listViewY, listX + listW - 6, listViewY + listViewH, isDark ? 0xFF101010 : 0xFFE0E0E0);
        screen.drawDarkBevel(context, listX + 6, listViewY, listW - 12, listViewH, true);

        int totalListItems = activeListToRender.size();
        // Trailing padding so scrolling to the bottom fully reveals the last row. Shared formula via
        // layout.maxListScroll keeps the renderer and the scroll inputs in sync.
        int totalListHeight = totalListItems * 18 + EditorLayout.LIST_TRAILING_PADDING;
        float maxListScroll = layout.maxListScroll(totalListItems);

        if (!session.isPreviewing) session.listScrollY = MathHelper.clamp(session.listScrollY, 0, maxListScroll);

        int lsbX = layout.lsbX;
        int lsbY = layout.lsbY;
        int lsbH = layout.lsbH;
        context.fill(lsbX, lsbY, lsbX + 6, lsbY + lsbH, isDark ? 0xFF000000 : 0xFFAAAAAA);
        int listThumbH = maxListScroll > 0
                ? Math.max(10, (int) ((listViewH / (float) Math.max(1, totalListHeight)) * lsbH))
                : lsbH;
        int listThumbY =
                maxListScroll > 0 ? lsbY + (int) ((activeScrollY / maxListScroll) * (lsbH - listThumbH)) : lsbY;
        context.fill(lsbX + 1, listThumbY + 1, lsbX + 5, listThumbY + listThumbH - 1, isDark ? 0xFF888888 : 0xFF666666);

        context.enableScissor(listX + 6, listViewY + 1, listX + listW - 14, listViewY + listViewH - 1);
        int startIndex = (int) (activeScrollY / 18);
        int visibleCount = (listViewH / 18) + 2;

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;

            String itemIdStr = activeListToRender.get(idx);
            Item item = Registries.ITEM.get(Identifier.tryParse(itemIdStr));
            int itemY = listViewY + (i * 18) - (int) (activeScrollY % 18) + 2;

            context.drawItem(item.getDefaultStack(), listX + 8, itemY);

            String name = item.getName().getString();
            context.getMatrices().pushMatrix();
            float scale = 0.75f;
            context.getMatrices().scale(scale, scale);
            int maxNameW = (int) ((listW - 55) / scale);
            if (MinecraftClient.getInstance().textRenderer.getWidth(name) > maxNameW)
                name = MinecraftClient.getInstance().textRenderer.trimToWidth(name, maxNameW - 6) + "...";
            int textColor = session.isPreviewing
                    ? ((session.previewType == 3) ? 0x66FFFFFF : 0xAAFFFFFF)
                    : (isDark ? 0xFFFFFFFF : 0xFF222222);
            context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    name,
                    (int) ((listX + 26) / scale),
                    (int) ((itemY + 6) / scale),
                    textColor,
                    false);
            context.getMatrices().popMatrix();

            if (!session.isPreviewing || session.previewType == 3) {
                int crossX = listX + listW - 22;
                int crossY = itemY + 4;
                boolean hoverCross =
                        !session.isPreviewing && editor.isHovering(crossX - 2, crossY - 2, 10, 10, mouseX, mouseY);
                context.drawTextWithShadow(
                        MinecraftClient.getInstance().textRenderer,
                        "x",
                        crossX,
                        crossY,
                        session.isPreviewing
                                ? 0xFFFF5555
                                : (hoverCross ? 0xFFFF5555 : (isDark ? 0xFFAAAAAA : 0xFF777777)));
            }
        }
        context.disableScissor();
    }

    void drawMainPanel(DrawContext context, int mouseX, int mouseY) {
        int mainX = layout.mainX;
        int mainY = layout.mainY;
        int mainW = layout.mainW;
        int mainH = layout.mainH;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        // --- 1. TABS LOGIC SEPARATION ---
        EditorSessionData.CreativeTabInfo searchTab = null;
        int searchTabIndexGlobal = -1;
        List<EditorSessionData.CreativeTabInfo> normalTabs = new ArrayList<>();
        List<Integer> normalIndices = new ArrayList<>();

        for (int i = 0; i < session.availableTabs.size(); i++) {
            EditorSessionData.CreativeTabInfo tab = session.availableTabs.get(i);
            if (tab == null) continue;
            if (tab.isSearchTab) {
                searchTab = tab;
                searchTabIndexGlobal = i;
            } else {
                normalTabs.add(tab);
                normalIndices.add(i);
            }
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
            int tabW = 22;
            int tabH = 22;
            int tabX = mainX + 3 + (col * 23);
            int tabY = isTop ? mainY - tabH + 2 : mainY + mainH - 2;

            boolean isSelected = (session.currentCreativeTabIndex == actualGlobalIndex);
            boolean hover = editor.isHovering(tabX, tabY, tabW, tabH, mouseX, mouseY);

            int bgColor = isDark
                    ? (isSelected ? UiColors.SURFACE_DARK : (hover ? 0xFF303030 : 0xFF151515))
                    : (isSelected ? UiColors.SURFACE_LIGHT : (hover ? 0xFFA0A0A0 : 0xFF8B8B8B));
            context.fill(tabX, tabY, tabX + tabW, tabY + tabH, bgColor);
            screen.drawDarkBevel(context, tabX, tabY, tabW, tabH, isSelected);
            context.drawItem(info.icon, tabX + 3, tabY + (isTop ? 2 : 4));

            if (hover) context.drawTooltip(MinecraftClient.getInstance().textRenderer, info.name, mouseX, mouseY);
        }

        // --- 3. DRAW MAIN PANEL WINDOW ---
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(context, mainX, mainY, mainW, mainH);

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
            int patchColor = isDark ? UiColors.SURFACE_DARK : UiColors.SURFACE_LIGHT;

            if (isTopActive) context.fill(tX + 1, mainY, tX + 21, mainY + 2, patchColor);
            else context.fill(tX + 1, mainY + mainH - 2, tX + 21, mainY + mainH, patchColor);
        }

        // --- 5. TITLE & CAROUSEL OR SEARCH BOX ---
        EditorSessionData.CreativeTabInfo activeTabInfo = session.availableTabs.get(session.currentCreativeTabIndex);
        String tabTitle = activeTabInfo != null
                ? activeTabInfo.name.getString()
                : Text.translatable("gui.chestseparators.edit_filter_rules").getString();

        if (activeTabInfo != null && activeTabInfo.isSearchTab) {
            int titleWidth = MinecraftClient.getInstance().textRenderer.getWidth(tabTitle);
            int maxTitleWidth = 60;
            float scale = 1.0f;

            if (titleWidth > maxTitleWidth) {
                scale = (float) maxTitleWidth / titleWidth;
            }

            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) (mainX + 12), (float) (mainY + 10 + (4 * (1 - scale))));
            context.getMatrices().scale(scale, scale);
            context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    tabTitle,
                    0,
                    0,
                    (isDark ? 0xFF909090 : 0xFF555555),
                    false);
            context.getMatrices().popMatrix();

            int boxX = mainX + 12 + (int) (titleWidth * scale) + 12;
            int boxY = mainY + 6;
            int boxW = (mainX + mainW - 12) - boxX;

            context.fill(boxX, boxY, boxX + boxW, boxY + 16, isDark ? 0xFF101010 : 0xFFE0E0E0);
            screen.drawDarkBevel(context, boxX, boxY, boxW, 16, true);
            editor.searchBox.setX(boxX + 4);
            editor.searchBox.setY(boxY + 4);
            editor.searchBox.setWidth(boxW - 8);
            editor.searchBox.render(
                    context,
                    mouseX,
                    mouseY,
                    MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true));

            if (editor.searchBox.getText().isEmpty() && !editor.searchBox.isFocused()) {
                context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        Text.translatable("gui.chestseparators.search"),
                        boxX + 6,
                        boxY + 4,
                        0xFF666666,
                        false);
            }
        } else {
            int visibleDots = Math.min(maxPages, 3);
            int carouselWidth = (maxPages > 1) ? ((12 * 2) + (visibleDots * 8)) : 0;
            int maxTextWidth = mainW - 24 - carouselWidth - 5;

            int textWidth = MinecraftClient.getInstance().textRenderer.getWidth(tabTitle);

            if (textWidth > maxTextWidth) {
                float scale = (float) maxTextWidth / textWidth;
                context.getMatrices().pushMatrix();

                context.getMatrices().translate((float) (mainX + 12), (float) (mainY + 10 + (4 * (1 - scale))));
                context.getMatrices().scale(scale, scale);

                context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        tabTitle,
                        0,
                        0,
                        (isDark ? 0xFF909090 : 0xFF555555),
                        false);
                context.getMatrices().popMatrix();
            } else {
                context.drawText(
                        MinecraftClient.getInstance().textRenderer,
                        tabTitle,
                        mainX + 12,
                        mainY + 10,
                        (isDark ? 0xFF909090 : 0xFF555555),
                        false);
            }

            drawPaginationCarousel(
                    context, mainX + mainW - 12, mainY + 10, maxPages, session.currentTabPage, mouseX, mouseY);
        }

        // --- 6. DRAW ITEM GRID ---
        int gridX = layout.gridX;
        int gridViewY = layout.gridViewY;
        int gridViewH = layout.gridViewH;
        int cols = layout.cols;
        int itemSize = layout.itemSize;

        context.fill(
                gridX - 2,
                gridViewY - 2,
                gridX + (cols * itemSize) + 2,
                gridViewY + gridViewH + 2,
                isDark ? 0xFF101010 : 0xFFE0E0E0);
        screen.drawDarkBevel(context, gridX - 2, gridViewY - 2, (cols * itemSize) + 4, gridViewH + 4, true);

        int totalGridItems = session.filteredItems.size();
        int totalGridHeight = (int) Math.ceil((double) totalGridItems / cols) * itemSize;
        float maxGridScroll = Math.max(0, totalGridHeight - gridViewH);
        session.gridScrollY = MathHelper.clamp(session.gridScrollY, 0, maxGridScroll);

        int msbX = layout.msbX;
        int msbY = layout.msbY;
        int msbH = layout.msbH;
        context.fill(msbX, msbY, msbX + 6, msbY + msbH, isDark ? 0xFF000000 : 0xFFAAAAAA);
        int gridThumbH = maxGridScroll > 0
                ? Math.max(10, (int) ((gridViewH / (float) Math.max(1, totalGridHeight)) * msbH))
                : msbH;
        int gridThumbY =
                maxGridScroll > 0 ? msbY + (int) ((session.gridScrollY / maxGridScroll) * (msbH - gridThumbH)) : msbY;
        context.fill(msbX + 1, gridThumbY + 1, msbX + 5, gridThumbY + gridThumbH - 1, isDark ? 0xFF888888 : 0xFF666666);

        context.enableScissor(gridX, gridViewY, gridX + (cols * itemSize), gridViewY + gridViewH);
        int startGridRow = (int) (session.gridScrollY / itemSize);
        int visibleGridRows = (gridViewH / itemSize) + 2;

        for (int r = 0; r < visibleGridRows; r++) {
            int absoluteRow = startGridRow + r;
            int drawY = gridViewY + (r * itemSize) - (int) (session.gridScrollY % itemSize);

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
                    if (session.previewType == 1) {
                        if (isAllowed) context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x66FFFFFF);
                        if (isPreviewed) context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x44FFFFFF);
                    } else if (session.previewType == 2) {
                        if (isAllowed) context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x66FFFFFF);
                        else context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x44FFFFFF);
                    } else if (session.previewType == 3) {
                        if (isAllowed) context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x44000000);
                    }
                } else if (isAllowed) {
                    context.fill(drawX + 1, drawY + 1, drawX + 17, drawY + 17, 0x66FFFFFF);
                }

                context.drawItem(item.getDefaultStack(), drawX + 1, drawY + 1);

                if (session.activeDropdownTags.isEmpty()
                        && editor.isHovering(drawX + 1, drawY + 1, 16, 16, mouseX, mouseY)
                        && mouseY >= gridViewY
                        && mouseY <= gridViewY + gridViewH) {
                    context.drawStrokedRectangle(drawX, drawY, 18, 18, 0xFFFFFFFF);
                    context.drawTooltip(MinecraftClient.getInstance().textRenderer, item.getName(), mouseX, mouseY);
                }
            }
        }
        context.disableScissor();
    }

    void drawTagDropdown(DrawContext context, int mouseX, int mouseY) {
        if (!session.activeDropdownTags.isEmpty()) {
            boolean isDark = GlobalChestConfig.instance.darkMode;
            int dropW = 120;
            int dropH = session.activeDropdownTags.size() * 14 + 4;
            int dX = session.dropdownX;
            int dY = session.dropdownY;
            if (dX + dropW > layout.screenWidth) dX = layout.screenWidth - dropW;
            if (dY + dropH > layout.screenHeight) dY = layout.screenHeight - dropH;
            session.dropdownX = dX;
            session.dropdownY = dY;

            context.fill(dX, dY, dX + dropW, dY + dropH, isDark ? 0xFF151515 : 0xFFE0E0E0);
            screen.drawDarkBevel(context, dX, dY, dropW, dropH, false);

            for (int i = 0; i < session.activeDropdownTags.size(); i++) {
                String tag = session.activeDropdownTags.get(i);
                int itemY = dY + 2 + (i * 14);
                boolean isHoveringTag = mouseX >= dX && mouseX <= dX + dropW && mouseY >= itemY && mouseY < itemY + 14;
                if (isHoveringTag)
                    context.fill(dX + 2, itemY, dX + dropW - 2, itemY + 14, isDark ? 0x40FFFFFF : 0x40000000);

                context.getMatrices().pushMatrix();
                float scale = 0.85f;
                if (MinecraftClient.getInstance().textRenderer.getWidth("#" + tag) * scale > dropW - 12)
                    scale = (float) (dropW - 12)
                            / MinecraftClient.getInstance().textRenderer.getWidth("#" + tag);
                context.getMatrices().translate((float) (dX + 6), (itemY + (14 - 8 * scale) / 2));
                context.getMatrices().scale(scale, scale);

                int textColor = isHoveringTag ? (isDark ? 0xFF55FF55 : 0xFF00AA00) : (isDark ? 0xFFDDDDDD : 0xFF222222);
                context.drawText(MinecraftClient.getInstance().textRenderer, "#" + tag, 0, 0, textColor, false);
                context.getMatrices().popMatrix();
            }
        }
    }

    void drawPaginationCarousel(
            DrawContext context, int rightBoundX, int y, int maxPages, int currentPage, int mouseX, int mouseY) {
        if (maxPages <= 1) return;

        // Sliding window: show at most 3 page dots centered on the current page.
        int visibleDots = Math.min(maxPages, 3);
        int startPage = Math.max(0, Math.min(currentPage - 1, maxPages - visibleDots));

        int dotSpacing = 8;
        int arrowWidth = 12;
        int totalWidth = (arrowWidth * 2) + (visibleDots * dotSpacing);
        int startX = rightBoundX - totalWidth;

        int currentX = startX;

        boolean hoverLeft = editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY);
        context.drawText(
                MinecraftClient.getInstance().textRenderer,
                "<",
                currentX + 2,
                y,
                hoverLeft ? 0xFF55FF55 : 0xFF888888,
                false);
        currentX += arrowWidth;

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

        boolean hoverRight = editor.isHovering(currentX, y - 2, arrowWidth, 12, mouseX, mouseY);
        context.drawText(
                MinecraftClient.getInstance().textRenderer,
                ">",
                currentX + 2,
                y,
                hoverRight ? 0xFF55FF55 : 0xFF888888,
                false);
    }
}
