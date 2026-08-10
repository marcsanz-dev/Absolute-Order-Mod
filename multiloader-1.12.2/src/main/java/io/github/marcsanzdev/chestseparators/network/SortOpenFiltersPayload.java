package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/**
 * Client-to-Server request sent right after a "push into the open chest" deposit, asking the server to
 * re-sort the open container's filtered groups by their priority order. Carries no data: it always
 * targets whatever container the sending player currently has open.
 */
public final class SortOpenFiltersPayload implements CsPayload {

    public SortOpenFiltersPayload() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash();
    }

    @Override
    public String toString() {
        return "SortOpenFiltersPayload[]";
    }

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "sort_open_filters");

    public static SortOpenFiltersPayload read(PacketBuffer buf) {
        return new SortOpenFiltersPayload();
    }

    @Override
    public void write(PacketBuffer buf) {}

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
