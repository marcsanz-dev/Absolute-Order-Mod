package io.github.marcsanzdev.chestseparators.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server request to fill the player's inventory from the currently open container (the
 * "fill inventory" button in the chest editor). The server reads the open menu's container and pulls
 * items the player's inventory filters want. The block position is used only as the origin of the
 * cosmetic fly-back animation.
 */
public record FillFromChestPayload(BlockPos animPos, boolean includeEmpty, boolean lockHotbar) implements CsPayload {

    public static final ResourceLocation ID = new ResourceLocation("chestseparators", "fill_from_chest");

    public static FillFromChestPayload read(FriendlyByteBuf buf) {
        return new FillFromChestPayload(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean());
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.animPos);
        buf.writeBoolean(this.includeEmpty);
        // Client-side setting (GlobalChestConfig is client-only), forwarded so the server-side re-sort can
        // honour it without referencing the client config class.
        buf.writeBoolean(this.lockHotbar);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
