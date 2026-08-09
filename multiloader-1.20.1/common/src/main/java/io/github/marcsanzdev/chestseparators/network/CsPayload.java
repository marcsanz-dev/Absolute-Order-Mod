package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Minimal payload abstraction for the 1.20.1 (pre-1.20.5) networking era, where vanilla has no
 * {@code CustomPacketPayload}/{@code StreamCodec}. Each payload carries its channel id and knows how to
 * serialize itself into a raw {@link FriendlyByteBuf}; {@link ModNet} performs the send. Each payload also
 * exposes a static {@code read(FriendlyByteBuf)} used by the receivers in {@link ModNetworking} and
 * {@link ModClientNetworking}.
 */
public interface CsPayload {

    ResourceLocation id();

    void write(FriendlyByteBuf buf);
}
