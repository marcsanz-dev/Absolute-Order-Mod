package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.world.ILockableContainer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Exposes a unified whitelist across both halves of a double chest ({@link InventoryLargeChest}, Mojmap
 * {@code CompoundContainer}), mapping the 54-slot GUI view onto the two physical 27-slot block inventories.
 *
 * <p>1.12.2 deltas: {@code container1}/{@code container2} (Container)→{@code upperChest}/{@code lowerChest}
 * ({@link ILockableContainer}, which extends IInventory and is the backing TileEntityChest per half).
 */
@Mixin(InventoryLargeChest.class)
public abstract class DoubleInventoryMixin implements IWhitelistProvider {

    @Override
    public Map<Integer, SlotWhitelist> getWhitelists() {
        Map<Integer, SlotWhitelist> combined = new HashMap<>();

        ILockableContainer upperChest =
                ((InventoryLargeChestAccessor) (Object) this).chestseparators$getUpperChest();
        ILockableContainer lowerChest =
                ((InventoryLargeChestAccessor) (Object) this).chestseparators$getLowerChest();

        // 1. Fetch data from the primary half (slots 0-26).
        if (upperChest instanceof IWhitelistProvider) {
            IWhitelistProvider p1 = (IWhitelistProvider) upperChest;
            if (p1.getWhitelists() != null) {
                combined.putAll(p1.getWhitelists());
            }
        }

        // 2. Fetch data from the secondary half and shift the visual index up by 27 (slots 27-53).
        if (lowerChest instanceof IWhitelistProvider) {
            IWhitelistProvider p2 = (IWhitelistProvider) lowerChest;
            if (p2.getWhitelists() != null) {
                for (Map.Entry<Integer, SlotWhitelist> entry : p2.getWhitelists().entrySet()) {
                    combined.put(entry.getKey() + 27, entry.getValue());
                }
            }
        }
        return combined;
    }

    @Override
    public void setWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        Map<Integer, SlotWhitelist> firstMap = new HashMap<>();
        Map<Integer, SlotWhitelist> secondMap = new HashMap<>();

        // Split the unified 54-slot GUI map into two distinct 27-slot physical block maps.
        for (Map.Entry<Integer, SlotWhitelist> entry : whitelists.entrySet()) {
            int slot = entry.getKey();
            if (slot < 27) {
                firstMap.put(slot, entry.getValue());
            } else {
                secondMap.put(slot - 27, entry.getValue());
            }
        }

        ILockableContainer upperChest =
                ((InventoryLargeChestAccessor) (Object) this).chestseparators$getUpperChest();
        ILockableContainer lowerChest =
                ((InventoryLargeChestAccessor) (Object) this).chestseparators$getLowerChest();
        if (upperChest instanceof IWhitelistProvider) {
            ((IWhitelistProvider) upperChest).setWhitelists(firstMap);
        }
        if (lowerChest instanceof IWhitelistProvider) {
            ((IWhitelistProvider) lowerChest).setWhitelists(secondMap);
        }
    }
}
