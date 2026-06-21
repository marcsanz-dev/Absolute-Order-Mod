package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-Server sync of the player's inventory filters (client-side only otherwise). The server
 * caches them per player so it can enforce the "Pick Up" rule: filtered inventory slots only accept
 * their item when items are picked up from the ground. Keyed by PlayerInventory slot index.
 */
public record InventoryFiltersPayload(Map<Integer, SlotWhitelist> filters) implements CustomPayload {

    public static final CustomPayload.Id<InventoryFiltersPayload> ID =
            new CustomPayload.Id<>(Identifier.of("chestseparators", "inventory_filters"));

    public static final PacketCodec<PacketByteBuf, InventoryFiltersPayload> CODEC =
            PacketCodec.of(InventoryFiltersPayload::write, InventoryFiltersPayload::new);

    private InventoryFiltersPayload(PacketByteBuf buf) {
        this(readMap(buf));
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(this.filters.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : this.filters.entrySet()) {
            buf.writeVarInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readMap(PacketByteBuf buf) {
        int size = buf.readVarInt();
        Map<Integer, SlotWhitelist> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int key = buf.readVarInt();
            map.put(key, SlotWhitelist.PACKET_CODEC.decode(buf));
        }
        return map;
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
