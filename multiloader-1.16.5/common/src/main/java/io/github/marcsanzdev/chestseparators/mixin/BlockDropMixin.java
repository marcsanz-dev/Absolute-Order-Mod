package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Copies mod data (Shulker UUID, non-standard-container whitelist) onto the block's dropped ItemStack.
 *
 * <p>1.20.1 has no data components, and the vanilla shulker loot table only copies a fixed set of keys, so
 * custom NBT would not ride along on its own. This writes the mod's keys into the item's {@code BlockEntityTag}
 * instead — the same NBT slot {@code BlockItem} restores into the placed block entity's {@code load(...)},
 * which reconnects a shulker to its UUID-keyed local filter config after a break/replace.
 */
@Mixin(Block.class)
public abstract class BlockDropMixin {

    @Inject(
            method =
                    "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;",
            at = @At("RETURN"))
    private static void onGetDrops(
            BlockState state,
            ServerLevel world,
            BlockPos pos,
            BlockEntity blockEntity,
            CallbackInfoReturnable<List<ItemStack>> cir) {
        injectDataToDrops(blockEntity, cir.getReturnValue());
    }

    @Inject(
            method =
                    "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"))
    private static void onGetDropsWithEntity(
            BlockState state,
            ServerLevel world,
            BlockPos pos,
            BlockEntity blockEntity,
            Entity entity,
            ItemStack tool,
            CallbackInfoReturnable<List<ItemStack>> cir) {
        injectDataToDrops(blockEntity, cir.getReturnValue());
    }

    private static void injectDataToDrops(BlockEntity blockEntity, List<ItemStack> drops) {
        if (blockEntity == null || drops == null || drops.isEmpty()) return;

        for (ItemStack drop : drops) {
            // Only enrich the drop that is actually the block itself.
            if (!(drop.getItem() instanceof BlockItem)) continue;

            CompoundTag beTag = new CompoundTag();

            if (blockEntity instanceof IShulkerUUIDProvider) {
                IShulkerUUIDProvider provider = (IShulkerUUIDProvider) blockEntity;
                UUID uuid = provider.getShulkerUUID();
                if (uuid != null) beTag.putString("ChestSeparatorsUUID", uuid.toString());
            }

            if (blockEntity instanceof IWhitelistProvider) {
                IWhitelistProvider provider = (IWhitelistProvider) blockEntity;
                // Standard Chests/Barrels keep their filters in world NBT only, not on the dropped item.
                boolean isStandardChest =
                        blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity;
                if (!isStandardChest) {
                    SlotWhitelist.writeMapToTag(beTag, "ChestSeparatorsWhitelists", provider.getWhitelists());
                }
            }

            // Merge into the item's BlockEntityTag so vanilla restores it on placement (no data components in 1.20.1).
            if (!beTag.isEmpty()) {
                CompoundTag itemTag = drop.getOrCreateTag();
                CompoundTag blockEntityTag = itemTag.getCompound("BlockEntityTag");
                blockEntityTag.merge(beTag);
                itemTag.put("BlockEntityTag", blockEntityTag);
            }
        }
    }
}
