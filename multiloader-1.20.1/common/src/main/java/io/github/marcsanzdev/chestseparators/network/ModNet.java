package io.github.marcsanzdev.chestseparators.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Raw-buffer send helpers for the 1.20.1 networking era. Vanilla 1.20.1 has no {@code CustomPacketPayload};
 * Architectury's {@link NetworkManager} routes packets by {@code ResourceLocation} + {@link FriendlyByteBuf},
 * so each {@link CsPayload} is serialized into a fresh buffer before being handed to the platform.
 */
public final class ModNet {

    private ModNet() {}

    private static FriendlyByteBuf serialize(CsPayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        payload.write(buf);
        return buf;
    }

    /** Client -> server. */
    public static void sendToServer(CsPayload payload) {
        NetworkManager.sendToServer(payload.id(), serialize(payload));
    }

    /** Server -> a single client. */
    public static void sendToPlayer(ServerPlayer player, CsPayload payload) {
        NetworkManager.sendToPlayer(player, payload.id(), serialize(payload));
    }
}
