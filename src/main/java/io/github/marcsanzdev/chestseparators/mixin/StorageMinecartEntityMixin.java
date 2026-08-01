package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.EntityWhitelistPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Adds IWhitelistProvider to chest minecarts (and hopper minecarts, harmlessly).
//
// Unlike other entity containers, hoppers CAN insert into these, so the filter is synced to the SERVER by
// entity UUID (see EntityWhitelistPayload) and persisted here in the entity's own NBT. That makes the
// Hopper Insert rule actually enforce on minecarts via HopperBlockEntityMixin, and survive world reload /
// chunk unload. On open the current filter is pushed back to the player so the editor shows the
// server-authoritative state.
@Mixin(StorageMinecartEntity.class)
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

    // Pushes the stored filter to the opening player so the editor displays the server-authoritative state,
    // mirroring the block-chest handler in LootableContainerBlockEntityMixin.
    @Inject(method = "createMenu", at = @At("RETURN"))
    private void chestseparators$onOpenMenu(
            int syncId,
            PlayerInventory playerInventory,
            PlayerEntity player,
            CallbackInfoReturnable<ScreenHandler> cir) {
        Entity self = (Entity) (Object) this;
        if (cir.getReturnValue() != null
                && self.getEntityWorld() != null
                && !self.getEntityWorld().isClient()
                && player instanceof ServerPlayerEntity serverPlayer
                && ServerPlayNetworking.canSend(serverPlayer, EntityWhitelistPayload.ID)) {
            ServerPlayNetworking.send(
                    serverPlayer, new EntityWhitelistPayload(self.getUuid(), this.chestSeparatorsWhitelists));
        }
    }

    // --- NBT world-save persistence (same schema as LootableContainerBlockEntityMixin) ---

    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void chestseparators$writeWhitelists(WriteView view, CallbackInfo ci) {
        if (this.chestSeparatorsWhitelists.isEmpty()) return;

        NbtCompound whitelistsTag = new NbtCompound();
        for (Map.Entry<Integer, SlotWhitelist> entry : this.chestSeparatorsWhitelists.entrySet()) {
            NbtCompound wlTag = new NbtCompound();
            SlotWhitelist wl = entry.getValue();

            wlTag.putString("GroupId", wl.groupId().toString());
            wlTag.putBoolean("RuleManual", wl.allowManual());
            wlTag.putBoolean("RuleShift", wl.allowShift());
            wlTag.putBoolean("RuleHopper", wl.allowHopper());

            NbtCompound itemsTag = new NbtCompound();
            int i = 0;
            for (String item : wl.allowedItems()) {
                itemsTag.putString(String.valueOf(i++), item);
            }
            wlTag.put("AllowedItems", itemsTag);

            whitelistsTag.put(String.valueOf(entry.getKey()), wlTag);
        }
        view.put("ChestSeparatorsWhitelists", NbtCompound.CODEC, whitelistsTag);
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void chestseparators$readWhitelists(ReadView view, CallbackInfo ci) {
        this.chestSeparatorsWhitelists.clear();
        view.read("ChestSeparatorsWhitelists", NbtCompound.CODEC).ifPresent(whitelistsTag -> {
            for (String key : whitelistsTag.getKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    whitelistsTag.getCompound(key).ifPresent(wlTag -> {
                        UUID groupId = UUID.fromString(wlTag.getString("GroupId")
                                .orElse(UUID.randomUUID().toString()));
                        boolean ruleManual = wlTag.getBoolean("RuleManual").orElse(true);
                        boolean ruleShift = wlTag.getBoolean("RuleShift").orElse(true);
                        boolean ruleHopper = wlTag.getBoolean("RuleHopper").orElse(true);

                        List<String> allowedItems = new ArrayList<>();
                        wlTag.getCompound("AllowedItems").ifPresent(itemsTag -> {
                            // Numeric keys, but an unordered key set: sort them so the player's arranged
                            // order in the filter list survives a reload.
                            List<String> keys = new ArrayList<>(itemsTag.getKeys());
                            keys.sort(java.util.Comparator.comparingInt(k -> {
                                try {
                                    return Integer.parseInt(k);
                                } catch (NumberFormatException e) {
                                    return Integer.MAX_VALUE;
                                }
                            }));
                            for (String itemKey : keys) {
                                itemsTag.getString(itemKey).ifPresent(allowedItems::add);
                            }
                        });

                        this.chestSeparatorsWhitelists.put(
                                slot, new SlotWhitelist(groupId, allowedItems, ruleManual, ruleShift, ruleHopper));
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }
}
