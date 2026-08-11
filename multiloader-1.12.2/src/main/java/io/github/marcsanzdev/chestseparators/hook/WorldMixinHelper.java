package io.github.marcsanzdev.chestseparators.hook;

import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Plain (non-mixin) helper carrying the {@link WorldMixin} setBlockState logic. Because this is ordinary code
 * (not a mixin), all vanilla references — {@code world.isRemote}, {@code world.getBlockState(pos)},
 * {@code world.provider.getDimension()} — are reobfuscated normally by the standard SRG remap, which the
 * manual montage's {@code @Shadow} path does not do. The mixin just forwards {@code (World) (Object) this}.
 */
public final class WorldMixinHelper {

    private WorldMixinHelper() {}

    public static void onSetBlockState(World world, BlockPos pos, IBlockState newState) {
        // Only act on the logical client side of the world.
        if (world.isRemote) {
            IBlockState oldState = world.getBlockState(pos);

            // Position-keyed containers whose separator config is stored by BlockPos: chests (incl. trapped).
            // Shulker boxes (UUID-keyed) and ender chests (global) use their own persistence and are skipped.
            boolean wasPosKeyedContainer = oldState.getBlock() instanceof BlockChest;

            if (wasPosKeyedContainer && oldState.getBlock() != newState.getBlock()) {
                String dim = String.valueOf(world.provider.getDimension());
                ChestConfigManager.getInstance().clearChest(pos, dim);
            }
        }
    }
}
