package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Sent by the server to inform the client if the lock was granted or denied.
public record EditorLockResponsePayload(BlockPos pos, boolean granted) implements CsPayload {

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "editor_lock_response");

    public static EditorLockResponsePayload read(FriendlyByteBuf buf) {
        return new EditorLockResponsePayload(buf.readBlockPos(), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.granted);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
