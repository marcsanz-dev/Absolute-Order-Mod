package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin exposing {@link GuiContainer}'s protected layout fields so the editor can align its overlay
 * with the vanilla container GUI. 1.12.2 field map (Mojmap → MCP): leftPos→guiLeft, topPos→guiTop,
 * imageWidth→xSize, imageHeight→ySize, hoveredSlot→hoveredSlot, menu→inventorySlots.
 */
@Mixin(GuiContainer.class)
public interface HandledScreenAccessor {

    @Accessor("guiLeft")
    int getX();

    @Accessor("guiLeft")
    void setX(int x);

    @Accessor("guiTop")
    int getY();

    @Accessor("xSize")
    int getBackgroundWidth();

    @Accessor("ySize")
    int getBackgroundHeight();

    @Accessor("hoveredSlot")
    Slot getFocusedSlot();

    @Accessor("inventorySlots")
    Container getHandler();
}
