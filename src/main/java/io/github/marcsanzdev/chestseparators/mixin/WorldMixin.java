package io.github.marcsanzdev.chestseparators.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Listens for block replacements and removes the corresponding local config file
 * when a chest is destroyed on the client side.
 *
 * <p>The double environment guard ({@code EnvType.CLIENT} + {@code isClient()}) prevents the
 * dedicated-server JVM from ever loading {@code ChestConfigManager}, which is a client-only class
 * annotated with {@code @Environment(EnvType.CLIENT)}. Without this guard, accessing it on the
 * server would throw a {@code ClassNotFoundException} at runtime.
 */
@Mixin(World.class)
public abstract class WorldMixin {

    @Shadow
    public abstract boolean isClient();

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    @Shadow
    public abstract net.minecraft.registry.RegistryKey<World> getRegistryKey();

    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("HEAD"))
    private void onSetBlockState(
            BlockPos pos, BlockState newState, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        // Guard 1: only execute on the physical client executable.
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            // Guard 2: only act on the logical client side of the world.
            if (this.isClient()) {
                BlockState oldState = this.getBlockState(pos);

                if (oldState.getBlock() instanceof ChestBlock && oldState.getBlock() != newState.getBlock()) {
                    String dim = this.getRegistryKey().getValue().toString();
                    io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                            .clearChest(pos, dim);
                }
            }
        }
    }
}
