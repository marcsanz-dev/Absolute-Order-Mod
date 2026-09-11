package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Payload for synchronizing whitelist data of ENTITY containers (chest/hopper minecarts) between the
// Server and the Client, keyed by the entity's UUID instead of a BlockPos.
//
// Used bidirectionally: C2S when the client saves a minecart filter (so the server can enforce the Hopper
// rule and persist it in the entity's NBT), and S2C when the container GUI opens (so the client shows the
// server-authoritative filter).
public record EntityWhitelistPayload(UUID entityUuid, Map<Integer, SlotWhitelist> whitelists)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EntityWhitelistPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath("chestseparators", "entity_whitelist_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityWhitelistPayload> CODEC =
            StreamCodec.ofMember(EntityWhitelistPayload::write, EntityWhitelistPayload::new);

    private EntityWhitelistPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), readMap(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.entityUuid);
        buf.writeInt(this.whitelists.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : this.whitelists.entrySet()) {
            buf.writeInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readMap(RegistryFriendlyByteBuf buf) {
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
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
