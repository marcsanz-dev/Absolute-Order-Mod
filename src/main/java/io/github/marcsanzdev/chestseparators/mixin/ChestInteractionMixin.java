package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.VehicleInventory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts the player's block and entity interactions to capture the context needed
 * for the mod's editor UI before the container screen is opened.
 *
 * <p>Minecraft's architecture separates the interaction event from the screen initializer,
 * so {@link ChestPosStorage} acts as a transient bridge between the two call sites.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ChestInteractionMixin {

    /**
     * Captures the clicked block position and, if present, the Shulker UUID from its block entity.
     * This runs before the server processes the interaction, so the data is ready by the time
     * the resulting container screen is initialized on the client.
     */
    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void captureChestPos(
            ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.MAIN_HAND) {
            BlockPos clickedPos = hitResult.getBlockPos();

            ChestPosStorage.lastClickedPos = clickedPos;
            ChestPosStorage.isEntityOpened = false;
            ChestPosStorage.lastOpenedShulkerUUID = null;

            if (MinecraftClient.getInstance().world != null) {
                ChestPosStorage.lastClickedDimension = MinecraftClient.getInstance()
                        .world
                        .getRegistryKey()
                        .getValue()
                        .toString();

                net.minecraft.block.entity.BlockEntity be =
                        MinecraftClient.getInstance().world.getBlockEntity(clickedPos);
                if (be instanceof io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider provider) {
                    ChestPosStorage.lastOpenedShulkerUUID = provider.getShulkerUUID();
                }
            }
        }
    }

    /**
     * Captures the UUID of entity-based inventories (Chest Minecarts, Chest Boats,
     * Donkeys, Llamas, etc.) so the editor can use UUID-keyed storage instead of BlockPos.
     */
    @Inject(method = "interactEntity", at = @At("HEAD"))
    private void captureEntity(
            PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.MAIN_HAND) {
            if (entity instanceof VehicleInventory
                    || entity.getClass().getName().contains("Chest")) {
                ChestPosStorage.lastClickedEntityUUID = entity.getUuid();
                ChestPosStorage.isEntityOpened = true;
                ChestPosStorage.lastOpenedShulkerUUID = null;

                if (MinecraftClient.getInstance().world != null) {
                    ChestPosStorage.lastClickedDimension = MinecraftClient.getInstance()
                            .world
                            .getRegistryKey()
                            .getValue()
                            .toString();
                }
            }
        }
    }
}
