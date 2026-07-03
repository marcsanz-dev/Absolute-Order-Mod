package io.github.marcsanzdev.chestseparators.client.ui.screens;

import net.minecraft.client.gui.DrawContext;

public interface IEditorSubScreen {
    // Se llama cuando se abre la pantalla o se redimensiona la ventana
    void init();

    // Se llama 60 veces por segundo para dibujar
    void render(DrawContext context, int mouseX, int mouseY, float delta);

    // Eventos de ratón y teclado
    boolean mouseClicked(double mouseX, double mouseY, int button);
    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY);
    boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount);
    boolean keyPressed(int keyCode, int scanCode, int modifiers);
    boolean charTyped(char chr, int modifiers);
}