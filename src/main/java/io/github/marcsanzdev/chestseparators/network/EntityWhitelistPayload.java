package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

// Payload for synchronizing whitelist data of ENTITY containers (chest/hopper minecarts) between the
// Server and the Client, keyed by the entity's UUID instead of a BlockPos.
//
// Used bidirectionally: C2S when the client saves a minecart filter (so the server can enforce the Hopper
// rule and persist it in the entity's NBT), and S2C when the container GUI opens (so the client shows the
// server-authoritative filter).
public record EntityWhitelistPayload(UUID entityUuid, Map<Integer, SlotWhitelist> whitelists) implements CustomPayload {

    public static final CustomPayload.Id<EntityWhitelistPayload> ID =
            new CustomPayload.Id<>(Identifier.of("chestseparators", "entity_whitelist_sync"));

    public static final PacketCodec<PacketByteBuf, EntityWhitelistPayload> CODEC =
            PacketCodec.of(EntityWhitelistPayload::write, EntityWhitelistPayload::new);

    private EntityWhitelistPayload(PacketByteBuf buf) {
        this(buf.readUuid(), readMap(buf));
    }

    private void write(PacketByteBuf buf) {
        buf.writeUuid(this.entityUuid);
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
