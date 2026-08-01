package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds {@link IWhitelistProvider} to chest/hopper minecarts. Unlike other entity containers, hoppers CAN
 * insert into these, so the filter is synced to the server by entity UUID (see {@code EntityWhitelistPayload})
 * and persisted here in the entity's own NBT, making the Hopper Insert rule enforce on minecarts (via
 * {@code HopperBlockEntityMixin}) and survive reload. (No S2C push on open here; the client fetches it once
 * the client receivers are migrated.)
 */
@Mixin(AbstractMinecartContainer.class)
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

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void chestseparators$writeWhitelists(ValueOutput view, CallbackInfo ci) {
        if (!this.chestSeparatorsWhitelists.isEmpty()) {
            view.store("ChestSeparatorsWhitelists", SlotWhitelist.MAP_CODEC, this.chestSeparatorsWhitelists);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void chestseparators$readWhitelists(ValueInput view, CallbackInfo ci) {
        this.chestSeparatorsWhitelists.clear();
        view.read("ChestSeparatorsWhitelists", SlotWhitelist.MAP_CODEC)
                .ifPresent(this.chestSeparatorsWhitelists::putAll);
    }
}
