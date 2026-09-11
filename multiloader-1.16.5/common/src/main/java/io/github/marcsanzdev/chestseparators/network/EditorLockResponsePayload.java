package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Sent by the server to inform the client if the lock was granted or denied.
public final class EditorLockResponsePayload implements CsPayload {

    private final BlockPos pos;
    private final boolean granted;

    public EditorLockResponsePayload(BlockPos pos, boolean granted) {
        this.pos = pos;
        this.granted = granted;
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean granted() {
        return granted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EditorLockResponsePayload x = (EditorLockResponsePayload) o;
        return granted == x.granted && java.util.Objects.equals(pos, x.pos);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(pos, granted);
    }

    @Override
    public String toString() {
        return "EditorLockResponsePayload[pos=" + pos + ", granted=" + granted + "]";
    }

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
