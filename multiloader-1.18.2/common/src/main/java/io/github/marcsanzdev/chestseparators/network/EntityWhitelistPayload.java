package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Payload for synchronizing whitelist data of ENTITY containers (chest/hopper minecarts) between the
// Server and the Client, keyed by the entity's UUID instead of a BlockPos.
//
// Used bidirectionally: C2S when the client saves a minecart filter (so the server can enforce the Hopper
// rule and persist it in the entity's NBT), and S2C when the container GUI opens (so the client shows the
// server-authoritative filter).
public record EntityWhitelistPayload(UUID entityUuid, Map<Integer, SlotWhitelist> whitelists) implements CsPayload {

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "entity_whitelist_sync");

    public static EntityWhitelistPayload read(FriendlyByteBuf buf) {
        return new EntityWhitelistPayload(buf.readUUID(), readMap(buf));
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
