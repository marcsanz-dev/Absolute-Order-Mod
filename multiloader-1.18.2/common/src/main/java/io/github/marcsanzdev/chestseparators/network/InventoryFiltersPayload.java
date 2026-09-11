package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server sync of the player's inventory filters (client-side only otherwise). The server
 * caches them per player so it can enforce the "Pick Up" rule: filtered inventory slots only accept
 * their item when items are picked up from the ground. Keyed by player-inventory slot index.
 */
public record InventoryFiltersPayload(Map<Integer, SlotWhitelist> filters) implements CsPayload {

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "inventory_filters");

    public static InventoryFiltersPayload read(FriendlyByteBuf buf) {
        return new InventoryFiltersPayload(readMap(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.filters.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : this.filters.entrySet()) {
            buf.writeVarInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readMap(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Integer, SlotWhitelist> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int key = buf.readVarInt();
            map.put(key, SlotWhitelist.PACKET_CODEC.decode(buf));
        }
        return map;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
