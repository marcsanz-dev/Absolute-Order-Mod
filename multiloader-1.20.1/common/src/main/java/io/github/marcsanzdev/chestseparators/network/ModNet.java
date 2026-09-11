package io.github.marcsanzdev.chestseparators.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
        try {
            NetworkManager.sendToPlayer(player, payload.id(), serialize(payload));
        } catch (Exception e) {
            // A client that genuinely cannot receive this packet must never take down the server thread.
            System.err.println("[chestseparators] S2C send failed for " + payload.id() + ": " + e);
        }
    }

    /**
     * Whether it is safe to send an S2C packet of {@code id} to {@code player}. Always {@code true}:
     * chestseparators is a REQUIRED dependency, so every connected client has registered all S2C receivers.
     *
     * <p>Replaces {@link NetworkManager#canPlayerReceive}, which false-negatives the local client on Forge
     * single-player (the integrated server never learns the client's registered receivers) and so silently
     * dropped every server-authoritative reply — filters, auto-deposit animations, whitelist sync. On the
     * loaders in this module (Fabric + Forge; no NeoForge) unconditional sends are safe.
     */
    public static boolean playerCanReceive(ServerPlayer player, ResourceLocation id) {
        return true;
    }
}
