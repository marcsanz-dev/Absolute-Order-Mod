package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.inventory.SimpleInventory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Adds {@link IWhitelistProvider} to {@link SimpleInventory}, which is used in two contexts:
 *
 * <ul>
 *   <li>The client-side dummy inventory backing open container GUIs. Whitelist data is mirrored
 *       here so that the client can reject invalid insertions locally before a server round-trip,
 *       preventing ghost items and UI flicker.</li>
 *   <li>The cargo inventory of donkeys, mules, llamas, and alpacas. Since these entities use
 *       a {@code SimpleInventory} internally, this mixin automatically gives them whitelist
 *       enforcement without a separate entity mixin.</li>
 * </ul>
 *
 * <p>No persistence is performed here; data is always sourced from the server payload or from
 * the entity's local UUID-keyed config file.
 */
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
