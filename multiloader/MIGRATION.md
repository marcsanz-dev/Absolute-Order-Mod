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

## Client / render cheat-sheet (GUI phase — verified)
The source is already written for **render era E5**, so the drawing API mostly matches (it uses `pushMatrix`,
not the old `push`). Core renames:
- `net.minecraft.client.gui.DrawContext` → `net.minecraft.client.gui.GuiGraphics` (`fill(x,y,x2,y2,argb)` is the same)
- `context.getMatrices()` → `context.pose()` — returns `org.joml.Matrix3x2fStack`; `pushMatrix()/translate(x,y)/scale(x,y)/popMatrix()` are JOML, unchanged
- `net.minecraft.util.Identifier` (client refs) → `net.minecraft.resources.Identifier`; `Identifier.of(ns,path)` → `Identifier.fromNamespaceAndPath(ns,path)`
- `net.minecraft.client.MinecraftClient` → `net.minecraft.client.Minecraft` (`getInstance()`, field `level`/`player`, field `gameDirectory`, `hasSingleplayerServer()`, `getSingleplayerServer()`, `getCurrentServer()`)
- NBT file I/O: `NbtCompound`→`CompoundTag` ; `NbtList`→`ListTag` ; `NbtString.of(s)`→`StringTag.valueOf(s)` ; `NbtElement`→`Tag` ; `NbtSizeTracker`→`NbtAccounter` (`unlimitedHeap()`) ; `NbtIo` (same). CompoundTag getters return Optional: `getString/getBoolean/getInt/getCompound/getList(k).ifPresent/orElse` ; `getKeys()`→`keySet()`
- `MinecraftServer.getSavePath(WorldSavePath.X)` → `getWorldPath(LevelResource.X)` ; `getSaveProperties()`→`getWorldData()` ; `ServerData.address`→`ip`
- `@Environment(EnvType.CLIENT)` stays as the Fabric annotation in `common` (Architectury remaps it to NeoForge's `@OnlyIn`).
### E5 input model (verified via javap on the Mojmap merged jar)
The E5 yarn source uses record-based input events. Mojmap uses the SAME model with different names:
- `net.minecraft.client.gui.Click` → `net.minecraft.client.input.MouseButtonEvent` (record: `.x()` double, `.y()` double, `.button()` int)
- `net.minecraft.client.input.KeyInput` → `net.minecraft.client.input.KeyEvent` (record: `.key()`, `.scancode()`, `.modifiers()`)
- `net.minecraft.client.input.CharInput` → `net.minecraft.client.input.CharacterEvent` (record: `.codepoint()`, `.modifiers()`, `.codepointAsString()`)
- `GuiEventListener`/`Screen` sigs: `mouseClicked(MouseButtonEvent, boolean doubled)`, `mouseReleased(MouseButtonEvent)`, `mouseDragged(MouseButtonEvent, double dx, double dy)`, `mouseScrolled(double,double,double,double)` (unchanged), `keyPressed(KeyEvent)`, `charTyped(CharacterEvent)`.

### `AbstractContainerScreen` (yarn `HandledScreen`) — E5 members (verified)
- fields: `x`→`leftPos`, `y`→`topPos`, `backgroundWidth`→`imageWidth`, `backgroundHeight`→`imageHeight`, `handler`→`menu`, `focusedSlot`→`hoveredSlot`, `titleX/Y`→`titleLabelX/Y`, `playerInventoryTitleX/Y`→`inventoryLabelX/Y`
- render methods: `drawSlot`→`renderSlot(GuiGraphics, Slot, int, int)` ; `drawSlots`→`renderSlots(GuiGraphics, int, int)` ; `renderMain`→`renderContents(GuiGraphics, int, int, float)` ; `drawMouseoverTooltip`→`renderTooltip(GuiGraphics, int, int)` ; `drawBackground`→`renderBg(GuiGraphics, float, int, int)` ; `drawForeground`→`renderLabels(GuiGraphics, int, int)`
- `HandledScreenAccessor`: keep invoker names `getHandler()/getX()/getY()`, retarget `@Accessor` to `menu`/`leftPos`/`topPos`.

### Screen-input hook = COMMON MIXIN (no Fabric API cross-loader)
The old client used Fabric `ScreenMouseEvents.allowMouseClick/Release/Drag/Scroll`. **Cross-loader decision: inject into `AbstractContainerScreen.mouseClicked/mouseReleased/mouseDragged/mouseScrolled` in the common `GenericContainerScreenMixin`** (return-cancellable via `CallbackInfoReturnable<Boolean>`), dropping the Fabric event bus entirely. Identical behavior on both loaders. keyPressed/mouseScrolled were already mixin-injected.

### Other verified client renames
- `net.minecraft.client.render.item.ItemRenderState` → `net.minecraft.client.renderer.item.ItemStackRenderState`
- `net.minecraft.client.item.ItemModelManager` → `net.minecraft.client.renderer.item.ItemModelResolver`
- `net.minecraft.client.gl.RenderPipelines` → `net.minecraft.client.renderer.RenderPipelines`
- `net.minecraft.client.render.command.OrderedRenderCommandQueue` → `net.minecraft.client.renderer.OrderedSubmitNodeCollector`
- `GuiGraphics` item/text (all present, standard): `renderItem(ItemStack,x,y[,seed])`, `renderFakeItem(ItemStack,x,y)`, `renderItemDecorations(Font, ItemStack, x, y[, String])`, `drawString(Font, str/Component, x, y, color[, shadow])`, `drawCenteredString(Font, ..., x, y, color)`, `drawWordWrap(Font, FormattedText, x, y, w, color)`. `drawStrokedRectangle(x,y,w,h,color)`→`renderOutline(x,y,w,h,color)`.
- `MinecraftClient.textRenderer` → `Minecraft.font` (field, type `net.minecraft.client.gui.Font`). Field `Minecraft.screen`, `setScreen(Screen)`.
- `Window` (from `client.getWindow()`, `com.mojang.blaze3d.platform.Window`): `getScaleFactor()`→`getGuiScale()` ; `getFramebufferWidth()`→`getWidth()` ; `getFramebufferHeight()`→`getHeight()` ; `getScaledWidth()`→`getGuiScaledWidth()` ; `getScaledHeight()`→`getGuiScaledHeight()` ; `getHandle()` unchanged
- `net.minecraft.text.Text` → `net.minecraft.network.chat.Component` (`Text.translatable`→`Component.translatable`, `Text.literal`→`Component.literal`)
- `net.minecraft.util.Formatting` → `net.minecraft.ChatFormatting`
- `net.minecraft.screen.slot.Slot` → `net.minecraft.world.inventory.Slot` (fields `x`,`y` unchanged)
- `net.minecraft.util.math.MathHelper` → `net.minecraft.util.Mth`
- `net.minecraft.item.Item`/`ItemStack` → `net.minecraft.world.item.Item`/`ItemStack` ; `net.minecraft.registry.Registries.ITEM` → `net.minecraft.core.registries.BuiltInRegistries.ITEM` (`.get(Identifier)`, `Identifier.tryParse`→`Identifier.tryParse` same)
- `net.minecraft.screen.ScreenHandler` → `net.minecraft.world.inventory.AbstractContainerMenu` ; `GenericContainerScreenHandler` → `net.minecraft.world.inventory.ChestMenu` ; `player.currentScreenHandler` → `player.containerMenu` ; `handler.getInventory()`→`ChestMenu.getContainer()`
- `net.minecraft.client.gui.widget.TextFieldWidget` → `net.minecraft.client.gui.components.EditBox` ; `net.minecraft.client.gui.screen.Screen` → `net.minecraft.client.gui.screens.Screen`
- `net.minecraft.client.option.KeyBinding` → `net.minecraft.client.KeyMapping` ; `net.minecraft.client.util.InputUtil` → `com.mojang.blaze3d.platform.InputConstants`
- `net.minecraft.sound.SoundEvents/SoundEvent` → `net.minecraft.sounds.SoundEvents/SoundEvent` ; `SoundCategory` → `net.minecraft.sounds.SoundSource`
- `net.minecraft.util.math.Vec3d`→`net.minecraft.world.phys.Vec3` ; `RotationAxis`→`com.mojang.math.Axis` ; `Direction`→`net.minecraft.core.Direction` ; `BlockPos` (`.offset`→`.relative`)
- Blocks/entities: `net.minecraft.block.*`→`net.minecraft.world.level.block.*` ; `net.minecraft.block.entity.*`→`net.minecraft.world.level.block.entity.*` ; `net.minecraft.block.enums.ChestType`→`net.minecraft.world.level.block.state.properties.ChestType` ; `ChestBlock.getDoubleBlockType`/`ChestType` connect helpers verify per use

## GUI phase — progress & remaining (as of this session)

**Committed & GREEN in `common`:**
- Foundation: EditorState, ModTextures, ChestConfigManager, UndoRedoHistory, UiColors, UiTheme.
- Client accessor/invoker mixins (`mixin/client/`): HandledScreenAccessor, ChestLidAccessor, EnderChestLidAccessor, ShulkerAnimationAccessor, CreativeInventoryScreenAccessor, CreativeSlotAccessor, RecipeBookScreenAccessor, RecipeBookWidgetInvoker + `access/LidAnimatorAccess`.
- Widgets: PressAnim, CustomWidget, ToolButtonWidget, ActionIconButtonWidget, WideButtonWidget.
- ModKeyBindings (Architectury `KeyMappingRegistry.register` + `KeyMapping.Category.register(Identifier)`), util/ChestPosStorage.

**THE WHOLE 28-FILE EDITOR CLUSTER NOW COMPILES GREEN in `common`** (`./gradlew :common:compileJava` BUILD SUCCESSFUL). Migrated with `tools/client-mechanical-rename.sed` (reproducible) + hand refactors:
- `ChestSeparatorsEditor` (hub, all screens, renderers, geometry/layout/session, MagnifierRenderer) — all mechanical renames applied; hub networking → `NetworkManager.canServerReceive/sendToServer` with `Payload.TYPE`.
- `network/ModClientNetworking` — client S2C receivers via `NetworkManager.registerReceiver(Side.S2C, X.TYPE, X.CODEC, (p,ctx)->ctx.queue(...))` (Shulker UUID / lock response / auto-deposit result); `@Environment(CLIENT)`.
- `event/KeyInputHandler` — Architectury `ClientTickEvent.CLIENT_POST`; `consumeClick()`, `options.keyShift.isDown()`; `isModifierPressed()` simplified to `keyMapping.isDown()`.
- `EditorInputHandler` — Fabric `ScreenMouseEvents.allow*` REMOVED; the release/drag/scroll bodies became public `onMouseReleased/onMouseDragged/onMouseScrolled(...)` (click = existing `handleClick`) for the container-screen mixin to call. `setupInputHandlers()` deleted (+ its call site in the editor).
- `AutoDepositAnimator` — all renames done (E5 item render: `updateForTopItem`, `ItemStackRenderState.submit`, `ItemOwner` [was `HeldItemContext`; methods `level()/position()/getVisualRotationYInDegrees()`], `Axis.YP`, `Vec3.atCenterOf`, `ClientLevel.playLocalSound/addParticle`, `state.getValue(ChestBlock.TYPE/FACING)`, `getClockWise/getCounterClockWise`, `ChestLidController.shouldBeOpen`, `camera.position()`, `getMainCamera()`, `getItemModelResolver()`, `OverlayTexture.NO_OVERLAY`). The Fabric `WorldRenderEvents`/`WorldRenderContext` hook was REMOVED; the render logic is now `public static void renderFlights(PoseStack, SubmitNodeCollector)` awaiting a LevelRenderer mixin to call it (Architectury has NO world-render event — verified).

**Still to do — WIRING via client mixins + entrypoints (the editor code is done; nothing calls it yet):**
1. Big client mixins (`mixin/client/`): **GenericContainerScreenMixin** (over `AbstractContainerScreen`: init / renderContents / renderSlot / keyPressed / charTyped / mouseScrolled + NEW `mouseClicked/mouseReleased/mouseDragged` injects that forward to `EditorInputHandler.handleClick/onMouseReleased/onMouseDragged/onMouseScrolled`), **CreativeInventoryScreenMixin** (`selectTab`/`checkTabClicked`/`checkTabHovering`/keyPressed/charTyped), InGameHudHotbarLinesMixin, StatusEffectsDisplayMixin, ArmorEquipClientMixin. Register in the `client` section of `absoluteorder.mixins.json`.
2. **`LevelRendererMixin`** — the deferred hard piece: inject into the world's after-entities render pass and call `AutoDepositAnimator.renderFlights(poseStack, submitNodeCollector)`. E5 `LevelRenderer.renderLevel` takes `Matrix4f`s (no PoseStack/collector), so the injection point/target needs research (likely a deeper entity-render method or the level pass that owns the `SubmitNodeCollector`). **Re-add the 3 dropped S2C open-pushes** (chest/shulker/minecart) in the server mixins once client S2C receivers register (see the S2C-send rule).
3. `mixin/WorldMixin` (depends on ChestConfigManager).
4. Client entrypoints: Fabric `ClientModInitializer` + NeoForge client init → a common `AbsoluteOrderClient.init()` (ModKeyBindings.register, ModClientNetworking.register, KeyInputHandler.register, AutoDepositAnimator.register). Config screen (ModMenu/Cloth), REI/EMI/JEI.

Verified E5 names for the remaining work: SoundEvents fields drop `BLOCK_`/`ENTITY_` prefix (`CHEST_OPEN`, `SHULKER_BOX_OPEN`, `ENDER_CHEST_OPEN`, `ITEM_PICKUP`); `EditBox.setBordered/setResponder/getValue/setValue`; `Slot.getContainerSlot()`; `Block.byItem`; `Minecraft.level`/`.screen`; `BuiltInRegistries.CREATIVE_MODE_TAB` (`.getKey`); `TextFieldWidget`→`EditBox`; `MutableText`→`MutableComponent`; `net.minecraft.registry.tag`→`net.minecraft.tags`.
