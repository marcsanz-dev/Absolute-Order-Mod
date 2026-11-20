package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// BlockEntityComponentMixin already handles the component copy. This mixin only strips the mod's NBT key
// from the tag written when an item is created from a block, which has no component equivalent.
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {

    @Inject(method = "removeComponentsFromTag", at = @At("TAIL"))
    private void onRemoveComponentsFromTag(ValueOutput view, CallbackInfo ci) {
        if ((Object) this instanceof IWhitelistProvider) {
            view.discard("ChestSeparatorsWhitelists");
        }
    }
}
