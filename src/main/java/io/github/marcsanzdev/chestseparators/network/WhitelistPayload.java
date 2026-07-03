package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

// Payload for synchronizing whitelist data between the Server and the Client.
// Used bidirectionally (C2S for saving, S2C for loading the GUI).
public record WhitelistPayload(BlockPos pos, Map<Integer, SlotWhitelist> whitelists) implements CustomPayload {

    public static final CustomPayload.Id<WhitelistPayload> ID = new CustomPayload.Id<>(Identifier.of("chestseparators", "whitelist_sync"));

    // Custom codec for safe network transmission.
    public static final PacketCodec<PacketByteBuf, WhitelistPayload> CODEC = PacketCodec.of(WhitelistPayload::write, WhitelistPayload::new);

    private WhitelistPayload(PacketByteBuf buf) {
        this(buf.readBlockPos(), readMap(buf));
    }

    private void write(PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.whitelists.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : this.whitelists.entrySet()) {
            buf.writeInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readMap(PacketByteBuf buf) {
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
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}