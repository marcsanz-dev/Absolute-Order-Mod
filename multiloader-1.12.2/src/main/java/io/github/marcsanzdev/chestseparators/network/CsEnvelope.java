package io.github.marcsanzdev.chestseparators.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * SimpleNetworkWrapper envelope: carries a mod channel id plus the raw payload bytes. This is what actually
 * travels the wire; {@link CsServerHandler}/{@link CsClientHandler} decode {@link #channel} to dispatch to
 * the right receiver, exactly as Architectury's {@code NetworkManager} routed by {@code ResourceLocation}.
 * Concrete subclasses ({@link CsServerBoundMessage} C2S / {@link CsClientBoundMessage} S2C) exist only so the
 * wrapper can assign each direction its own discriminator + handler + {@code Side}.
 */
public abstract class CsEnvelope implements IMessage {

    public ResourceLocation channel;
    public byte[] data;

    protected CsEnvelope() {}

    protected CsEnvelope(ResourceLocation channel, byte[] data) {
        this.channel = channel;
        this.data = data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        this.channel = new ResourceLocation(pb.readString(256));
        this.data = pb.readByteArray();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeString(channel.toString());
        pb.writeByteArray(data);
    }
}
