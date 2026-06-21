package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-Server request to auto-deposit the player's inventory into nearby filtered containers.
 * The client forwards its own config (radius, through-walls) so the behaviour matches the player's
 * settings; the server clamps the radius to a safe maximum before acting.
 */
public record AutoDepositRequestPayload(int radius, boolean throughWalls) implements CustomPayload {

    public static final CustomPayload.Id<AutoDepositRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of("chestseparators", "auto_deposit_request"));

    public static final PacketCodec<PacketByteBuf, AutoDepositRequestPayload> CODEC =
            PacketCodec.of(AutoDepositRequestPayload::write, AutoDepositRequestPayload::new);

    private AutoDepositRequestPayload(PacketByteBuf buf) {
        this(buf.readVarInt(), buf.readBoolean());
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(this.radius);
        buf.writeBoolean(this.throughWalls);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
