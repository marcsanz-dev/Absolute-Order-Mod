package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-Server request to auto-deposit the player's inventory into nearby filtered containers.
 * The client forwards its own config (radius, through-walls) so the behaviour matches the player's
 * settings; the server clamps the radius to a safe maximum before acting.
 *
 * <p>Ender Chest and entity (chest minecart / chest boat) filters are stored client-side only, so
 * they travel with the request: the ender filter as a single map, and entity filters keyed by entity
 * UUID. The server uses them to deposit into those containers when they are in range.
 */
public record AutoDepositRequestPayload(
        int radius,
        boolean throughWalls,
        Map<Integer, SlotWhitelist> enderWhitelists,
        Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists)
        implements CustomPayload {

    public static final CustomPayload.Id<AutoDepositRequestPayload> ID =
            new CustomPayload.Id<>(Identifier.of("chestseparators", "auto_deposit_request"));

    public static final PacketCodec<PacketByteBuf, AutoDepositRequestPayload> CODEC =
            PacketCodec.of(AutoDepositRequestPayload::write, AutoDepositRequestPayload::new);

    private AutoDepositRequestPayload(PacketByteBuf buf) {
        this(buf.readVarInt(), buf.readBoolean(), readWlMap(buf), readEntityMap(buf));
    }

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(this.radius);
        buf.writeBoolean(this.throughWalls);
        writeWlMap(buf, this.enderWhitelists);
        buf.writeVarInt(this.entityWhitelists.size());
        for (Map.Entry<UUID, Map<Integer, SlotWhitelist>> entry : this.entityWhitelists.entrySet()) {
            buf.writeUuid(entry.getKey());
            writeWlMap(buf, entry.getValue());
        }
    }

    private static void writeWlMap(PacketByteBuf buf, Map<Integer, SlotWhitelist> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : map.entrySet()) {
            buf.writeVarInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readWlMap(PacketByteBuf buf) {
        int size = buf.readVarInt();
        Map<Integer, SlotWhitelist> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int key = buf.readVarInt();
            map.put(key, SlotWhitelist.PACKET_CODEC.decode(buf));
        }
        return map;
    }

    private static Map<UUID, Map<Integer, SlotWhitelist>> readEntityMap(PacketByteBuf buf) {
        int size = buf.readVarInt();
        Map<UUID, Map<Integer, SlotWhitelist>> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUuid();
            map.put(uuid, readWlMap(buf));
        }
        return map;
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
