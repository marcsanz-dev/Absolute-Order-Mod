package io.github.marcsanzdev.chestseparators.mixin;

import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Intercepts BlockEntity data component initialization to load and save custom state.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {

    @Inject(method = "readComponents", at = @At("TAIL"))
    private void onReadComponents(ComponentsAccess components, CallbackInfo ci) {
        if ((Object) this instanceof IShulkerUUIDProvider provider) {
            UUID uuid = components.get(ChestSeparatorsComponents.SHULKER_UUID);
            if (uuid != null) {
                provider.setShulkerUUID(uuid);
            }
        }

        if ((Object) this instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = components.get(ChestSeparatorsComponents.SLOT_WHITELISTS);
            if (whitelists != null) {
                provider.setWhitelists(new HashMap<>(whitelists));
            }
        }
    }

    @Inject(method = "addComponents", at = @At("TAIL"))
    private void onAddComponents(ComponentMap.Builder builder, CallbackInfo ci) {
        if ((Object) this instanceof IShulkerUUIDProvider provider) {
            UUID shulkerId = provider.getShulkerUUID();
            if (shulkerId != null) {
                builder.add(ChestSeparatorsComponents.SHULKER_UUID, shulkerId);
            }
        }

        if ((Object) this instanceof IWhitelistProvider provider) {
            Map<Integer, SlotWhitelist> whitelists = provider.getWhitelists();
            if (whitelists != null && !whitelists.isEmpty()) {
                builder.add(ChestSeparatorsComponents.SLOT_WHITELISTS, new HashMap<>(whitelists));
            }
        }
    }

    @Inject(method = "removeFromCopiedStackData", at = @At("TAIL"))
    private void onRemoveFromCopiedStackData(WriteView view, CallbackInfo ci) {
        if ((Object) this instanceof IWhitelistProvider) {
            view.remove("ChestSeparatorsWhitelists");
        }
    }
}