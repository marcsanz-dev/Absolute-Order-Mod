package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.access.LidAnimatorAccess;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Ender-chest counterpart to ChestLidAccessor. 1.16.5 has no ChestLidController; the lid opens while
// openCount > 0, so opening/closing just drives that field. See LidAnimatorAccess.
@Mixin(EnderChestBlockEntity.class)
public abstract class EnderChestLidAccessor implements LidAnimatorAccess {

    @Shadow
    public int openCount;

    @Override
    public void chestseparators$setLidOpen(boolean open) {
        this.openCount = open ? 1 : 0;
    }
}
