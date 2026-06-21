package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.client.ui.UiColors;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Renders the always-on left-side whitelist preview panel shown in normal mode, including the
 * per-rule indicator tabs. Pure rendering, extracted verbatim from ScreenViewGroups.
 */
final class WhitelistPreviewPanelRenderer {

    private final ScreenViewGroups screen;
    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;

    WhitelistPreviewPanelRenderer(ScreenViewGroups screen) {
        this.screen = screen;
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
    }

    void renderWhitelistPreviewPanel(DrawContext context, int mouseX, int mouseY) {
        int listW = layout.listW;
        int listH = layout.listH;
        int listX = layout.listX;
        int listY = layout.listY;

        int guiX = layout.guiX;
        int guiY = layout.guiY;

        boolean isDark = GlobalChestConfig.instance.darkMode;

        context.fill(
                listX, listY, listX + listW, listY + listH, isDark ? UiColors.SURFACE_DARK : UiColors.SURFACE_LIGHT);
        screen.drawDarkBevel(context, listX, listY, listW, listH, false);

        Slot hoveredSlot = null;
        for (Slot slot : editor.accessor.getHandler().slots) {
            if (ChestSeparatorsEditor.isEditableSlot(slot)
                    && editor.isHovering(guiX + slot.x - 1, guiY + slot.y - 1, 18, 18, mouseX, mouseY)) {
                hoveredSlot = slot;
                break;
            }
        }

        if (hoveredSlot != null) {
            var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
            if (whitelists != null && whitelists.containsKey(hoveredSlot.getIndex())) {
                SlotWhitelist wl = whitelists.get(hoveredSlot.getIndex());
                UUID currentGroupId = wl.groupId();

                if (!session.isPreviewing || !currentGroupId.equals(session.lastHoveredGroupId)) {
                    session.previewItems = new ArrayList<>(wl.allowedItems());
                    session.listScrollY = 0f;
                    session.isPreviewing = true;
                    session.lastHoveredGroupId = currentGroupId;

                    long now = System.currentTimeMillis();
                    session.previewStartTime = now;
                    session.lastPreviewTime = now;
                    session.previewScrollDirectionDown = true;
                    session.userOverrodePreviewScroll = false;
                }
            } else {
                session.isPreviewing = false;
                session.previewItems.clear();
                session.lastHoveredGroupId = null;
            }
        } else {
            if (!editor.isHovering(listX, listY, listW, listH, mouseX, mouseY)) {
                session.isPreviewing = false;
                session.previewItems.clear();
                session.lastHoveredGroupId = null;
            }
        }

        Text title = session.isPreviewing
                ? Text.translatable("gui.chestseparators.group_filter")
                : Text.translatable("gui.chestseparators.no_filter");
        int colorTitle = session.isPreviewing ? 0xFF55FF55 : 0xFFAAAAAA;
        int itemCount = session.isPreviewing ? session.previewItems.size() : 0;

        context.getMatrices().pushMatrix();
        float scaleC = 0.8f;
        context.getMatrices().scale(scaleC, scaleC);
        context.drawText(
                MinecraftClient.getInstance().textRenderer,
                title,
                (int) ((listX + 8) / scaleC),
                (int) ((listY + 8) / scaleC),
                colorTitle,
                false);
        context.drawText(
                MinecraftClient.getInstance().textRenderer,
                Text.translatable("gui.chestseparators.items_count", itemCount),
                (int) ((listX + 8) / scaleC),
                (int) ((listY + 18) / scaleC),
                0xFFFFFFFF,
                false);
        context.getMatrices().popMatrix();

        int listViewY = listY + 32;
        int listViewH = listH - 38;
        context.fill(listX + 6, listViewY, listX + listW - 6, listViewY + listViewH, isDark ? 0xFF101010 : 0xFFE0E0E0);
        screen.drawDarkBevel(context, listX + 6, listViewY, listW - 12, listViewH, true);

        if (!session.isPreviewing) {
            Text hoverText = Text.translatable("gui.chestseparators.hover_group");
            int textX = listX + 12;
            int textY = listViewY + 10;
            int maxTextW = listW - 24;

            for (net.minecraft.text.OrderedText line :
                    MinecraftClient.getInstance().textRenderer.wrapLines(hoverText, maxTextW)) {
                context.drawText(MinecraftClient.getInstance().textRenderer, line, textX, textY, 0xFF666666, false);
                textY += MinecraftClient.getInstance().textRenderer.fontHeight + 2;
            }
            return;
        }

        int totalListItems = session.previewItems.size();
        int totalListHeight = totalListItems * 18;
        float maxListScroll = Math.max(0, totalListHeight - listViewH);

        long now = System.currentTimeMillis();
        float dt = (now - session.lastPreviewTime) / 1000f;
        session.lastPreviewTime = now;

        if (!session.userOverrodePreviewScroll && maxListScroll > 0 && (now - session.previewStartTime > 1000)) {
            if (GlobalChestConfig.instance.enablePreviewAnimation) {
                float speed = 25f;
                if (session.previewScrollDirectionDown) {
                    session.listScrollY += speed * dt;
                    if (session.listScrollY >= maxListScroll) {
                        session.listScrollY = maxListScroll;
                        session.previewScrollDirectionDown = false;
                        session.previewStartTime = now;
                    }
                } else {
                    session.listScrollY -= speed * dt;
                    if (session.listScrollY <= 0) {
                        session.listScrollY = 0;
                        session.previewScrollDirectionDown = true;
                        session.previewStartTime = now;
                    }
                }
            }
        }

        session.listScrollY = MathHelper.clamp(session.listScrollY, 0, maxListScroll);

        context.enableScissor(listX + 6, listViewY + 1, listX + listW - 14, listViewY + listViewH - 1);
        int startIndex = (int) (session.listScrollY / 18);
        int visibleCount = (listViewH / 18) + 2;

        if (session.previewItems.isEmpty()) {
            Text emptyText = Text.translatable("gui.chestseparators.empty_whitelist");
            int textX = listX + 12;
            int textY = listViewY + 10;
            int maxTextW = listW - 24;

            for (net.minecraft.text.OrderedText line :
                    MinecraftClient.getInstance().textRenderer.wrapLines(emptyText, maxTextW)) {
                context.drawText(MinecraftClient.getInstance().textRenderer, line, textX, textY, 0xFF888888, false);
                textY += MinecraftClient.getInstance().textRenderer.fontHeight + 2;
            }
        }

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;

            String itemIdStr = session.previewItems.get(idx);
            net.minecraft.item.Item item =
                    net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.tryParse(itemIdStr));

            int itemY = listViewY + (i * 18) - (int) (session.listScrollY % 18) + 2;
            context.drawItem(item.getDefaultStack(), listX + 8, itemY);

            String name = item.getName().getString();
            context.getMatrices().pushMatrix();
            float scale = 0.75f;
            context.getMatrices().scale(scale, scale);
            int maxNameW = (int) ((listW - 35) / scale);
            if (MinecraftClient.getInstance().textRenderer.getWidth(name) > maxNameW) {
                name = MinecraftClient.getInstance().textRenderer.trimToWidth(name, maxNameW - 6) + "...";
            }
            int textColor = isDark ? 0xFFDDDDDD : 0xFF222222;
            context.drawText(
                    MinecraftClient.getInstance().textRenderer,
                    name,
                    (int) ((listX + 26) / scale),
                    (int) ((itemY + 6) / scale),
                    textColor,
                    false);
            context.getMatrices().popMatrix();
        }
        context.disableScissor();

        // Draw the rule indicator tabs to the left of the preview panel.
        if (session.isPreviewing && session.lastHoveredGroupId != null) {
            SlotWhitelist activeWl = null;
            var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
            if (whitelists != null) {
                for (SlotWhitelist wl : whitelists.values()) {
                    if (wl.groupId().equals(session.lastHoveredGroupId)) {
                        activeWl = wl;
                        break;
                    }
                }
            }

            if (activeWl != null) {
                int tabX = listX - 20;
                int startY = listY + 12;

                drawRuleTab(context, tabX, startY, ModTextures.ICON_CURSOR, activeWl.allowManual());
                drawRuleTab(context, tabX, startY + 24, ModTextures.ICON_SHIFT, activeWl.allowShift());
                drawRuleTab(
                        context,
                        tabX,
                        startY + 48,
                        session.isPlayerInventory ? ModTextures.ICON_PICKUP : ModTextures.ICON_HOPPER,
                        activeWl.allowHopper());
            }
        }

        int lsbX = listX + listW - 13;
        int lsbH = listViewH - 1;
        context.fill(lsbX, listViewY, lsbX + 6, listViewY + lsbH, isDark ? 0xFF000000 : 0xFFAAAAAA);
        int listThumbH = maxListScroll > 0
                ? Math.max(10, (int) ((listViewH / (float) Math.max(1, totalListHeight)) * lsbH))
                : lsbH;
        int listThumbY = maxListScroll > 0
                ? listViewY + (int) ((session.listScrollY / maxListScroll) * (lsbH - listThumbH))
                : listViewY;
        context.fill(lsbX + 1, listThumbY + 1, lsbX + 5, listThumbY + listThumbH - 1, isDark ? 0xFF888888 : 0xFF666666);

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;
            net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(
                    net.minecraft.util.Identifier.tryParse(session.previewItems.get(idx)));
            int itemY = listViewY + (i * 18) - (int) (session.listScrollY % 18) + 2;

            if (editor.isHovering(listX + 8, itemY, 16, 16, mouseX, mouseY)
                    && mouseY >= listViewY
                    && mouseY <= listViewY + listViewH) {
                context.drawStrokedRectangle(listX + 7, itemY - 1, 18, 18, 0xFFFFFFFF);
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, item.getName(), mouseX, mouseY);
            }
        }
    }

    private void drawRuleTab(DrawContext context, int x, int y, net.minecraft.util.Identifier icon, boolean isActive) {
        int w = 20;
        int h = 20;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        int bgColor = isDark
                ? (isActive ? UiColors.SURFACE_DARK : 0xFF151515)
                : (isActive ? UiColors.SURFACE_LIGHT : 0xFF8B8B8B);
        context.fill(x, y, x + w, y + h, bgColor);

        screen.drawDarkBevel(context, x, y, w, h, isActive);

        // Paint over the bevel border on the right edge so the tab appears flush with the panel.
        if (isActive) {
            int patchColor = isDark ? UiColors.SURFACE_DARK : UiColors.SURFACE_LIGHT;
            context.fill(x + 18, y + 1, x + 22, y + 19, patchColor);
        }

        int color = isActive ? -1 : (isDark ? 0xFF555555 : 0xFF555555);
        com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
        context.drawTexture(pipeline, icon, x + 2, y + 2, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, color);
    }
}
