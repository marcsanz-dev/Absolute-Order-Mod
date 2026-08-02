package io.github.marcsanzdev.chestseparators.client.config;

import java.util.Optional;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** An integer slider that draws a 16×16 mod icon at its left, with the row shifted right to make room. */
public class IconIntegerSliderEntry extends IntegerSliderEntry {

    private static final int ICON_SIZE = 16;
    private static final int SHIFT = 22;
    private static final int TEX = 128;

    private final Identifier icon;

    public IconIntegerSliderEntry(
            Component fieldName,
            int min,
            int max,
            int value,
            int defaultValue,
            Consumer<Integer> saveConsumer,
            Component tooltip,
            Identifier icon) {
        super(
                fieldName,
                min,
                max,
                value,
                Component.translatable("text.cloth-config.reset_value"),
                () -> defaultValue,
                saveConsumer,
                () -> Optional.of(new Component[] {tooltip}),
                false);
        this.icon = icon;
        setTextGetter(v -> Component.literal(v + "%"));
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int index,
            int y,
            int x,
            int entryWidth,
            int entryHeight,
            int mouseX,
            int mouseY,
            boolean isHovered,
            float delta) {
        super.extractRenderState(graphics, index, y, x + SHIFT, entryWidth - SHIFT, entryHeight, mouseX, mouseY, isHovered, delta);
        int iconY = y + (entryHeight - ICON_SIZE) / 2;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED, icon, x, iconY, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, TEX, TEX, TEX, TEX, -1);
    }
}
