package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Server -> Client sync of an ENTITY container's whitelist (chest/hopper minecart), keyed by entity UUID.
// A separate payload ID from the C2S {@link EntityWhitelistPayload} because NeoForge (via Architectury)
// forbids registering one payload id for both directions.
public record EntityWhitelistS2CPayload(UUID entityUuid, Map<Integer, SlotWhitelist> whitelists)
        implements CsPayload {

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "entity_whitelist_sync_s2c");

    public static EntityWhitelistS2CPayload read(FriendlyByteBuf buf) {
        return new EntityWhitelistS2CPayload(buf.readUUID(), readMap(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.entityUuid);
        buf.writeInt(this.whitelists.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : this.whitelists.entrySet()) {
            buf.writeInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readMap(FriendlyByteBuf buf) {
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
