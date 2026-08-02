package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Payload for synchronizing whitelist data between the Server and the Client.
// Used bidirectionally (C2S for saving, S2C for loading the GUI).
public record WhitelistPayload(BlockPos pos, Map<Integer, SlotWhitelist> whitelists) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WhitelistPayload> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath("chestseparators", "whitelist_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WhitelistPayload> CODEC =
            StreamCodec.ofMember(WhitelistPayload::write, WhitelistPayload::new);

    private WhitelistPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), readMap(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
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
