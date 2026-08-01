package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Implements {@link IWhitelistProvider} on all standard lootable containers (Chests, Barrels, Shulker
 * Boxes — every subclass of {@code RandomizableContainerBlockEntity}) and owns the canonical NBT
 * persistence for whitelist data under the "ChestSeparatorsWhitelists" key.
 *
 * <p>Note: the container GUI does NOT push its whitelist to the opener here. That would be an S2C send,
 * and the S2C payload types are only registered once the client-side receivers are migrated (the GUI
 * phase). Until then the client fetches the whitelist on open via {@code WhitelistRequestPayload}
 * (C2S → S2C response). Sending S2C from the integrated server before its type is registered crashes on
 * NeoForge ("codec is null" in NetworkManager.sendToPlayer).
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
