package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorGeometry;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.CustomWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractEditorScreen implements IEditorSubScreen {

    protected final ChestSeparatorsEditor editor;
    protected final EditorSessionData session;
    protected final EditorGeometry geometry;
    protected final EditorLayout layout;

    // Lista automática donde guardaremos los botones de cada pantalla
    protected final List<CustomWidget> widgets = new ArrayList<>();

    public AbstractEditorScreen(ChestSeparatorsEditor editor) {
        this.editor = editor;
        this.session = editor.session;
        this.geometry = editor.geometry;
        this.layout = editor.layout;
    }

    @Override
    public void init() {
        widgets.clear();
        buildWidgets();
    }

    // Cada pantalla hija definirá qué botones tiene aquí
    protected abstract void buildWidgets();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dibuja automáticamente todos los botones de esta pantalla
        for (CustomWidget widget : widgets) {
            widget.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Pasa el clic automáticamente a los botones
        for (CustomWidget widget : widgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true; // Si el botón consume el clic, paramos
            }
        }
        return false;
    }

    // Métodos vacíos por defecto para no obligar a implementarlos si una pantalla no los usa
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { return false; }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    @Override public boolean charTyped(char chr, int modifiers) { return false; }

    protected void drawDarkBevel(DrawContext context, int x, int y, int width, int height, boolean sunken) {
        boolean isDark = GlobalChestConfig.instance.darkMode;

        // Si es oscuro, usa tus colores actuales. Si es claro, usa los Vanilla.
        int light = isDark ? 0xFF505050 : 0xFFFFFFFF;
        int dark = isDark ? 0xFF000000 : 0xFF555555;

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
        }
    }

    protected void drawCheckerboard(DrawContext context, int x, int y, int width, int height, int squareSize) {
        for (int i = 0; i < width; i += squareSize) {
            for (int j = 0; j < height; j += squareSize) {
                int drawW = Math.min(squareSize, width - i);
                int drawH = Math.min(squareSize, height - j);
                int color = (((i / squareSize) + (j / squareSize)) % 2 == 0) ? 0xFF888888 : 0xFF555555;
                context.fill(x + i, y + j, x + i + drawW, y + j + drawH, color);
            }
        }
    }
}