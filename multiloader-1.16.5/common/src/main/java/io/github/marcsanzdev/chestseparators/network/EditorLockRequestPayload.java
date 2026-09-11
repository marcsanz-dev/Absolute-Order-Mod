package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Sent by the client to request a lock on a chest before opening the editor,
// or to release the lock when closing the editor.
public final class EditorLockRequestPayload implements CsPayload {

    private final BlockPos pos;
    private final boolean isLocking;

    public EditorLockRequestPayload(BlockPos pos, boolean isLocking) {
        this.pos = pos;
        this.isLocking = isLocking;
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean isLocking() {
        return isLocking;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EditorLockRequestPayload x = (EditorLockRequestPayload) o;
        return isLocking == x.isLocking && java.util.Objects.equals(pos, x.pos);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(pos, isLocking);
    }

    @Override
    public String toString() {
        return "EditorLockRequestPayload[pos=" + pos + ", isLocking=" + isLocking + "]";
    }

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
