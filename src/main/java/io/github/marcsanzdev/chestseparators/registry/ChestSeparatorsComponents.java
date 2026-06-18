package io.github.marcsanzdev.chestseparators.registry;

import com.mojang.serialization.Codec;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.Map;

// Registry class for custom Data Components.
public class ChestSeparatorsComponents {

    public static final ComponentType<String> SHULKER_UUID = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("chestseparators", "shulker_uuid"),
            ComponentType.<String>builder().codec(Codec.STRING).build()
    );

    // New component mapping slot indices to their respective whitelist configurations.
    // Registered natively to ensure the game preserves the filters when containers are broken or moved.
    // New component mapping slot indices to their respective whitelist configurations.
    // Registered natively to ensure the game preserves the filters when containers are broken or moved.
    public static final ComponentType<Map<Integer, SlotWhitelist>> SLOT_WHITELISTS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("chestseparators", "slot_whitelists"),
            ComponentType.<Map<Integer, SlotWhitelist>>builder().codec(
                    Codec.unboundedMap(Codec.STRING, SlotWhitelist.CODEC).xmap(
                            stringMap -> {
                                Map<Integer, SlotWhitelist> intMap = new java.util.HashMap<>();
                                stringMap.forEach((k, v) -> {
                                    try { intMap.put(Integer.parseInt(k), v); }
                                    catch (NumberFormatException ignored) {}
                                });
                                return intMap;
                            },
                            intMap -> {
                                Map<String, SlotWhitelist> stringMap = new java.util.HashMap<>();
                                intMap.forEach((k, v) -> stringMap.put(String.valueOf(k), v));
                                return stringMap;
                            }
                    )
            ).build()
    );

    public static void initialize() {
    }
}