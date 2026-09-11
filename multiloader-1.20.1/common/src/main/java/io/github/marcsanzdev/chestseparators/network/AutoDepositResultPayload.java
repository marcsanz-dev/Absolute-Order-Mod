package io.github.marcsanzdev.chestseparators.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server-to-Client report of a completed auto-deposit. Lists one {@link Flight} per (item type,
 * destination container) pair so the client can animate the deposited items flying from the player
 * toward each chest. Carries an {@link ItemStack}, written with the 1.20.1 {@code FriendlyByteBuf} item
 * codec.
 */
public record AutoDepositResultPayload(List<Flight> flights, boolean reverse) implements CsPayload {

    /** One animated transfer: a representative stack (with the moved count) and its destination. */
    public record Flight(ItemStack stack, BlockPos target) {}

    /** Deposit flights (player -> chest). The grab path uses the two-arg form with reverse=true. */
    public AutoDepositResultPayload(List<Flight> flights) {
        this(flights, false);
    }

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "auto_deposit_result");

    public static AutoDepositResultPayload read(FriendlyByteBuf buf) {
        return new AutoDepositResultPayload(readFlights(buf), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.flights.size());
        for (Flight flight : this.flights) {
            buf.writeItem(flight.stack());
            buf.writeBlockPos(flight.target());
        }
        buf.writeBoolean(this.reverse);
    }

    private static List<Flight> readFlights(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Flight> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = buf.readItem();
            BlockPos target = buf.readBlockPos();
            list.add(new Flight(stack, target));
        }
        return list;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
