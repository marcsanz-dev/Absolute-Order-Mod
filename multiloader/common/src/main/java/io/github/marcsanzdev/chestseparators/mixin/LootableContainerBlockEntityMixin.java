package io.github.marcsanzdev.chestseparators.mixin;

import dev.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.WhitelistPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements {@link IWhitelistProvider} on all standard lootable containers (Chests, Barrels, Shulker
 * Boxes — every subclass of {@code RandomizableContainerBlockEntity}). Owns the canonical NBT
 * persistence for whitelist data under the "ChestSeparatorsWhitelists" key, and pushes the container's
 * current whitelist to the opening player when the GUI is created.
 */
@Mixin(RandomizableContainerBlockEntity.class)
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
        this.setChanged();
    }

    /**
     * Pushes the saved whitelist to the opening player whenever the container GUI is created, so the
     * client does not need to send a separate {@link io.github.marcsanzdev.chestseparators.network.WhitelistRequestPayload}.
     */
    @Inject(
            method =
                    "createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            at = @At("RETURN"))
    private void onOpenMenu(
            int syncId,
            Inventory playerInventory,
            Player player,
            CallbackInfoReturnable<AbstractContainerMenu> cir) {
        if (cir.getReturnValue() != null
                && this.level != null
                && !this.level.isClientSide()
                && player instanceof ServerPlayer serverPlayer) {
            if (NetworkManager.canPlayerReceive(serverPlayer, WhitelistPayload.TYPE)) {
                NetworkManager.sendToPlayer(
                        serverPlayer, new WhitelistPayload(this.worldPosition, this.chestSeparatorsWhitelists));
            }
        }
    }

    // --- NBT world-save persistence (Codec-based; one shared on-disk shape with the data component) ---

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!this.chestSeparatorsWhitelists.isEmpty()) {
            out.store("ChestSeparatorsWhitelists", SlotWhitelist.MAP_CODEC, this.chestSeparatorsWhitelists);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        this.chestSeparatorsWhitelists.clear();
        in.read("ChestSeparatorsWhitelists", SlotWhitelist.MAP_CODEC)
                .ifPresent(this.chestSeparatorsWhitelists::putAll);
    }
}
