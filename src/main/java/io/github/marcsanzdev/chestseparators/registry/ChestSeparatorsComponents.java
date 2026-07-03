package io.github.marcsanzdev.chestseparators.registry;

import com.mojang.serialization.Codec;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registry class for custom Data Components.
 * <p>
 * Data Components are utilized to persist custom data directly onto ItemStacks.
 * This ensures that container configurations (like Shulker Box layouts or whitelists)
 * survive state changes, such as breaking a block and picking it up as an inventory item.
 */
public class ChestSeparatorsComponents {

    /**
     * Component type for storing the unique identifier of a Shulker Box.
     * <p>
     * This allows the visual layout and group configurations to remain linked
     * to the physical item when stored in other inventories.
     */
    public static final ComponentType<UUID> SHULKER_UUID = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("chestseparators", "shulker_uuid"),
            ComponentType.<UUID>builder().codec(Uuids.CODEC).build()
    );

    /**
     * Component type for storing the mapping of slot indices to their whitelist configurations.
     * <p>
     * Natively registered with a custom Codec that handles the conversion between String keys
     * (required for serialization) and Integer keys (used in runtime memory). This ensures
     * the game natively preserves the filters when containers are broken or moved.
     */
    public static final ComponentType<Map<Integer, SlotWhitelist>> SLOT_WHITELISTS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("chestseparators", "slot_whitelists"),
            ComponentType.<Map<Integer, SlotWhitelist>>builder().codec(
                    Codec.unboundedMap(Codec.STRING, SlotWhitelist.CODEC).xmap(
                            stringMap -> {
                                Map<Integer, SlotWhitelist> intMap = new HashMap<>();
                                stringMap.forEach((k, v) -> {
                                    try { intMap.put(Integer.parseInt(k), v); }
                                    catch (NumberFormatException ignored) {}
                                });
                                return intMap;
                            },
                            intMap -> {
                                Map<String, SlotWhitelist> stringMap = new HashMap<>();
                                intMap.forEach((k, v) -> stringMap.put(String.valueOf(k), v));
                                return stringMap;
                            }
                    )
            ).build()
    );

    /**
     * Initializes the component registry.
     * <p>
     * Calling this method during the mod bootstrap phase ensures all static fields
     * are evaluated and registered into the game's registry system.
     */
    public static void initialize() {
    }
}