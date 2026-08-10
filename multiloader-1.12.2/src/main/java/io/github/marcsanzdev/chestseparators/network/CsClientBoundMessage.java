package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.util.ResourceLocation;

/** Server → client envelope (handled by {@link CsClientHandler} on {@code Side.CLIENT}). */
public final class CsClientBoundMessage extends CsEnvelope {

    public CsClientBoundMessage() {
        super();
    }

    public CsClientBoundMessage(ResourceLocation channel, byte[] data) {
        super(channel, data);
    }
}
