package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transfers mod data between placed BlockEntities and dropped ItemStacks via Minecraft's
 * Data Component system. This mixin handles only the item-drop lifecycle:
 *
 * <ul>
 *   <li>{@code addComponents} — copies whitelist and UUID data into the dropped ItemStack's components.</li>
 *   <li>{@code readComponents} — restores that data back into the BlockEntity when the item is placed.</li>
 * </ul>
 *
 * NBT world-save persistence is handled separately by {@link LootableContainerBlockEntityMixin},
 * which owns the canonical "ChestSeparatorsWhitelists" NBT key.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityComponentMixin {

    @Inject(method = "addComponents", at = @At("TAIL"))
    protected void onAddComponents(ComponentMap.Builder builder, CallbackInfo ci) {
        if ((Object) this instanceof IShulkerUUIDProvider shulkerProvider) {
            UUID uuid = shulkerProvider.getShulkerUUID();
            if (uuid != null) {
                builder.add(ChestSeparatorsComponents.SHULKER_UUID, uuid.toString());
            }
        }

        if ((Object) this instanceof IWhitelistProvider whitelistProvider) {
            Map<Integer, SlotWhitelist> map = whitelistProvider.getWhitelists();
            if (map != null && !map.isEmpty()) {
                builder.add(ChestSeparatorsComponents.SLOT_WHITELISTS, map);
            }
        }
    }

    @Inject(method = "readComponents(Lnet/minecraft/component/ComponentsAccess;)V", at = @At("TAIL"))
    protected void onReadComponents(ComponentsAccess components, CallbackInfo ci) {
        if ((Object) this instanceof IShulkerUUIDProvider shulkerProvider) {
            String uuidStr = components.get(ChestSeparatorsComponents.SHULKER_UUID);
            if (uuidStr != null && !uuidStr.isEmpty()) {
                try {
                    shulkerProvider.setShulkerUUID(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if ((Object) this instanceof IWhitelistProvider whitelistProvider) {
            Map<Integer, SlotWhitelist> map = components.get(ChestSeparatorsComponents.SLOT_WHITELISTS);
            if (map != null && !map.isEmpty()) {
                whitelistProvider.setWhitelists(map);
            }
        }
    }
}
