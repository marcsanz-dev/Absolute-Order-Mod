package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import io.github.marcsanzdev.chestseparators.client.compat.GuiGraphics;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.MathHelper;

public class ScreenColorPicker extends AbstractEditorScreen {

    GuiTextField hexField;
    GuiTextField rField;
    GuiTextField gField;
    GuiTextField bField;

    private boolean isUpdatingFields = false;
    int hoveredPixelColor = 0xFFFFFF;
    // Consecutive frames the eyedropper has sampled a near-white pixel — used to reject the one-frame white
    // flash caused by the (white) dropper icon of the previous frame landing on the sample point on fast moves.
    private int whiteSampleStreak = 0;

    private final ColorPickerInputHandler inputHandler;

    public ScreenColorPicker(ChestSeparatorsEditor editor) {
        super(editor);
        this.inputHandler = new ColorPickerInputHandler(this);
    }

    @Override
    protected void buildWidgets() {

        int w = layout.popupW;
        int x = layout.popupX;
        int y = layout.popupY;
        int btnY = y + layout.popupH - 30;
        int bH = 20;

        WideButtonWidget btnEyedropper = new WideButtonWidget(
                x + 10,
                btnY,
                85,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.eyedropper").getFormattedText(),
                ModTextures.ICON_SM_EYEDROPPER,
                () -> {
                    session.isEyedropperActive = true;
                    // TODO(1.12.2 port): GLFW cursor input mode has no LWJGL2 equivalent (no window handle);
                    // the OS cursor was hidden here on E5, but cannot be toggled on E1.
                    editor.playClickSound(1.2f);
                });
        btnEyedropper.tooltipText =
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.eyedropper").getFormattedText();
        btnEyedropper.texSize = 128;
        widgets.add(btnEyedropper);

        WideButtonWidget btnExit = new WideButtonWidget(
                x + 175,
                btnY,
                55,
                bH,
                new net.minecraft.util.text.TextComponentTranslation("button.chestseparators.exit").getFormattedText(),
                ModTextures.ICON_SM_CANCEL,
                () -> {
                    saveCurrentCustomColor();
                    editor.playClickSound(0.8f);
                    clearSelection();
                    session.isColorPickerOpen = false;
                    closeAndRestore();
                });
        btnExit.tooltipText =
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.exit").getFormattedText();
        btnExit.deferAction = true; // flash before the Exit button closes the colour picker
        widgets.add(btnExit);

        Minecraft client = Minecraft.getMinecraft();

        int rightPanelX = layout.popupX + 155;
        int textX = rightPanelX + 5;
        int fieldX = textX + 12;
        int fieldY = layout.popupY + 85;

        hexField = new GuiTextField(0, client.fontRenderer, fieldX, fieldY, 60, 12);
        hexField.setMaxStringLength(6);
        hexField.setEnableBackgroundDrawing(false);
        boolean isDark = GlobalChestConfig.instance.darkMode;
        hexField.setTextColor(isDark ? 0xFFFFFFFF : 0xFF222222);
        hexField.setValidator(text -> text.matches("^[0-9a-fA-F]*$"));
        // TODO(1.12.2 port): EditBox responder — GuiTextField has no per-change callback, so the
        // normalize-to-uppercase / updateColorFromFields(true) reaction cannot fire on each keystroke.

        rField = new GuiTextField(1, client.fontRenderer, fieldX, fieldY + 20, 30, 12);
        rField.setMaxStringLength(3);
        rField.setEnableBackgroundDrawing(false);
        rField.setTextColor(0xFFFF5555);
        rField.setValidator(text -> text.matches("^[0-9]*$"));
        // TODO(1.12.2 port): EditBox responder — GuiTextField has no per-change callback, so the
        // clamp-to-255 / updateColorFromFields(false) reaction cannot fire on each keystroke.

        gField = new GuiTextField(2, client.fontRenderer, fieldX, fieldY + 36, 30, 12);
        gField.setMaxStringLength(3);
        gField.setEnableBackgroundDrawing(false);
        gField.setTextColor(0xFF55FF55);
        gField.setValidator(text -> text.matches("^[0-9]*$"));
        // TODO(1.12.2 port): EditBox responder — GuiTextField has no per-change callback, so the
        // clamp-to-255 / updateColorFromFields(false) reaction cannot fire on each keystroke.

        bField = new GuiTextField(3, client.fontRenderer, fieldX, fieldY + 52, 30, 12);
        bField.setMaxStringLength(3);
        bField.setEnableBackgroundDrawing(false);
        bField.setTextColor(0xFF5555FF);
        bField.setValidator(text -> text.matches("^[0-9]*$"));
        // TODO(1.12.2 port): EditBox responder — GuiTextField has no per-change callback, so the
        // clamp-to-255 / updateColorFromFields(false) reaction cannot fire on each keystroke.

        updateFieldsFromColor();
    }

    void saveCurrentCustomColor() {
        int customIndex = (session.pickerTargetMode == 0)
                ? session.editingLineCustomIndex
                : ((session.pickerTargetMode == 1) ? session.editingBgCustomIndex : session.editingComboCustomIndex);

        if (customIndex != -1) {
            ChestConfigManager.getInstance()
                    .setCustomColor(customIndex, session.pickerCurrentRGB, session.pickerTargetMode);
            ChestConfigManager.getInstance().saveWorldPalette();
        }
    }

    void clearSelection() {
        if (session.pickerTargetMode == 0) session.editingLineCustomIndex = -1;
        else if (session.pickerTargetMode == 1) session.editingBgCustomIndex = -1;
        else session.editingComboCustomIndex = -1;
    }

    public void onOpen() {
        updateFieldsFromColor();
    }

    public void updateFieldsFromColor() {
        if (hexField == null) return;
        isUpdatingFields = true;

        if (session.pickerCurrentRGB == 0 && !editor.colorPickerModified) {
            hexField.setText("");
            rField.setText("");
            gField.setText("");
            bField.setText("");
            boolean isDark = GlobalChestConfig.instance.darkMode;
            hexField.setTextColor(isDark ? 0xFFFFFFFF : 0xFF222222);
        } else {
            int r = (session.pickerCurrentRGB >> 16) & 0xFF;
            int g = (session.pickerCurrentRGB >> 8) & 0xFF;
            int b = session.pickerCurrentRGB & 0xFF;

            hexField.setText(String.format("%02X%02X%02X", r, g, b));
            rField.setText(String.valueOf(r));
            gField.setText(String.valueOf(g));
            bField.setText(String.valueOf(b));

            hexField.setTextColor(session.pickerCurrentRGB | 0xFF000000);
        }

        isUpdatingFields = false;
    }

    void updateColorFromFields(boolean fromHex) {
        if (isUpdatingFields) return;

        editor.colorPickerModified = true;

        try {
            int r, g, b;
            if (fromHex) {
                String hex = hexField.getText().trim().replace("#", "");
                if (hex.length() == 6) {
                    int color = Integer.parseInt(hex, 16);
                    r = (color >> 16) & 0xFF;
                    g = (color >> 8) & 0xFF;
                    b = color & 0xFF;
                } else return;
            } else {
                r = MathHelper.clamp(Integer.parseInt(rField.getText().isEmpty() ? "0" : rField.getText()), 0, 255);
                g = MathHelper.clamp(Integer.parseInt(gField.getText().isEmpty() ? "0" : gField.getText()), 0, 255);
                b = MathHelper.clamp(Integer.parseInt(bField.getText().isEmpty() ? "0" : bField.getText()), 0, 255);
            }

            session.pickerCurrentRGB = (r << 16) | (g << 8) | b;
            float[] hsb = Color.RGBtoHSB(r, g, b, null);
            session.pickerHue = hsb[0];
            session.pickerSat = hsb[1];
            session.pickerVal = hsb[2];

            isUpdatingFields = true;
            if (fromHex) {
                rField.setText(String.valueOf(r));
                gField.setText(String.valueOf(g));
                bField.setText(String.valueOf(b));
            } else {
                hexField.setText(String.format("%02X%02X%02X", r, g, b));
                hexField.setTextColor(session.pickerCurrentRGB | 0xFF000000);
            }
            isUpdatingFields = false;

            saveCurrentCustomColor();

        } catch (NumberFormatException ignored) {
        }
    }

    public void closeAndRestore() {
        boolean hasNoColor = (session.pickerCurrentRGB == 0 && !editor.colorPickerModified);

        if (editor.colorPickerModified) {
            saveCurrentCustomColor();
        } else if (hasNoColor) {
            // Revert to the previous color and deselect the custom slot.
            if (session.pickerTargetMode == 0) {
                session.lineColorIndex = editor.restoreColorIndex;
                session.editingLineCustomIndex = -1;
            } else if (session.pickerTargetMode == 1) {
                session.bgColorIndex = editor.restoreColorIndex;
                session.editingBgCustomIndex = -1;
            } else {
                session.comboColorIndex = editor.restoreColorIndex;
                session.editingComboCustomIndex = -1;
            }
        }

        editor.playCloseSound();
        session.isColorPickerOpen = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputHandler.onMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return inputHandler.onMouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return inputHandler.onMouseReleased(mouseX, mouseY, button);
    }

    public boolean keyPressed(io.github.marcsanzdev.chestseparators.client.input.KeyEvent input) {
        return inputHandler.onKeyPressed(input);
    }

    public boolean charTyped(io.github.marcsanzdev.chestseparators.client.input.CharacterEvent input) {
        return inputHandler.onCharTyped(input);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!session.isColorPickerOpen) return;

        if (session.isEyedropperActive) {
            int sampled = ColorPickerGradients.readHoveredPixelColor(mouseX, mouseY);
            // Trust a near-white reading only after it has held for a second frame: a real white target
            // stays put, but the previous frame's white dropper icon flashing onto the sample point during
            // a fast move lasts a single frame. Every other colour updates live and instantly.
            boolean nearWhite =
                    ((sampled >> 16) & 0xFF) >= 0xF0 && ((sampled >> 8) & 0xFF) >= 0xF0 && (sampled & 0xFF) >= 0xF0;
            if (!nearWhite || whiteSampleStreak >= 1) {
                hoveredPixelColor = sampled;
            }
            whiteSampleStreak = nearWhite ? whiteSampleStreak + 1 : 0;
            // Whole-screen zoom loupe for precise color picking, drawn before the eyedropper cursor so
            // the cursor stays visible and is not captured into the loupe.
            if (GlobalChestConfig.instance.magnifierEnabled) {
                boolean circle = GlobalChestConfig.instance.magnifierShape == GlobalChestConfig.MagnifierShape.CIRCLE;
                io.github.marcsanzdev.chestseparators.client.ui.MagnifierRenderer.render(
                        context, mouseX, mouseY, circle);
            }
            renderEyedropperCursor(context, mouseX, mouseY);
            return;
        }

        context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0x77000000);

        // Re-draw only the 8 custom swatches above the dim layer so they remain visible and clickable.
        int pCol3X = layout.paletteCol3X;
        int pY = layout.paletteY;
        int sS = layout.swatchSize;
        int gap = 4;
        int tabMode = session.pickerTargetMode;
        int[] worldColors = ChestConfigManager.getInstance().getCustomColors(tabMode);

        for (int i = 0; i < 8; i++) {
            int slotY = pY + (i * (sS + gap));
            editor.screenDrawLines.drawSwatch(
                    context, pCol3X, slotY, worldColors[i], 16 + i, mouseX, mouseY, true, tabMode);
        }

        int w = layout.popupW;
        int h = layout.popupH;
        int x = layout.popupX;
        int y = layout.popupY;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.panel(context, x, y, w, h);

        Minecraft client = Minecraft.getMinecraft();

        ITextComponent titleText = new net.minecraft.util.text.TextComponentTranslation("window.chestseparators.edit_color");
        titleText.getStyle().setBold(Boolean.TRUE);
        int titleWidth = client.fontRenderer.getStringWidth(titleText.getFormattedText());
        int rightPanelStartX = layout.popupX + 155;
        int maxTitleWidth = (rightPanelStartX - 5) - (x + 12);

        float titleScale = 1.0f;
        if (titleWidth > maxTitleWidth) titleScale = (float) maxTitleWidth / titleWidth;

        context.pose().pushPose();
        context.pose().translate((float) (x + 12), (float) (y + 12 + (9 * (1.0f - titleScale)) / 2), 0.0F);
        context.pose().scale(titleScale, titleScale, 1.0F);
        context.drawString(client.fontRenderer, titleText, 0, 0, isDark ? 0xFFE0E0E0 : 0xFF333333, false);
        context.pose().popPose();

        ITextComponent instructionText = new net.minecraft.util.text.TextComponentTranslation("gui.chestseparators.color_picker.instructions");
        instructionText.getStyle().setColor(TextFormatting.GRAY);
        int maxTextWidth = 135;
        float scale = 1.0f;

        java.util.List<String> wrappedLines =
                client.fontRenderer.listFormattedStringToWidth(instructionText.getFormattedText(), maxTextWidth);

        // Auto-shrink: if the text wraps beyond 2 lines, reduce scale by 5% and recalculate
        // (a smaller scale means a larger logical wrap width, allowing more text per line).
        while (wrappedLines.size() > 2 && scale > 0.4f) {
            scale -= 0.05f;
            int wrapWidth = (int) (maxTextWidth / scale);
            wrappedLines = client.fontRenderer.listFormattedStringToWidth(instructionText.getFormattedText(), wrapWidth);
        }

        context.pose().pushPose();

        float yOffset = (2 - wrappedLines.size()) * (client.fontRenderer.FONT_HEIGHT + 2) * scale / 2.0f;
        context.pose().translate((float) (x + 12), (float) (y + 26) + yOffset, 0.0F);
        context.pose().scale(scale, scale, 1.0F);

        int instructY = 0;
        for (String line : wrappedLines) {
            context.drawString(client.fontRenderer, line, 0, instructY, isDark ? 0xFFFFFFFF : 0xFF222222, false);
            instructY += client.fontRenderer.FONT_HEIGHT + 2;
        }
        context.pose().popPose();

        int contentY = layout.popupY + 45;
        int contentX = layout.popupX + 12;

        ColorPickerGradients.drawSaturationValueBox(
                context, contentX, contentY, layout.pickerBoxSize, layout.pickerBoxSize, session.pickerHue);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundBorder(
                context, contentX - 1, contentY - 1, 102, 102, 0x33FFFFFF);

        int cursorX = contentX + (int) (session.pickerSat * 100);
        int cursorY = contentY + (int) ((1.0f - session.pickerVal) * 100);
        context.renderOutline(cursorX - 2, cursorY - 2, 5, 5, 0xFF000000);
        context.renderOutline(cursorX - 1, cursorY - 1, 3, 3, 0xFFFFFFFF);

        int hueX = contentX + 115;
        ColorPickerGradients.drawHueBar(context, hueX, contentY, layout.pickerHueWidth, layout.pickerBoxSize);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundBorder(
                context, hueX - 1, contentY - 1, 22, 102, 0x33FFFFFF);

        int hueCursorY = contentY + (int) (session.pickerHue * 100);
        context.fill(hueX - 3, hueCursorY - 1, hueX + 23, hueCursorY + 2, 0xFF000000);
        context.fill(hueX - 1, hueCursorY, hueX + 21, hueCursorY + 1, 0xFFFFFFFF);

        int rightPanelX = layout.popupX + 155;
        int pSize = 64;
        int previewX = rightPanelX + 6;
        int previewY = layout.popupY + 10;

        if (session.pickerCurrentRGB == 0 && !editor.colorPickerModified) {
            drawCheckerboard(context, previewX, previewY, pSize, pSize, 8);
            drawColorBevel(context, previewX, previewY, pSize, pSize, 0xFF555555, false);
        } else {
            context.fill(previewX, previewY, previewX + pSize, previewY + pSize, 0xFF000000 | session.pickerCurrentRGB);
            drawColorBevel(context, previewX, previewY, pSize, pSize, session.pickerCurrentRGB, false);
        }

        int textX = rightPanelX + 5;
        int fieldY = layout.popupY + 84;

        int dynamicColor = isDark ? 0xFFFFFFFF : 0xFF222222;
        if (session.pickerCurrentRGB != 0 || editor.colorPickerModified) {
            dynamicColor = session.pickerCurrentRGB | 0xFF000000;
        }

        context.drawString(client.fontRenderer, "#:", textX, fieldY + 1, dynamicColor, true);
        context.drawString(client.fontRenderer, "R:", textX, fieldY + 21, 0xFFFF5555, true);
        context.drawString(client.fontRenderer, "G:", textX, fieldY + 37, 0xFF55FF55, true);
        context.drawString(client.fontRenderer, "B:", textX, fieldY + 53, 0xFF5555FF, true);

        if (hexField != null) hexField.drawTextBox();
        if (rField != null) rField.drawTextBox();
        if (gField != null) gField.drawTextBox();
        if (bField != null) bField.drawTextBox();

        super.render(context, mouseX, mouseY, delta);

        // Custom color slot tooltips are drawn last so they layer above the picker window.
        for (int i = 0; i < 8; i++) {
            int slotY = pY + (i * (sS + gap));
            if (editor.isHovering(pCol3X, slotY, sS, sS, mouseX, mouseY)) {
                context.renderTooltip(
                        client.fontRenderer,
                        new net.minecraft.util.text.TextComponentTranslation("color.chestseparators.custom", (i + 1)),
                        mouseX,
                        mouseY);
            }
        }
    }

    private void renderEyedropperCursor(GuiGraphics context, int mx, int my) {
        // The diagonal dropper's tip sits at (3.47, 12.53) within its 16px footprint; offset the sprite so
        // that tip lands exactly on (mx, my) — the pixel that readHoveredPixelColor(mx, my) samples.
        int iconX = mx - 3;
        int iconY = my - 13;

        int previewX = mx + 18;
        int previewY = my - 24;
        int pw = 16;
        int ph = 16;

        context.fill(previewX, previewY, previewX + pw, previewY + ph, 0xFF000000 | hoveredPixelColor);
        drawColorBevel(context, previewX, previewY, pw, ph, hoveredPixelColor, false);

        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context,
                io.github.marcsanzdev.chestseparators.client.ModTextures.ICON_SM_EYEDROPPER,
                iconX,
                iconY,
                0.0F,
                0.0F,
                16,
                16,
                128,
                128,
                128,
                128,
                -1);
    }
}
