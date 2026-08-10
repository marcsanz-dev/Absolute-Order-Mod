package io.github.marcsanzdev.chestseparators.access;

/**
 * Shared duck interface for driving a container's lid animation (client-side, cosmetic). 1.16.5 has no
 * {@code ChestLidController} (that class was extracted only in 1.17): the chest/ender-chest lid opens while
 * their {@code openCount} field is &gt; 0 and the block entity's own tick interpolates the angle. So opening
 * or closing the lid just sets that field, and each container exposes it through this common interface so the
 * animator code stays container-agnostic.
 */
public interface LidAnimatorAccess {
    void chestseparators$setLidOpen(boolean open);
}
