package io.github.marcsanzdev.chestseparators.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.UUID;

/**
 * Represents the whitelist and behavioral configuration for a single inventory slot.
 * <p>
 * This record dictates what items are permitted to enter the slot and through which
 * insertion methods. It also includes a group identifier to logically and visually
 * link multiple slots that share the same configuration.
 *
 * @param groupId      The unique identifier linking this slot to a specific colored group or logical layout.
 * @param allowedItems A list of item registry names (or tags) permitted in this slot.
 * @param allowManual  Whether players can manually place items into this slot via the container UI.
 * @param allowShift   Whether items can be quick-moved (shift-clicked) into this slot.
 * @param allowHopper  Whether automation systems (e.g., hoppers, pipes) can insert items into this slot.
 */
public record SlotWhitelist(UUID groupId, List<String> allowedItems, boolean allowManual, boolean allowShift, boolean allowHopper) {

    /**
     * The DataFixerUpper codec used for serializing and deserializing this record.
     * <p>
     * This is primarily utilized for saving the whitelist data persistently into the world's NBT
     * structures (such as block entity data in .mca files) and within ItemStack Data Components.
     */
    public static final Codec<SlotWhitelist> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.CODEC.fieldOf("group_id").forGetter(SlotWhitelist::groupId),
            Codec.STRING.listOf().fieldOf("allowed_items").forGetter(SlotWhitelist::allowedItems),
            Codec.BOOL.fieldOf("allow_manual").forGetter(SlotWhitelist::allowManual),
            Codec.BOOL.fieldOf("allow_shift").forGetter(SlotWhitelist::allowShift),
            Codec.BOOL.fieldOf("allow_hopper").forGetter(SlotWhitelist::allowHopper)
    ).apply(instance, SlotWhitelist::new));

    /**
     * The network packet codec used for encoding and decoding this record into byte buffers.
     * <p>
     * This ensures highly efficient data transmission between the logical server and the client
     * during configuration synchronization events.
     */
    public static final PacketCodec<PacketByteBuf, SlotWhitelist> PACKET_CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, SlotWhitelist::groupId,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), SlotWhitelist::allowedItems,
            PacketCodecs.BOOLEAN, SlotWhitelist::allowManual,
            PacketCodecs.BOOLEAN, SlotWhitelist::allowShift,
            PacketCodecs.BOOLEAN, SlotWhitelist::allowHopper,
            SlotWhitelist::new
    );
}