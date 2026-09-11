package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private {@code setSelectedTab} so the editor can jump the creative screen to its
 *  inventory tab when a toolbar button is used (the player inventory is only present on that tab). */
@Mixin(CreativeInventoryScreen.class)
public interface CreativeInventoryScreenAccessor {

    @Invoker("setSelectedTab")
    void chestseparators$setSelectedTab(ItemGroup group);
}
