package io.github.marcsanzdev.chestseparators.integration;

import java.util.Optional;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** An integer slider that draws a 16×16 mod icon at its left, with the row shifted right to make room. */
public class IconIntegerSliderEntry extends IntegerSliderEntry {

    private static final int ICON_SIZE = 16;
    private static final int SHIFT = 22;
    private static final int TEX = 128;

    private final Identifier icon;

    public IconIntegerSliderEntry(
            Text fieldName,
            int min,
            int max,
            int value,
            int defaultValue,
            Consumer<Integer> saveConsumer,
            Text tooltip,
            Identifier icon) {
        super(
                fieldName,
                min,
                max,
                value,
                Text.translatable("text.cloth-config.reset_value"),
                () -> defaultValue,
                saveConsumer,
                () -> Optional.of(new Text[] {tooltip}),
                false);
        this.icon = icon;
        setTextGetter(v -> Text.literal(v + "%"));
    }

    @Override
    public void render(
            DrawContext graphics,
            int index,
            int y,
            int x,
            int entryWidth,
            int entryHeight,
            int mouseX,
            int mouseY,
            boolean isHovered,
            float delta) {
        super.render(graphics, index, y, x + SHIFT, entryWidth - SHIFT, entryHeight, mouseX, mouseY, isHovered, delta);
        int iconY = y + (entryHeight - ICON_SIZE) / 2;
        graphics.drawTexture(
                RenderPipelines.GUI_TEXTURED, icon, x, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, TEX, TEX, TEX, TEX, -1);
    }
}
