package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Sent by the client to request a lock on a chest before opening the editor,
// or to release the lock when closing the editor.
public record EditorLockRequestPayload(BlockPos pos, boolean isLocking) implements CsPayload {

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "editor_lock_request");

    public static EditorLockRequestPayload read(FriendlyByteBuf buf) {
        return new EditorLockRequestPayload(buf.readBlockPos(), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.isLocking);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
