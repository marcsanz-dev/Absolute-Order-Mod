package io.github.marcsanzdev.chestseparators.network;

import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Payload for synchronizing whitelist data between the Server and the Client.
// Used bidirectionally (C2S for saving, S2C for loading the GUI).
public record WhitelistPayload(BlockPos pos, Map<Integer, SlotWhitelist> whitelists) implements CsPayload {

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "whitelist_sync");

    public static WhitelistPayload read(FriendlyByteBuf buf) {
        return new WhitelistPayload(buf.readBlockPos(), readMap(buf));
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeInt(this.whitelists.size());
        for (Map.Entry<Integer, SlotWhitelist> entry : this.whitelists.entrySet()) {
            buf.writeInt(entry.getKey());
            SlotWhitelist.PACKET_CODEC.encode(buf, entry.getValue());
        }
    }

    private static Map<Integer, SlotWhitelist> readMap(FriendlyByteBuf buf) {
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
    public ResourceLocation id() {
        return ID;
    }
}
