package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockableLoot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Implements {@link IWhitelistProvider} on all standard lootable containers (Chests, Shulker Boxes — every
 * subclass of {@link TileEntityLockableLoot}, Mojmap {@code RandomizableContainerBlockEntity}). This is the
 * duck-type the enforcement paths read: the Hopper rule ({@code HopperBlockEntityMixin}), the shift-click
 * quick-move ({@code ScreenHandlerWhitelistMixin}) and the slot check ({@code SlotWhitelistMixin}) all call
 * {@link #getWhitelists()} on the backing inventory. The whitelist is populated server-side by the client
 * sync ({@code WhitelistPayload} -> {@link #setWhitelists}).
 *
 * <p>TODO(1.12.2 port): the modern mixin also owns (a) an on-open S2C push injected into {@code createMenu}
 * and (b) world-NBT persistence via overridden {@code save}/{@code load}. Neither is portable at this class
 * level: {@code TileEntityLockableLoot} does NOT declare {@code createContainer}, {@code writeToNBT} or
 * {@code readFromNBT} (they are abstract/inherited and only concrete on the subclasses TileEntityChest /
 * TileEntityShulkerBox), so injecting into them here fails to locate a target. On 1.12.2 chest filters are
 * position-keyed and persist client-side via {@code ChestConfigManager} (see {@code WorldMixin}); the
 * server receives the active filter through the C2S sync above, so enforcement is unaffected. Shulker Box
 * whitelist NBT is carried on the item by {@code ShulkerBoxBlockMixin}.
 */
@Mixin(TileEntityLockableLoot.class)
public abstract class LootableContainerBlockEntityMixin implements IWhitelistProvider {

    @Unique
    private Map<Integer, SlotWhitelist> chestSeparatorsWhitelists = new HashMap<>();

    @Override
    public Map<Integer, SlotWhitelist> getWhitelists() {
        return this.chestSeparatorsWhitelists;
    }

    @Override
    public void setWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        this.chestSeparatorsWhitelists = new HashMap<>(whitelists);
        ((TileEntity) (Object) this).markDirty();
    }
}
