package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-Server request sent right after a "push into the open chest" deposit (the deposit button /
 * hotkey), asking the server to re-sort the open container's filtered groups by their priority order.
 *
 * <p>The push itself is performed client-side through vanilla slot clicks (so client prediction and the
 * server agree), which can leave a just-deposited higher-priority item behind lower-priority items already
 * stored. This follow-up lets the server authoritatively re-lay each group in priority order — packed to
 * the first slot — without the client juggling stacks and risking a desync. Carries no data: it always
 * targets whatever container the sending player currently has open.
 */
public record SortOpenFiltersPayload() implements CustomPayload {

    public static final CustomPayload.Id<SortOpenFiltersPayload> ID =
            new CustomPayload.Id<>(Identifier.of("chestseparators", "sort_open_filters"));

    public static final PacketCodec<PacketByteBuf, SortOpenFiltersPayload> CODEC =
            PacketCodec.unit(new SortOpenFiltersPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
