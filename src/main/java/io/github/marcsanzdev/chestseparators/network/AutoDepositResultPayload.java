package io.github.marcsanzdev.chestseparators.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Server-to-Client report of a completed auto-deposit. Lists one {@link Flight} per (item type,
 * destination container) pair so the client can animate the deposited items flying from the player
 * toward each chest. Carries an {@link ItemStack} so it must travel over a {@link RegistryByteBuf}.
 */
public record AutoDepositResultPayload(List<Flight> flights, boolean reverse) implements CustomPayload {

    /** One animated transfer: a representative stack (with the moved count) and its destination. */
    public record Flight(ItemStack stack, BlockPos target) {}

    /** Deposit flights (player -> chest). The grab path uses the two-arg form with reverse=true. */
    public AutoDepositResultPayload(List<Flight> flights) {
        this(flights, false);
    }

    public static final CustomPayload.Id<AutoDepositResultPayload> ID =
            new CustomPayload.Id<>(Identifier.of("chestseparators", "auto_deposit_result"));

    public static final PacketCodec<RegistryByteBuf, AutoDepositResultPayload> CODEC =
            PacketCodec.of(AutoDepositResultPayload::write, AutoDepositResultPayload::new);

    private AutoDepositResultPayload(RegistryByteBuf buf) {
        this(readFlights(buf), buf.readBoolean());
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarInt(this.flights.size());
        for (Flight flight : this.flights) {
            ItemStack.PACKET_CODEC.encode(buf, flight.stack());
            buf.writeBlockPos(flight.target());
        }
        buf.writeBoolean(this.reverse);
    }

    private static List<Flight> readFlights(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<Flight> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = ItemStack.PACKET_CODEC.decode(buf);
            BlockPos target = buf.readBlockPos();
            list.add(new Flight(stack, target));
        }
        return list;
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
