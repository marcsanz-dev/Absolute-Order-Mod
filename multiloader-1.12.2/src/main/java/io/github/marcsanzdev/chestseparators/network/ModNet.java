package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

/**
 * Thin send facade over {@link CsNetwork}, keeping the modern call shape ({@code ModNet.sendToServer(payload)} /
 * {@code ModNet.sendToPlayer(player, payload)}) so the ported receiver bodies read unchanged.
 */
public final class ModNet {

    private ModNet() {}

    /** Client -> server. */
    public static void sendToServer(CsPayload payload) {
        CsNetwork.WRAPPER.sendToServer(new CsServerBoundMessage(payload.id(), CsNetwork.serialize(payload)));
    }

    /** Server -> a single client. */
    public static void sendToPlayer(EntityPlayerMP player, CsPayload payload) {
        try {
            CsNetwork.WRAPPER.sendTo(new CsClientBoundMessage(payload.id(), CsNetwork.serialize(payload)), player);
        } catch (Exception e) {
            // A client that genuinely cannot receive this packet must never take down the server thread.
            System.err.println("[chestseparators] S2C send failed for " + payload.id() + ": " + e);
        }
    }

    /**
     * Whether it is safe to send an S2C packet of {@code id} to {@code player}. Always {@code true}:
     * chestseparators is a REQUIRED dependency, so every connected client has registered all S2C receivers.
     */
    public static boolean playerCanReceive(EntityPlayerMP player, ResourceLocation id) {
        return true;
    }
}
