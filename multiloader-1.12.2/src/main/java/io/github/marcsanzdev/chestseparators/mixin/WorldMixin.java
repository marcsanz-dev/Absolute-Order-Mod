package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.hook.WorldMixinHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the local config file when a position-keyed container (chest / trapped chest) is destroyed on the
 * client side. Registered in the mixin config's {@code client} section so the dedicated-server JVM never
 * loads {@code ChestConfigManager}; the {@code isRemote} guard then restricts it to the logical client side.
 *
 * <p>1.12.2 deltas from the modern mixin: {@code Level}→{@code World}; the hook target is the 3-arg
 * {@code setBlockState(BlockPos, IBlockState, int)} (no {@code maxUpdateDepth}); {@code isClientSide()}→the
 * {@code isRemote} field; {@code dimension().location()}→{@code provider.getDimension()} (int id, matching the
 * String the ported ChestConfigManager keys by); barrels do not exist in 1.12.2, so only chests are handled.
 */
@Mixin(World.class)
public abstract class WorldMixin {

    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At("HEAD"))
    private void onSetBlockState(
            BlockPos pos, IBlockState newState, int flags, CallbackInfoReturnable<Boolean> cir) {
        // Logic lives in a plain helper so the vanilla field/method access is reobfuscated normally
        // (the manual montage does not emit @Shadow mappings to the refmap).
        WorldMixinHelper.onSetBlockState((World) (Object) this, pos, newState);
    }
}
