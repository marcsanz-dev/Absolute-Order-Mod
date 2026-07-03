package io.github.marcsanzdev.chestseparators.access;

import java.util.UUID;

/**
 * Interface injected via Mixin to expose the internal UUID of a Shulker Box.
 * <p>
 * This allows other parts of the codebase to retrieve or modify the unique identifier
 * associated with a specific Shulker Box block entity without requiring direct access
 * to the modified class.
 */
public interface IShulkerUUIDProvider {

    /**
     * Retrieves the UUID associated with this Shulker Box.
     * <p>
     * If no UUID is currently assigned to the block entity, implementations
     * should generate, store, and return a newly created UUID.
     *
     * @return The unique identifier for this Shulker Box.
     */
    UUID getShulkerUUID();

    /**
     * Restores or assigns a previously saved UUID to this Shulker Box instance.
     * <p>
     * This is typically used to maintain data persistence when the block is
     * placed back into the world from an {@code ItemStack}.
     *
     * @param uuid The unique identifier to assign to this block entity.
     */
    void setShulkerUUID(UUID uuid);
}