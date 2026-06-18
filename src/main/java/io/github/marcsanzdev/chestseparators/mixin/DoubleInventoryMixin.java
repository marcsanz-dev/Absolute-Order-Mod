package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.HashMap;
import java.util.Map;

@Mixin(DoubleInventory.class)
public abstract class DoubleInventoryMixin implements IWhitelistProvider {

    @Shadow @Final private Inventory first;
    @Shadow @Final private Inventory second;

    @Override
    public Map<Integer, SlotWhitelist> getWhitelists() {
        Map<Integer, SlotWhitelist> combined = new HashMap<>();

        // 1. Fetch data from the primary half (slots 0-26)
        if (this.first instanceof IWhitelistProvider p1 && p1.getWhitelists() != null) {
            combined.putAll(p1.getWhitelists());
        }

        // 2. Fetch data from the secondary half and shift the visual index up by 27 (slots 27-53)
        if (this.second instanceof IWhitelistProvider p2 && p2.getWhitelists() != null) {
            for (Map.Entry<Integer, SlotWhitelist> entry : p2.getWhitelists().entrySet()) {
                combined.put(entry.getKey() + 27, entry.getValue());
            }
        }
        return combined;
    }

    @Override
    public void setWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        Map<Integer, SlotWhitelist> firstMap = new HashMap<>();
        Map<Integer, SlotWhitelist> secondMap = new HashMap<>();

        // Split the unified 54-slot GUI map into two distinct 27-slot physical block maps
        for (Map.Entry<Integer, SlotWhitelist> entry : whitelists.entrySet()) {
            int slot = entry.getKey();
            if (slot < 27) {
                firstMap.put(slot, entry.getValue());
            } else {
                secondMap.put(slot - 27, entry.getValue());
            }
        }

        if (this.first instanceof IWhitelistProvider p1) p1.setWhitelists(firstMap);
        if (this.second instanceof IWhitelistProvider p2) p2.setWhitelists(secondMap);
    }
}