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

/**
 * Injects a persistent mod UUID into each Shulker Box block entity.
 *
 * <p>The UUID is generated lazily on the server, persisted to world chunk NBT, and
 * transmitted to the client via {@link ShulkerUUIDPayload} whenever a player opens the box.
 * The client never generates a UUID on its own — it always waits for the authoritative payload.
 */
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
            // On the client side, never generate a UUID — wait for the authoritative S2C payload.
            if (this.world != null && this.world.isClient()) {
                return null;
            }
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

    @Inject(method = "writeData", at = @At("TAIL"))
    protected void onWriteData(WriteView view, CallbackInfo ci) {
        if (this.chestSeparatorsUUID != null) {
            view.putString("ChestSeparatorsUUID", this.chestSeparatorsUUID.toString());
        }
    }

    /**
     * Transmits the Shulker UUID to the opening player. Includes a capability check to ensure
     * vanilla-only clients never receive an unrecognized packet.
     */
    @Inject(method = "onOpen", at = @At("HEAD"))
    private void onShulkerOpened(ContainerUser user, CallbackInfo ci) {
        if (this.world != null && !this.world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            if (ServerPlayNetworking.canSend(serverPlayer, ShulkerUUIDPayload.ID)) {
                ServerPlayNetworking.send(serverPlayer, new ShulkerUUIDPayload(this.getShulkerUUID()));
            }
        }
    }
}
