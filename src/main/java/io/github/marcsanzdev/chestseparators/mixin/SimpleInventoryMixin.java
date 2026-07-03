package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.minecraft.inventory.SimpleInventory;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;

// Mixes into the client-side dummy inventory used by GUIs to allow it to store
// whitelist data. This prevents the client from predicting invalid item insertions,
// effectively eliminating UI flickering and ghost items.
@Mixin(SimpleInventory.class)
public abstract class SimpleInventoryMixin implements IWhitelistProvider {
    private Map<Integer, SlotWhitelist> whitelists = null;

    @Override
    public Map<Integer, SlotWhitelist> getWhitelists() {
        return this.whitelists;
    }

    @Override
    public void setWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        this.whitelists = whitelists;
    }
}