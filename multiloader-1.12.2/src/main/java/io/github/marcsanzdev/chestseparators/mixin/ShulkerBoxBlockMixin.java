package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces an empty Shulker Box to drop as an item in Creative mode when it carries mod data. Vanilla skips
 * the item drop for empty Shulker Boxes in Creative; without this the separator UUID / slot filters would
 * be lost on break. The mod's NBT (whitelist + UUID) is embedded onto the item's {@code BlockEntityTag},
 * restored into the placed tile entity's {@code readFromNBT}.
 *
 * <p>1.12.2 deltas: {@code playerWillDestroy}→{@code onBlockHarvested} (the pre-removal hook on the player-
 * harvest path); {@code isClientSide()}→{@code World#isRemote}; {@code BlockEntity#save}→
 * {@code TileEntity#writeToNBT}; {@code ItemStack#addTagElement}→{@code setTagInfo}; {@code ItemEntity}→
 * {@code EntityItem}; {@code setDefaultPickUpDelay}→{@code setDefaultPickupDelay};
 * {@code Level#addFreshEntity}→{@code World#spawnEntity}; {@code asItem()}→{@code Item.getItemFromBlock}.
 */
@Mixin(BlockShulkerBox.class)
public class ShulkerBoxBlockMixin {

    @Inject(method = "onBlockHarvested", at = @At("HEAD"))
    private void forceDropEmptyCustomShulker(
            World world, BlockPos pos, IBlockState state, EntityPlayer player, CallbackInfo ci) {
        // Survival always drops the item; only Creative needs special handling.
        if (!world.isRemote && player.isCreative()) {
            TileEntity be = world.getTileEntity(pos);

            if (be instanceof TileEntityShulkerBox) {
                TileEntityShulkerBox shulker = (TileEntityShulkerBox) be;
                if (shulker.isEmpty()) {
                    boolean hasCustomData = false;

                    if (shulker instanceof IShulkerUUIDProvider) {
                        IShulkerUUIDProvider provider = (IShulkerUUIDProvider) shulker;
                        if (provider.getShulkerUUID() != null) {
                            hasCustomData = true;
                        }
                    }

                    if (shulker instanceof IWhitelistProvider) {
                        IWhitelistProvider provider = (IWhitelistProvider) shulker;
                        if (provider.getWhitelists() != null
                                && !provider.getWhitelists().isEmpty()) {
                            hasCustomData = true;
                        }
                    }

                    if (hasCustomData) {
                        ItemStack itemStack = new ItemStack(Item.getItemFromBlock((Block) (Object) this));
                        // Write the tile entity's NBT onto the item's "BlockEntityTag" by hand, exactly as
                        // vanilla shulker-drop logic does, so it is restored on placement.
                        NBTTagCompound beTag = ((TileEntity) shulker).writeToNBT(new NBTTagCompound());
                        itemStack.setTagInfo("BlockEntityTag", beTag);

                        EntityItem itemEntity = new EntityItem(
                                world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, itemStack);
                        itemEntity.setDefaultPickupDelay();
                        world.spawnEntity(itemEntity);
                    }
                }
            }
        }
    }
}
