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
public final class FillFromChestPayload implements CsPayload {

    private final BlockPos animPos;
    private final boolean includeEmpty;
    private final boolean lockHotbar;

    public FillFromChestPayload(BlockPos animPos, boolean includeEmpty, boolean lockHotbar) {
        this.animPos = animPos;
        this.includeEmpty = includeEmpty;
        this.lockHotbar = lockHotbar;
    }

    public BlockPos animPos() {
        return animPos;
    }

    public boolean includeEmpty() {
        return includeEmpty;
    }

    public boolean lockHotbar() {
        return lockHotbar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FillFromChestPayload x = (FillFromChestPayload) o;
        return includeEmpty == x.includeEmpty && lockHotbar == x.lockHotbar && java.util.Objects.equals(animPos, x.animPos);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(animPos, includeEmpty, lockHotbar);
    }

    @Override
    public String toString() {
        return "FillFromChestPayload[animPos=" + animPos + ", includeEmpty=" + includeEmpty + ", lockHotbar="
                + lockHotbar + "]";
    }

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
