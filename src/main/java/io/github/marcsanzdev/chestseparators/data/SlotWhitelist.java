package io.github.marcsanzdev.chestseparators.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.UUID;

// Represents the whitelist configuration for a single inventory slot.
// Now includes a groupId to visually and logically link multiple slots together.
public record SlotWhitelist(UUID groupId, List<String> allowedItems, boolean allowManual, boolean allowShift, boolean allowHopper) {

    // Codec used for saving this data into the world's NBT files (.mca) and Item Data Components.
    public static final Codec<SlotWhitelist> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.CODEC.fieldOf("group_id").forGetter(SlotWhitelist::groupId),
            Codec.STRING.listOf().fieldOf("allowed_items").forGetter(SlotWhitelist::allowedItems),
            Codec.BOOL.fieldOf("allow_manual").forGetter(SlotWhitelist::allowManual),
            Codec.BOOL.fieldOf("allow_shift").forGetter(SlotWhitelist::allowShift),
            Codec.BOOL.fieldOf("allow_hopper").forGetter(SlotWhitelist::allowHopper)
    ).apply(instance, SlotWhitelist::new));

    // PacketCodec used for efficiently transmitting this data over the network between Client and Server.
    public static final PacketCodec<PacketByteBuf, SlotWhitelist> PACKET_CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, SlotWhitelist::groupId,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), SlotWhitelist::allowedItems,
            PacketCodecs.BOOLEAN, SlotWhitelist::allowManual,
            PacketCodecs.BOOLEAN, SlotWhitelist::allowShift,
            PacketCodecs.BOOLEAN, SlotWhitelist::allowHopper,
            SlotWhitelist::new
    );
}