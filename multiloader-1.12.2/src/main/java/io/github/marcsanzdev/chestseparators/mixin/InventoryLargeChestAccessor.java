package io.github.marcsanzdev.chestseparators.mixin;

import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.world.ILockableContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link InventoryLargeChest}'s private-final {@code upperChest} / {@code lowerChest} halves via
 * {@code @Accessor} mixins, replacing {@code @Shadow @Final} declarations that the manual montage's annotation
 * processor does not emit to the refmap (and so fail to bind under SRG/obf in production).
 */
@Mixin(InventoryLargeChest.class)
public interface InventoryLargeChestAccessor {

    @Accessor("upperChest")
    ILockableContainer chestseparators$getUpperChest();

    @Accessor("lowerChest")
    ILockableContainer chestseparators$getLowerChest();
}
