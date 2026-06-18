package io.github.marcsanzdev.chestseparators.util;

// Tracks the current slot interaction type using ThreadLocal to prevent
// Client and Server threads from overwriting each other's state in singleplayer.
public class ClickTracker {
    public static final ThreadLocal<Boolean> IS_SHIFT_CLICK = ThreadLocal.withInitial(() -> false);
}
