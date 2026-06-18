package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.WhitelistPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements {@link IWhitelistProvider} on all standard lootable containers:
 * Chests, Barrels, and Shulker Boxes. Owns the canonical NBT persistence
 * for whitelist data under the "ChestSeparatorsWhitelists" key.
 *
 * <p>Also transmits the container's current whitelist state to the opening player
 * immediately upon GUI creation, eliminating a separate request round-trip for
 * the common case.
 */
@Mixin(LootableContainerBlockEntity.class)
public abstract class LootableContainerBlockEntityMixin extends BlockEntity implements IWhitelistProvider {

    @Unique
    private Map<Integer, SlotWhitelist> chestSeparatorsWhitelists = new HashMap<>();

    public LootableContainerBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public Map<Integer, SlotWhitelist> getWhitelists() {
        return this.chestSeparatorsWhitelists;
    }

    @Override
    public void setWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        this.chestSeparatorsWhitelists = new HashMap<>(whitelists);
        this.markDirty();
    }

    /**
     * Pushes the saved whitelist to the opening player whenever the container GUI is created,
     * so the client does not need to send a separate {@link io.github.marcsanzdev.chestseparators.network.WhitelistRequestPayload}.
     */
    @Inject(method = "createMenu", at = @At("RETURN"))
    private void onOpenMenu(
            int syncId,
            PlayerInventory playerInventory,
            PlayerEntity player,
            CallbackInfoReturnable<ScreenHandler> cir) {
        if (cir.getReturnValue() != null
                && this.world != null
                && !this.world.isClient()
                && player instanceof ServerPlayerEntity serverPlayer) {
            if (ServerPlayNetworking.canSend(serverPlayer, WhitelistPayload.ID)) {
                ServerPlayNetworking.send(serverPlayer, new WhitelistPayload(this.pos, this.chestSeparatorsWhitelists));
            }
        }
    }

    // --- NBT world-save persistence ---

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);

        if (!this.chestSeparatorsWhitelists.isEmpty()) {
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
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);

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
                            for (String itemKey : itemsTag.getKeys()) {
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
