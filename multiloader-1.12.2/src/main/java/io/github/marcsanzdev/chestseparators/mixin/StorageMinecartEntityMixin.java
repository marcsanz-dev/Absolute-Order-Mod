package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds {@link IWhitelistProvider} to chest/hopper minecarts. Unlike other entity containers, hoppers CAN
 * insert into these, so the filter is synced to the server by entity UUID (see {@code EntityWhitelistPayload})
 * and persisted here in the entity's own NBT, making the Hopper Insert rule enforce on minecarts (via
 * {@code HopperBlockEntityMixin}) and survive reload.
 *
 * <p>1.12.2 deltas: {@code AbstractMinecartContainer}→{@link EntityMinecartContainer};
 * {@code addAdditionalSaveData}→{@code writeEntityToNBT}; {@code readAdditionalSaveData}→
 * {@code readEntityFromNBT}.
 *
 * <p>TODO(1.12.2 port): the modern on-open S2C push (inject into {@code createMenu} RETURN) is omitted here.
 * {@code createContainer(InventoryPlayer, EntityPlayer)} is not declared on {@code EntityMinecartContainer}
 * in 1.12.2 — it is abstract and implemented only on the concrete subclasses ({@code EntityMinecartChest},
 * {@code EntityMinecartHopper}) — so it cannot be injected at this class level without adding per-subclass
 * mixins. The editor still receives the server-authoritative filter through the existing
 * {@code EntityWhitelistPayload} request round-trip; enforcement + reload persistence are unaffected.
 */
@Mixin(EntityMinecartContainer.class)
public abstract class StorageMinecartEntityMixin implements IWhitelistProvider {

    @Unique
    private Map<Integer, SlotWhitelist> chestSeparatorsWhitelists = new HashMap<>();

    @Override
    public Map<Integer, SlotWhitelist> getWhitelists() {
        return this.chestSeparatorsWhitelists;
    }

    @Override
    public void setWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        this.chestSeparatorsWhitelists = new HashMap<>(whitelists);
    }

    @Inject(method = "writeEntityToNBT", at = @At("TAIL"))
    private void chestseparators$writeWhitelists(NBTTagCompound tag, CallbackInfo ci) {
        SlotWhitelist.writeMapToTag(tag, "ChestSeparatorsWhitelists", this.chestSeparatorsWhitelists);
    }

    @Inject(method = "readEntityFromNBT", at = @At("TAIL"))
    private void chestseparators$readWhitelists(NBTTagCompound tag, CallbackInfo ci) {
        this.chestSeparatorsWhitelists = SlotWhitelist.readMapFromTag(tag, "ChestSeparatorsWhitelists");
    }
}
