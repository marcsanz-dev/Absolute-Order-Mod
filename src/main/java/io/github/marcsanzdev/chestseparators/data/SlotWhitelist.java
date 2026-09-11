package io.github.marcsanzdev.chestseparators.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Uuids;

// Represents the whitelist configuration for a single inventory slot.
// Now includes a groupId to visually and logically link multiple slots together.
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
                    Uuids.CODEC.fieldOf("group_id").forGetter(SlotWhitelist::groupId),
                    Codec.STRING.listOf().fieldOf("allowed_items").forGetter(SlotWhitelist::allowedItems),
                    Codec.BOOL.fieldOf("allow_manual").forGetter(SlotWhitelist::allowManual),
                    Codec.BOOL.fieldOf("allow_shift").forGetter(SlotWhitelist::allowShift),
                    Codec.BOOL.fieldOf("allow_hopper").forGetter(SlotWhitelist::allowHopper),
                    Codec.INT.optionalFieldOf("target_count", 0).forGetter(SlotWhitelist::targetCount))
            .apply(instance, SlotWhitelist::new));

    // PacketCodec used for efficiently transmitting this data over the network between Client and Server.
    public static final PacketCodec<PacketByteBuf, SlotWhitelist> PACKET_CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC,
            SlotWhitelist::groupId,
            PacketCodecs.STRING.collect(PacketCodecs.toList()),
            SlotWhitelist::allowedItems,
            PacketCodecs.BOOLEAN,
            SlotWhitelist::allowManual,
            PacketCodecs.BOOLEAN,
            SlotWhitelist::allowShift,
            PacketCodecs.BOOLEAN,
            SlotWhitelist::allowHopper,
            PacketCodecs.VAR_INT,
            SlotWhitelist::targetCount,
            SlotWhitelist::new);
}
