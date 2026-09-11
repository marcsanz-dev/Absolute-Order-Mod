package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

// Payload for synchronizing whitelist data between the Server and the Client.
// Used bidirectionally (C2S for saving, S2C for loading the GUI).
public final class WhitelistPayload implements CsPayload {

    private final BlockPos pos;
    private final Map<Integer, SlotWhitelist> whitelists;

    public WhitelistPayload(BlockPos pos, Map<Integer, SlotWhitelist> whitelists) {
        this.pos = pos;
        this.whitelists = whitelists;
    }

    public BlockPos pos() {
        return pos;
    }

    public Map<Integer, SlotWhitelist> whitelists() {
        return whitelists;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WhitelistPayload x = (WhitelistPayload) o;
        return java.util.Objects.equals(pos, x.pos) && java.util.Objects.equals(whitelists, x.whitelists);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(pos, whitelists);
    }

    @Override
    public String toString() {
        return "WhitelistPayload[pos=" + pos + ", whitelists=" + whitelists + "]";
    }

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "whitelist_sync");

    public static WhitelistPayload read(PacketBuffer buf) {
        return new WhitelistPayload(buf.readBlockPos(), readMap(buf));
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.whitelists.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : this.whitelists.entrySet()) {
            buf.writeInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readMap(PacketBuffer buf) {
        int size = buf.readInt();
        Map<Integer, SlotWhitelist> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int key = buf.readInt();
            SlotWhitelist value = SlotWhitelist.PACKET_CODEC.decode(buf);
            map.put(key, value);
        }
        return map;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
