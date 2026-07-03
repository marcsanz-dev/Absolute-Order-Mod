package io.github.marcsanzdev.chestseparators.util;

/**
 * Tracks the current slot interaction type using thread-local storage.
 * <p>
 * Utilizing {@link ThreadLocal} prevents the Client and Server threads from
 * overwriting each other's state during integrated singleplayer sessions,
 * ensuring accurate click-type detection for whitelist enforcement.
 */
public class ClickTracker {

    private static final ThreadLocal<Boolean> IS_SHIFT_CLICK = ThreadLocal.withInitial(() -> false);

    /**
     * Retrieves the shift-click state for the current active thread.
     *
     * @return {@code true} if the current interaction is a shift-click, {@code false} otherwise.
     */
    public static boolean isShiftClick() {
        return IS_SHIFT_CLICK.get();
    }

    /**
     * Sets the shift-click state for the current active thread.
     *
     * @param isShiftClick The state to assign to the current thread.
     */
    public static void setShiftClick(boolean isShiftClick) {
        IS_SHIFT_CLICK.set(isShiftClick);
    }

    /**
     * Removes the current thread's value for this thread-local variable.
     * <p>
     * This must be called at the end of the interaction lifecycle to prevent memory
     * leaks and ensure clean state boundaries in thread-pooled environments.
     */
    public static void clear() {
        IS_SHIFT_CLICK.remove();
    }
}