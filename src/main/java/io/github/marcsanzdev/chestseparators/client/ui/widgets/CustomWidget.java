package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import net.minecraft.client.gui.DrawContext;

public abstract class CustomWidget {
    public int x;
    public int y;
    public int width;
    public int height;

    public boolean isActive = false;
    public boolean isDisabled = false;
    public String tooltipText = null;

    protected Runnable onClickAction;

    public CustomWidget(int x, int y, int width, int height, Runnable onClickAction) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onClickAction = onClickAction;
    }

    // Every specific widget must implement its own drawing logic
    public abstract void render(DrawContext context, int mouseX, int mouseY, float delta);

    public boolean isHovering(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !isDisabled && isHovering(mouseX, mouseY)) {
            if (onClickAction != null) {
                onClickAction.run();
            }
            return true; // Click consumed
        }
        return false;
    }

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
}