package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/**
 * Minimal payload abstraction, mirroring the modern ports so payload classes stay nearly verbatim. Each
 * payload carries its channel id and knows how to serialize itself into a {@link PacketBuffer}; {@link ModNet}
 * performs the send. Each payload also exposes a static {@code read(PacketBuffer)} used by the receivers.
 *
 * <p>1.12.2 has no {@code CustomPacketPayload}; sends ride Forge's {@link net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper}
 * via the two envelope messages in {@link CsNetwork}, which route by this {@link ResourceLocation} exactly as
 * Architectury's {@code NetworkManager} did (so the whole payload+receiver layer ports 1:1).
 */
public interface CsPayload {

    ResourceLocation id();

    void write(PacketBuffer buf);
}
