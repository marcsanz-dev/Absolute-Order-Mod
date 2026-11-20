package io.github.marcsanzdev.chestseparators.util;

/**
 * Continuous Keep slider mapping, with five visible reference marks.
 * Left to right: All, 64, 32, 16, 1. All (0) remains distinct from exactly 64.
 * Within the numeric portion, interpolation allows EVERY integer from 1 to 64.
 */
public final class InventoryRetentionSteps {
    private static final int[] MARKERS = {0, 64, 32, 16, 1};

    private InventoryRetentionSteps() {}

    public static int count() {
        return MARKERS.length;
    }

    public static int valueForStop(int index) {
        return MARKERS[Math.max(0, Math.min(MARKERS.length - 1, index))];
    }

    /** Dragging exactly to the left end selects All; its neighboring region selects 64. */
    public static int valueAtFraction(double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        if (f == 0.0) return 0;
        if (f <= 0.25) return 64;
        if (f <= 0.50) return 64 - (int) Math.round((f - 0.25) / 0.25 * 32.0);
        if (f <= 0.75) return 32 - (int) Math.round((f - 0.50) / 0.25 * 16.0);
        return Math.max(1, 16 - (int) Math.round((f - 0.75) / 0.25 * 15.0));
    }

    /** Inverse mapping: a saved custom quantity is drawn at its true position, not snapped to a mark. */
    public static double fractionForValue(int value) {
        if (value <= 0) return 0.0;
        if (value >= 64) return 0.25;
        if (value >= 32) return 0.25 + (64 - value) / 32.0 * 0.25;
        if (value >= 16) return 0.50 + (32 - value) / 16.0 * 0.25;
        return 0.75 + (16 - value) / 15.0 * 0.25;
    }

    /**
     * Return the 64/32/16 stack-size detent nearest the pointer, or -1 outside the
     * requested pixel radius. All and 1 remain ordinary endpoints rather than magnetic
     * marks. Callers use a smaller radius to enter a detent than to leave it (hysteresis).
     * Actual values between detents are still selectable with dragging or the wheel.
     */
    public static int detentNearFraction(double fraction, double trackWidth, double radiusPixels) {
        if (trackWidth <= 0 || radiusPixels < 0) return -1;
        double pointerX = Math.max(0.0, Math.min(1.0, fraction)) * trackWidth;
        for (int value : new int[] {64, 32, 16}) {
            if (Math.abs(pointerX - fractionForValue(value) * trackWidth) <= radiusPixels) {
                return value;
            }
        }
        return -1;
    }

    /** Scroll wheel gives exact counts even when the game uses a large GUI scale. */
    public static int adjustByWheel(int value, int steps) {
        if (steps == 0) return value;
        int start = value <= 0 ? 64 : Math.max(1, Math.min(64, value));
        return Math.max(1, Math.min(64, start + steps));
    }
}
