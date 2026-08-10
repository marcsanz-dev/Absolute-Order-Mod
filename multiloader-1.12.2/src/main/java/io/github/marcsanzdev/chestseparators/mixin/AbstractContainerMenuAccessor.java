package io.github.marcsanzdev.chestseparators.mixin;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the protected {@code mergeItemStack} (Mojmap {@code moveItemStackTo}) so the armor shift-click
 * fallback can reuse it. */
@Mixin(Container.class)
public interface AbstractContainerMenuAccessor {

    @Invoker("mergeItemStack")
    boolean chestseparators$moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean fromLast);
}
