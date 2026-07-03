package io.github.marcsanzdev.chestseparators.client.ui.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.WideButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.awt.Color;
import java.nio.ByteBuffer;

public class ScreenColorPicker extends AbstractEditorScreen {

    private TextFieldWidget hexField;
    private TextFieldWidget rField;
    private TextFieldWidget gField;
    private TextFieldWidget bField;

    private boolean isUpdatingFields = false;
    private int hoveredPixelColor = 0xFFFFFF;

    public ScreenColorPicker(ChestSeparatorsEditor editor) {
        super(editor);
    }

    @Override
    protected void buildWidgets() {

        int w = layout.popupW;
        int x = layout.popupX;
        int y = layout.popupY;
        int btnY = y + layout.popupH - 30;
        int bH = 20;

        // --- BOTONES ESTÁNDAR ---

        WideButtonWidget btnEyedropper = new WideButtonWidget(x + 10, btnY, 85, bH, Text.translatable("button.chestseparators.eyedropper").getString(), ModTextures.ICON_EYEDROPPER2, () -> {
            session.isEyedropperActive = true;
            org.lwjgl.glfw.GLFW.glfwSetInputMode(MinecraftClient.getInstance().getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN);
            editor.playClickSound(1.2f);
        });
        btnEyedropper.tooltipText = Text.translatable("tooltip.chestseparators.desc.eyedropper").getString();
        widgets.add(btnEyedropper);

        WideButtonWidget btnExit = new WideButtonWidget(x + 175, btnY, 55, bH, Text.translatable("button.chestseparators.exit").getString(), ModTextures.ICON_CANCEL, () -> {
            saveCurrentCustomColor();
            editor.playClickSound(0.8f);
            clearSelection();
            session.isColorPickerOpen = false;
            closeAndRestore();
        });
        btnExit.tooltipText = Text.translatable("tooltip.chestseparators.desc.exit").getString();
        widgets.add(btnExit);

        // --- CAJAS DE TEXTO ---
        MinecraftClient client = MinecraftClient.getInstance();

        int rightPanelX = layout.popupX + 155;
        int textX = rightPanelX + 5;
        int fieldX = textX + 12;
        int fieldY = layout.popupY + 85;

        hexField = new TextFieldWidget(client.textRenderer, fieldX, fieldY, 60, 12, Text.translatable("gui.chestseparators.hex"));
        hexField.setMaxLength(6);
        hexField.setDrawsBackground(false);
        boolean isDark = GlobalChestConfig.instance.darkMode;
        hexField.setEditableColor(isDark ? 0xFFFFFFFF : 0xFF222222);
        // Filtro: Solo permite números del 0 al 9 y letras de la A a la F (mayúsculas o minúsculas)
        hexField.setTextPredicate(text -> text.matches("^[0-9a-fA-F]*$"));
        hexField.setChangedListener(text -> {
            String upper = text.toUpperCase();
            // Si el texto tiene minúsculas, lo reescribimos en mayúsculas automáticamente
            if (!text.equals(upper)) {
                hexField.setText(upper);
            } else {
                updateColorFromFields(true);
            }
        });

        rField = new TextFieldWidget(client.textRenderer, fieldX, fieldY + 20, 30, 12, Text.translatable("gui.chestseparators.color_r"));
        rField.setMaxLength(3);
        rField.setDrawsBackground(false);
        rField.setEditableColor(0xFFFF5555);
        // Filtro: Solo permite números (bloquea el signo + y el - permitiendo que funcionen tus atajos)
        rField.setTextPredicate(text -> text.matches("^[0-9]*$"));
        rField.setChangedListener(text -> {
            // Clampeo automático si te pasas de 255
            if (!text.isEmpty() && Integer.parseInt(text) > 255) {
                rField.setText("255");
            } else {
                updateColorFromFields(false);
            }
        });

        gField = new TextFieldWidget(client.textRenderer, fieldX, fieldY + 36, 30, 12, Text.translatable("gui.chestseparators.color_g"));
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

        bField = new TextFieldWidget(client.textRenderer, fieldX, fieldY + 52, 30, 12, Text.translatable("gui.chestseparators.color_b"));
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

    // --- NUEVO: Función de guardado dinámico ---
    private void saveCurrentCustomColor() {
        int customIndex = (session.pickerTargetMode == 0) ? session.editingLineCustomIndex : ((session.pickerTargetMode == 1) ? session.editingBgCustomIndex : session.editingComboCustomIndex);

        if (customIndex != -1) {
            ChestConfigManager.getInstance().setCustomColor(customIndex, session.pickerCurrentRGB, session.pickerTargetMode);
            ChestConfigManager.getInstance().saveWorldPalette();
        }
    }

    private void clearSelection() {
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
            hexField.setEditableColor(isDark ? 0xFFFFFFFF : 0xFF222222); // Color por defecto si está vacío
        } else {
            int r = (session.pickerCurrentRGB >> 16) & 0xFF;
            int g = (session.pickerCurrentRGB >> 8) & 0xFF;
            int b = session.pickerCurrentRGB & 0xFF;

            hexField.setText(String.format("%02X%02X%02X", r, g, b));
            rField.setText(String.valueOf(r));
            gField.setText(String.valueOf(g));
            bField.setText(String.valueOf(b));

            // --- NUEVO: Asignamos el color seleccionado al texto Hexadecimal ---
            hexField.setEditableColor(session.pickerCurrentRGB | 0xFF000000);
        }

        isUpdatingFields = false;
    }

    private void updateColorFromFields(boolean fromHex) {
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
            }
            isUpdatingFields = false;

            saveCurrentCustomColor();

        } catch (NumberFormatException ignored) {}
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!session.isColorPickerOpen) return false;

        if (session.isEyedropperActive) {
            if (button == 1) {
                session.isEyedropperActive = false;
                org.lwjgl.glfw.GLFW.glfwSetInputMode(MinecraftClient.getInstance().getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
                editor.playClickSound(0.8f);
                return true;
            }
            if (button == 0) {
                editor.colorPickerModified = true; // El cuentagotas cuenta como modificación
                session.pickerCurrentRGB = hoveredPixelColor;
                float[] hsb = Color.RGBtoHSB((hoveredPixelColor >> 16) & 0xFF, (hoveredPixelColor >> 8) & 0xFF, hoveredPixelColor & 0xFF, null);
                session.pickerHue = hsb[0];
                session.pickerSat = hsb[1];
                session.pickerVal = hsb[2];
                updateFieldsFromColor();
                saveCurrentCustomColor();

                session.isEyedropperActive = false;
                org.lwjgl.glfw.GLFW.glfwSetInputMode(MinecraftClient.getInstance().getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
                editor.playClickSound(1.2f);
                return true;
            }
            return false;
        }

        if (button == 0) {
            int pCol3X = layout.paletteCol3X;
            int pY = layout.paletteY;
            int sS = layout.swatchSize;
            int gap = 4;
            int tabMode = session.pickerTargetMode;

            for (int i = 0; i < 8; i++) {
                int slotY = pY + (i * (sS + gap));
                if (editor.isHovering(pCol3X, slotY, sS, sS, mouseX, mouseY)) {
                    editor.playClickSound(1.0f);
                    if (editor.colorPickerModified) saveCurrentCustomColor();

                    if (tabMode == 0) { session.editingLineCustomIndex = i; session.lineColorIndex = 16 + i; }
                    else if (tabMode == 1) { session.editingBgCustomIndex = i; session.bgColorIndex = 16 + i; }
                    else { session.editingComboCustomIndex = i; session.comboColorIndex = 16 + i; }

                    int newColor = ChestConfigManager.getInstance().getCustomColors(tabMode)[i];
                    editor.colorPickerModified = false; // Al saltar a otro, lo reseteamos

                    if (newColor == 0) {
                        session.pickerCurrentRGB = 0;
                    } else {
                        newColor = newColor & 0xFFFFFF;
                        session.pickerCurrentRGB = newColor;
                        float[] hsb = Color.RGBtoHSB((newColor >> 16) & 0xFF, (newColor >> 8) & 0xFF, newColor & 0xFF, null);
                        session.pickerHue = hsb[0];
                        session.pickerSat = hsb[1];
                        session.pickerVal = hsb[2];
                    }
                    updateFieldsFromColor();
                    return true;
                }
            }
        }

        boolean inPopup = mouseX >= layout.popupX && mouseX <= layout.popupX + layout.popupW && mouseY >= layout.popupY && mouseY <= layout.popupY + layout.popupH;

        if (inPopup) {
            if (button == 0) {
                boolean clickedText = false;
                MinecraftClient client = MinecraftClient.getInstance();

                if (hexField != null) {
                    if (editor.isHovering(hexField.getX(), hexField.getY(), hexField.getWidth(), hexField.getHeight(), mouseX, mouseY)) {
                        hexField.setFocused(true);
                        // ELIMINAMOS EL "- 4" porque no hay fondo Vanilla
                        int localX = (int)(mouseX - hexField.getX());
                        hexField.setCursor(client.textRenderer.trimToWidth(hexField.getText(), Math.max(0, localX)).length(), false);
                        clickedText = true;
                    } else hexField.setFocused(false);
                }

                if (rField != null) {
                    if (editor.isHovering(rField.getX(), rField.getY(), rField.getWidth(), rField.getHeight(), mouseX, mouseY)) {
                        rField.setFocused(true);
                        int localX = (int)(mouseX - rField.getX());
                        rField.setCursor(client.textRenderer.trimToWidth(rField.getText(), Math.max(0, localX)).length(), false);
                        clickedText = true;
                    } else rField.setFocused(false);
                }

                if (gField != null) {
                    if (editor.isHovering(gField.getX(), gField.getY(), gField.getWidth(), gField.getHeight(), mouseX, mouseY)) {
                        gField.setFocused(true);
                        int localX = (int)(mouseX - gField.getX());
                        gField.setCursor(client.textRenderer.trimToWidth(gField.getText(), Math.max(0, localX)).length(), false);
                        clickedText = true;
                    } else gField.setFocused(false);
                }

                if (bField != null) {
                    if (editor.isHovering(bField.getX(), bField.getY(), bField.getWidth(), bField.getHeight(), mouseX, mouseY)) {
                        bField.setFocused(true);
                        int localX = (int)(mouseX - bField.getX());
                        bField.setCursor(client.textRenderer.trimToWidth(bField.getText(), Math.max(0, localX)).length(), false);
                        clickedText = true;
                    } else bField.setFocused(false);
                }

                if (clickedText) {
                    editor.playClickSound(1.0f);
                    return true;
                }

                int contentX = layout.popupX + 12;
                int contentY = layout.popupY + 45;
                // ... (el resto sigue igual)

                if (mouseX >= contentX && mouseX <= contentX + 100 && mouseY >= contentY && mouseY <= contentY + 100) {
                    session.isDraggingSatVal = true;
                    session.lastClickedHue = false;
                    updateColorFromMouse(mouseX, mouseY, true);
                    return true;
                }
                else if (mouseX >= contentX + 115 && mouseX <= contentX + 135 && mouseY >= contentY && mouseY <= contentY + 100) {
                    session.isDraggingHue = true;
                    session.lastClickedHue = true;
                    updateColorFromMouse(mouseX, mouseY, false);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        } else {
            if (button == 0 || button == 1) {
                if (GlobalChestConfig.instance.closeOnClickOutside) {
                    closeAndRestore(); // Nuestro nuevo método de cierre
                    return true;
                }
            }
        }
        return false;
    }

    public void closeAndRestore() {
        boolean hasNoColor = (session.pickerCurrentRGB == 0 && !editor.colorPickerModified);

        if (editor.colorPickerModified) {
            saveCurrentCustomColor();
        } else if (hasNoColor) {
            // SI SIGUE VACÍO: Volvemos al color anterior y quitamos la selección del hueco personalizado
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!session.isColorPickerOpen || session.isEyedropperActive) return false;

        if (button == 0) {
            if (session.isDraggingSatVal) {
                updateColorFromMouse(mouseX, mouseY, true);
                return true;
            } else if (session.isDraggingHue) {
                updateColorFromMouse(mouseX, mouseY, false);
                return true;
            }
        }
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            session.isDraggingSatVal = false;
            session.isDraggingHue = false;
        }
        return false;
    }

    private void updateColorFromMouse(double mx, double my, boolean isSatValBox) {
        editor.colorPickerModified = true;
        int contentX = layout.popupX + 12;
        int contentY = layout.popupY + 45;

        if (isSatValBox) {
            float relX = MathHelper.clamp((float)(mx - contentX), 0.0f, 100.0f);
            float relY = MathHelper.clamp((float)(my - contentY), 0.0f, 100.0f);

            session.pickerSat = relX / 100.0f;
            session.pickerVal = 1.0f - (relY / 100.0f);
        } else {
            float relY = MathHelper.clamp((float)(my - contentY), 0.0f, 100.0f);
            session.pickerHue = relY / 100.0f;
        }

        session.pickerCurrentRGB = Color.HSBtoRGB(session.pickerHue, session.pickerSat, session.pickerVal);
        updateFieldsFromColor();

        saveCurrentCustomColor();
    }

    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (!session.isColorPickerOpen) return false;

        if (session.isEyedropperActive && input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            session.isEyedropperActive = false;
            org.lwjgl.glfw.GLFW.glfwSetInputMode(MinecraftClient.getInstance().getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
            return true;
        }

        int key = input.key();
        boolean isAnyFieldFocused = (hexField != null && hexField.isFocused()) ||
                (rField != null && rField.isFocused()) ||
                (gField != null && gField.isFocused()) ||
                (bField != null && bField.isFocused());

        // --- NUEVA LÓGICA GENERAL DE LA TECLA ESCAPE ---
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (isAnyFieldFocused) {
                // Si estás escribiendo, solo quitamos el foco
                if (hexField != null) hexField.setFocused(false);
                if (rField != null) rField.setFocused(false);
                if (gField != null) gField.setFocused(false);
                if (bField != null) bField.setFocused(false);
            } else {
                // Si no estás escribiendo, cerramos y restauramos
                closeAndRestore();
            }
            return true;
        }

        // --- 1. NAVEGACIÓN CON TABULADOR ---
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            if (hexField != null && hexField.isFocused()) { hexField.setFocused(false); rField.setFocused(true); }
            else if (rField != null && rField.isFocused()) { rField.setFocused(false); gField.setFocused(true); }
            else if (gField != null && gField.isFocused()) { gField.setFocused(false); bField.setFocused(true); }
            else if (bField != null && bField.isFocused()) { bField.setFocused(false); hexField.setFocused(true); }
            else if (hexField != null) { hexField.setFocused(true); }
            return true;
        }

        // --- 2. ACCIONES DENTRO DE LAS CAJAS DE TEXTO ---
        if (isAnyFieldFocused) {
            boolean isUp = key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD || key == org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL;
            boolean isDown = key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT;

            if (isUp || isDown) {
                editor.colorPickerModified = true; // El teclado también cuenta como modificación
                int delta = isUp ? 1 : -1;
                try {
                    if (hexField.isFocused()) {
                        String hex = hexField.getText().trim().replace("#", "");
                        int val = hex.isEmpty() ? 0 : Integer.parseInt(hex, 16);
                        val = MathHelper.clamp(val + delta, 0, 0xFFFFFF);
                        hexField.setText(String.format("%02X%02X%02X", (val >> 16) & 0xFF, (val >> 8) & 0xFF, val & 0xFF));
                        updateColorFromFields(true);
                    } else if (rField.isFocused()) {
                        int val = Integer.parseInt(rField.getText().isEmpty() ? "0" : rField.getText());
                        rField.setText(String.valueOf(MathHelper.clamp(val + delta, 0, 255)));
                        updateColorFromFields(false);
                    } else if (gField.isFocused()) {
                        int val = Integer.parseInt(gField.getText().isEmpty() ? "0" : gField.getText());
                        gField.setText(String.valueOf(MathHelper.clamp(val + delta, 0, 255)));
                        updateColorFromFields(false);
                    } else if (bField.isFocused()) {
                        int val = Integer.parseInt(bField.getText().isEmpty() ? "0" : bField.getText());
                        bField.setText(String.valueOf(MathHelper.clamp(val + delta, 0, 255)));
                        updateColorFromFields(false);
                    }
                    saveCurrentCustomColor();
                } catch (NumberFormatException ignored) {}
                return true;
            }

            // Procesamos teclas especiales (borrar, copiar, pegar) dentro de los TextFields
            if (hexField != null && hexField.isFocused()) hexField.keyPressed(input);
            else if (rField != null && rField.isFocused()) rField.keyPressed(input);
            else if (gField != null && gField.isFocused()) gField.keyPressed(input);
            else if (bField != null && bField.isFocused()) bField.keyPressed(input);

            // Devolvemos SIEMPRE true. Esto "se come" la pulsación para que Minecraft no la vea
            return true;
        }
        // --- 3. MOVIMIENTO DE PRECISIÓN EN LOS CUADROS DE COLOR ---
        else {
            float step = 0.01f;
            boolean changed = false;

            if (session.lastClickedHue) {
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
                    session.pickerHue = MathHelper.clamp(session.pickerHue - step, 0.0f, 1.0f);
                    changed = true;
                } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                    session.pickerHue = MathHelper.clamp(session.pickerHue + step, 0.0f, 1.0f);
                    changed = true;
                }
            } else {
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
                    session.pickerSat = MathHelper.clamp(session.pickerSat - step, 0.0f, 1.0f);
                    changed = true;
                } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
                    session.pickerSat = MathHelper.clamp(session.pickerSat + step, 0.0f, 1.0f);
                    changed = true;
                } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
                    session.pickerVal = MathHelper.clamp(session.pickerVal + step, 0.0f, 1.0f);
                    changed = true;
                } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                    session.pickerVal = MathHelper.clamp(session.pickerVal - step, 0.0f, 1.0f);
                    changed = true;
                }
            }

            if (changed) {
                editor.colorPickerModified = true; // Movimiento con flechas es modificación
                session.pickerCurrentRGB = Color.HSBtoRGB(session.pickerHue, session.pickerSat, session.pickerVal);
                updateFieldsFromColor();
                saveCurrentCustomColor();
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (!session.isColorPickerOpen) return false;
        if (hexField != null && hexField.isFocused()) return hexField.charTyped(input);
        if (rField != null && rField.isFocused()) return rField.charTyped(input);
        if (gField != null && gField.isFocused()) return gField.charTyped(input);
        if (bField != null && bField.isFocused()) return bField.charTyped(input);
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!session.isColorPickerOpen) return;

        if (session.isEyedropperActive) {
            readHoveredPixelColor(mouseX, mouseY);
            renderEyedropperCursor(context, mouseX, mouseY);
            return;
        }

        // --- NIVEL 2 DE PROFUNDIDAD ---
        // 1. Oscurecemos TODA la pantalla con la sombra negra
        context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0x77000000);

        // 2. RE-DIBUJAMOS solo los 8 recuadros por encima de la sombra para que brillen
        int pCol3X = layout.paletteCol3X;
        int pY = layout.paletteY;
        int sS = layout.swatchSize;
        int gap = 4;
        int tabMode = session.pickerTargetMode;
        int[] worldColors = ChestConfigManager.getInstance().getCustomColors(tabMode);

        for (int i = 0; i < 8; i++) {
            int slotY = pY + (i * (sS + gap));
            // Usamos la función de la otra clase para que el dibujo sea 100% idéntico
            editor.screenDrawLines.drawSwatch(context, pCol3X, slotY, worldColors[i], 16 + i, mouseX, mouseY, true, tabMode);
        }

        // 3. Dibujamos la ventana del Color Picker normal
        int w = layout.popupW; int h = layout.popupH;
        int x = layout.popupX; int y = layout.popupY;
        boolean isDark = GlobalChestConfig.instance.darkMode;

        context.fill(x, y, x + w, y + h, isDark ? 0xFF212121 : 0xFFC6C6C6);
        drawDarkBevel(context, x, y, w, h, false);

        MinecraftClient client = MinecraftClient.getInstance();

        Text titleText = Text.translatable("window.chestseparators.edit_color").formatted(Formatting.BOLD);
        int titleWidth = client.textRenderer.getWidth(titleText);
        int rightPanelStartX = layout.popupX + 155;
        int maxTitleWidth = (rightPanelStartX - 5) - (x + 12);

        float titleScale = 1.0f;
        if (titleWidth > maxTitleWidth) titleScale = (float) maxTitleWidth / titleWidth;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)(x + 12), (float)(y + 12 + (9 * (1.0f - titleScale)) / 2));
        context.getMatrices().scale(titleScale, titleScale);
        context.drawText(client.textRenderer, titleText, 0, 0, isDark ? 0xFFE0E0E0 : 0xFF333333, false);
        context.getMatrices().popMatrix();

        Text instructionText = Text.translatable("gui.chestseparators.color_picker.instructions").formatted(Formatting.GRAY);
        int maxTextWidth = 135;
        float scale = 1.0f;

        // 1. Obtenemos cómo Minecraft cortaría las líneas a escala normal (1.0)
        java.util.List<net.minecraft.text.OrderedText> wrappedLines = client.textRenderer.wrapLines(instructionText, maxTextWidth);

        // 2. Bucle de auto-ajuste: Si ocupa más de 2 líneas, reducimos la escala un 5% y recalculamos
        while (wrappedLines.size() > 2 && scale > 0.4f) {
            scale -= 0.05f;
            int wrapWidth = (int) (maxTextWidth / scale); // Al hacer la escala más pequeña, el ancho lógico disponible es mayor
            wrappedLines = client.textRenderer.wrapLines(instructionText, wrapWidth);
        }

        context.getMatrices().pushMatrix();

        // Un pequeño detalle: si por la escala se ha quedado muy pequeño, lo centramos un poco verticalmente
        float yOffset = (2 - wrappedLines.size()) * (client.textRenderer.fontHeight + 2) * scale / 2.0f;
        context.getMatrices().translate((float)(x + 12), (float)(y + 26) + yOffset);
        context.getMatrices().scale(scale, scale);

        int instructY = 0;
        for (net.minecraft.text.OrderedText line : wrappedLines) {
            context.drawText(client.textRenderer, line, 0, instructY, isDark ? 0xFFFFFFFF : 0xFF222222, false);
            instructY += client.textRenderer.fontHeight + 2;
        }
        context.getMatrices().popMatrix();

        int contentY = layout.popupY + 45;
        int contentX = layout.popupX + 12;

        drawSaturationValueBox(context, contentX, contentY, layout.pickerBoxSize, layout.pickerBoxSize);
        drawDarkBevel(context, contentX - 1, contentY - 1, 102, 102, true);

        int cursorX = contentX + (int)(session.pickerSat * 100);
        int cursorY = contentY + (int)((1.0f - session.pickerVal) * 100);
        context.drawStrokedRectangle(cursorX - 2, cursorY - 2, 5, 5, 0xFF000000);
        context.drawStrokedRectangle(cursorX - 1, cursorY - 1, 3, 3, 0xFFFFFFFF);

        int hueX = contentX + 115;
        drawHueBar(context, hueX, contentY, layout.pickerHueWidth, layout.pickerBoxSize);
        drawDarkBevel(context, hueX - 1, contentY - 1, 22, 102, true);

        int hueCursorY = contentY + (int)(session.pickerHue * 100);
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

        // --- 4. TOOLTIPS DE LOS COLORES PERSONALIZADOS ---
        // Se dibujan AL FINAL del todo para que floten por encima de la ventana del Picker si se cruzan
        for (int i = 0; i < 8; i++) {
            int slotY = pY + (i * (sS + gap));
            if (editor.isHovering(pCol3X, slotY, sS, sS, mouseX, mouseY)) {
                context.drawTooltip(client.textRenderer, Text.translatable("color.chestseparators.custom", (i + 1)), mouseX, mouseY);
            }
        }
    }

    private void readHoveredPixelColor(int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        double scale = client.getWindow().getScaleFactor();
        int fbX = (int) (mouseX * scale);
        int fbY = client.getWindow().getFramebufferHeight() - (int) (mouseY * scale) - 1;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buffer = stack.malloc(4);
            GL11.glReadPixels(fbX, fbY, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            int r = buffer.get(0) & 0xFF;
            int g = buffer.get(1) & 0xFF;
            int b = buffer.get(2) & 0xFF;
            hoveredPixelColor = (r << 16) | (g << 8) | b;
        }
    }

    private void renderEyedropperCursor(DrawContext context, int mx, int my) {
        int iconX = mx - 3;
        int iconY = my - 12;

        int previewX = mx + 18;
        int previewY = my - 24;
        int pw = 16; int ph = 16;

        context.fill(previewX, previewY, previewX + pw, previewY + ph, 0xFF000000 | hoveredPixelColor);
        drawColorBevel(context, previewX, previewY, pw, ph, hoveredPixelColor, false);

        com.mojang.blaze3d.pipeline.RenderPipeline pipeline = net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED;
        context.drawTexture(pipeline, io.github.marcsanzdev.chestseparators.client.ModTextures.ICON_EYEDROPPER, iconX, iconY, 0.0F, 0.0F, 16, 16, 32, 32, 32, 32, -1);
    }

    private void drawColorBevel(DrawContext context, int x, int y, int width, int height, int baseColor, boolean sunken) {
        int light = editor.shiftColor(baseColor, 80) | 0xFF000000;
        int dark = editor.shiftColor(baseColor, -80) | 0xFF000000;
        int shadow = editor.shiftColor(baseColor, -40) | 0xFF000000;

        if (sunken) {
            context.fill(x, y, x + width - 1, y + 1, dark);
            context.fill(x, y, x + 1, y + height - 1, dark);
            context.fill(x + width - 1, y, x + width, y + height, light);
            context.fill(x, y + height - 1, x + width, y + height, light);
        } else {
            context.fill(x, y, x + width - 1, y + 1, light);
            context.fill(x, y, x + 1, y + height - 1, light);
            context.fill(x + width - 1, y, x + width, y + height, dark);
            context.fill(x, y + height - 1, x + width, y + height, dark);
            context.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, shadow);
            context.fill(x + 1, y + height - 2, x + width - 2, y + height - 1, shadow);
        }
    }

    private void drawSaturationValueBox(DrawContext context, int x, int y, int w, int h) {
        int step = 2;
        for (int i = 0; i < w; i += step) {
            for (int j = 0; j < h; j += step) {
                float sat = (float)i / w;
                float val = 1.0f - ((float)j / h);
                int color = Color.HSBtoRGB(session.pickerHue, sat, val);
                context.fill(x + i, y + j, x + i + step, y + j + step, color);
            }
        }
    }

    private void drawHueBar(DrawContext context, int x, int y, int w, int h) {
        for (int i = 0; i < h; i++) {
            float hue = (float)i / h;
            int color = Color.HSBtoRGB(hue, 1.0f, 1.0f);
            context.fill(x, y + i, x + w, y + i + 1, color);
        }
    }

}