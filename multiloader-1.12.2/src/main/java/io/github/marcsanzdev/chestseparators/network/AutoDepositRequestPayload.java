package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;

/**
 * Client-to-Server request to auto-deposit the player's inventory into nearby filtered containers.
 * The client forwards its own config (radius, through-walls) so the behaviour matches the player's
 * settings; the server clamps the radius to a safe maximum before acting.
 *
 * <p>Ender Chest and entity (chest minecart / chest boat) filters are stored client-side only, so
 * they travel with the request: the ender filter as a single map, and entity filters keyed by entity
 * UUID. The server uses them to deposit into those containers when they are in range.
 */
public final class AutoDepositRequestPayload implements CsPayload {

    private final int radius;
    private final boolean throughWalls;
    private final int action;
    private final Map<Integer, SlotWhitelist> enderWhitelists;
    private final Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists;

    public AutoDepositRequestPayload(
            int radius,
            boolean throughWalls,
            int action,
            Map<Integer, SlotWhitelist> enderWhitelists,
            Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists) {
        this.radius = radius;
        this.throughWalls = throughWalls;
        this.action = action;
        this.enderWhitelists = enderWhitelists;
        this.entityWhitelists = entityWhitelists;
    }

    public int radius() {
        return radius;
    }

    public boolean throughWalls() {
        return throughWalls;
    }

    public int action() {
        return action;
    }

    public Map<Integer, SlotWhitelist> enderWhitelists() {
        return enderWhitelists;
    }

    public Map<UUID, Map<Integer, SlotWhitelist>> entityWhitelists() {
        return entityWhitelists;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AutoDepositRequestPayload x = (AutoDepositRequestPayload) o;
        return radius == x.radius
                && throughWalls == x.throughWalls
                && action == x.action
                && java.util.Objects.equals(enderWhitelists, x.enderWhitelists)
                && java.util.Objects.equals(entityWhitelists, x.entityWhitelists);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(radius, throughWalls, action, enderWhitelists, entityWhitelists);
    }

    @Override
    public String toString() {
        return "AutoDepositRequestPayload[radius=" + radius + ", throughWalls=" + throughWalls + ", action="
                + action + ", enderWhitelists=" + enderWhitelists + ", entityWhitelists=" + entityWhitelists + "]";
    }

    /** Deposit every matching item (double-sneak / dedicated hotkey). */
    public static final int ACTION_DEPOSIT_ALL = 0;

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "auto_deposit_request");

    public static AutoDepositRequestPayload read(PacketBuffer buf) {
        return new AutoDepositRequestPayload(
                buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), readWlMap(buf), readEntityMap(buf));
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeVarInt(this.radius);
        buf.writeBoolean(this.throughWalls);
        buf.writeVarInt(this.action);
        writeWlMap(buf, this.enderWhitelists);
        buf.writeVarInt(this.entityWhitelists.size());
        for (Map.Entry<UUID, Map<Integer, SlotWhitelist>> entry : this.entityWhitelists.entrySet()) {
            buf.writeUniqueId(entry.getKey());
            writeWlMap(buf, entry.getValue());
        }
    }

    private static void writeWlMap(PacketBuffer buf, Map<Integer, SlotWhitelist> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : map.entrySet()) {
            buf.writeVarInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readWlMap(PacketBuffer buf) {
        int size = buf.readVarInt();
        Map<Integer, SlotWhitelist> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int key = buf.readVarInt();
            map.put(key, SlotWhitelist.PACKET_CODEC.decode(buf));
        }
        return map;
    }

    private static Map<UUID, Map<Integer, SlotWhitelist>> readEntityMap(PacketBuffer buf) {
        int size = buf.readVarInt();
        Map<UUID, Map<Integer, SlotWhitelist>> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUniqueId();
            map.put(uuid, readWlMap(buf));
        }
        return map;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
