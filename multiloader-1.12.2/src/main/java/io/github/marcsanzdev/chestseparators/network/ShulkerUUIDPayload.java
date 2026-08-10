package io.github.marcsanzdev.chestseparators.network;

import java.util.UUID;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

// Defines the network packet responsible for transmitting the Shulker Box UUID
// from the server to the client.
public final class ShulkerUUIDPayload implements CsPayload {

    private final UUID shulkerUUID;

    public ShulkerUUIDPayload(UUID shulkerUUID) {
        this.shulkerUUID = shulkerUUID;
    }

    public UUID shulkerUUID() {
        return shulkerUUID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShulkerUUIDPayload x = (ShulkerUUIDPayload) o;
        return java.util.Objects.equals(shulkerUUID, x.shulkerUUID);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(shulkerUUID);
    }

    @Override
    public String toString() {
        return "ShulkerUUIDPayload[shulkerUUID=" + shulkerUUID + "]";
    }

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "shulker_uuid");

    public static ShulkerUUIDPayload read(PacketBuffer buf) {
        return new ShulkerUUIDPayload(buf.readUniqueId());
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeUniqueId(this.shulkerUUID);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
