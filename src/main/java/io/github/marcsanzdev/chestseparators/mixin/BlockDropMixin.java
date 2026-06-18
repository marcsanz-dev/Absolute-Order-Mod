package io.github.marcsanzdev.chestseparators.mixin;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockDropMixin {

    @Inject(
            method =
                    "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;)Ljava/util/List;",
            at = @At("RETURN"))
    private static void onGetDroppedStacks(
            BlockState state,
            ServerWorld world,
            BlockPos pos,
            BlockEntity blockEntity,
            CallbackInfoReturnable<List<ItemStack>> cir) {
        injectDataToDrops(blockEntity, cir.getReturnValue());
    }

    @Inject(
            method =
                    "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"))
    private static void onGetDroppedStacksWithEntity(
            BlockState state,
            ServerWorld world,
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
            // Check that the drop is actually the block itself
            if (drop.getItem() instanceof net.minecraft.item.BlockItem) {
                if (blockEntity instanceof io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider provider) {
                    java.util.UUID uuid = provider.getShulkerUUID();
                    if (uuid != null)
                        drop.set(
                                io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents.SHULKER_UUID,
                                uuid.toString());
                }

                if (blockEntity instanceof io.github.marcsanzdev.chestseparators.access.IWhitelistProvider provider) {
                    // EXCLUSION: Do not save whitelists to the dropped item if it is a standard Chest or Barrel
                    boolean isStandardChest = blockEntity instanceof net.minecraft.block.entity.ChestBlockEntity
                            || blockEntity instanceof net.minecraft.block.entity.BarrelBlockEntity;

                    if (!isStandardChest) {
                        java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists =
                                provider.getWhitelists();
                        if (whitelists != null && !whitelists.isEmpty()) {
                            drop.set(
                                    io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents
                                            .SLOT_WHITELISTS,
                                    new java.util.HashMap<>(whitelists));
                        }
                    }
                }
            }
        }
    }
}
