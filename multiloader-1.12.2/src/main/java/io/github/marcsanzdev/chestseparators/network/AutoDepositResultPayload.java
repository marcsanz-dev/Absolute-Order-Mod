package io.github.marcsanzdev.chestseparators.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/**
 * Server-to-Client report of a completed auto-deposit. Lists one {@link Flight} per (item type,
 * destination container) pair so the client can animate the deposited items flying from the player
 * toward each chest. Carries an {@link ItemStack}, written with the {@code PacketBuffer} item codec.
 */
public final class AutoDepositResultPayload implements CsPayload {

    /** One animated transfer: a representative stack (with the moved count) and its destination. */
    public static final class Flight {
        private final ItemStack stack;
        private final BlockPos target;

        public Flight(ItemStack stack, BlockPos target) {
            this.stack = stack;
            this.target = target;
        }

        public ItemStack stack() {
            return stack;
        }

        public BlockPos target() {
            return target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Flight x = (Flight) o;
            return java.util.Objects.equals(stack, x.stack) && java.util.Objects.equals(target, x.target);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(stack, target);
        }

        @Override
        public String toString() {
            return "Flight[stack=" + stack + ", target=" + target + "]";
        }
    }

    private final List<Flight> flights;
    private final boolean reverse;

    public AutoDepositResultPayload(List<Flight> flights, boolean reverse) {
        this.flights = flights;
        this.reverse = reverse;
    }

    /** Deposit flights (player -> chest). The grab path uses the two-arg form with reverse=true. */
    public AutoDepositResultPayload(List<Flight> flights) {
        this(flights, false);
    }

    public List<Flight> flights() {
        return flights;
    }

    public boolean reverse() {
        return reverse;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AutoDepositResultPayload x = (AutoDepositResultPayload) o;
        return reverse == x.reverse && java.util.Objects.equals(flights, x.flights);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(flights, reverse);
    }

    @Override
    public String toString() {
        return "AutoDepositResultPayload[flights=" + flights + ", reverse=" + reverse + "]";
    }

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "auto_deposit_result");

    public static AutoDepositResultPayload read(PacketBuffer buf) {
        return new AutoDepositResultPayload(readFlights(buf), buf.readBoolean());
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeVarInt(this.flights.size());
        for (Flight flight : this.flights) {
            buf.writeItemStack(flight.stack());
            buf.writeBlockPos(flight.target());
        }
        buf.writeBoolean(this.reverse);
    }

    private static List<Flight> readFlights(PacketBuffer buf) {
        int size = buf.readVarInt();
        List<Flight> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack;
            try {
                stack = buf.readItemStack();
            } catch (java.io.IOException e) {
                stack = ItemStack.EMPTY;
            }
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
