package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

// Sent by the client to request a lock on a chest before opening the editor,
// or to release the lock when closing the editor.
public record EditorLockRequestPayload(BlockPos pos, boolean isLocking) implements CustomPayload {

    public static final CustomPayload.Id<EditorLockRequestPayload> ID = new CustomPayload.Id<>(Identifier.of("chestseparators", "editor_lock_request"));
    public static final PacketCodec<PacketByteBuf, EditorLockRequestPayload> CODEC = PacketCodec.of(EditorLockRequestPayload::write, EditorLockRequestPayload::new);

    private EditorLockRequestPayload(PacketByteBuf buf) {
        this(buf.readBlockPos(), buf.readBoolean());
    }

    private void write(PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.isLocking);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}