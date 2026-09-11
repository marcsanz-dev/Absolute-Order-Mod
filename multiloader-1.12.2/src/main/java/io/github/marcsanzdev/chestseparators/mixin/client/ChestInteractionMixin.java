package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts the player's block and entity interactions to capture the context needed for the mod's editor
 * UI before the container screen is opened. Minecraft separates the interaction from the screen initializer,
 * so {@link ChestPosStorage} bridges the two call sites.
 *
 * <p>1.12.2 (E1): {@code MultiPlayerGameMode}→{@code PlayerControllerMP}; {@code useItemOn}→
 * {@code processRightClickBlock}; {@code interact}→{@code interactWithEntity} (the 3-arg overload — the
 * RayTraceResult overload is disambiguated by full descriptor); the dimension discriminator is the integer
 * dimension id as a String ({@code world.provider.getDimension()}), matching what the server persists.
 */
@Mixin(PlayerControllerMP.class)
public abstract class ChestInteractionMixin {

    @Inject(method = "processRightClickBlock", at = @At("HEAD"))
    private void chestseparators$captureChestPos(
            EntityPlayerSP player,
            WorldClient world,
            BlockPos pos,
            EnumFacing face,
            Vec3d vec,
            EnumHand hand,
            CallbackInfoReturnable<EnumActionResult> cir) {
        if (hand == EnumHand.MAIN_HAND) {
            ChestPosStorage.lastClickedPos = pos;
            ChestPosStorage.isEntityOpened = false;
            ChestPosStorage.lastOpenedShulkerUUID = null;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world != null) {
                ChestPosStorage.lastClickedDimension = String.valueOf(mc.world.provider.getDimension());

                TileEntity be = mc.world.getTileEntity(pos);
                if (be instanceof IShulkerUUIDProvider) {
                    ChestPosStorage.lastOpenedShulkerUUID = ((IShulkerUUIDProvider) be).getShulkerUUID();
                }
            }
        }
    }

    @Inject(
            method =
                    "interactWithEntity(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/EnumActionResult;",
            at = @At("HEAD"))
    private void chestseparators$captureEntity(
            EntityPlayer player, Entity entity, EnumHand hand, CallbackInfoReturnable<EnumActionResult> cir) {
        if (hand == EnumHand.MAIN_HAND) {
            if (entity instanceof EntityMinecartContainer
                    || entity.getClass().getName().contains("Chest")) {
                ChestPosStorage.lastClickedEntityUUID = entity.getUniqueID();
                ChestPosStorage.isEntityOpened = true;
                // Chest/hopper minecarts accept hopper input, so the Hopper rule is available for them.
                ChestPosStorage.isMinecartEntity = entity instanceof EntityMinecartContainer;
                ChestPosStorage.lastOpenedShulkerUUID = null;

                Minecraft mc = Minecraft.getMinecraft();
                if (mc.world != null) {
                    ChestPosStorage.lastClickedDimension = String.valueOf(mc.world.provider.getDimension());
                }
            }
        }
    }
}
