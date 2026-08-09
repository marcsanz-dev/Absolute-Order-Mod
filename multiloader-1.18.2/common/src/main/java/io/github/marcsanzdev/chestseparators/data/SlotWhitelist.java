package io.github.marcsanzdev.chestseparators.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.SerializableUUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;

// Represents the whitelist configuration for a single inventory slot.
// Includes a groupId to visually and logically link multiple slots together.
// targetCount is the desired amount the player wants to keep of this filter in their inventory
// (0 = no target / unlimited); it drives the grab and deposit-junk hotkeys.
public record SlotWhitelist(
        UUID groupId,
        List<String> allowedItems,
        boolean allowManual,
        boolean allowShift,
        boolean allowHopper,
        int targetCount) {

    /** Backwards-compatible constructor for the pre-targetCount call sites (defaults to no target). */
    public SlotWhitelist(
            UUID groupId, List<String> allowedItems, boolean allowManual, boolean allowShift, boolean allowHopper) {
        this(groupId, allowedItems, allowManual, allowShift, allowHopper, 0);
    }

    // Codec used for saving this data into the world's NBT files (.mca) and Item Data Components.
    // target_count is optional so worlds saved before this field still load (defaulting to 0).
    public static final Codec<SlotWhitelist> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    SerializableUUID.CODEC.fieldOf("group_id").forGetter(SlotWhitelist::groupId),
                    Codec.STRING.listOf().fieldOf("allowed_items").forGetter(SlotWhitelist::allowedItems),
                    Codec.BOOL.fieldOf("allow_manual").forGetter(SlotWhitelist::allowManual),
                    Codec.BOOL.fieldOf("allow_shift").forGetter(SlotWhitelist::allowShift),
                    Codec.BOOL.fieldOf("allow_hopper").forGetter(SlotWhitelist::allowHopper),
                    Codec.INT.optionalFieldOf("target_count", 0).forGetter(SlotWhitelist::targetCount))
            .apply(instance, SlotWhitelist::new));

    /**
     * Manual {@link FriendlyByteBuf} (de)serializer for the network layer. 1.20.1 predates
     * {@code StreamCodec}/{@code ByteBufCodecs}, so this replaces the composite stream codec while keeping
     * the {@code PACKET_CODEC.encode(buf, wl)} / {@code PACKET_CODEC.decode(buf)} call shape used by every
     * payload unchanged.
     */
    public static final class PacketCodec {

        public void encode(FriendlyByteBuf buf, SlotWhitelist wl) {
            buf.writeUUID(wl.groupId());
            buf.writeVarInt(wl.allowedItems().size());
            for (String item : wl.allowedItems()) {
                buf.writeUtf(item);
            }
            buf.writeBoolean(wl.allowManual());
            buf.writeBoolean(wl.allowShift());
            buf.writeBoolean(wl.allowHopper());
            buf.writeVarInt(wl.targetCount());
        }

        public SlotWhitelist decode(FriendlyByteBuf buf) {
            UUID groupId = buf.readUUID();
            int count = buf.readVarInt();
            List<String> allowedItems = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                allowedItems.add(buf.readUtf());
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

    // Codec for a whole slot->filter map. The map is keyed by int slot index, but NBT/JSON map keys must
    // be strings, so this stores string keys and converts back on load. Reused by the data component and
    // by the block-entity NBT persistence so both share one on-disk shape.
    public static final Codec<Map<Integer, SlotWhitelist>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC)
            .xmap(
                    stringMap -> {
                        Map<Integer, SlotWhitelist> intMap = new HashMap<>();
                        stringMap.forEach((k, v) -> {
                            try {
                                intMap.put(Integer.parseInt(k), v);
                            } catch (NumberFormatException ignored) {
                            }
                        });
                        return intMap;
                    },
                    intMap -> {
                        Map<String, SlotWhitelist> stringMap = new HashMap<>();
                        intMap.forEach((k, v) -> stringMap.put(String.valueOf(k), v));
                        return stringMap;
                    });

    /**
     * Writes a slot→filter map into a {@link CompoundTag} under {@code key} using {@link #MAP_CODEC}, so
     * the on-disk shape matches everything else. 1.20.1 block-entities persist via {@code CompoundTag}
     * (there is no {@code ValueOutput}); vanilla's {@code BlockEntityTag} then rides this NBT onto the
     * dropped item automatically for shulker boxes. No-op for an empty/null map.
     */
    public static void writeMapToTag(CompoundTag tag, String key, Map<Integer, SlotWhitelist> map) {
        if (map == null || map.isEmpty()) return;
        MAP_CODEC.encodeStart(NbtOps.INSTANCE, map).result().ifPresent(encoded -> tag.put(key, encoded));
    }

    /** Reads a slot→filter map previously written by {@link #writeMapToTag}. Returns a mutable map. */
    public static Map<Integer, SlotWhitelist> readMapFromTag(CompoundTag tag, String key) {
        Map<Integer, SlotWhitelist> map = new HashMap<>();
        if (tag.contains(key)) {
            MAP_CODEC.parse(NbtOps.INSTANCE, tag.get(key)).result().ifPresent(map::putAll);
        }
        return map;
    }
}
