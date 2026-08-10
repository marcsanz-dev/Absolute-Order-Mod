package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.network.ModNet;
import io.github.marcsanzdev.chestseparators.network.ShulkerUUIDPayload;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects a persistent mod UUID into each Shulker Box tile entity: generated lazily on the server and
 * persisted to world NBT. On open it transmits that UUID to the opener ({@link ShulkerUUIDPayload} S2C)
 * so the client can key the shulker's local filter config.
 *
 * <p>1.12.2 deltas: {@code startOpen}→{@code openInventory}; {@code load(BlockState,CompoundTag)}→
 * {@code readFromNBT(NBTTagCompound)}; {@code save}→{@code writeToNBT(NBTTagCompound)} (returns the tag);
 * {@code isClientSide()}→{@code World#isRemote}; {@code setChanged}→{@code markDirty}. Level/markDirty are
 * reached by casting through {@link TileEntity} (safe — the target extends it).
 */
@Mixin(TileEntityShulkerBox.class)
public abstract class ShulkerBoxBlockEntityMixin implements IShulkerUUIDProvider {

    @Unique
    private UUID chestSeparatorsUUID;

    @Override
    public UUID getShulkerUUID() {
        if (this.chestSeparatorsUUID == null) {
            World world = ((TileEntity) (Object) this).getWorld();
            // On the client, never generate a UUID — wait for the authoritative S2C payload.
            if (world != null && world.isRemote) {
                return null;
            }
            this.chestSeparatorsUUID = UUID.randomUUID();
            ((TileEntity) (Object) this).markDirty();
        }
        return this.chestSeparatorsUUID;
    }

    @Override
    public void setShulkerUUID(UUID uuid) {
        this.chestSeparatorsUUID = uuid;
        ((TileEntity) (Object) this).markDirty();
    }

    /** Transmits the Shulker UUID to the opening player so the editor can load its UUID-keyed local config. */
    @Inject(method = "openInventory", at = @At("HEAD"))
    private void chestseparators$onShulkerOpened(EntityPlayer user, CallbackInfo ci) {
        World world = ((TileEntity) (Object) this).getWorld();
        if (world != null && !world.isRemote && user instanceof EntityPlayerMP) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) user;
            if (ModNet.playerCanReceive(serverPlayer, ShulkerUUIDPayload.ID)) {
                ModNet.sendToPlayer(serverPlayer, new ShulkerUUIDPayload(this.getShulkerUUID()));
            }
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    protected void onLoad(NBTTagCompound tag, CallbackInfo ci) {
        String uuidString = tag.getString("ChestSeparatorsUUID");
        if (!uuidString.isEmpty()) {
            try {
                this.chestSeparatorsUUID = UUID.fromString(uuidString);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    protected void onSave(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        if (this.chestSeparatorsUUID != null) {
            tag.setString("ChestSeparatorsUUID", this.chestSeparatorsUUID.toString());
        }
    }
}
