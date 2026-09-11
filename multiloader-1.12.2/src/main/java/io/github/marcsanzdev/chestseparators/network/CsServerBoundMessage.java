package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.util.ResourceLocation;

/** Client → server envelope (handled by {@link CsServerHandler} on {@code Side.SERVER}). */
public final class CsServerBoundMessage extends CsEnvelope {

    public CsServerBoundMessage() {
        super();
    }

    public CsServerBoundMessage(ResourceLocation channel, byte[] data) {
        super(channel, data);
    }
}
