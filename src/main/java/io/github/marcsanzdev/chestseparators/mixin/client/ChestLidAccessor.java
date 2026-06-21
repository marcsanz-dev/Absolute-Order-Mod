package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ChestLidAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the private lid animator of a chest so the auto-deposit feedback can briefly play the
// vanilla chest-opening animation (client-side, cosmetic) on the chests that receive items.
@Mixin(ChestBlockEntity.class)
public interface ChestLidAccessor {

    @Accessor("lidAnimator")
    ChestLidAnimator getLidAnimator();
}
