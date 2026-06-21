package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client-to-Server request to fill the player's inventory from the currently open container (the
 * "fill inventory" button in the chest editor). The server reads the open screen handler's container
 * and pulls items the player's inventory filters want, up to each filter's target count. The block
 * position is used only as the origin of the cosmetic fly-back animation.
 */
public record FillFromChestPayload(BlockPos animPos) implements CustomPayload {

    public static final CustomPayload.Id<FillFromChestPayload> ID =
            new CustomPayload.Id<>(Identifier.of("chestseparators", "fill_from_chest"));

    public static final PacketCodec<PacketByteBuf, FillFromChestPayload> CODEC =
            PacketCodec.of(FillFromChestPayload::write, FillFromChestPayload::new);

    private FillFromChestPayload(PacketByteBuf buf) {
        this(buf.readBlockPos());
    }

    private void write(PacketByteBuf buf) {
        buf.writeBlockPos(this.animPos);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
