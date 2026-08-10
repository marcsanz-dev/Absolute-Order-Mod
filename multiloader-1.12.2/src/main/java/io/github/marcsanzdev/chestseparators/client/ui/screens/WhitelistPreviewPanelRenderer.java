package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import io.github.marcsanzdev.chestseparators.client.compat.GuiGraphics;
import net.minecraft.inventory.Slot;
import net.minecraft.util.text.ITextComponent;
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

    void renderWhitelistPreviewPanel(GuiGraphics context, int mouseX, int mouseY) {
        int listW = layout.listW;
        int listH = layout.listH;
        int listX = layout.listX;
        int listY = layout.listY;

        int guiX = layout.guiX;
        int guiY = layout.guiY;

        boolean isDark = GlobalChestConfig.instance.darkMode;

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(context, listX, listY, listW, listH);

        Slot hoveredSlot = null;
        for (Slot slot : editor.accessor.getHandler().inventorySlots) {
            if (ChestSeparatorsEditor.isEditableSlot(slot)
                    && editor.isHovering(guiX + slot.xPos - 1, guiY + slot.yPos - 1, 18, 18, mouseX, mouseY)) {
                hoveredSlot = slot;
                break;
            }
        }

        if (hoveredSlot != null) {
            java.util.Map<Integer, SlotWhitelist> whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
            int hoveredKey = ChestSeparatorsEditor.slotKey(hoveredSlot);
            if (whitelists != null && whitelists.containsKey(hoveredKey)) {
                SlotWhitelist wl = whitelists.get(hoveredKey);
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

        ITextComponent title = session.isPreviewing
                ? new net.minecraft.util.text.TextComponentTranslation("gui.chestseparators.group_filter")
                : new net.minecraft.util.text.TextComponentTranslation("gui.chestseparators.no_filter");
        int colorTitle = session.isPreviewing ? 0xFF55FF55 : 0xFFAAAAAA;
        int itemCount = session.isPreviewing ? session.previewItems.size() : 0;

        context.pose().pushPose();
        float scaleC = 0.8f;
        context.pose().scale(scaleC, scaleC, 1.0F);
        context.drawString(
                Minecraft.getMinecraft().fontRenderer,
                title,
                (int) ((listX + 8) / scaleC),
                (int) ((listY + 8) / scaleC),
                colorTitle,
                false);
        context.drawString(
                Minecraft.getMinecraft().fontRenderer,
                new net.minecraft.util.text.TextComponentTranslation("gui.chestseparators.items_count", itemCount),
                (int) ((listX + 8) / scaleC),
                (int) ((listY + 18) / scaleC),
                0xFFFFFFFF,
                false);
        context.pose().popPose();

        int listViewY = listY + 32;
        int listViewH = listH - 38;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.inset(
                context, listX + 6, listViewY, listW - 12, listViewH);

        if (!session.isPreviewing) {
            ITextComponent hoverText = new net.minecraft.util.text.TextComponentTranslation("gui.chestseparators.hover_group");
            int textX = listX + 12;
            int textY = listViewY + 10;
            int maxTextW = listW - 24;

            for (String line :
                    Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(hoverText.getFormattedText(), maxTextW)) {
                context.drawString(Minecraft.getMinecraft().fontRenderer, line, textX, textY, 0xFF666666, false);
                textY += Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT + 2;
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
            ITextComponent emptyText = new net.minecraft.util.text.TextComponentTranslation("gui.chestseparators.empty_whitelist");
            int textX = listX + 12;
            int textY = listViewY + 10;
            int maxTextW = listW - 24;

            for (String line :
                    Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(emptyText.getFormattedText(), maxTextW)) {
                context.drawString(Minecraft.getMinecraft().fontRenderer, line, textX, textY, 0xFF888888, false);
                textY += Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT + 2;
            }
        }

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;

            String itemIdStr = session.previewItems.get(idx);
            // 1.12.2: net.minecraft.core.Registry no longer exists — resolve the id via Item#getByNameOrId.
            net.minecraft.item.Item item = net.minecraft.item.Item.getByNameOrId(itemIdStr);
            // getByNameOrId returns null for an absent id (the E4 Registry#get returned AIR); skip it.
            if (item == null) continue;

            int itemY = listViewY + (i * 18) - (int) (session.listScrollY % 18) + 2;
            context.renderItem(new net.minecraft.item.ItemStack(item), listX + 8, itemY);

            String name = new net.minecraft.item.ItemStack(item).getDisplayName();
            context.pose().pushPose();
            float scale = 0.75f;
            context.pose().scale(scale, scale, 1.0F);
            int maxNameW = (int) ((listW - 35) / scale);
            if (Minecraft.getMinecraft().fontRenderer.getStringWidth(name) > maxNameW) {
                name = Minecraft.getMinecraft().fontRenderer.trimStringToWidth(name, maxNameW - 6) + "...";
            }
            int textColor = isDark ? 0xFFDDDDDD : 0xFF222222;
            context.drawString(
                    Minecraft.getMinecraft().fontRenderer,
                    name,
                    (int) ((listX + 26) / scale),
                    (int) ((itemY + 6) / scale),
                    textColor,
                    false);
            context.pose().popPose();
        }
        context.disableScissor();

        // Draw the rule indicator tabs to the left of the preview panel.
        if (session.isPreviewing && session.lastHoveredGroupId != null) {
            SlotWhitelist activeWl = null;
            java.util.Map<Integer, SlotWhitelist> whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
            if (whitelists != null) {
                for (SlotWhitelist wl : whitelists.values()) {
                    if (wl.groupId().equals(session.lastHoveredGroupId)) {
                        activeWl = wl;
                        break;
                    }
                }
            }

            if (activeWl != null) {
                // Tabs sit on the OUTER side of the panel: to its left on the normal left dock, to its right
                // when the panel is flipped right (recipe book open), so they never point back into the GUI.
                int tabX = layout.listOnRight ? (listX + listW) : (listX - 20);
                int startY = listY + 12;

                drawRuleTab(context, tabX, startY, ModTextures.ICON_SM_MANUAL, activeWl.allowManual());
                drawRuleTab(context, tabX, startY + 24, ModTextures.ICON_SM_SHIFT, activeWl.allowShift());
                // The Hopper rule can never be toggled on an Ender Chest (hoppers cannot reach it), so its
                // indicator tab would always be meaningless — omit it entirely there.
                if (!session.isEnderChest) {
                    drawRuleTab(
                            context,
                            tabX,
                            startY + 48,
                            session.isPlayerInventory ? ModTextures.ICON_SM_PICKUP : ModTextures.ICON_SM_HOPPER,
                            activeWl.allowHopper());
                }
            }
        }

        int lsbX = listX + listW - 13;
        int lsbH = listViewH - 1;
        int listThumbH = maxListScroll > 0
                ? Math.max(10, (int) ((listViewH / (float) Math.max(1, totalListHeight)) * lsbH))
                : lsbH;
        int listThumbY = maxListScroll > 0
                ? listViewY + (int) ((session.listScrollY / maxListScroll) * (lsbH - listThumbH))
                : listViewY;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.scrollbar(
                context, lsbX, listViewY, 6, lsbH, listThumbY, listThumbH);

        for (int i = 0; i < visibleCount; i++) {
            int idx = startIndex + i;
            if (idx >= totalListItems) break;
            // 1.12.2: resolve the id via Item#getByNameOrId (net.minecraft.core.Registry is gone).
            net.minecraft.item.Item item = net.minecraft.item.Item.getByNameOrId(session.previewItems.get(idx));
            if (item == null) continue;
            int itemY = listViewY + (i * 18) - (int) (session.listScrollY % 18) + 2;

            if (editor.isHovering(listX + 8, itemY, 16, 16, mouseX, mouseY)
                    && mouseY >= listViewY
                    && mouseY <= listViewY + listViewH) {
                context.renderOutline(listX + 7, itemY - 1, 18, 18, 0xFFFFFFFF);
                context.renderTooltip(Minecraft.getMinecraft().fontRenderer,
                        new net.minecraft.util.text.TextComponentString(new net.minecraft.item.ItemStack(item).getDisplayName()), mouseX, mouseY);
            }
        }
    }

    private void drawRuleTab(GuiGraphics context, int x, int y, net.minecraft.util.ResourceLocation icon, boolean isActive) {
        int w = 20;
        int h = 20;
        // Cristal tab embedded flush into the preview panel: it attaches on the edge that faces the panel —
        // its right edge on the normal left dock, its left edge when the panel is flipped to the right.
        int attach = layout.listOnRight
                ? io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ATTACH_LEFT
                : io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ATTACH_RIGHT;
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.tab(context, x, y, w, h, false, isActive, attach);

        int color = isActive
                ? io.github.marcsanzdev.chestseparators.client.ui.UiTheme.ICON_ACTIVE
                : io.github.marcsanzdev.chestseparators.client.ui.UiTheme.TEXT_MUTED;
        // An active tab draws 1px smaller; shrink its icon by the same proportion.
        if (isActive) {
            io.github.marcsanzdev.chestseparators.client.ui.UiTheme.pushActiveContent(context, x, y, w, h);
        }
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context, icon, x + 2, y + 2, 0.0F, 0.0F, 16, 16, 128, 128, 128, 128, color);
        if (isActive) {
            context.pose().popPose();
        }
    }
}
