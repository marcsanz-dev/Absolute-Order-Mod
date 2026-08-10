package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Client-to-Server request packet to fetch whitelist data for a specific chest.
public final class WhitelistRequestPayload implements CsPayload {

    private final BlockPos pos;

    public WhitelistRequestPayload(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WhitelistRequestPayload x = (WhitelistRequestPayload) o;
        return java.util.Objects.equals(pos, x.pos);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(pos);
    }

    @Override
    public String toString() {
        return "WhitelistRequestPayload[pos=" + pos + "]";
    }

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "whitelist_request");

    public static WhitelistRequestPayload read(FriendlyByteBuf buf) {
        return new WhitelistRequestPayload(buf.readBlockPos());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
