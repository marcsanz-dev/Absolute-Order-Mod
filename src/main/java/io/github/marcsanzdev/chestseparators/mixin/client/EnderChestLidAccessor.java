package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.access.LidAnimatorAccess;
import net.minecraft.block.entity.ChestLidAnimator;
import net.minecraft.block.entity.EnderChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Ender-chest counterpart of ChestLidAccessor. Kept as its own single-target accessor because the ender
// chest's lidAnimator field has a different intermediary name than the chest's; sharing one @Accessor
// across both targets remaps to only one and crashes the other at runtime. See LidAnimatorAccess.
@Mixin(EnderChestBlockEntity.class)
public interface EnderChestLidAccessor extends LidAnimatorAccess {

    @Override
    @Accessor("lidAnimator")
    ChestLidAnimator getLidAnimator();
}
