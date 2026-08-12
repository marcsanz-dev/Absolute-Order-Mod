package io.github.marcsanzdev.chestseparators.util;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * Canonical string key for a filtered item in the 1.12.2 port.
 *
 * <p>1.12.2 predates the 1.13 "flattening": variants like the six plank types, wool colours, the enchanted
 * golden apple or cooked salmon are NOT separate item ids — they are the same {@link Item} distinguished by
 * metadata ({@code minecraft:planks} meta 0-5, {@code minecraft:golden_apple} meta 1, {@code
 * minecraft:cooked_fish} meta 1, ...). To let a filter target a specific variant (as the modern flattened
 * ports do natively) the key is {@code "namespace:path:meta"} for items that use metadata for variants
 * ({@link Item#getHasSubtypes()}), and plain {@code "namespace:path"} otherwise (tools/armour, whose metadata
 * is durability, stay item-level so a damaged sword is not a different filter entry).
 *
 * <p><b>Backward-compatible matching.</b> A filter entry <i>with</i> meta ({@code minecraft:planks:2}) matches
 * only that variant; a filter entry <i>without</i> meta ({@code minecraft:planks}) matches ANY variant. So the
 * pre-metadata filters and presets keep working unchanged (they read as "any variant"), while new entries can
 * be precise. All matching/ranking MUST go through {@link #matches}/{@link #rank} so client and server agree.
 */
public final class ItemKey {

    private ItemKey() {}

    /** Canonical key for a stack: {@code ns:path} or {@code ns:path:meta} for subtyped items. */
    public static String of(ItemStack stack) {
        return of(stack.getItem(), stack.getMetadata());
    }

    /** Canonical key for an item + metadata. */
    public static String of(Item item, int meta) {
        String id = baseId(item);
        return item.getHasSubtypes() ? id + ":" + meta : id;
    }

    /** The registry id of a stack's item, without any metadata suffix. */
    public static String baseId(ItemStack stack) {
        return baseId(stack.getItem());
    }

    private static String baseId(Item item) {
        ResourceLocation rl = item.getRegistryName();
        return rl == null ? "minecraft:air" : rl.toString();
    }

    /**
     * True when {@code allowed} lists this stack, matching a precise {@code ns:path:meta} entry for its
     * variant OR a plain {@code ns:path} entry (which means "any variant" / a legacy pre-metadata entry).
     */
    public static boolean matches(List<String> allowed, ItemStack stack) {
        if (allowed == null || stack.isEmpty()) return false;
        return allowed.contains(of(stack)) || allowed.contains(baseId(stack));
    }

    /**
     * Order index of this stack within {@code allowed} (the filter's priority order), or -1 if absent.
     * Prefers the precise variant entry; falls back to the plain "any variant" entry.
     */
    public static int rank(List<String> allowed, ItemStack stack) {
        if (allowed == null || stack.isEmpty()) return -1;
        int r = allowed.indexOf(of(stack));
        if (r < 0) r = allowed.indexOf(baseId(stack));
        return r;
    }

    // ---- Rendering / lookup: key string -> item / meta / display stack -----------------------------------

    /** The {@link Item} for a key ({@code ns:path} or {@code ns:path:meta}); null if the id is unknown. */
    public static Item item(String key) {
        return Item.getByNameOrId(stripMeta(key));
    }

    /** The metadata encoded in a key, or 0 when the key carries none. */
    public static int meta(String key) {
        int last = key.lastIndexOf(':');
        int first = key.indexOf(':');
        if (last > first) {
            try {
                return Integer.parseInt(key.substring(last + 1));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** A display {@link ItemStack} for a key (correct variant), or {@link ItemStack#EMPTY} if unknown. */
    public static ItemStack stack(String key) {
        Item item = item(key);
        if (item == null) return ItemStack.EMPTY;
        return new ItemStack(item, 1, meta(key));
    }

    /** Strips a trailing {@code :meta} (a numeric third segment) from a key, leaving {@code ns:path}. */
    private static String stripMeta(String key) {
        int last = key.lastIndexOf(':');
        int first = key.indexOf(':');
        if (last > first) {
            String tail = key.substring(last + 1);
            boolean numeric = !tail.isEmpty();
            for (int i = 0; i < tail.length(); i++) {
                if (!Character.isDigit(tail.charAt(i))) {
                    numeric = false;
                    break;
                }
            }
            if (numeric) return key.substring(0, last);
        }
        return key;
    }
}
