package io.github.marcsanzdev.chestseparators.mixin;

import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// BlockEntityComponentMixin already handles addComponents and readComponents.
// This mixin only handles the NBT cleanup on copy, which has no equivalent in BlockEntityComponentMixin.
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {

    @Inject(method = "removeFromCopiedStackData", at = @At("TAIL"))
    private void onRemoveFromCopiedStackData(net.minecraft.storage.WriteView view, CallbackInfo ci) {
        if ((Object) this instanceof io.github.marcsanzdev.chestseparators.access.IWhitelistProvider) {
            view.remove("ChestSeparatorsWhitelists");
        }
    }
}
