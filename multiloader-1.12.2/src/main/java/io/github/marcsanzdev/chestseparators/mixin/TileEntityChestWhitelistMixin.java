package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityChest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * World-NBT persistence of a chest's per-slot filters.
 *
 * <p>{@link LootableContainerBlockEntityMixin} adds the whitelist storage and {@code IWhitelistProvider} on the
 * shared superclass {@code TileEntityLockableLoot}, but that class does NOT declare {@code writeToNBT}/
 * {@code readFromNBT} — only the concrete subclasses do — so the actual save/load has to live here on
 * {@code TileEntityChest}. Without it a chest's filters were held only in the server's memory and vanished when
 * the world reloaded: on open the client re-requests them ({@code WhitelistRequestPayload}) and the server
 * answered with an empty set. The NBT key matches the modern versions ({@code "ChestSeparatorsWhitelists"}).
 * {@code setWhitelists} already marks the TE dirty, so an edited chest is written on the next save.
 */
@Mixin(TileEntityChest.class)
public abstract class TileEntityChestWhitelistMixin {

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void chestseparators$saveWhitelists(
            NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        // writeMapToTag no-ops on an empty/null map, so untouched chests write nothing extra.
        SlotWhitelist.writeMapToTag(
                tag, "ChestSeparatorsWhitelists", ((IWhitelistProvider) (Object) this).getWhitelists());
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void chestseparators$loadWhitelists(NBTTagCompound tag, CallbackInfo ci) {
        Map<Integer, SlotWhitelist> loaded = SlotWhitelist.readMapFromTag(tag, "ChestSeparatorsWhitelists");
        if (!loaded.isEmpty()) {
            ((IWhitelistProvider) (Object) this).setWhitelists(loaded);
        }
    }
}
