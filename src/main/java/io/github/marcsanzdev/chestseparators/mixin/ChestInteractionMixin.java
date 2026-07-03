package io.github.marcsanzdev.chestseparators.mixin; // Asegúrate de que el package coincida con donde lo hayas movido

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

@Mixin(ClientPlayerInteractionManager.class)
public class ChestInteractionMixin {

    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void captureChestPos(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.MAIN_HAND) {
            BlockPos clickedPos = hitResult.getBlockPos();

            ChestPosStorage.setLastClickedPos(clickedPos);
            ChestPosStorage.setEntityOpened(false);

            // ¡VITAL! Limpiamos la memoria del UUID anterior
            ChestPosStorage.setLastOpenedShulkerUUID(null);

            if (MinecraftClient.getInstance().world != null) {
                ChestPosStorage.setLastClickedDimension(MinecraftClient.getInstance().world.getRegistryKey().getValue().toString());

                // ¡NUEVO! Leemos el bloque físico para ver si es una Shulker con UUID
                net.minecraft.block.entity.BlockEntity be = MinecraftClient.getInstance().world.getBlockEntity(clickedPos);
                if (be instanceof io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider provider) {
                    ChestPosStorage.setLastOpenedShulkerUUID(provider.getShulkerUUID());
                }
            }
        }
    }

    @Inject(method = "interactEntity", at = @At("HEAD"))
    private void captureEntity(PlayerEntity player, Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (hand == Hand.MAIN_HAND) {
            if (entity instanceof VehicleInventory || entity.getClass().getName().contains("Chest")) {
                ChestPosStorage.setLastClickedEntityUUID(entity.getUuid());
                ChestPosStorage.setEntityOpened(true);
                ChestPosStorage.setLastOpenedShulkerUUID(null); // Limpiamos por si acaso

                if (MinecraftClient.getInstance().world != null) {
                    ChestPosStorage.setLastClickedDimension(MinecraftClient.getInstance().world.getRegistryKey().getValue().toString());
                }
            }
        }
    }
}