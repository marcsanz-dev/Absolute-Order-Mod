package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.UiColors;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import java.awt.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

public class ScreenColorPicker extends AbstractEditorScreen {

    TextFieldWidget hexField;
    TextFieldWidget rField;
    TextFieldWidget gField;
    TextFieldWidget bField;

    private boolean isUpdatingFields = false;
    int hoveredPixelColor = 0xFFFFFF;

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
                Text.translatable("button.chestseparators.eyedropper").getString(),
                ModTextures.ICON_EYEDROPPER2,
                () -> {
                    session.isEyedropperActive = true;
                    org.lwjgl.glfw.GLFW.glfwSetInputMode(
                            MinecraftClient.getInstance().getWindow().getHandle(),
                            org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                            org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN);
                    editor.playClickSound(1.2f);
                });
        btnEyedropper.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.eyedropper").getString();
        widgets.add(btnEyedropper);

        WideButtonWidget btnExit = new WideButtonWidget(
                x + 175,
                btnY,
                55,
                bH,
                Text.translatable("button.chestseparators.exit").getString(),
                ModTextures.ICON_CANCEL,
                () -> {
                    saveCurrentCustomColor();
                    editor.playClickSound(0.8f);
                    clearSelection();
                    session.isColorPickerOpen = false;
                    closeAndRestore();
                });
        btnExit.tooltipText =
                Text.translatable("tooltip.chestseparators.desc.exit").getString();
        widgets.add(btnExit);

        MinecraftClient client = MinecraftClient.getInstance();

        int rightPanelX = layout.popupX + 155;
        int textX = rightPanelX + 5;
        int fieldX = textX + 12;
        int fieldY = layout.popupY + 85;

        hexField = new TextFieldWidget(
                client.textRenderer, fieldX, fieldY, 60, 12, Text.translatable("gui.chestseparators.hex"));
        hexField.setMaxLength(6);
        hexField.setDrawsBackground(false);
        boolean isDark = GlobalChestConfig.instance.darkMode;
        hexField.setEditableColor(isDark ? 0xFFFFFFFF : 0xFF222222);
        hexField.setTextPredicate(text -> text.matches("^[0-9a-fA-F]*$"));
        hexField.setChangedListener(text -> {
            String upper = text.toUpperCase();
            // Normalize to uppercase so the hex string is always canonical.
            if (!text.equals(upper)) {
                hexField.setText(upper);
            } else {
                updateColorFromFields(true);
            }
        });

        rField = new TextFieldWidget(
                client.textRenderer, fieldX, fieldY + 20, 30, 12, Text.translatable("gui.chestseparators.color_r"));
        rField.setMaxLength(3);
        rField.setDrawsBackground(false);
        rField.setEditableColor(0xFFFF5555);
        rField.setTextPredicate(text -> text.matches("^[0-9]*$"));
        rField.setChangedListener(text -> {
            if (!text.isEmpty() && Integer.parseInt(text) > 255) {
                rField.setText("255");
            } else {
                updateColorFromFields(false);
            }
        });

        gField = new TextFieldWidget(
                client.textRenderer, fieldX, fieldY + 36, 30, 12, Text.translatable("gui.chestseparators.color_g"));
        gField.setMaxLength(3);
        gField.setDrawsBackground(false);
        gField.setEditableColor(0xFF55FF55);
        gField.setTextPredicate(text -> text.matches("^[0-9]*$"));
        gField.setChangedListener(text -> {
            if (!text.isEmpty() && Integer.parseInt(text) > 255) {
                gField.setText("255");
            } else {
                updateColorFromFields(false);
            }
        });

        bField = new TextFieldWidget(
                client.textRenderer, fieldX, fieldY + 52, 30, 12, Text.translatable("gui.chestseparators.color_b"));
        bField.setMaxLength(3);
        bField.setDrawsBackground(false);
        bField.setEditableColor(0xFF5555FF);
        bField.setTextPredicate(text -> text.matches("^[0-9]*$"));
        bField.setChangedListener(text -> {
            if (!text.isEmpty() && Integer.parseInt(text) > 255) {
                bField.setText("255");
            } else {
                updateColorFromFields(false);
            }
        });

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
            hexField.setEditableColor(isDark ? 0xFFFFFFFF : 0xFF222222);
        } else {
            int r = (session.pickerCurrentRGB >> 16) & 0xFF;
            int g = (session.pickerCurrentRGB >> 8) & 0xFF;
            int b = session.pickerCurrentRGB & 0xFF;

            hexField.setText(String.format("%02X%02X%02X", r, g, b));
            rField.setText(String.valueOf(r));
            gField.setText(String.valueOf(g));
            bField.setText(String.valueOf(b));

            hexField.setEditableColor(session.pickerCurrentRGB | 0xFF000000);
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
                hexField.setEditableColor(session.pickerCurrentRGB | 0xFF000000);
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

        editor.playClickSound(0.8f);
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

    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        return inputHandler.onKeyPressed(input);
    }

    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        return inputHandler.onCharTyped(input);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!session.isColorPickerOpen) return;

        if (session.isEyedropperActive) {
            hoveredPixelColor = ColorPickerGradients.readHoveredPixelColor(mouseX, mouseY);
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

        MinecraftClient client = MinecraftClient.getInstance();

        Text titleText = Text.translatable("window.chestseparators.edit_color").formatted(Formatting.BOLD);
        int titleWidth = client.textRenderer.getWidth(titleText);
        int rightPanelStartX = layout.popupX + 155;
        int maxTitleWidth = (rightPanelStartX - 5) - (x + 12);

        float titleScale = 1.0f;
        if (titleWidth > maxTitleWidth) titleScale = (float) maxTitleWidth / titleWidth;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) (x + 12), (float) (y + 12 + (9 * (1.0f - titleScale)) / 2));
        context.getMatrices().scale(titleScale, titleScale);
        context.drawText(client.textRenderer, titleText, 0, 0, isDark ? 0xFFE0E0E0 : 0xFF333333, false);
        context.getMatrices().popMatrix();

        Text instructionText = Text.translatable("gui.chestseparators.color_picker.instructions")
                .formatted(Formatting.GRAY);
        int maxTextWidth = 135;
        float scale = 1.0f;

        java.util.List<net.minecraft.text.OrderedText> wrappedLines =
                client.textRenderer.wrapLines(instructionText, maxTextWidth);

        // Auto-shrink: if the text wraps beyond 2 lines, reduce scale by 5% and recalculate
        // (a smaller scale means a larger logical wrap width, allowing more text per line).
        while (wrappedLines.size() > 2 && scale > 0.4f) {
            scale -= 0.05f;
            int wrapWidth = (int) (maxTextWidth / scale);
            wrappedLines = client.textRenderer.wrapLines(instructionText, wrapWidth);
        }

        context.getMatrices().pushMatrix();

        float yOffset = (2 - wrappedLines.size()) * (client.textRenderer.fontHeight + 2) * scale / 2.0f;
        context.getMatrices().translate((float) (x + 12), (float) (y + 26) + yOffset);
        context.getMatrices().scale(scale, scale);

        int instructY = 0;
        for (net.minecraft.text.OrderedText line : wrappedLines) {
            context.drawText(client.textRenderer, line, 0, instructY, isDark ? 0xFFFFFFFF : 0xFF222222, false);
            instructY += client.textRenderer.fontHeight + 2;
        }
        context.getMatrices().popMatrix();

        int contentY = layout.popupY + 45;
        int contentX = layout.popupX + 12;

        ColorPickerGradients.drawSaturationValueBox(
                context, contentX, contentY, layout.pickerBoxSize, layout.pickerBoxSize, session.pickerHue);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.roundBorder(
                context, contentX - 1, contentY - 1, 102, 102, 0x33FFFFFF);

        int cursorX = contentX + (int) (session.pickerSat * 100);
        int cursorY = contentY + (int) ((1.0f - session.pickerVal) * 100);
        context.drawStrokedRectangle(cursorX - 2, cursorY - 2, 5, 5, 0xFF000000);
        context.drawStrokedRectangle(cursorX - 1, cursorY - 1, 3, 3, 0xFFFFFFFF);

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

        context.drawText(client.textRenderer, "#:", textX, fieldY + 1, dynamicColor, true);
        context.drawText(client.textRenderer, "R:", textX, fieldY + 21, 0xFFFF5555, true);
        context.drawText(client.textRenderer, "G:", textX, fieldY + 37, 0xFF55FF55, true);
        context.drawText(client.textRenderer, "B:", textX, fieldY + 53, 0xFF5555FF, true);

        if (hexField != null) hexField.render(context, mouseX, mouseY, delta);
        if (rField != null) rField.render(context, mouseX, mouseY, delta);
        if (gField != null) gField.render(context, mouseX, mouseY, delta);
        if (bField != null) bField.render(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);

        // Custom color slot tooltips are drawn last so they layer above the picker window.
        for (int i = 0; i < 8; i++) {
            int slotY = pY + (i * (sS + gap));
            if (editor.isHovering(pCol3X, slotY, sS, sS, mouseX, mouseY)) {
                context.drawTooltip(
                        client.textRenderer,
                        Text.translatable("color.chestseparators.custom", (i + 1)),
                        mouseX,
                        mouseY);
            }
        }
    }

    private void renderEyedropperCursor(DrawContext context, int mx, int my) {
        int iconX = mx - 3;
        int iconY = my - 12;

        int previewX = mx + 18;
        int previewY = my - 24;
        int pw = 16;
        int ph = 16;

        context.fill(previewX, previewY, previewX + pw, previewY + ph, 0xFF000000 | hoveredPixelColor);
        drawColorBevel(context, previewX, previewY, pw, ph, hoveredPixelColor, false);

        com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
        context.drawTexture(
                pipeline,
                io.github.marcsanzdev.chestseparators.client.ModTextures.ICON_EYEDROPPER,
                iconX,
                iconY,
                0.0F,
                0.0F,
                16,
                16,
                32,
                32,
                32,
                32,
                -1);
    }
}
