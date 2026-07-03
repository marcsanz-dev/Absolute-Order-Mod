package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShulkerBoxBlock.class)
public class ShulkerBoxBlockMixin {

    @Inject(method = "onBreak", at = @At("HEAD"))
    private void forceDropEmptyCustomShulker(World world, BlockPos pos, BlockState state, PlayerEntity player, CallbackInfoReturnable<BlockState> cir) {
        // Intervenimos SOLO si el jugador está en creativo (en supervivencia el juego ya la dropea siempre)
        if (!world.isClient() && player.isCreative()) {
            BlockEntity be = world.getBlockEntity(pos);

            if (be instanceof ShulkerBoxBlockEntity shulker && shulker.isEmpty()) {
                boolean hasCustomData = false;

                // 1. Comprobamos si tiene líneas visuales (UUID)
                if (shulker instanceof IShulkerUUIDProvider provider && provider.getShulkerUUID() != null) {
                    hasCustomData = true;
                }

                // 2. Comprobamos si tiene Filtros
                if (shulker instanceof IWhitelistProvider provider && provider.getWhitelists() != null && !provider.getWhitelists().isEmpty()) {
                    hasCustomData = true;
                }

                // Si la Shulker está vacía de objetos, pero TIENE datos de nuestro mod, forzamos que caiga al suelo.
                if (hasCustomData) {
                    // AQUÍ ESTÁ LA SOLUCIÓN: Directamente cogemos el bloque actual y lo convertimos en ítem
                    ItemStack itemStack = new ItemStack(((ShulkerBoxBlock)(Object)this).asItem());

                    // Empaquetamos nuestra memoria en el ítem usando los métodos nativos
                    itemStack.applyComponentsFrom(shulker.createComponentMap());

                    // Hacemos aparecer el ítem en el mundo
                    ItemEntity itemEntity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, itemStack);
                    itemEntity.setToDefaultPickupDelay();
                    world.spawnEntity(itemEntity);
                }
            }
        }
    }
}