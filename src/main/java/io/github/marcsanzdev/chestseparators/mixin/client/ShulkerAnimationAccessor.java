package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the private animation stage of a shulker box so the auto-deposit feedback can drive its
// lid-opening animation (client-side, cosmetic): the block entity's ticker advances the progress
// once the stage is set to OPENING/CLOSING.
@Mixin(ShulkerBoxBlockEntity.class)
public interface ShulkerAnimationAccessor {

    @Accessor("animationStage")
    void setAnimationStage(ShulkerBoxBlockEntity.AnimationStage stage);
}
