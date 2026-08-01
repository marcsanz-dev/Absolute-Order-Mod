package io.github.marcsanzdev.chestseparators.access;

import net.minecraft.block.entity.ChestLidAnimator;

/**
 * Shared duck interface exposing a container's private {@link ChestLidAnimator}. Chests and ender chests
 * each declare their OWN {@code lidAnimator} field, so a single multi-target {@code @Accessor} remaps to
 * only one of the two intermediary names and crashes on the other at runtime (fine in dev, where named
 * mappings resolve both). Each container therefore gets its own single-target accessor mixin, and both
 * expose the animator through this common interface so the animator code can stay container-agnostic.
 */
public interface LidAnimatorAccess {
    ChestLidAnimator getLidAnimator();
}
