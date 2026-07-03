package io.github.marcsanzdev.chestseparators.access;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;

/**
 * Interface injected via Mixin into {@code BlockEntity} classes to expose
 * their internal inventory whitelist data.
 */
public interface IWhitelistProvider {

    /**
     * Retrieves the current mapping of inventory slot indices to their respective
     * whitelist configurations.
     *
     * @return A map containing the configured whitelists, where the key is the slot index.
     */
    Map<Integer, SlotWhitelist> getWhitelists();

    /**
     * Replaces the current whitelist mapping with a new collection of configurations.
     * <p>
     * Implementations must ensure that updating this map marks the underlying
     * block entity as dirty to guarantee proper data serialization and saving.
     *
     * @param whitelists The new map of slot indices to whitelist configurations.
     */
    void setWhitelists(Map<Integer, SlotWhitelist> whitelists);
}