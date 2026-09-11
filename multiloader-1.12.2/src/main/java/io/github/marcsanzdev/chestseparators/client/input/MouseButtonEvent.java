package io.github.marcsanzdev.chestseparators.client.input;

/**
 * Compatibility stand-in for the E5 {@code net.minecraft.client.input.MouseButtonEvent} record (absent in
 * 1.20.1 / E4). Built from the vanilla {@code mouseClicked(double, double, int)} boundary. Component names
 * mirror the vanilla accessors used across the codebase ({@code x()}, {@code y()}, {@code button()}).
 */
public final class MouseButtonEvent {

    private final double x;
    private final double y;
    private final int button;

    public MouseButtonEvent(double x, double y, int button) {
        this.x = x;
        this.y = y;
        this.button = button;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public int button() {
        return button;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MouseButtonEvent other = (MouseButtonEvent) o;
        return Double.compare(x, other.x) == 0
                && Double.compare(y, other.y) == 0
                && button == other.button;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y, button);
    }

    @Override
    public String toString() {
        return "MouseButtonEvent[x=" + x + ", y=" + y + ", button=" + button + "]";
    }
}
