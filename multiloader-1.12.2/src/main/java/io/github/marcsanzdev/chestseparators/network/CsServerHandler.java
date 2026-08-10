package io.github.marcsanzdev.chestseparators.network;

import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server-side dispatch of every C2S envelope. Decodes the channel, looks up the registered
 * {@link CsNetwork.ServerReceiver}, and runs it on the server main thread (SimpleNetworkWrapper handlers run
 * on the network thread, so anything touching world/inventory state must be scheduled) — mirroring the
 * server-authoritative model of the modern ports.
 */
public final class CsServerHandler implements IMessageHandler<CsServerBoundMessage, IMessage> {

    @Override
    public IMessage onMessage(CsServerBoundMessage msg, MessageContext ctx) {
        final EntityPlayerMP player = ctx.getServerHandler().player;
        final CsNetwork.ServerReceiver receiver = CsNetwork.serverReceiver(msg.channel);
        if (receiver == null || player == null) {
            return null;
        }
        final byte[] data = msg.data;
        player.getServerWorld().addScheduledTask(() -> {
            PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(data));
            receiver.receive(buf, player);
        });
        return null;
    }
}
