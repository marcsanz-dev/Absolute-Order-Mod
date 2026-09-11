package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.access.LidAnimatorAccess;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ChestLidAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the chest's private lid animator so the auto-deposit feedback can briefly play the vanilla
// opening animation (client-side, cosmetic). Single-target on purpose: a shared @Accessor across both
// ChestBlockEntity and EnderChestBlockEntity remaps to only one field's intermediary name and crashes the
// other at runtime. See EnderChestLidAccessor and LidAnimatorAccess.
@Mixin(ChestBlockEntity.class)
public interface ChestLidAccessor extends LidAnimatorAccess {

    @Override
    @Accessor("lidAnimator")
    ChestLidAnimator getLidAnimator();
}
