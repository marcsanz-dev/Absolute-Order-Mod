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
public final class EntityWhitelistS2CPayload implements CsPayload {

    private final UUID entityUuid;
    private final Map<Integer, SlotWhitelist> whitelists;

    public EntityWhitelistS2CPayload(UUID entityUuid, Map<Integer, SlotWhitelist> whitelists) {
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
        EntityWhitelistS2CPayload x = (EntityWhitelistS2CPayload) o;
        return java.util.Objects.equals(entityUuid, x.entityUuid) && java.util.Objects.equals(whitelists, x.whitelists);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(entityUuid, whitelists);
    }

    @Override
    public String toString() {
        return "EntityWhitelistS2CPayload[entityUuid=" + entityUuid + ", whitelists=" + whitelists + "]";
    }

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
