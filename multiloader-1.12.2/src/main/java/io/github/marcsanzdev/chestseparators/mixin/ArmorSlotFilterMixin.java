package io.github.marcsanzdev.chestseparators.mixin;

/**
 * TODO(1.12.2 port): NOT PORTED — intentionally excluded from mixins.absoluteorder.json.
 *
 * <p>The modern mixin targets {@code net.minecraft.world.inventory.ArmorSlot}. There is no equivalent named
 * class in 1.12.2: player armor slots are <em>anonymous</em> {@code Slot} subclasses created inline in the
 * {@code ContainerPlayer} constructor. They override {@code isItemValid(ItemStack)} without calling
 * {@code super}, and have no addressable class name to {@code @Mixin(targets = ...)} against reliably.
 *
 * <p>Consequences of not porting:
 * <ul>
 *   <li>{@code SlotWhitelistMixin} (injected into {@code Slot#isItemValid}) does NOT fire for armor slots,
 *       because the anonymous subclass overrides {@code isItemValid} and never calls {@code super}. So
 *       direct (cursor) placement of a non-whitelisted item into an armor slot is not enforced by a
 *       slot-level mixin on 1.12.2.</li>
 * </ul>
 *
 * <p>Enforcement that IS still covered elsewhere:
 * <ul>
 *   <li>Right-click / use-to-equip is blocked by {@code ArmorEquipFilterMixin}
 *       ({@code NetHandlerPlayServer#processTryUseItem}).</li>
 *   <li>Shift-click quick-move INTO an armor slot flows through {@code Container#mergeItemStack}, whose
 *       {@code Slot#isItemValid} call site is {@code @Redirect}-ed by {@code ScreenHandlerWhitelistMixin},
 *       so that path is enforced.</li>
 * </ul>
 *
 * This class carries no {@code @Mixin} annotation and no Minecraft references, so it compiles cleanly and
 * is never loaded as a mixin.
 */
public final class ArmorSlotFilterMixin {
    private ArmorSlotFilterMixin() {}
}
