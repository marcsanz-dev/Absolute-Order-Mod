package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

// Applies the whitelist data structure to all standard containers (Chests, Barrels, Shulker Boxes).
// We only use this mixin to implement the interface and store the variables.
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

    // Transmits the saved whitelist data to the client whenever the container GUI is opened.
    @Inject(method = "createMenu", at = @At("RETURN"))
    private void onOpenMenu(int syncId, net.minecraft.entity.player.PlayerInventory playerInventory, net.minecraft.entity.player.PlayerEntity player, CallbackInfoReturnable<ScreenHandler> cir) {
        if (cir.getReturnValue() != null && this.world != null && !this.world.isClient() && player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(serverPlayer, io.github.marcsanzdev.chestseparators.network.WhitelistPayload.ID)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer, new io.github.marcsanzdev.chestseparators.network.WhitelistPayload(this.pos, this.chestSeparatorsWhitelists));
            }
        }
    }

    // --- LÓGICA DE GUARDADO EN EL MUNDO (NBT) ---

    // --- LÓGICA DE GUARDADO EN EL MUNDO (NBT / VIEW) ---

    @Override
    protected void writeData(net.minecraft.storage.WriteView view) {
        super.writeData(view); // Llamamos al original de BlockEntity

        if (!this.chestSeparatorsWhitelists.isEmpty()) {
            net.minecraft.nbt.NbtCompound whitelistsTag = new net.minecraft.nbt.NbtCompound();
            for (Map.Entry<Integer, SlotWhitelist> entry : this.chestSeparatorsWhitelists.entrySet()) {
                net.minecraft.nbt.NbtCompound wlTag = new net.minecraft.nbt.NbtCompound();
                SlotWhitelist wl = entry.getValue();

                wlTag.putString("GroupId", wl.groupId().toString());
                wlTag.putBoolean("RuleManual", wl.allowManual());
                wlTag.putBoolean("RuleShift", wl.allowShift());
                wlTag.putBoolean("RuleHopper", wl.allowHopper());

                net.minecraft.nbt.NbtCompound itemsTag = new net.minecraft.nbt.NbtCompound();
                int i = 0;
                for (String item : wl.allowedItems()) {
                    itemsTag.putString(String.valueOf(i++), item);
                }
                wlTag.put("AllowedItems", itemsTag);

                whitelistsTag.put(String.valueOf(entry.getKey()), wlTag);
            }
            view.put("ChestSeparatorsWhitelists", net.minecraft.nbt.NbtCompound.CODEC, whitelistsTag);
        }
    }

    @Override
    protected void readData(net.minecraft.storage.ReadView view) {
        super.readData(view); // Llamamos al original de BlockEntity

        this.chestSeparatorsWhitelists.clear();
        view.read("ChestSeparatorsWhitelists", net.minecraft.nbt.NbtCompound.CODEC).ifPresent(whitelistsTag -> {
            for (String key : whitelistsTag.getKeys()) {
                try {
                    int slot = Integer.parseInt(key);
                    whitelistsTag.getCompound(key).ifPresent(wlTag -> {
                        java.util.UUID groupId = java.util.UUID.fromString(wlTag.getString("GroupId").orElse(java.util.UUID.randomUUID().toString()));
                        boolean ruleManual = wlTag.getBoolean("RuleManual").orElse(true);
                        boolean ruleShift = wlTag.getBoolean("RuleShift").orElse(true);
                        boolean ruleHopper = wlTag.getBoolean("RuleHopper").orElse(true);

                        java.util.List<String> allowedItems = new java.util.ArrayList<>();
                        wlTag.getCompound("AllowedItems").ifPresent(itemsTag -> {
                            for (String itemKey : itemsTag.getKeys()) {
                                itemsTag.getString(itemKey).ifPresent(allowedItems::add);
                            }
                        });

                        this.chestSeparatorsWhitelists.put(slot, new SlotWhitelist(groupId, allowedItems, ruleManual, ruleShift, ruleHopper));
                    });
                } catch (Exception ignored) {}
            }
        });
    }



}