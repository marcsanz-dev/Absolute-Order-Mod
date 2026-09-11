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

/**
 * Forces an empty Shulker Box to drop as an item in Creative mode when it carries mod data.
 *
 * <p>Vanilla skips the item drop for empty Shulker Boxes in Creative mode. If the box has a
 * separator UUID or slot-whitelist filters, that data would be permanently lost on break.
 * This mixin intercepts the break event and manually spawns the item with all data components
 * already embedded via {@code createComponentMap()}.
 */
@Mixin(ShulkerBoxBlock.class)
public class ShulkerBoxBlockMixin {

    @Inject(method = "onBreak", at = @At("HEAD"))
    private void forceDropEmptyCustomShulker(
            World world, BlockPos pos, BlockState state, PlayerEntity player, CallbackInfoReturnable<BlockState> cir) {
        // Survival mode always drops the item; only Creative needs special handling.
        if (!world.isClient() && player.isCreative()) {
            BlockEntity be = world.getBlockEntity(pos);

            if (be instanceof ShulkerBoxBlockEntity shulker && shulker.isEmpty()) {
                boolean hasCustomData = false;

                if (shulker instanceof IShulkerUUIDProvider provider && provider.getShulkerUUID() != null) {
                    hasCustomData = true;
                }

                if (shulker instanceof IWhitelistProvider provider
                        && provider.getWhitelists() != null
                        && !provider.getWhitelists().isEmpty()) {
                    hasCustomData = true;
                }

                if (hasCustomData) {
                    ItemStack itemStack = new ItemStack(((ShulkerBoxBlock) (Object) this).asItem());
                    itemStack.applyComponentsFrom(shulker.createComponentMap());

                    ItemEntity itemEntity =
                            new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, itemStack);
                    itemEntity.setToDefaultPickupDelay();
                    world.spawnEntity(itemEntity);
                }
            }
        }
    }
}
