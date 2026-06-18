package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

// Client-to-Server request packet to fetch whitelist data for a specific chest.
public record WhitelistRequestPayload(BlockPos pos) implements CustomPayload {

    public static final CustomPayload.Id<WhitelistRequestPayload> ID = new CustomPayload.Id<>(Identifier.of("chestseparators", "whitelist_request"));
    public static final PacketCodec<PacketByteBuf, WhitelistRequestPayload> CODEC = PacketCodec.of(WhitelistRequestPayload::write, WhitelistRequestPayload::new);

    private WhitelistRequestPayload(PacketByteBuf buf) {
        this(buf.readBlockPos());
    }

    private void write(PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}