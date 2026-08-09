package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Client-to-Server request packet to fetch whitelist data for a specific chest.
public record WhitelistRequestPayload(BlockPos pos) implements CsPayload {

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
