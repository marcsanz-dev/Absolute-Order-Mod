package io.github.marcsanzdev.chestseparators.client.input;

/**
 * Compatibility stand-in for the E5 {@code net.minecraft.client.input.CharacterEvent} record (absent in
 * 1.20.1 / E4). Built from the vanilla {@code charTyped(char, int)} boundary. {@link #codepoint()} carries
 * the typed character promoted to an int, matching the E5 accessor name.
 */
public final class CharacterEvent {

    private final int codepoint;
    private final int modifiers;

    public CharacterEvent(int codepoint, int modifiers) {
        this.codepoint = codepoint;
        this.modifiers = modifiers;
    }

    public int codepoint() {
        return codepoint;
    }

    public int modifiers() {
        return modifiers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CharacterEvent other = (CharacterEvent) o;
        return codepoint == other.codepoint && modifiers == other.modifiers;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(codepoint, modifiers);
    }

    @Override
    public String toString() {
        return "CharacterEvent[codepoint=" + codepoint + ", modifiers=" + modifiers + "]";
    }
}
