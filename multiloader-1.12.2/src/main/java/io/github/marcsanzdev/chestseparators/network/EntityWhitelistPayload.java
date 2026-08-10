package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

// Payload for synchronizing whitelist data of ENTITY containers (chest/hopper minecarts) between the
// Server and the Client, keyed by the entity's UUID instead of a BlockPos.
//
// Used bidirectionally: C2S when the client saves a minecart filter (so the server can enforce the Hopper
// rule and persist it in the entity's NBT), and S2C when the container GUI opens (so the client shows the
// server-authoritative filter).
public final class EntityWhitelistPayload implements CsPayload {

    private final UUID entityUuid;
    private final Map<Integer, SlotWhitelist> whitelists;

    public EntityWhitelistPayload(UUID entityUuid, Map<Integer, SlotWhitelist> whitelists) {
        this.entityUuid = entityUuid;
        this.whitelists = whitelists;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public Map<Integer, SlotWhitelist> whitelists() {
        return whitelists;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityWhitelistPayload x = (EntityWhitelistPayload) o;
        return java.util.Objects.equals(entityUuid, x.entityUuid) && java.util.Objects.equals(whitelists, x.whitelists);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(entityUuid, whitelists);
    }

    @Override
    public String toString() {
        return "EntityWhitelistPayload[entityUuid=" + entityUuid + ", whitelists=" + whitelists + "]";
    }

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "entity_whitelist_sync");

    public static EntityWhitelistPayload read(PacketBuffer buf) {
        return new EntityWhitelistPayload(buf.readUniqueId(), readMap(buf));
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeUniqueId(this.entityUuid);
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
