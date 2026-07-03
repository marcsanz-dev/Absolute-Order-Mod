package io.github.marcsanzdev.chestseparators.mixin;

import com.mojang.serialization.Codec;
import io.github.marcsanzdev.chestseparators.access.IShulkerUUIDProvider;
import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.registry.ChestSeparatorsComponents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the transition of custom data between placed blocks and dropped ItemStacks,
 * as well as NBT serialization for general BlockEntity data.
 */
@Mixin(BlockEntity.class)
public abstract class BlockEntityComponentMixin {

    // --- DATA COMPONENTS (ITEM DROPS) ---

    @Inject(method = "addComponents", at = @At("TAIL"))
    protected void onAddComponents(ComponentMap.Builder builder, CallbackInfo ci) {
        if ((Object) this instanceof IShulkerUUIDProvider shulkerProvider) {
            UUID uuid = shulkerProvider.getShulkerUUID();
            if (uuid != null) {
                builder.add(ChestSeparatorsComponents.SHULKER_UUID, uuid);
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
            UUID uuid = components.get(ChestSeparatorsComponents.SHULKER_UUID);
            if (uuid != null) {
                shulkerProvider.setShulkerUUID(uuid);
            }
        }

        if ((Object) this instanceof IWhitelistProvider whitelistProvider) {
            Map<Integer, SlotWhitelist> map = components.get(ChestSeparatorsComponents.SLOT_WHITELISTS);
            if (map != null && !map.isEmpty()) {
                whitelistProvider.setWhitelists(map);
            }
        }
    }

    // --- NBT SERIALIZATION (WORLD SAVE FILES) ---

    @Inject(method = "readData", at = @At("TAIL"))
    protected void onReadData(ReadView view, CallbackInfo ci) {
        if ((Object) this instanceof IWhitelistProvider whitelistProvider) {
            view.read("chestseparators_whitelists", Codec.unboundedMap(Codec.STRING, SlotWhitelist.CODEC).xmap(
                            stringMap -> {
                                Map<Integer, SlotWhitelist> intMap = new HashMap<>();
                                stringMap.forEach((k, v) -> intMap.put(Integer.parseInt(k), v));
                                return intMap;
                            },
                            intMap -> {
                                Map<String, SlotWhitelist> stringMap = new HashMap<>();
                                intMap.forEach((k, v) -> stringMap.put(String.valueOf(k), v));
                                return stringMap;
                            }
                    ))
                    .ifPresent(whitelistProvider::setWhitelists);
        }
    }

    @Inject(method = "writeData", at = @At("TAIL"))
    protected void onWriteData(WriteView view, CallbackInfo ci) {
        if ((Object) this instanceof IWhitelistProvider whitelistProvider) {
            Map<Integer, SlotWhitelist> map = whitelistProvider.getWhitelists();
            if (map != null && !map.isEmpty()) {
                // Apply the exact same Codec mapping as in reading to ensure Minecraft accepts the save format
                Codec<Map<Integer, SlotWhitelist>> mapCodec = Codec.unboundedMap(Codec.STRING, SlotWhitelist.CODEC).xmap(
                        stringMap -> {
                            Map<Integer, SlotWhitelist> intMap = new HashMap<>();
                            stringMap.forEach((k, v) -> intMap.put(Integer.parseInt(k), v));
                            return intMap;
                        },
                        intMap -> {
                            Map<String, SlotWhitelist> stringMap = new HashMap<>();
                            intMap.forEach((k, v) -> stringMap.put(String.valueOf(k), v));
                            return stringMap;
                        }
                );

                view.put("chestseparators_whitelists", mapCodec, map);
            }
        }
    }
}