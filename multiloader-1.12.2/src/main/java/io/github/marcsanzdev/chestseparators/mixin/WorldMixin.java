package io.github.marcsanzdev.chestseparators.mixin;

import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Shadow
    public boolean isRemote;

    @Shadow
    public WorldProvider provider;

    @Shadow
    public abstract IBlockState getBlockState(BlockPos pos);

    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At("HEAD"))
    private void onSetBlockState(
            BlockPos pos, IBlockState newState, int flags, CallbackInfoReturnable<Boolean> cir) {
        // Only act on the logical client side of the world.
        if (this.isRemote) {
            IBlockState oldState = this.getBlockState(pos);

            // Position-keyed containers whose separator config is stored by BlockPos: chests (incl. trapped).
            // Shulker boxes (UUID-keyed) and ender chests (global) use their own persistence and are skipped.
            boolean wasPosKeyedContainer = oldState.getBlock() instanceof BlockChest;

            if (wasPosKeyedContainer && oldState.getBlock() != newState.getBlock()) {
                String dim = String.valueOf(this.provider.getDimension());
                io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                        .clearChest(pos, dim);
            }
        }
    }
}
