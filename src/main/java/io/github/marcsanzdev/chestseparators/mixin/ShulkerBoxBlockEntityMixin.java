package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.network.ShulkerUUIDPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.entity.ContainerUser;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

// Mixin to inject a persistent UUID into the Vanilla Shulker Box block entity.
@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerBoxBlockEntityMixin extends BlockEntity implements IShulkerUUIDProvider {

    @Unique
    private UUID chestSeparatorsUUID;

    public ShulkerBoxBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public UUID getShulkerUUID() {
        if (this.chestSeparatorsUUID == null) {
            this.chestSeparatorsUUID = UUID.randomUUID();
            this.markDirty();
        }
        return this.chestSeparatorsUUID;
    }

    @Override
    public void setShulkerUUID(UUID uuid) {
        this.chestSeparatorsUUID = uuid;
        this.markDirty();
    }

    // Preserves the UUID in the world save files (.mca chunks).
    @Inject(method = "readData", at = @At("TAIL"))
    protected void onReadData(ReadView view, CallbackInfo ci) {
        view.getOptionalString("ChestSeparatorsUUID").ifPresent(uuidString -> {
            if (!uuidString.isEmpty()) {
                try {
                    this.chestSeparatorsUUID = UUID.fromString(uuidString);
                } catch (IllegalArgumentException ignored) {}
            }
        });
    }

    // Preserves the UUID in the world save files (.mca chunks).
    @Inject(method = "writeData", at = @At("TAIL"))
    protected void onWriteData(WriteView view, CallbackInfo ci) {
        if (this.chestSeparatorsUUID != null) {
            view.putString("ChestSeparatorsUUID", this.chestSeparatorsUUID.toString());
        }
    }

    // Safely transmits the persisted UUID payload to the client interface when opened.
    // Includes a network check to ensure vanilla clients do not receive unknown packets.
    @Inject(method = "onOpen", at = @At("HEAD"))
    private void onShulkerOpened(ContainerUser user, CallbackInfo ci) {
        if (this.world != null && !this.world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            // Verifies if the connected player's client can handle our custom S2C payload.
            if (ServerPlayNetworking.canSend(serverPlayer, ShulkerUUIDPayload.ID)) {
                ServerPlayNetworking.send(serverPlayer, new ShulkerUUIDPayload(this.getShulkerUUID()));
            }
        }
    }



}