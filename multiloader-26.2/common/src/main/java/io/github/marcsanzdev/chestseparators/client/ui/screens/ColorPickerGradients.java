package io.github.marcsanzdev.chestseparators.client.ui.screens;

import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Stateless drawing helpers for the color picker: the saturation/value gradient box, the hue
 * strip, and the framebuffer pixel read used by the eyedropper tool.
 */
final class ColorPickerGradients {

    private ColorPickerGradients() {}

    /** Fills a saturation (x) by value (y) gradient for a fixed hue. */
    static void drawSaturationValueBox(GuiGraphicsExtractor context, int x, int y, int w, int h, float hue) {
        int step = 2;
        for (int i = 0; i < w; i += step) {
            for (int j = 0; j < h; j += step) {
                float sat = (float) i / w;
                float val = 1.0f - ((float) j / h);
                int color = Color.HSBtoRGB(hue, sat, val);
                context.fill(x + i, y + j, x + i + step, y + j + step, color);
            }
        }
    }

    /** Fills a vertical hue strip at full saturation and value. */
    static void drawHueBar(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        for (int i = 0; i < h; i++) {
            float hue = (float) i / h;
            int color = Color.HSBtoRGB(hue, 1.0f, 1.0f);
            context.fill(x, y + i, x + w, y + i + 1, color);
        }
    }

    /** Reads the RGB color of the framebuffer pixel under the cursor (for the eyedropper). */
    static int readHoveredPixelColor(int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        double scale = client.getWindow().getGuiScale();
        int fbX = (int) (mouseX * scale);
        int fbY = client.getWindow().getHeight() - (int) (mouseY * scale) - 1;
        // Crash-safe readback (forces a sane pixel-pack state, bounds-checked read).
        int[] px = io.github.marcsanzdev.chestseparators.client.ui.SafePixelReader.readArgb(fbX, fbY, 1, 1);
        return px == null ? 0 : (px[0] & 0x00FFFFFF);
    }
}
