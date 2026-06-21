package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ChestLidAnimator;
import net.minecraft.block.entity.EnderChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the private lid animator of chests and ender chests (both use ChestLidAnimator) so the
// auto-deposit feedback can briefly play the vanilla opening animation (client-side, cosmetic) on
// the containers that receive items.
@Mixin({ChestBlockEntity.class, EnderChestBlockEntity.class})
public interface ChestLidAccessor {

    @Accessor("lidAnimator")
    ChestLidAnimator getLidAnimator();
}
