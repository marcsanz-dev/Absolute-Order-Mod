package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

// Defines the network packet responsible for transmitting the Shulker Box UUID
// from the server to the client.
public record ShulkerUUIDPayload(UUID shulkerUUID) implements CustomPayload {

    public static final CustomPayload.Id<ShulkerUUIDPayload> ID = new CustomPayload.Id<>(Identifier.of("chestseparators", "shulker_uuid"));

    // Custom codec utilizing lambda expressions to ensure absolute compatibility
    // without relying on potentially unstable vanilla UUID codec mappings.
    public static final PacketCodec<PacketByteBuf, ShulkerUUIDPayload> CODEC = CustomPayload.codecOf(
            (payload, buf) -> buf.writeUuid(payload.shulkerUUID()),
            buf -> new ShulkerUUIDPayload(buf.readUuid())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}