package io.github.marcsanzdev.chestseparators.mixin;

/**
 * TODO(1.12.2 port): NOT PORTED — intentionally excluded from mixins.absoluteorder.json.
 *
 * <p>The modern mixin injects into {@code Block#getDrops(BlockState, ServerLevel, BlockPos, BlockEntity[,
 * Entity, ItemStack])} at RETURN and copies a block entity's mod NBT onto each dropped {@code ItemStack}.
 * 1.12.2 has no such overload: {@code Block} only exposes
 * <pre>
 *   void            getDrops(NonNullList&lt;ItemStack&gt;, IBlockAccess, BlockPos, IBlockState, int fortune)
 *   List&lt;ItemStack&gt; getDrops(IBlockAccess, BlockPos, IBlockState, int fortune)
 * </pre>
 * neither of which receives the broken block's {@code TileEntity}, so there is no block-entity context at
 * the drop site to copy NBT from.
 *
 * <p>Why this is acceptable on 1.12.2:
 * <ul>
 *   <li>Shulker Boxes carry their tile-entity NBT (UUID + whitelist) onto the dropped item through the
 *       vanilla shulker drop path, and the empty-in-Creative edge case is handled by
 *       {@code ShulkerBoxBlockMixin} (which writes {@code BlockEntityTag} onto the item).</li>
 *   <li>Chests/Barrels were already skipped by the modern mixin ({@code isStandardChest}); barrels do not
 *       exist in 1.12.2, and chest filters are position-keyed and persisted client-side via
 *       {@code ChestConfigManager} (see {@code WorldMixin}), not item-carried.</li>
 * </ul>
 *
 * This class carries no {@code @Mixin} annotation and no Minecraft references, so it compiles cleanly and
 * is never loaded as a mixin.
 */
public final class BlockDropMixin {
    private BlockDropMixin() {}
}
