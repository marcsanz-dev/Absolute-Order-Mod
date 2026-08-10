package io.github.marcsanzdev.chestseparators.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.PacketBuffer;

// Represents the whitelist configuration for a single inventory slot.
// Includes a groupId to visually and logically link multiple slots together.
// targetCount is the desired amount the player wants to keep of this filter in their inventory
// (0 = no target / unlimited); it drives the grab and deposit-junk hotkeys.
//
// 1.12.2 predates DataFixerUpper/Codec, so the modern ports' Codec/MAP_CODEC + FriendlyByteBuf stream
// codec are replaced here by hand-written NBT (NBTTagCompound) and PacketBuffer (de)serialization. The
// on-disk field names (group_id / allowed_items / allow_manual / allow_shift / allow_hopper / target_count)
// are kept identical to the Codec so the shape reads the same.
public final class SlotWhitelist {

    private final UUID groupId;
    private final List<String> allowedItems;
    private final boolean allowManual;
    private final boolean allowShift;
    private final boolean allowHopper;
    private final int targetCount;

    public SlotWhitelist(
            UUID groupId,
            List<String> allowedItems,
            boolean allowManual,
            boolean allowShift,
            boolean allowHopper,
            int targetCount) {
        this.groupId = groupId;
        this.allowedItems = allowedItems;
        this.allowManual = allowManual;
        this.allowShift = allowShift;
        this.allowHopper = allowHopper;
        this.targetCount = targetCount;
    }

    public UUID groupId() {
        return groupId;
    }

    public List<String> allowedItems() {
        return allowedItems;
    }

    public boolean allowManual() {
        return allowManual;
    }

    public boolean allowShift() {
        return allowShift;
    }

    public boolean allowHopper() {
        return allowHopper;
    }

    public int targetCount() {
        return targetCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotWhitelist other = (SlotWhitelist) o;
        return allowManual == other.allowManual
                && allowShift == other.allowShift
                && allowHopper == other.allowHopper
                && targetCount == other.targetCount
                && java.util.Objects.equals(groupId, other.groupId)
                && java.util.Objects.equals(allowedItems, other.allowedItems);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(groupId, allowedItems, allowManual, allowShift, allowHopper, targetCount);
    }

    @Override
    public String toString() {
        return "SlotWhitelist[groupId=" + groupId + ", allowedItems=" + allowedItems + ", allowManual=" + allowManual
                + ", allowShift=" + allowShift + ", allowHopper=" + allowHopper + ", targetCount=" + targetCount + "]";
    }

    /** Backwards-compatible constructor for the pre-targetCount call sites (defaults to no target). */
    public SlotWhitelist(
            UUID groupId, List<String> allowedItems, boolean allowManual, boolean allowShift, boolean allowHopper) {
        this(groupId, allowedItems, allowManual, allowShift, allowHopper, 0);
    }

    // ---- NBT persistence (world files + item BlockEntityTag) --------------------------------------------

    /** Serialises this filter into a fresh {@link NBTTagCompound} (matches the modern Codec's field names). */
    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("group_id", groupId.toString());
        NBTTagList items = new NBTTagList();
        for (String item : allowedItems) {
            items.appendTag(new NBTTagString(item));
        }
        tag.setTag("allowed_items", items);
        tag.setBoolean("allow_manual", allowManual);
        tag.setBoolean("allow_shift", allowShift);
        tag.setBoolean("allow_hopper", allowHopper);
        tag.setInteger("target_count", targetCount);
        return tag;
    }

    /** Reads a filter previously written by {@link #toNBT()}. target_count is optional (defaults to 0). */
    public static SlotWhitelist fromNBT(NBTTagCompound tag) {
        UUID groupId;
        try {
            groupId = UUID.fromString(tag.getString("group_id"));
        } catch (IllegalArgumentException e) {
            groupId = UUID.randomUUID();
        }
        List<String> allowedItems = new ArrayList<>();
        NBTTagList items = tag.getTagList("allowed_items", 8); // 8 = TAG_STRING
        for (int i = 0; i < items.tagCount(); i++) {
            allowedItems.add(items.getStringTagAt(i));
        }
        boolean allowManual = tag.getBoolean("allow_manual");
        boolean allowShift = tag.getBoolean("allow_shift");
        boolean allowHopper = tag.getBoolean("allow_hopper");
        int targetCount = tag.hasKey("target_count") ? tag.getInteger("target_count") : 0;
        return new SlotWhitelist(groupId, allowedItems, allowManual, allowShift, allowHopper, targetCount);
    }

    /**
     * Writes a slot→filter map into {@code tag} under {@code key} as a compound keyed by string slot index
     * (NBT/JSON map keys must be strings), so the on-disk shape matches everything else. No-op for an
     * empty/null map. Mirrors the modern {@code MAP_CODEC}.
     */
    public static void writeMapToTag(NBTTagCompound tag, String key, Map<Integer, SlotWhitelist> map) {
        if (map == null || map.isEmpty()) return;
        NBTTagCompound mapTag = new NBTTagCompound();
        for (Map.Entry<Integer, SlotWhitelist> e : map.entrySet()) {
            mapTag.setTag(String.valueOf(e.getKey()), e.getValue().toNBT());
        }
        tag.setTag(key, mapTag);
    }

    /** Reads a slot→filter map previously written by {@link #writeMapToTag}. Returns a mutable map. */
    public static Map<Integer, SlotWhitelist> readMapFromTag(NBTTagCompound tag, String key) {
        Map<Integer, SlotWhitelist> map = new HashMap<>();
        if (tag.hasKey(key)) {
            NBTTagCompound mapTag = tag.getCompoundTag(key);
            for (String slotKey : mapTag.getKeySet()) {
                try {
                    map.put(Integer.parseInt(slotKey), fromNBT(mapTag.getCompoundTag(slotKey)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return map;
    }

    // ---- Network (de)serialisation ----------------------------------------------------------------------

    /**
     * Manual {@link PacketBuffer} (de)serializer for the network layer, keeping the
     * {@code PACKET_CODEC.encode(buf, wl)} / {@code PACKET_CODEC.decode(buf)} call shape used by every
     * payload unchanged.
     */
    public static final class PacketCodec {

        public void encode(PacketBuffer buf, SlotWhitelist wl) {
            buf.writeUniqueId(wl.groupId());
            buf.writeVarInt(wl.allowedItems().size());
            for (String item : wl.allowedItems()) {
                buf.writeString(item);
            }
            buf.writeBoolean(wl.allowManual());
            buf.writeBoolean(wl.allowShift());
            buf.writeBoolean(wl.allowHopper());
            buf.writeVarInt(wl.targetCount());
        }

        public SlotWhitelist decode(PacketBuffer buf) {
            UUID groupId = buf.readUniqueId();
            int count = buf.readVarInt();
            List<String> allowedItems = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                allowedItems.add(buf.readString(32767));
            }
            boolean allowManual = buf.readBoolean();
            boolean allowShift = buf.readBoolean();
            boolean allowHopper = buf.readBoolean();
            int targetCount = buf.readVarInt();
            return new SlotWhitelist(groupId, allowedItems, allowManual, allowShift, allowHopper, targetCount);
        }
    }

    // Manual buffer (de)serializer for network transmission between client and server.
    public static final PacketCodec PACKET_CODEC = new PacketCodec();
}
