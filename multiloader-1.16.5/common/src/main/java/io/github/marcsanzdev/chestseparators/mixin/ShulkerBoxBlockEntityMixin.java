package io.github.marcsanzdev.chestseparators.mixin;

import me.shedaniel.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.network.ModNet;
import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.network.ShulkerUUIDPayload;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects a persistent mod UUID into each Shulker Box block entity: generated lazily on the server and
 * persisted to world NBT. On open it transmits that UUID to the opener ({@link ShulkerUUIDPayload} S2C)
 * so the client can key the shulker's local filter config; a capability check keeps vanilla-only clients
 * from receiving an unknown packet.
 */
@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerBoxBlockEntityMixin extends BlockEntity implements IShulkerUUIDProvider {

    @Unique
    private UUID chestSeparatorsUUID;

    // 1.16.5 BlockEntity has a single-arg constructor; only present to satisfy the `extends BlockEntity` shadow.
    public ShulkerBoxBlockEntityMixin(BlockEntityType<?> type) {
        super(type);
    }

    @Override
    public UUID getShulkerUUID() {
        if (this.chestSeparatorsUUID == null) {
            // On the client, never generate a UUID — wait for the authoritative S2C payload.
            if (this.level != null && this.level.isClientSide()) {
                return null;
            }
            this.chestSeparatorsUUID = UUID.randomUUID();
            this.setChanged();
        }
        return this.chestSeparatorsUUID;
    }

    @Override
    public void setShulkerUUID(UUID uuid) {
        this.chestSeparatorsUUID = uuid;
        this.setChanged();
    }

    /** Transmits the Shulker UUID to the opening player so the editor can load its UUID-keyed local config. */
    @Inject(method = "startOpen", at = @At("HEAD"))
    private void chestseparators$onShulkerOpened(Player user, CallbackInfo ci) {
        if (this.getLevel() != null
                && !this.getLevel().isClientSide()
                && user instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) user;
            if (io.github.marcsanzdev.chestseparators.network.ModNet.playerCanReceive(serverPlayer, ShulkerUUIDPayload.ID)) {
                ModNet.sendToPlayer(serverPlayer, new ShulkerUUIDPayload(this.getShulkerUUID()));
            }
        }
    }

    // 1.16.5 load takes (BlockState, CompoundTag) and save (not saveAdditional) returns the tag.
    @Inject(method = "load", at = @At("TAIL"))
    protected void onLoad(BlockState state, CompoundTag tag, CallbackInfo ci) {
        String uuidString = tag.getString("ChestSeparatorsUUID");
        if (!uuidString.isEmpty()) {
            try {
                this.chestSeparatorsUUID = UUID.fromString(uuidString);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Inject(method = "save", at = @At("TAIL"))
    protected void onSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.chestSeparatorsUUID != null) {
            tag.putString("ChestSeparatorsUUID", this.chestSeparatorsUUID.toString());
        }
    }
}
