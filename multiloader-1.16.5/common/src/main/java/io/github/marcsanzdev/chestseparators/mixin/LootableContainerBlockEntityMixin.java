package io.github.marcsanzdev.chestseparators.mixin;

import me.shedaniel.architectury.networking.NetworkManager;
import io.github.marcsanzdev.chestseparators.network.ModNet;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.network.WhitelistS2CPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements {@link IWhitelistProvider} on all standard lootable containers (Chests, Barrels, Shulker
 * Boxes — every subclass of {@code RandomizableContainerBlockEntity}) and owns the canonical NBT
 * persistence for whitelist data under the "ChestSeparatorsWhitelists" key.
 *
 * <p>On open it pushes the saved whitelist to the opener (S2C) so the editor shows the server-authoritative
 * state immediately, without a {@code WhitelistRequestPayload} round-trip. The S2C payload type is a
 * dedicated one ({@link WhitelistS2CPayload}) registered by the client receiver at client init — NeoForge
 * forbids a single payload id for both directions, so the C2S save and this S2C push use different ids.
 */
@Mixin(RandomizableContainerBlockEntity.class)
public abstract class LootableContainerBlockEntityMixin extends BlockEntity implements IWhitelistProvider {

    @Unique
    private Map<Integer, SlotWhitelist> chestSeparatorsWhitelists = new HashMap<>();

    // 1.16.5 BlockEntity has a single-arg constructor (BlockPos/BlockState were added to it in 1.17). This
    // ctor only exists to satisfy the `extends BlockEntity` shadow; it is never invoked at runtime.
    public LootableContainerBlockEntityMixin(BlockEntityType<?> type) {
        super(type);
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
     * Pushes the saved whitelist to the opening player whenever the container GUI is created, so the client
     * shows the server-authoritative filters immediately (and co-viewers in multiplayer stay in sync).
     */
    @Inject(method = "createMenu", at = @At("RETURN"))
    private void chestseparators$onOpenMenu(
            int syncId, Inventory playerInventory, Player player, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        if (cir.getReturnValue() != null
                && this.getLevel() != null
                && !this.getLevel().isClientSide()
                && player instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            if (io.github.marcsanzdev.chestseparators.network.ModNet.playerCanReceive(serverPlayer, WhitelistS2CPayload.ID)) {
                ModNet.sendToPlayer(
                        serverPlayer, new WhitelistS2CPayload(this.getBlockPos(), this.chestSeparatorsWhitelists));
            }
        }
    }

    // --- NBT world-save persistence (Codec-based CompoundTag; vanilla's BlockEntityTag rides this NBT onto
    // the dropped item for shulker boxes, replacing the 1.20.5+ data-component round-trip). ---

    // 1.16.5 persists via save(CompoundTag)->CompoundTag and load(BlockState, CompoundTag) (saveAdditional and
    // the state-less load arrived in 1.17/1.18).
    @Override
    public CompoundTag save(CompoundTag tag) {
        super.save(tag);
        SlotWhitelist.writeMapToTag(tag, "ChestSeparatorsWhitelists", this.chestSeparatorsWhitelists);
        return tag;
    }

    @Override
    public void load(BlockState state, CompoundTag tag) {
        super.load(state, tag);
        this.chestSeparatorsWhitelists = SlotWhitelist.readMapFromTag(tag, "ChestSeparatorsWhitelists");
    }
}
