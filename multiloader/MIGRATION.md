# Code migration — single-loader (yarn) → `common` (Mojmap)

Tracks moving the real mod code from the old single-loader project (`../src/main`, yarn mappings) into
the multiloader `common/` module (Mojmap). Work **one class at a time**, migrate its yarn names to
Mojmap, then `./gradlew :common:compileJava` — the compiler is the verifier for the mapping names.
Validate behaviour against `../MOD_SPEC.md`; never change behaviour during the move.

## Order (by dependency, non-render core first)

1. ✅ `data/SlotWhitelist` — filter record (Codec + StreamCodec). Done.
2. ✅ `access/IWhitelistProvider` — duck interface (no MC types). Done.
3. ✅ `ChestSeparatorsState` — common holder for the server-side maps `INVENTORY_FILTERS` /
   `LOCKED_CHESTS` (the pure-state parts of `ChestSeparatorsMain`, split from Fabric registration). Done.
4. ✅ `util/FilterPriority` — ordering/occupancy logic; yarn→Mojmap renames applied. Compiles. Done.
5. ✅ `config/GlobalChestConfig` — Gson POJO; only change was the config dir → `Platform.getConfigFolder()`.
   Loaded from common `AbsoluteOrder.init()`. Done.
6. ✅ Networking + server logic → `network/` (all 11 payloads as Mojmap `CustomPacketPayload`, plus
   `ModNetworking` = Architectury registration + the whole server core ported from `ChestSeparatorsMain`:
   auto-deposit, fill-from-open, whitelist persist/broadcast/expel, editor locks, reorder). Compiles.
   **Still TODO on the client side:** the S2C receivers (old `ModClientNetworking`) — they touch the
   editor/animator GUI classes, so they move with step 8.
7. ✅ Persistence + component registration: `ChestSeparatorsComponents` (Architectury `DeferredRegister`),
   and BE/entity NBT via the snapshot's ValueInput/ValueOutput + Codec (`SlotWhitelist.MAP_CODEC`). Done.
8. ✅ ALL server-side mixins (22) migrated to `common/mixin`, in `absoluteorder.mixins.json`, wired into
   fabric.mod.json + neoforge.mods.toml. Both jars build AND runtime-apply clean on NeoForge (0 failures).
   Architectury transformer remaps common mixins to intermediary/SRG (no refmap needed). NO accesswidener
   needed — used public accessors (`getSelectedSlot()`, `getNonEquipmentItems()`) and an `@Invoker`
   (`AbstractContainerMenuAccessor`) for the one protected method (`moveItemStackTo`).
   Persistence: BlockEntityComponentMixin, BlockEntityMixin, LootableContainerBlockEntityMixin,
   ShulkerBoxBlockEntityMixin, BlockDropMixin, StorageMinecartEntityMixin, ChestBoatEntityMixin.
   Enforcement: SlotWhitelistMixin, ScreenHandlerClickMixin, ScreenHandlerWhitelistMixin (@Redirect),
   HopperBlockEntityMixin, DoubleInventoryMixin, SimpleInventoryMixin, ArmorSlotFilterMixin,
   ArmorEquipFilterMixin, ScreenHandlerSwapFilterMixin, ServerPlayNetworkHandlerSwapFilterMixin,
   PlayerInventoryFilterMixin, ShulkerBoxSlotMixin, ShulkerBoxBlockMixin, PlayerScreenHandlerFallbackMixin.
   **Deferred to the GUI phase:** `WorldMixin` (client-only, depends on the not-yet-migrated
   `ChestConfigManager`), and the two dropped S2C pushes (chest/minecart/shulker open) — re-add once the
   client S2C receivers register the payload types.
9. ⬜ GUI/overlay (era E5) + `ChestConfigManager`/presets/undo + client S2C receivers (`ModClientNetworking`)
   + `WorldMixin` + config screen (ModMenu/Cloth integration) + REI/EMI/JEI.

## Architectury networking model (used by `ModNetworking`)
- **C2S**: `NetworkManager.registerReceiver(Side.C2S, TYPE, CODEC, (payload, ctx) -> ctx.queue(...))` in
  **common** init (runs both physical sides; the client's integrated server needs it too).
- **S2C**: on the **dedicated server** call `NetworkManager.registerS2CPayloadType(TYPE, CODEC)` (guard with
  `Platform.getEnvironment() == Env.SERVER`); on the **client** call `registerReceiver(Side.S2C, ...)` — that
  one registration covers both the send-type and the receiver, so do NOT also call `registerS2CPayloadType`
  on the client (double `playS2C().register` → crash).
- Send: `NetworkManager.sendToPlayer(ServerPlayer, payload)`; gate with `canPlayerReceive(player, TYPE)`.
- `ctx.getPlayer()` returns `Player` (cast to `ServerPlayer` server-side); `ctx.queue(Runnable)` = run on
  main thread (replaces Fabric `context.server().execute`).
- `PlayerLookup.tracking(world, pos)` → `serverLevel.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false)`.
- Disconnect cleanup → `dev.architectury.event.events.common.PlayerEvent.PLAYER_QUIT.register(player -> ...)`.
- **⚠ S2C send safety:** an S2C payload can only be sent once its TYPE is registered on the sending
  physical side. On a client/integrated-server that means the client S2C RECEIVER must be registered
  (that registration also registers the send-type). Until the client receivers are migrated, do NOT send
  ANY S2C payload from the integrated server — `NetworkManager.canPlayerReceive` is unreliable on NeoForge
  (returns true for a C2S-only-registered type) and `sendToPlayer` then NPEs with "codec is null". This is
  why the `createMenu` whitelist-push was dropped; re-add it (or rely on WhitelistRequestPayload) only after
  the client S2C receivers register `WhitelistPayload`/`EntityWhitelistPayload`/etc.

## ⚠ This 1.21.11 mapping is NOT stable Mojmap
The mappings layer (`loom.mappings.1_21_11.layered+hash.40359`) reflects late-2025 Mojang snapshot renames.
**`ResourceLocation` was renamed to `Identifier`** → `net.minecraft.resources.Identifier` (Mojmap package,
yarn-style class name; `Identifier.fromNamespaceAndPath(ns,path)`, `Identifier.parse(str)`). Everything
else checked so far is standard Mojmap. When a name looks off, verify against the mapped merged jar with
`javap -cp <jar> <fqcn>` (jar under `~/.gradle/caches/fabric-loom/minecraftMaven/.../minecraft-merged-1.21.11-*.jar`).

## yarn → Mojmap cheat-sheet (verified via the common compiler)

Classes:
- `net.minecraft.network.PacketByteBuf` → `net.minecraft.network.FriendlyByteBuf`
- `net.minecraft.network.RegistryByteBuf` → `net.minecraft.network.RegistryFriendlyByteBuf`
- `net.minecraft.network.packet.CustomPayload` → `net.minecraft.network.protocol.common.custom.CustomPacketPayload` (nested `Type<T>`; override `type()` not `getId()`)
- `net.minecraft.network.codec.PacketCodec` → `net.minecraft.network.codec.StreamCodec`
- `net.minecraft.network.codec.PacketCodecs` → `net.minecraft.network.codec.ByteBufCodecs`
- `net.minecraft.util.Uuids` → `net.minecraft.core.UUIDUtil`
- `net.minecraft.util.Identifier` → `net.minecraft.resources.Identifier` (see anomaly above — NOT ResourceLocation)
- `net.minecraft.inventory.Inventory` → `net.minecraft.world.Container`
- `net.minecraft.inventory.DoubleInventory` → `net.minecraft.world.CompoundContainer`
- `net.minecraft.inventory.SimpleInventory` → `net.minecraft.world.SimpleContainer`
- `net.minecraft.entity.player.PlayerInventory` → `net.minecraft.world.entity.player.Inventory` (Mojmap's *player* inventory is literally `Inventory`; `INVENTORY_SIZE`=36, `SELECTION_SIZE`=9; `add(stack)`, public field `player`)
- `net.minecraft.item.ItemStack` → `net.minecraft.world.item.ItemStack`
- `net.minecraft.item.Item` → `net.minecraft.world.item.Item`
- `net.minecraft.registry.Registries` → `net.minecraft.core.registries.BuiltInRegistries`
- `net.minecraft.screen.slot.Slot` → `net.minecraft.world.inventory.Slot`
- `net.minecraft.screen.ScreenHandler` → `net.minecraft.world.inventory.AbstractContainerMenu` (`slots`, `broadcastChanges()`)
- `net.minecraft.util.collection.DefaultedList` → `net.minecraft.core.NonNullList`
- `net.minecraft.util.math.BlockPos` → `net.minecraft.core.BlockPos`; `Direction` → `net.minecraft.core.Direction`
- `net.minecraft.util.math.Vec3d` → `net.minecraft.world.phys.Vec3`; `Box` → `net.minecraft.world.phys.AABB`
- `net.minecraft.util.hit.BlockHitResult/HitResult` → `net.minecraft.world.phys.BlockHitResult/HitResult`
- `net.minecraft.world.RaycastContext` → `net.minecraft.world.level.ClipContext` (`ClipContext.Block.COLLIDER`, `ClipContext.Fluid.NONE`)
- `net.minecraft.world.World` → `net.minecraft.world.level.Level`; `ServerWorld` → `net.minecraft.server.level.ServerLevel`
- `net.minecraft.server.network.ServerPlayerEntity` → `net.minecraft.server.level.ServerPlayer`
- `net.minecraft.entity.ItemEntity` → `net.minecraft.world.entity.item.ItemEntity`; `Entity` → `net.minecraft.world.entity.Entity`
- `net.minecraft.world.chunk.WorldChunk` → `net.minecraft.world.level.chunk.LevelChunk`; `ChunkPos` → `net.minecraft.world.level.ChunkPos`
- `net.minecraft.block.*` → `net.minecraft.world.level.block.*` (`Blocks`, `ChestBlock`); block entity → `...block.entity.*`
- `net.minecraft.block.enums.ChestType` → `net.minecraft.world.level.block.state.properties.ChestType`

Members:
- `PacketCodec.of(A::write, A::new)` → `StreamCodec.ofMember(A::write, A::new)` ; `PacketCodec.unit(x)` → `StreamCodec.unit(x)` ; `CustomPayload.codecOf((v,buf)->…, buf->…)` → `StreamCodec.ofMember((v,buf)->…, buf->…)`
- `ItemStack.PACKET_CODEC` → `ItemStack.STREAM_CODEC` (needs RegistryFriendlyByteBuf) ; `buf.readUuid/writeUuid` → `readUUID/writeUUID`
- `PacketCodec.tuple(...)` → `StreamCodec.composite(...)` ; `PacketCodecs.STRING.collect(toList())` → `ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())` ; `PacketCodecs.BOOLEAN`→`ByteBufCodecs.BOOL` ; `PacketCodecs.VAR_INT`→`ByteBufCodecs.VAR_INT`
- `Uuids.CODEC`→`UUIDUtil.CODEC` ; `Uuids.PACKET_CODEC`→`UUIDUtil.STREAM_CODEC`
- `Container.getStack(i)`→`getItem(i)` ; `setStack(i,s)`→`setItem(i,s)` ; `removeStack(i)`→`removeItemNoUpdate(i)` ; `size()`→`getContainerSize()` ; `markDirty()`→`setChanged()` ; `getMaxCount(s)`→`getMaxStackSize(s)` ; `isValid(i,s)`→`canPlaceItem(i,s)`
- `Slot.inventory`→`container` ; `getIndex()`→`getContainerSlot()` ; `getStack()`→`getItem()` ; `getMaxItemCount(s)`→`getMaxStackSize(s)` ; `canInsert(s)`→`mayPlace(s)`
- `ItemStack.areItemsAndComponentsEqual(a,b)`→`isSameItemSameComponents(a,b)` ; `getMaxCount()`→`getMaxStackSize()` ; `decrement(n)`→`shrink(n)` ; `increment(n)`→`grow(n)` ; `copyWithCount(n)` (same)
- `Registries.ITEM.getId(item)`→`BuiltInRegistries.ITEM.getKey(item)`
- Entity/player: `getUuid()`→`getUUID()` ; `getEntityWorld()`→`level()` ; `getEyePos()`→`getEyePosition()` ; `getBlockPos()`→`blockPosition()` ; `getPos()`→`position()` ; `getEyeY()` (same) ; `getBoundingBox().expand(r)`→`.inflate(r)` ; `setVelocity(...)`→`setDeltaMovement(...)` ; `setToDefaultPickupDelay()`→`setDefaultPickUpDelay()`
- Player: `currentScreenHandler`→`containerMenu` ; `playerScreenHandler`→`inventoryMenu` ; `insertStack(s)`→`getInventory().add(s)`
- Vec3: `squaredDistanceTo(v)`→`distanceToSqr(v)` ; `Vec3d.ofCenter(pos)`→`Vec3.atCenterOf(pos)`
- World/Level: `getOtherEntities(e,box,pred)`→`getEntities(e,box,pred)` ; `getEntity(uuid)` (same, on Level) ; `raycast(ctx)`→`clip(ctx)` ; `spawnEntity(e)`→`addFreshEntity(e)`
- BlockState `state.get(prop)`→`getValue(prop)` ; `BlockPos.offset(dir)`→`relative(dir)` ; `Direction.Type.HORIZONTAL`→`Direction.Plane.HORIZONTAL` ; `rotateYClockwise()`→`getClockWise()` ; `rotateYCounterclockwise()`→`getCounterClockWise()`
- `ChestBlock.CHEST_TYPE`→`ChestBlock.TYPE` ; `ChestBlock.FACING` (same) ; `ScreenHandler.sendContentUpdates()`→`broadcastChanges()`
