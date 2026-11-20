package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server -> Client sync of an ENTITY container's whitelist (chest/hopper minecart), keyed by entity UUID.
// A separate payload ID from the C2S {@link EntityWhitelistPayload} because NeoForge (via Architectury)
// forbids registering one payload id for both directions.
public record EntityWhitelistS2CPayload(UUID entityUuid, Map<Integer, SlotWhitelist> whitelists)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EntityWhitelistS2CPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath("chestseparators", "entity_whitelist_sync_s2c"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityWhitelistS2CPayload> CODEC =
            StreamCodec.ofMember(EntityWhitelistS2CPayload::write, EntityWhitelistS2CPayload::new);

    private EntityWhitelistS2CPayload(RegistryFriendlyByteBuf buf) {
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
