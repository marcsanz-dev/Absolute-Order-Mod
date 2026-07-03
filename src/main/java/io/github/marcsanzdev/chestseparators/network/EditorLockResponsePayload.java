package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

// Sent by the server to inform the client if the lock was granted or denied.
public record EditorLockResponsePayload(BlockPos pos, boolean granted) implements CustomPayload {

    public static final CustomPayload.Id<EditorLockResponsePayload> ID = new CustomPayload.Id<>(Identifier.of("chestseparators", "editor_lock_response"));
    public static final PacketCodec<PacketByteBuf, EditorLockResponsePayload> CODEC = PacketCodec.of(EditorLockResponsePayload::write, EditorLockResponsePayload::new);

    private EditorLockResponsePayload(PacketByteBuf buf) {
        this(buf.readBlockPos(), buf.readBoolean());
    }

    private void write(PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.granted);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}