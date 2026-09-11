package io.github.marcsanzdev.chestseparators.client.input;

/**
 * Compatibility stand-in for the E5 {@code net.minecraft.client.input.KeyEvent} record, which does not
 * exist in 1.20.1 (E4). The editor's internal input pipeline is written against this record; the vanilla
 * {@code keyPressed(int, int, int)} boundaries (screen mixins) build one from the primitive GLFW arguments.
 * Component names mirror the vanilla accessors used across the codebase (notably {@code key()}).
 */
public final class KeyEvent {

    private final int key;
    private final int scancode;
    private final int modifiers;

    public KeyEvent(int key, int scancode, int modifiers) {
        this.key = key;
        this.scancode = scancode;
        this.modifiers = modifiers;
    }

    public int key() {
        return key;
    }

    public int scancode() {
        return scancode;
    }

    public int modifiers() {
        return modifiers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyEvent other = (KeyEvent) o;
        return key == other.key && scancode == other.scancode && modifiers == other.modifiers;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(key, scancode, modifiers);
    }

    @Override
    public String toString() {
        return "KeyEvent[key=" + key + ", scancode=" + scancode + ", modifiers=" + modifiers + "]";
    }
}
