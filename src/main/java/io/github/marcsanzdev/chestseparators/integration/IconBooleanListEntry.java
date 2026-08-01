package io.github.marcsanzdev.chestseparators.integration;

import java.util.Optional;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * A Cloth Config boolean toggle that draws a 16×16 mod icon at the left of the row, with the label shifted
 * right to make room. The right-aligned value/reset buttons keep their place because {@code x + entryWidth}
 * is left unchanged. Icons are the 128px smooth line glyphs, so they sample at texture size 128.
 */
public class IconBooleanListEntry extends BooleanListEntry {

    private static final int ICON_SIZE = 16;
    private static final int SHIFT = 22;
    private static final int TEX = 128;

    private final Identifier icon;

    public IconBooleanListEntry(
            Text fieldName,
            boolean value,
            boolean defaultValue,
            Consumer<Boolean> saveConsumer,
            Text tooltip,
            Identifier icon) {
        super(
                fieldName,
                value,
                Text.translatable("text.cloth-config.reset_value"),
                () -> defaultValue,
                saveConsumer,
                () -> Optional.of(new Text[] {tooltip}),
                false);
        this.icon = icon;
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
