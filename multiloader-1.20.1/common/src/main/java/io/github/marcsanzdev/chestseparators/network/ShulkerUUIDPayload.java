package io.github.marcsanzdev.chestseparators.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Defines the network packet responsible for transmitting the Shulker Box UUID
// from the server to the client.
public record ShulkerUUIDPayload(UUID shulkerUUID) implements CsPayload {

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "shulker_uuid");

    public static ShulkerUUIDPayload read(FriendlyByteBuf buf) {
        return new ShulkerUUIDPayload(buf.readUUID());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.shulkerUUID);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
