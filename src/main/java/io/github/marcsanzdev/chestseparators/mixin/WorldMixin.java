package io.github.marcsanzdev.chestseparators.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

@Mixin(World.class)
public abstract class WorldMixin {

    @Shadow public abstract boolean isClient();
    @Shadow public abstract BlockState getBlockState(BlockPos pos);
    @Shadow public abstract net.minecraft.registry.RegistryKey<World> getRegistryKey();

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At("HEAD"))
    private void onSetBlockState(BlockPos pos, BlockState newState, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        // 1. Escudo de entorno: Solo pasamos de aquí si físicamente estamos en el ejecutable del Cliente
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {

            // 2. Escudo lógico: Solo actuamos en el lado cliente del mundo
            if (this.isClient()) {
                BlockState oldState = this.getBlockState(pos);

                if (oldState.getBlock() instanceof ChestBlock && oldState.getBlock() != newState.getBlock()) {
                    String dim = this.getRegistryKey().getValue().toString();

                    // Al llamar al manager de esta forma, el servidor dedicado nunca intentará cargar la clase,
                    // y nos libramos del "crasheo" de ClassNotFoundException.
                    io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance().clearChest(pos, dim);
                }
            }
        }
    }
}