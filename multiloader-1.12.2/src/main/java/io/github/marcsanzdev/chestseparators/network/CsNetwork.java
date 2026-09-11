package io.github.marcsanzdev.chestseparators.network;

import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The SimpleNetworkWrapper hub. Recreates the Architectury networking model on legacy Forge: a single
 * channel carries two envelope messages (C2S / S2C), and receivers register by {@link ResourceLocation}
 * into per-side maps that the envelope handlers dispatch to. Payload classes and the server/client receiver
 * bodies therefore port 1:1 from the modern base.
 */
public final class CsNetwork {

    private CsNetwork() {}

    public static final String CHANNEL = "chestseparators";
    public static SimpleNetworkWrapper WRAPPER;

    /** A C2S receiver: reads the payload from {@code buf} and acts server-authoritatively for {@code player}. */
    public interface ServerReceiver {
        void receive(PacketBuffer buf, EntityPlayerMP player);
    }

    /** An S2C receiver: reads the payload from {@code buf} and updates client state for {@code player}. */
    public interface ClientReceiver {
        void receive(PacketBuffer buf, EntityPlayer player);
    }

    private static final Map<ResourceLocation, ServerReceiver> SERVER_RECEIVERS = new HashMap<>();
    private static final Map<ResourceLocation, ClientReceiver> CLIENT_RECEIVERS = new HashMap<>();

    /** Registers the channel + the two envelope messages. Call once in mod init (common side). */
    public static void init() {
        WRAPPER = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        // Distinct discriminators per direction. Registering the client handler here (common) is safe — see
        // CsClientHandler's javadoc — and gives the server the discriminator it needs for outbound S2C sends.
        WRAPPER.registerMessage(CsServerHandler.class, CsServerBoundMessage.class, 0, Side.SERVER);
        WRAPPER.registerMessage(CsClientHandler.class, CsClientBoundMessage.class, 1, Side.CLIENT);
    }

    public static void registerServer(ResourceLocation id, ServerReceiver receiver) {
        SERVER_RECEIVERS.put(id, receiver);
    }

    public static void registerClient(ResourceLocation id, ClientReceiver receiver) {
        CLIENT_RECEIVERS.put(id, receiver);
    }

    static ServerReceiver serverReceiver(ResourceLocation id) {
        return SERVER_RECEIVERS.get(id);
    }

    static ClientReceiver clientReceiver(ResourceLocation id) {
        return CLIENT_RECEIVERS.get(id);
    }

    /** Serializes a payload into a fresh byte[] via its {@link CsPayload#write}. */
    static byte[] serialize(CsPayload payload) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        payload.write(buf);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }
}
