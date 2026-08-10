package io.github.marcsanzdev.chestseparators.network;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client-side dispatch of every S2C envelope, scheduled onto the client thread.
 *
 * <p>All client-only ({@link Minecraft}) references live INSIDE {@link #onMessage} — never in fields, the
 * superclass, or method signatures — so this class can be registered from common init (it needs a stable
 * discriminator on both sides for the server's outbound sends) without the dedicated server ever loading
 * {@code Minecraft}: HotSpot only links the method body when {@code onMessage} first runs, which happens
 * only on a physical client.
 */
public final class CsClientHandler implements IMessageHandler<CsClientBoundMessage, IMessage> {

    @Override
    public IMessage onMessage(CsClientBoundMessage msg, MessageContext ctx) {
        final CsNetwork.ClientReceiver receiver = CsNetwork.clientReceiver(msg.channel);
        if (receiver == null) {
            return null;
        }
        final byte[] data = msg.data;
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(data));
            receiver.receive(buf, mc.player);
        });
        return null;
    }
}
