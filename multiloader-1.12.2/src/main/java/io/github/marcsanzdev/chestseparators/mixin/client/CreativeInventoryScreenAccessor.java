package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes the private {@code setCurrentCreativeTab} so the editor can jump the creative screen to its
 * inventory tab when a toolbar button is used (the player inventory is only present on that tab).
 */
@Mixin(GuiContainerCreative.class)
public interface CreativeInventoryScreenAccessor {

    @Invoker("setCurrentCreativeTab")
    void chestseparators$setSelectedTab(CreativeTabs group);
}
