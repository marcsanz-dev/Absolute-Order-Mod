# Code migration — single-loader (yarn) → `common` (Mojmap)

Tracks moving the real mod code from the old single-loader project (`../src/main`, yarn mappings) into
the multiloader `common/` module (Mojmap). Work **one class at a time**, migrate its yarn names to
Mojmap, then `./gradlew :common:compileJava` — the compiler is the verifier for the mapping names.
Validate behaviour against `../MOD_SPEC.md`; never change behaviour during the move.

## Order (by dependency, non-render core first)

1. ✅ `data/SlotWhitelist` — filter record (Codec + StreamCodec). Done.
2. ✅ `access/IWhitelistProvider` — duck interface (no MC types). Done.
3. ⬜ Common bootstrap holder for the server-side maps `INVENTORY_FILTERS` / `LOCKED_CHESTS` etc.
   (the parts of `ChestSeparatorsMain` that are pure state, split from Fabric registration).
4. ⬜ `util/FilterPriority` — needs #3 + #2. Ordering/occupancy logic; many yarn→Mojmap renames.
5. ⬜ Enforcement mixins (`ScreenHandlerWhitelistMixin`, `HopperBlockEntityMixin`, slot mixins…) — go in
   the platform modules' mixins, targeting Mojmap classes; audit intermediary/SRG names per the guide.
6. ⬜ Networking via Architectury Networking (lock, whitelist sync, push/pull, sort-open-filters).
7. ⬜ Persistence (block data component / entity NBT) + presets/undo (client-local parts stay client).
8. ⬜ GUI/overlay (era E5), then config screen + REI/EMI/JEI.

## yarn → Mojmap cheat-sheet (verified via the common compiler)

Classes:
- `net.minecraft.network.PacketByteBuf` → `net.minecraft.network.FriendlyByteBuf`
- `net.minecraft.network.codec.PacketCodec` → `net.minecraft.network.codec.StreamCodec`
- `net.minecraft.network.codec.PacketCodecs` → `net.minecraft.network.codec.ByteBufCodecs`
- `net.minecraft.util.Uuids` → `net.minecraft.core.UUIDUtil`
- `net.minecraft.inventory.Inventory` → `net.minecraft.world.Container`
- `net.minecraft.entity.player.PlayerInventory` → `net.minecraft.world.entity.player.Inventory` (note the collision: Mojmap's *player* inventory is literally `Inventory`)
- `net.minecraft.item.ItemStack` → `net.minecraft.world.item.ItemStack`
- `net.minecraft.item.Item` → `net.minecraft.world.item.Item`
- `net.minecraft.registry.Registries` → `net.minecraft.core.registries.BuiltInRegistries`
- `net.minecraft.util.Identifier` → `net.minecraft.resources.ResourceLocation`
- `net.minecraft.screen.slot.Slot` → `net.minecraft.world.inventory.Slot`
- `net.minecraft.util.collection.DefaultedList` → `net.minecraft.core.NonNullList`

Members (unverified until compiled — expected):
- `PacketCodec.tuple(...)` → `StreamCodec.composite(...)`  ✅
- `PacketCodecs.STRING.collect(PacketCodecs.toList())` → `ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())`  ✅
- `PacketCodecs.BOOLEAN` → `ByteBufCodecs.BOOL`  ✅ ; `PacketCodecs.VAR_INT` → `ByteBufCodecs.VAR_INT`  ✅
- `Uuids.CODEC` → `UUIDUtil.CODEC`  ✅ ; `Uuids.PACKET_CODEC` → `UUIDUtil.STREAM_CODEC`  ✅
- `Container.getStack(i)` → `getItem(i)` ; `size()` → `getContainerSize()` ; `getMaxCount(s)` → `getMaxStackSize(s)` ; `isValid(i,s)` → `canPlaceItem(i,s)`
- `Slot.inventory` → `container` ; `getIndex()` → `getContainerSlot()` ; `getStack()` → `getItem()` ; `getMaxItemCount(s)` → `getMaxStackSize(s)` ; `canInsert(s)` → `mayPlace(s)`
- `ItemStack.areItemsAndComponentsEqual(a,b)` → `isSameItemSameComponents(a,b)` ; `getMaxCount()` → `getMaxStackSize()`
- `Registries.ITEM.getId(item)` → `BuiltInRegistries.ITEM.getKey(item)`
- `player.getUuid()` → `getUUID()`
