package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.access.LidAnimatorAccess;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Drives the chest's lid animation for the auto-deposit feedback (client-side, cosmetic). 1.16.5 has no
// ChestLidController (added in 1.17): the lid opens while openCount > 0 and the block entity's tick()
// interpolates the angle, so opening/closing just sets that field. See EnderChestLidAccessor and
// LidAnimatorAccess.
@Mixin(ChestBlockEntity.class)
public abstract class ChestLidAccessor implements LidAnimatorAccess {

    @Shadow
    protected int openCount;

    @Override
    public void chestseparators$setLidOpen(boolean open) {
        this.openCount = open ? 1 : 0;
    }
}
