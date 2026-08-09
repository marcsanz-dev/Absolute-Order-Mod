package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces an empty Shulker Box to drop as an item in Creative mode when it carries mod data. Vanilla skips
 * the item drop for empty Shulker Boxes in Creative; without this the separator UUID / slot filters would
 * be lost on break. 1.20.1 has no data components, so the block-entity NBT (whitelist + UUID) is embedded
 * onto the item via {@code saveToItem} (vanilla's BlockEntityTag), restored into the placed BE's load(...).
 */
@Mixin(ShulkerBoxBlock.class)
public class ShulkerBoxBlockMixin {

    @Inject(method = "playerWillDestroy", at = @At("HEAD"))
    private void forceDropEmptyCustomShulker(
            Level world, BlockPos pos, BlockState state, Player player, CallbackInfo ci) {
        // Survival always drops the item; only Creative needs special handling.
        if (!world.isClientSide() && player.isCreative()) {
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
                    shulker.saveToItem(itemStack);

                    ItemEntity itemEntity =
                            new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, itemStack);
                    itemEntity.setDefaultPickUpDelay();
                    world.addFreshEntity(itemEntity);
                }
            }
        }
    }
}
