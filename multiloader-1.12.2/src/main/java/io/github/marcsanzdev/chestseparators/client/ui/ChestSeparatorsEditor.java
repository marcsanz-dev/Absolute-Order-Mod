package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.screens.ScreenColorPicker;
import io.github.marcsanzdev.chestseparators.client.ui.screens.ScreenDrawLines;
import io.github.marcsanzdev.chestseparators.client.ui.screens.ScreenEditFilter;
import io.github.marcsanzdev.chestseparators.client.ui.screens.ScreenViewGroups;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.client.Minecraft;
import io.github.marcsanzdev.chestseparators.client.compat.GuiGraphics;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.inventory.GuiShulkerBox;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class ChestSeparatorsEditor {

    private static ChestSeparatorsEditor activeInstance;

    public static ChestSeparatorsEditor getInstance() {
        return activeInstance;
    }

    public EditorSessionData getSession() {
        return this.session;
    }

    public final GuiContainer screen;
    public final HandledScreenAccessor accessor;

    public static EditorSessionData activeSession = new EditorSessionData();
    public final EditorSessionData session = activeSession;
    public EditorGeometry geometry;
    public EditorRenderer renderer;
    public EditorInputHandler inputHandler;
    public EditorLayout layout;

    public ScreenViewGroups screenViewGroups;
    public ScreenDrawLines screenDrawLines;
    public ScreenColorPicker screenColorPicker;
    public ScreenEditFilter screenEditFilter;

    public ToolButtonWidget entryButton;
    public ToolButtonWidget whitelistButton;
    public ToolButtonWidget depositButton;
    public ToolButtonWidget fillButton;
    public ToolButtonWidget presetsButton;
    public ToolButtonWidget chestPresetsButton;
    public final io.github.marcsanzdev.chestseparators.client.ui.screens.PresetsMenu presetsMenu =
            new io.github.marcsanzdev.chestseparators.client.ui.screens.PresetsMenu(this);
    public GuiTextField searchBox;
    public GuiTextField whitelistSearchBox;

    public boolean isHoveringDeposit = false;
    public boolean isHoveringFill = false;
    public boolean depositPreviewShift = false;
    public boolean suspendDepositPreview = false;
    public long depositClickTime = 0;
    public long fillClickTime = 0;
    public final Map<Integer, Integer> previewSourceRemaining = new HashMap<>();
    public final Map<Integer, ItemStack> previewTargetIncoming = new HashMap<>();

    public static final int TOOL_ERASER_ID = -1;

    public boolean hasEditorLock = false;

    public boolean colorPickerModified = false;
    public int restoreColorIndex = -1;

    // Slots changed by the most recent undo/redo, blinked briefly to show what happened.
    public Map<Integer, ChestConfigManager.SlotChange> undoHighlights = new HashMap<>();
    public long undoHighlightStart = 0;

    public ChestSeparatorsEditor(GuiContainer screen) {
        this.screen = screen;
        this.accessor = (HandledScreenAccessor) screen;
        activeInstance = this;
    }

    public void init() {

        // Determine the editor target up-front so geometry/layout (which count the editable slots) are
        // computed for the right slot set. The creative screen counts as a player-inventory context too
        // (its inventory tab shows the real player inventory).
        session.isPlayerInventory = isInventoryContextScreen(this.screen);

        this.layout = new EditorLayout();
        this.geometry = new EditorGeometry(session, accessor);
        this.layout.update(screen, accessor, getSidebarYOffset());

        this.renderer = new EditorRenderer(this, session, layout, screen, accessor);
        this.inputHandler = new EditorInputHandler(this, session, geometry, layout, screen, accessor);

        this.screenViewGroups = new ScreenViewGroups(this);
        this.screenViewGroups.init();

        this.screenDrawLines = new ScreenDrawLines(this);
        this.screenDrawLines.init();

        this.screenColorPicker = new ScreenColorPicker(this);
        this.screenColorPicker.init();

        this.screenEditFilter = new ScreenEditFilter(this);
        this.screenEditFilter.init();

        session.isInventoryScreenContext = isInventoryContextScreen(this.screen);
        session.isPlayerInventory = session.isInventoryScreenContext;

        // The survival inventory screen has no container context: clear any stale chest data left in
        // ChestPosStorage. In a chest screen we keep the chest context so the edit target can toggle.
        session.currentChestPos = session.isInventoryScreenContext ? null : ChestPosStorage.lastClickedPos;
        session.currentDimension = ChestPosStorage.lastClickedDimension;
        session.isEntityChest = !session.isInventoryScreenContext && ChestPosStorage.isEntityOpened;
        session.isMinecartChest = session.isEntityChest && ChestPosStorage.isMinecartEntity;
        session.currentEntityUUID = session.isInventoryScreenContext ? null : ChestPosStorage.lastClickedEntityUUID;
        session.isEnderChest = false;

        session.isShulkerBox = !session.isInventoryScreenContext && this.screen instanceof GuiShulkerBox;
        session.currentShulkerUUID = session.isInventoryScreenContext ? null : ChestPosStorage.lastOpenedShulkerUUID;

        // Consume the interactBlock -> screen-init handoff: the values now live in the session, so clear
        // the shared storage here (on read) instead of in the screen's removed(). Clearing on removed()
        // races with the NEXT container: interactBlock(B) sets the position, then the OLD screen's
        // removed() would null it before B's init reads it, leaving currentChestPos == null and silently
        // dropping B's saves. Clearing on read keeps leak-prevention without the race.
        ChestPosStorage.lastClickedPos = null;
        ChestPosStorage.lastClickedEntityUUID = null;
        ChestPosStorage.lastOpenedShulkerUUID = null;
        ChestPosStorage.isEntityOpened = false;
        ChestPosStorage.isMinecartEntity = false;

        ChestConfigManager.getInstance().loadWorldPalette();
        loadConfigForCurrentTarget();
        requestChestWhitelistsIfNeeded();

        int x = accessor.getX();
        int y = accessor.getY();
        int bgWidth = accessor.getBackgroundWidth();

        this.entryButton = new io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget(
                x + bgWidth - 22,
                y - 22,
                io.github.marcsanzdev.chestseparators.client.ModTextures.BTN_EDIT_LINES,
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.edit_mode").getFormattedText(),
                () -> {
                    toggleState(EditorState.DRAW_LINES);
                    playClickSound(1.0f);
                });

        this.whitelistButton = new io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget(
                x + bgWidth - 44,
                y - 22,
                io.github.marcsanzdev.chestseparators.client.ModTextures.BTN_WHITELIST,
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.whitelist_mode").getFormattedText(),
                () -> {
                    boolean isFilterState = (session.currentState == EditorState.VIEW_GROUPS
                            || session.currentState == EditorState.SELECT_SLOTS
                            || session.currentState == EditorState.EDIT_FILTER);

                    if (isFilterState) {
                        toggleState(EditorState.HIDDEN);
                        playClickSound(1.0f);
                    } else {
                        if (session.currentChestPos != null && !session.isEntityChest && !session.isShulkerBox) {
                            // chestseparators is a required dependency, so every server has the receiver.
                            io.github.marcsanzdev.chestseparators.network.ModNet.sendToServer(
                                    new io.github.marcsanzdev.chestseparators.network.EditorLockRequestPayload(
                                            session.currentChestPos, true));
                        } else {
                            toggleState(EditorState.VIEW_GROUPS);
                            playClickSound(1.0f);
                        }
                    }
                });

        // PUSH items into the chest. Works from every editor screen (not just the closed editor).
        this.depositButton = new ToolButtonWidget(0, 0, ModTextures.BTN_DEPOSIT, "", () -> {
            this.depositClickTime = System.currentTimeMillis();

            executeDeposit(isShiftDown());
            playClickSound(1.2f);
        });

        // PULL items from the chest. Shift also pulls unfiltered items into free inventory space.
        this.fillButton = new ToolButtonWidget(
                0,
                0,
                ModTextures.ICON_BACKPACK_FULL,
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.pull_from_chest").getFormattedText(),
                () -> {
                    this.fillClickTime = System.currentTimeMillis();
                    requestFillFromOpenChest(isShiftDown());
                });

        // "Inventory presets" menu opener: a top icon available in any editor context.
        this.presetsButton = new ToolButtonWidget(
                0,
                0,
                ModTextures.ICON_SAVE,
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.presets").getFormattedText(),
                () -> {
                    presetsMenu.resetView();
                    boolean openingSame = session.isPresetsMenuOpen && !session.presetsMenuChestMode;
                    session.presetsMenuChestMode = false;
                    session.isPresetsMenuOpen = !openingSame;
                    // Only one of layout/filters/presets may be selected at a time: opening presets
                    // closes any open layout or filter sub-screen.
                    if (session.isPresetsMenuOpen && session.currentState != EditorState.HIDDEN) {
                        toggleState(EditorState.HIDDEN);
                    }
                    playClickSound(1.0f);
                });

        // "Chest presets" menu opener: independent from inventory presets, only when a chest is open.
        this.chestPresetsButton = new ToolButtonWidget(
                0,
                0,
                ModTextures.ICON_COPY,
                new net.minecraft.util.text.TextComponentTranslation("tooltip.chestseparators.desc.chest_presets").getFormattedText(),
                () -> {
                    presetsMenu.resetView();
                    boolean openingSame = session.isPresetsMenuOpen && session.presetsMenuChestMode;
                    session.presetsMenuChestMode = true;
                    session.isPresetsMenuOpen = !openingSame;
                    if (session.isPresetsMenuOpen && session.currentState != EditorState.HIDDEN) {
                        toggleState(EditorState.HIDDEN);
                    }
                    playClickSound(1.0f);
                });

        // Smooth vector-exported icons (128px, linear-filtered via .mcmeta) for the toolbar. They are
        // single-color white glyphs tinted by the button state (light-gray / accent blue).
        ToolButtonWidget[] toolbarBtns = {
            this.entryButton, this.whitelistButton, this.depositButton,
            this.fillButton, this.presetsButton, this.chestPresetsButton
        };
        net.minecraft.util.ResourceLocation[] toolbarIcons = {
            // Edit-layout reuses the combo icon (crossed pincel + brocha) — same meaning, same glyph.
            // Deposit = PUSH items into the chest = UP arrow (ICON_SM_FILL); fill = PULL items out of the
            // chest into your inventory = DOWN arrow (ICON_SM_DEPOSIT). Matches the chest-above/inventory-
            // below mental model.
            ModTextures.ICON_SM_COMBO, ModTextures.ICON_SM_FILTER, ModTextures.ICON_SM_FILL,
            ModTextures.ICON_SM_DEPOSIT, ModTextures.ICON_SM_INV_PRESETS, ModTextures.ICON_SM_CHEST_PRESETS
        };
        for (int i = 0; i < toolbarBtns.length; i++) {
            toolbarBtns[i].baseIcon = toolbarIcons[i];
            toolbarBtns[i].maskIcon = null;
            toolbarBtns[i].texSize = 128;
            toolbarBtns[i].tintByState = true;
        }

        // Toolbar positioning is now handled per-frame by EditorRenderer#renderToolbar, which docks the
        // visible icons into a vertical column on the right edge of the container GUI (adaptive to how
        // many are enabled in the config). The buttons keep their constructor positions until first render.

        session.allGameItems.clear();
        Minecraft client = Minecraft.getMinecraft();

        if (client.world != null) {
            // Read the survival item set from the already-built creative search group.
            //
            // We must NOT call CreativeModeTabs.updateDisplayContext here. That rebuilds the GLOBAL creative
            // display context, and because we do not also reload the vanilla creative SearchManager index,
            // it corrupts that index so the creative-inventory search returns zero results for every query.
            // The display context is shared across all screens, so calling it when opening ANY container
            // (a chest, a barrel, the inventory...) would break creative search for the rest of the session.
            // Reading getDisplayStacks() below does not rebuild anything; the context is already populated by
            // vanilla on world join / resource reload.
            // 1.19.2 has no creative display context / aggregating search group; gather the survival item set
            // by filling every concrete creative tab (skipping the special hotbar/inventory/search tabs).
            net.minecraft.util.NonNullList<ItemStack> allStacks = net.minecraft.util.NonNullList.create();
            for (CreativeTabs group : CreativeTabs.CREATIVE_TAB_ARRAY) {
                if (group == null
                        || group == CreativeTabs.HOTBAR
                        || group == CreativeTabs.INVENTORY
                        || group == CreativeTabs.SEARCH) continue;
                group.displayAllRelevantItems(allStacks);
            }
            for (ItemStack stack : allStacks) {
                Item item = stack.getItem();
                if (!stack.isEmpty() && !isNonSurvivalItem(item) && !session.allGameItems.contains(item)) {
                    session.allGameItems.add(item);
                }
            }

            // Fallback: if the creative display context has not been built yet (rare — e.g. first open before
            // vanilla populated it), derive the set straight from the item registry so the filter editor is
            // never empty.
            if (session.allGameItems.isEmpty()) {
                for (Item item : Item.REGISTRY) {
                    if (item != Items.AIR && !isNonSurvivalItem(item)) session.allGameItems.add(item);
                }
            }
        }

        session.filteredItems.clear();
        session.filteredItems.addAll(session.allGameItems);

        int w = 240;
        int h = 200;
        int guiX = (screen.width - w) / 2;
        int guiY = (screen.height - h) / 2;

        session.availableTabs.clear();
        for (int i = 0; i < 16; i++) {
            session.availableTabs.add(null);
        }

        EditorSessionData.CreativeTabInfo chestTab = new EditorSessionData.CreativeTabInfo(
                new net.minecraft.util.text.TextComponentTranslation("itemGroup.chestseparators.chest_and_inventory"),
                new ItemStack(net.minecraft.init.Blocks.CHEST),
                null,
                false,
                true);
        // Chest & Inventory sits one slot further right (13) so brewing can take slot 12 and land immediately
        // to its left — keeping the custom tab as the right-most of the bottom row.
        session.availableTabs.set(13, chestTab);

        java.util.List<EditorSessionData.CreativeTabInfo> modTabs = new java.util.ArrayList<>();
        // 1.19.2 creative tabs are the classic static CreativeModeTab.TABS array (no registry, no E5 category
        // ids). Map each onto the editor's fixed slot grid; the leftovers (brewing, misc, mod tabs) append after.
        for (CreativeTabs group : CreativeTabs.CREATIVE_TAB_ARRAY) {
            if (group == null
                    || group == CreativeTabs.HOTBAR
                    || group == CreativeTabs.INVENTORY) continue;

            boolean isSearch = group == CreativeTabs.SEARCH;
            // Resolve the tab name through the CLIENT I18n (net.minecraft.client.resources.I18n), which holds
            // the vanilla itemGroup.* keys; group.getTranslatedTabLabel() goes through the common LanguageMap,
            // which does not carry these client-only keys here, so it renders the raw "itemGroup.buildingBlocks".
            EditorSessionData.CreativeTabInfo tab = new EditorSessionData.CreativeTabInfo(
                    new net.minecraft.util.text.TextComponentString(
                            net.minecraft.client.resources.I18n.format("itemGroup." + group.getTabLabel())),
                    group.getIconItemStack(), group, isSearch, false);

            if (group == CreativeTabs.BUILDING_BLOCKS) session.availableTabs.set(0, tab);
            else if (group == CreativeTabs.DECORATIONS) session.availableTabs.set(1, tab);
            else if (group == CreativeTabs.TRANSPORTATION) session.availableTabs.set(2, tab);
            else if (group == CreativeTabs.REDSTONE) session.availableTabs.set(4, tab);
            else if (isSearch) session.availableTabs.set(7, tab);
            else if (group == CreativeTabs.TOOLS) session.availableTabs.set(8, tab);
            else if (group == CreativeTabs.COMBAT) session.availableTabs.set(9, tab);
            else if (group == CreativeTabs.FOOD) session.availableTabs.set(10, tab);
            else if (group == CreativeTabs.MATERIALS) session.availableTabs.set(11, tab);
            else if (group == CreativeTabs.BREWING) session.availableTabs.set(12, tab);
            else modTabs.add(tab);
        }
        session.availableTabs.addAll(modTabs);

        int firstValidTab = 0;
        for (int i = 0; i < session.availableTabs.size(); i++) {
            if (session.availableTabs.get(i) != null) {
                firstValidTab = i;
                break;
            }
        }
        switchCreativeTab(firstValidTab);

        this.searchBox = new GuiTextField(
                0,
                Minecraft.getMinecraft().fontRenderer,
                guiX + 10,
                guiY + 10,
                100,
                12);
        this.searchBox.setEnableBackgroundDrawing(false);
        this.searchBox.setMaxStringLength(50);
        // TODO(1.12.2 port): GuiTextField has no per-change responder; the equivalent bodies live in
        // onSearchTextChanged()/onWhitelistSearchChanged() and are invoked from the input handling below
        // (and should be called by the editor screen mixin boundary once it is ported).

        this.whitelistSearchBox = new GuiTextField(
                1,
                Minecraft.getMinecraft().fontRenderer,
                guiX,
                guiY,
                100,
                12);
        this.whitelistSearchBox.setEnableBackgroundDrawing(false);
        this.whitelistSearchBox.setMaxStringLength(50);
        // Mouse routing is done by the common GenericContainerScreenMixin, which forwards
        // clicked/released/dragged/scrolled to inputHandler; no per-screen event registration needed.
    }

    /** Responder body for {@link #searchBox}: re-filters the item grid when the search tab is active. */
    public void onSearchTextChanged() {
        if (session.availableTabs.get(session.currentCreativeTabIndex) == null
                || !session.availableTabs.get(session.currentCreativeTabIndex).isSearchTab) return;
        switchCreativeTab(session.currentCreativeTabIndex);
    }

    /** Responder body for {@link #whitelistSearchBox}: rebuilds the visible allowed-items list. */
    public void onWhitelistSearchChanged() {
        session.listScrollY = 0f;
        updateWhitelistSearchCache();
    }

    public void onClose() {
        releaseLock();
        ChestConfigManager.getInstance().clearCurrentConfig();
        ChestConfigManager.getInstance().getCurrentWhitelists().clear();

        ChestPosStorage.lastOpenedShulkerUUID = null;
        session.isColorPickerOpen = false;
        activeSession = new EditorSessionData();
        // TODO(1.12.2 port): E1 has no GLFW window handle / glfwSetInputMode; the modern cursor-mode reset
        // (GLFW_CURSOR -> GLFW_CURSOR_NORMAL) is a no-op on the LWJGL2 client and is dropped here.
    }

    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (renderer != null) renderer.render(context, mouseX, mouseY, delta);
    }

    /**
     * Loads the working maps for the current screen: the container's config (chest variants) plus the
     * player inventory profile mirrored at offset keys, so both are edited simultaneously.
     */
    private void loadConfigForCurrentTarget() {
        ChestConfigManager manager = ChestConfigManager.getInstance();

        // 1. Container portion (clears the working maps). The inventory screen has no container.
        if (session.isInventoryScreenContext) {
            manager.clearCurrentConfig();
            manager.getCurrentWhitelists().clear();
        } else if (session.isShulkerBox && session.currentShulkerUUID != null) {
            manager.loadShulkerConfig(session.currentShulkerUUID);
        } else if (session.isEntityChest && session.currentEntityUUID != null) {
            manager.loadEntityConfig(session.currentEntityUUID);
        } else if (session.currentChestPos != null && Minecraft.getMinecraft().world != null) {
            if (Minecraft.getMinecraft()
                            .world
                            .getBlockState(session.currentChestPos)
                            .getBlock()
                    == Blocks.ENDER_CHEST) {
                session.isEnderChest = true;
                manager.loadEnderConfig();
            } else {
                manager.loadConfig(session.currentChestPos, session.currentDimension);
            }
        }

        // 2. Player inventory portion, mirrored into the working maps at offset keys.
        manager.loadInventoryProfile();
        manager.mirrorInventoryIntoCurrent();
    }

    /** Fetches the chest's server-authoritative whitelists, when a real chest is open. */
    private void requestChestWhitelistsIfNeeded() {
        // Shulker boxes are UUID-keyed and self-contained like ender/entity configs: their whitelist is
        // restored from the local .dat by loadShulkerConfig, so must NOT be overwritten by the position-keyed
        // server request — the server drops the block entity's whitelist when the shulker is broken, so it
        // would answer empty and wipe the just-loaded filters.
        if (!session.isInventoryScreenContext
                && session.currentChestPos != null
                && !session.isEntityChest
                && !session.isEnderChest
                && !session.isShulkerBox) {
            io.github.marcsanzdev.chestseparators.network.ModNet.sendToServer(
                    new io.github.marcsanzdev.chestseparators.network.WhitelistRequestPayload(
                            session.currentChestPos));
        }
    }

    public int getSidebarYOffset() {
        return geometry.getContainerSlotCount() > 27 ? 0 : -18;
    }

    /**
     * Items that appear in the creative menu but are not obtainable in survival, so they are kept out
     * of the filter list and out of "Allow All": every spawn egg, the player head, the spawners,
     * reinforced deepslate, and the infested-stone blocks.
     */
    private static boolean isNonSurvivalItem(Item item) {
        if (item instanceof net.minecraft.item.ItemMonsterPlacer) return true;
        if (item == Items.SKULL) return true;
        net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(item);
        return block == Blocks.MOB_SPAWNER
                // TRIAL_SPAWNER (1.21+) and REINFORCED_DEEPSLATE (1.19+) do not exist in 1.18.2.
                // 1.12.2 collapses all infested-stone variants into the single MONSTER_EGG block (meta-keyed).
                || block == Blocks.MONSTER_EGG;
                // INFESTED_DEEPSLATE is a 1.17+ block and does not exist in 1.16.5.
    }

    public boolean isEditMode() {
        return session.currentState != EditorState.HIDDEN;
    }

    /**
     * Whether a slot can be decorated/filtered by the editor. The player's own inventory slots are
     * always editable (chest screens edit them simultaneously with the chest); container slots are
     * editable only in a container screen (this also excludes the crafting slots of the inventory
     * screen). Chest and inventory edits coexist via {@link #slotKey} namespacing.
     */
    public static boolean isEditableSlot(Slot slot) {
        if (slot.inventory instanceof net.minecraft.entity.player.InventoryPlayer) return true;
        return !activeSession.isInventoryScreenContext;
    }

    /**
     * The real inventory index of a slot. In the creative inventory the visible slots are
     * {@code CreativeSlot} wrappers whose {@code getIndex()} is the creative handler's slot ID (armor 5-8,
     * hotbar 36-44, offhand 45), not the inventory index the server enforces by. Unwrapping to the
     * underlying slot yields the same index the survival inventory and chests report, so editing from
     * creative lands filters/layouts on the correct slot.
     */
    public static int realIndex(Slot slot) {
        // TODO(1.12.2 port): the creative-slot unwrap (E5 CreativeSlotAccessor mixin) is out of scope for this
        // cluster (mixin.client accessor not ported), so creative-screen slots report the wrapper index. The
        // survival inventory and chests are unaffected.
        return io.github.marcsanzdev.chestseparators.util.SlotIndex.of(slot);
    }

    /**
     * The working-map key for a slot. Player-inventory slots are offset into their own namespace so
     * chest and inventory data (which share raw slot indices) coexist while both are edited at once.
     */
    public static int slotKey(Slot slot) {
        return slot.inventory instanceof net.minecraft.entity.player.InventoryPlayer
                ? realIndex(slot) + ChestConfigManager.PLAYER_KEY_OFFSET
                : realIndex(slot);
    }

    /** True if a slot belongs to the player inventory namespace (used for the mutual-exclusion dimming). */
    public static boolean isPlayerSlot(Slot slot) {
        return slot.inventory instanceof net.minecraft.entity.player.InventoryPlayer;
    }

    /** Screens the editor treats as the player-inventory context: survival inventory and creative. */
    private static boolean isInventoryContextScreen(GuiContainer s) {
        return s instanceof GuiInventory
                || s instanceof net.minecraft.client.gui.inventory.GuiContainerCreative;
    }

    // While the editor is active, the creative screen's own tab switching is blocked (the tab bar must not
    // react to clicks). This flag lets the mod's OWN switch below through that block.
    public static boolean allowCreativeTabSwitch = false;

    /**
     * When a toolbar button is used on the creative screen while it is NOT on the inventory tab, jump to
     * that tab so the player's real inventory is present to interact with. No-op otherwise.
     */
    public void ensureCreativeInventoryTab() {
        if (this.screen instanceof net.minecraft.client.gui.inventory.GuiContainerCreative) {
            // Jump to the survival-inventory tab (the only one that shows the player's real inventory slots to
            // edit). The tab-switch block mixin normally cancels this while editing, so flag it as the mod's
            // own switch. Then rebuild the layout, since the tab change swaps the container's slot set.
            allowCreativeTabSwitch = true;
            try {
                ((io.github.marcsanzdev.chestseparators.mixin.client.CreativeInventoryScreenAccessor) this.screen)
                        .chestseparators$setSelectedTab(net.minecraft.creativetab.CreativeTabs.INVENTORY);
            } finally {
                allowCreativeTabSwitch = false;
            }
            this.layout.update(this.screen, this.accessor, getSidebarYOffset());
        }
    }

    /** Finds the slot for a working-map key (the inverse of {@link #slotKey}); null if none matches. */
    public Slot slotForKey(int key) {
        for (Slot s : accessor.getHandler().inventorySlots) {
            if (slotKey(s) == key) return s;
        }
        return null;
    }

    /**
     * The slots whose on-screen 16×16 box intersects the rectangle dragged from {@code start} to
     * {@code current}, restricted to the namespace (chest vs. player inventory) that {@code start} belongs
     * to. Uses real slot positions ({@code slot.x}/{@code slot.y}), NOT raw indices: the hotbar is drawn
     * BELOW the inventory rows even though its indices (0-8) come first, so {@code index / 9} row math
     * wrongly treats the hotbar as the top row and over-selects the intervening rows when a drag crosses
     * between them. Visual-box intersection selects exactly the cells the cursor swept over.
     */
    public java.util.List<Slot> slotsInDragBox(Slot start, Slot current) {
        java.util.List<Slot> out = new java.util.ArrayList<>();
        if (start == null || current == null) return out;
        boolean playerNs = isPlayerSlot(start);

        // Every slot the rectangle touches is selected, armor and offhand included: they are ordinary
        // targets for a filter, and skipping them meant a drag reaching one simply did nothing there.
        // Membership is decided purely by whether the slot's cell overlaps the swept rectangle.
        int minX = Math.min(start.xPos, current.xPos);
        int maxX = Math.max(start.xPos, current.xPos) + 16;
        int minY = Math.min(start.yPos, current.yPos);
        int maxY = Math.max(start.yPos, current.yPos) + 16;
        for (Slot s : accessor.getHandler().inventorySlots) {
            if (!isEditableSlot(s)) continue;
            if (isPlayerSlot(s) != playerNs) continue;
            if (s.xPos < maxX && s.xPos + 16 > minX && s.yPos < maxY && s.yPos + 16 > minY) out.add(s);
        }
        return out;
    }

    /**
     * Armor slots in the filter under edit that restrict which items they accept. Normal, hotbar and
     * offhand slots accept anything, so they never appear here. Recomputed each time the filter editor
     * opens; used to limit the item picker to equippable items for armor filters.
     */
    private final java.util.List<Slot> filterConstraintSlots = new java.util.ArrayList<>();

    /** True when at least one selected slot accepts anything (so the filter has no item restriction). */
    private boolean filterHasUnrestrictedSlot = true;

    /** Detects which of the filter's selected slots are restrictive (reject a non-equippable sentinel). */
    public void recomputeFilterConstraints() {
        filterConstraintSlots.clear();
        filterHasUnrestrictedSlot = false;
        ItemStack sentinel = new ItemStack(net.minecraft.init.Blocks.STONE);
        // Measure each slot's VANILLA restriction (armor checks), not our own whitelist enforcement —
        // otherwise, when editing an existing filter, its saved whitelist blocks the sentinel and the
        // slot is wrongly flagged as restricted, hiding all game items from the picker.
        io.github.marcsanzdev.chestseparators.util.ClickTracker.BYPASS_ENFORCEMENT.set(true);
        try {
            for (int key : session.selectedSlots) {
                Slot s = slotForKey(key);
                if (s == null) continue;
                if (s.isItemValid(sentinel)) filterHasUnrestrictedSlot = true;
                else filterConstraintSlots.add(s);
            }
        } finally {
            io.github.marcsanzdev.chestseparators.util.ClickTracker.BYPASS_ENFORCEMENT.set(false);
        }
        if (filterConstraintSlots.isEmpty()) filterHasUnrestrictedSlot = true;
    }

    /**
     * Whether an item may be added to the filter currently being edited. Always true unless the filter
     * is made up only of armor slots, in which case the item must be equippable in at least one of them
     * (so selecting all four armor slots accepts any armor piece, not none).
     */
    public boolean isItemAllowedForFilter(Item item) {
        if (filterHasUnrestrictedSlot) return true;
        ItemStack stack = new ItemStack(item);
        io.github.marcsanzdev.chestseparators.util.ClickTracker.BYPASS_ENFORCEMENT.set(true);
        try {
            for (Slot s : filterConstraintSlots) {
                if (s.isItemValid(stack)) return true;
            }
        } finally {
            io.github.marcsanzdev.chestseparators.util.ClickTracker.BYPASS_ENFORCEMENT.set(false);
        }
        return false;
    }

    public void releaseLock() {
        if (hasEditorLock && session.currentChestPos != null) {
            io.github.marcsanzdev.chestseparators.network.ModNet.sendToServer(
                    new io.github.marcsanzdev.chestseparators.network.EditorLockRequestPayload(
                            session.currentChestPos, false));
            hasEditorLock = false;
        }
    }

    public void toggleState(EditorState targetState) {
        EditorState previousState = session.currentState;

        if (session.currentState == targetState) {
            session.currentState = EditorState.HIDDEN;
        } else {
            session.currentState = targetState;
        }

        // Release the editor lock when transitioning away from any filter-editing state.
        boolean wasFilterState = (previousState == EditorState.VIEW_GROUPS
                || previousState == EditorState.SELECT_SLOTS
                || previousState == EditorState.EDIT_FILTER);
        boolean isFilterState = (session.currentState == EditorState.VIEW_GROUPS
                || session.currentState == EditorState.SELECT_SLOTS
                || session.currentState == EditorState.EDIT_FILTER);

        if (wasFilterState && !isFilterState) {
            releaseLock();
        }

        if (session.currentState == EditorState.DRAW_LINES || session.currentState == EditorState.HIDDEN) {
            this.session.selectedSlots.clear();
        }

        if (session.currentState == EditorState.HIDDEN) {
            GlobalChestConfig.saveConfig();
            session.editingLineCustomIndex = -1;
            session.editingBgCustomIndex = -1;
        }
    }

    public void startCreatingGroup(Slot initialSlot) {
        toggleState(EditorState.SELECT_SLOTS);
        session.selectedGroupId = UUID.randomUUID();
        session.selectedSlots.clear();

        if (initialSlot != null) {
            int key = slotKey(initialSlot);
            session.selectedSlots.add(key);
            session.lastClickedSlotIndex = key;
            session.lastSlotClickTime = System.currentTimeMillis();
            session.isSelecting = true;
        }
        playClickSound(1.0f);
    }

    public void prepareFilterMenu() {
        session.currentAllowedItems.clear();
        // The third rule is context-dependent: "Hopper" for a chest filter, "Pick Up" for an inventory
        // filter (they share the ruleHopper flag). Seed its default from the matching config option so an
        // inventory group defaults to the Pick Up default and a chest group to the Hopper default.
        boolean invFilter = false;
        for (int key : session.selectedSlots) {
            if (io.github.marcsanzdev.chestseparators.data.ChestConfigManager.isInventoryKey(key)) {
                invFilter = true;
                break;
            }
        }
        session.ruleManual = GlobalChestConfig.instance.defaultRuleManual;
        session.ruleShift = GlobalChestConfig.instance.defaultRuleShift;
        session.ruleHopper = invFilter
                ? GlobalChestConfig.instance.defaultRulePickup
                : GlobalChestConfig.instance.defaultRuleHopper;
        session.gridScrollY = 0f;
        session.listScrollY = 0f;

        if (session.selectedGroupId != null) {
            Map<Integer, SlotWhitelist> whitelists = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                    .getCurrentWhitelists();
            boolean isExistingGroup = false;
            if (whitelists != null) {
                isExistingGroup =
                        whitelists.values().stream().anyMatch(wl -> session.selectedGroupId.equals(wl.groupId()));
            }

            if (isExistingGroup) {
                session.selectedSlots.clear();
                boolean loadedRules = false;

                for (java.util.Map.Entry<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> entry :
                        whitelists.entrySet()) {
                    if (session.selectedGroupId.equals(entry.getValue().groupId())) {
                        session.selectedSlots.add(entry.getKey());

                        if (!loadedRules) {
                            session.currentAllowedItems.addAll(entry.getValue().allowedItems());
                            session.ruleManual = entry.getValue().allowManual();
                            session.ruleShift = entry.getValue().allowShift();
                            session.ruleHopper = entry.getValue().allowHopper();
                            loadedRules = true;
                        }
                    }
                }
            }
        }
        recomputeFilterConstraints();
        resetToDefaultCreativeTab();
        updateWhitelistSearchCache();

        // Rebuild the filter screen's widgets now that the container type is fully known. The initial
        // build happens during the editor's init(), BEFORE loadConfigForCurrentTarget() determines
        // isEnderChest — so without this the Ender Chest would keep its Hopper Insert button (which must be
        // dropped) and the rule buttons would not be re-centred.
        this.screenEditFilter.init();
    }

    public void saveCurrentGroup() {
        Map<Integer, SlotWhitelist> whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
        if (whitelists == null) whitelists = new java.util.HashMap<>();

        for (int slotIndex : session.selectedSlots) {
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl =
                    new io.github.marcsanzdev.chestseparators.data.SlotWhitelist(
                            session.selectedGroupId,
                            new ArrayList<>(session.currentAllowedItems),
                            session.ruleManual,
                            session.ruleShift,
                            session.ruleHopper,
                            0);
            whitelists.put(slotIndex, wl);
        }
        ChestConfigManager.getInstance().setCurrentWhitelists(whitelists);

        saveSmart();
        syncClientInventoryWhitelists(whitelists);
        // sendWhitelistToServer() self-guards: it forwards block chests/shulkers and chest minecarts, and
        // skips ender chests, the player inventory, and other entity containers (all local-only).
        sendWhitelistToServer();
    }

    public void deleteCurrentGroup() {
        if (session.selectedGroupId == null) return;
        Map<Integer, SlotWhitelist> whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
        if (whitelists != null) {
            whitelists
                    .entrySet()
                    .removeIf(entry ->
                            session.selectedGroupId.equals(entry.getValue().groupId()));
            ChestConfigManager.getInstance().setCurrentWhitelists(whitelists);

            saveSmart();
            syncClientInventoryWhitelists(whitelists);
            sendWhitelistToServer();
        }
    }

    /**
     * Pushes the container's own filters to the server. Self-guarding: it only sends for targets the
     * server can actually enforce and persist — real block chests/shulkers, and chest/hopper minecarts
     * (synced by entity UUID). Ender chests, the player inventory, and other entity containers (chest
     * boats, animals) stay local-only and are skipped here.
     */
    public void sendWhitelistToServer() {
        if (session.isEnderChest || session.isPlayerInventory) return;

        Map<Integer, SlotWhitelist> chestOnly = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                .chestOnlyWhitelists();

        if (session.isMinecartChest && session.currentEntityUUID != null) {
            io.github.marcsanzdev.chestseparators.network.ModNet.sendToServer(
                    new io.github.marcsanzdev.chestseparators.network.EntityWhitelistPayload(
                            session.currentEntityUUID, chestOnly));
            return;
        }

        // Any other entity container (chest boat, animals) is local-only.
        if (session.isEntityChest) return;

        BlockPos posToSend = session.currentChestPos != null ? session.currentChestPos : BlockPos.ORIGIN;
        io.github.marcsanzdev.chestseparators.network.ModNet.sendToServer(
                new io.github.marcsanzdev.chestseparators.network.WhitelistPayload(
                        posToSend,
                        // Only the chest's own filters; inventory filters (offset keys) are local.
                        chestOnly));
    }

    public void playClickSound(float pitch) {
        playUiSound(SoundEvents.UI_BUTTON_CLICK, pitch);
    }

    /** Plays any UI sound event on the master track at the given pitch (non-positional, like the click). */
    public void playUiSound(net.minecraft.util.SoundEvent sound, float pitch) {
        Minecraft.getMinecraft()
                .getSoundHandler()
                .playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(sound, pitch));
    }

    /**
     * The single "a panel was dismissed" sound. Every screen that can be closed by clicking outside it (or on
     * dead space) must play THIS, so exiting layout, filters, the item picker, presets and the palette all
     * sound identical instead of each using its own pitch.
     */
    public void playCloseSound() {
        playClickSound(1.0f);
    }

    public void saveSmart() {
        ChestConfigManager manager = ChestConfigManager.getInstance();

        // Container portion (chest screens only) — chest-only keys are saved to the container's store.
        if (!session.isInventoryScreenContext) {
            if (session.isShulkerBox && session.currentShulkerUUID != null) {
                manager.saveShulkerConfig(session.currentShulkerUUID);
            } else if (session.isEntityChest && session.currentEntityUUID != null) {
                manager.saveEntityConfig(session.currentEntityUUID);
            } else if (session.isEnderChest) {
                manager.saveEnderConfig();
            } else if (session.currentChestPos != null) {
                manager.saveConfig(session.currentChestPos, session.currentDimension);
            }
        }

        // Player inventory portion (always) — extracted from the offset keys and synced for Pick Up.
        manager.saveInventoryFromCurrent();
        io.github.marcsanzdev.chestseparators.network.ModClientNetworking.sendInventoryFilters();
    }

    /** Asks the server to fill the inventory from the container currently open (the top fill icon). */
    /**
     * Pulls items from the open container into the inventory. Normally only items the inventory filters
     * want (up to each target). With {@code includeEmpty} (Shift), also pulls the rest of the container's
     * items into any free inventory space — the slots that have no filter.
     */
    public void requestFillFromOpenChest(boolean includeEmpty) {
        playClickSound(1.0f);
        net.minecraft.util.math.BlockPos pos = session.currentChestPos != null
                ? session.currentChestPos
                : (Minecraft.getMinecraft().player != null
                        ? Minecraft.getMinecraft().player.getPosition()
                        : net.minecraft.util.math.BlockPos.ORIGIN);
        boolean lockHotbar = io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance == null
                || io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.lockHotbarOnReorder;
        io.github.marcsanzdev.chestseparators.network.ModNet.sendToServer(
                new io.github.marcsanzdev.chestseparators.network.FillFromChestPayload(
                        pos, includeEmpty, lockHotbar));
    }

    /** Loads inventory preset {@code index} (1-based) into the live editor, or reports it is empty. */
    public void loadInventoryPresetSlot(int index) {
        ChestConfigManager manager = ChestConfigManager.getInstance();
        if (!manager.loadInventoryPreset(index)) {
            showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.preset_empty", index), TextFormatting.RED);
            playClickSound(0.6f);
            return;
        }
        saveSmart();
        syncClientInventoryWhitelists(manager.getCurrentWhitelists());
        showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.preset_loaded", index), TextFormatting.GREEN);
        playClickSound(1.1f);
    }

    /** Saves the current inventory layout + filters into preset {@code index} (1-based). */
    public void saveInventoryPresetSlot(int index) {
        ChestConfigManager.getInstance().saveInventoryPreset(index);
        showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.preset_saved", index), TextFormatting.GREEN);
        playClickSound(1.2f);
    }

    /** The open container's slot count (27 vs 54), used to pick the matching chest-preset set. */
    public int chestPresetSize() {
        return geometry.getNamespaceSlotCount(false);
    }

    /** Loads chest preset {@code index} (1-based) of the open container's size, or reports it is empty. */
    public void loadChestPresetSlot(int index) {
        ChestConfigManager manager = ChestConfigManager.getInstance();
        if (!manager.loadChestPreset(chestPresetSize(), index)) {
            showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.preset_empty", index), TextFormatting.RED);
            playClickSound(0.6f);
            return;
        }
        saveSmart();
        sendWhitelistToServer();
        syncClientInventoryWhitelists(manager.getCurrentWhitelists());
        showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.preset_loaded", index), TextFormatting.GREEN);
        playClickSound(1.1f);
    }

    /** Saves the open chest's current layout + filters into its size's chest preset {@code index}. */
    public void saveChestPresetSlot(int index) {
        ChestConfigManager.getInstance().saveChestPreset(chestPresetSize(), index);
        showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.preset_saved", index), TextFormatting.GREEN);
        playClickSound(1.2f);
    }

    /** Deletes inventory preset {@code index} (1-based). */
    public void deleteInventoryPresetSlot(int index) {
        ChestConfigManager.getInstance().deleteInventoryPreset(index);
        showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.preset_deleted", index), TextFormatting.RED);
        playClickSound(0.8f);
    }

    /** Deletes chest preset {@code index} (1-based) of the open container's size. */
    public void deleteChestPresetSlot(int index) {
        ChestConfigManager.getInstance().deleteChestPreset(chestPresetSize(), index);
        showStatus(new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.preset_deleted", index), TextFormatting.RED);
        playClickSound(0.8f);
    }

    public void triggerActionAnimation(int actionId) {
        session.clickedActionId = actionId;
        session.clickedActionTime = System.currentTimeMillis();
    }

    /**
     * Finalizes a unified undo/redo: persists the restored state, re-syncs filters to the server and
     * the local inventory (the shared history can change filters too), and shows feedback naming what
     * was undone/redone. {@code labelKey} comes from {@link ChestConfigManager#undo()}/redo(); a null
     * label means there was nothing to do.
     */
    public void applyUndoRedo(String labelKey, boolean isRedo) {
        if (labelKey == null) {
            playClickSound(0.5f);
            return;
        }
        saveSmart();
        sendWhitelistToServer();
        syncClientInventoryWhitelists(ChestConfigManager.getInstance().getCurrentWhitelists());

        // Blink the slots that changed, colored by the kind of change.
        undoHighlights = new HashMap<>(ChestConfigManager.getInstance().getLastUndoChanges());
        undoHighlightStart = System.currentTimeMillis();

        String feedbackKey = isRedo ? "message.chestseparators.redone" : "message.chestseparators.undone";
        showStatus(new net.minecraft.util.text.TextComponentTranslation(feedbackKey, new net.minecraft.util.text.TextComponentTranslation(labelKey)), TextFormatting.GRAY);
        playClickSound(0.8f);
    }

    public void showStatus(ITextComponent message, TextFormatting color) {
        ITextComponent copy = message.createCopy();
        copy.getStyle().setColor(color);
        session.statusMessage = copy;
        session.statusMessageTime = System.currentTimeMillis();
        session.statusMessageColor = -1; // keep the §-code named colour
    }

    /** Shows a status message tinted with an exact ARGB/RGB color (used for color copy/paste feedback). */
    public void showStatus(ITextComponent message, int rgbColor) {
        // E1 §-codes only carry the 16 named colours, so the arbitrary RGB can't live on the text component;
        // stash it and let renderStatusMessage draw the (unstyled) message with this exact colour instead.
        session.statusMessage = message.createCopy();
        session.statusMessageTime = System.currentTimeMillis();
        session.statusMessageColor = rgbColor & 0xFFFFFF;
    }

    public boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Whether either Shift key is held right now (polled from the keyboard at call time). */
    private static boolean isShiftDown() {
        return org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT);
    }

    public int shiftColor(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = MathHelper.clamp(r + amount, 0, 255);
        g = MathHelper.clamp(g + amount, 0, 255);
        b = MathHelper.clamp(b + amount, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void openColorPicker(int initialColor, int targetMode) {
        session.isColorPickerOpen = true;
        session.pickerTargetMode = targetMode;
        this.colorPickerModified = false;

        // Default cursors to the top-left of the picker (pure red, full saturation, full brightness).
        session.pickerHue = 0.0f;
        session.pickerSat = 1.0f;
        session.pickerVal = 1.0f;

        if (initialColor == 0) {
            // Keep RGB at 0 to show the checkerboard transparency pattern for an empty slot.
            session.pickerCurrentRGB = 0;
        } else {
            // Convert the existing color to HSB so the cursors open at the correct position.
            float[] hsb = new float[3];
            int r = (initialColor >> 16) & 0xFF;
            int g = (initialColor >> 8) & 0xFF;
            int b = initialColor & 0xFF;
            java.awt.Color.RGBtoHSB(r, g, b, hsb);
            session.pickerHue = hsb[0];
            session.pickerSat = hsb[1];
            session.pickerVal = hsb[2];
            session.pickerCurrentRGB = initialColor | 0xFF000000;
        }

        if (this.screenColorPicker != null) {
            this.screenColorPicker.onOpen();
        }
    }

    public void updateTracePath(String step) {
        if (step == null || step.isEmpty()) return;
        if (session.tracePath.isEmpty()) {
            session.tracePath.add(step);
            return;
        }
        if (session.tracePath.get(session.tracePath.size() - 1).equals(step)) {
            return;
        }

        int index = session.tracePath.indexOf(step);
        if (index != -1) {
            session.tracePath.subList(index + 1, session.tracePath.size()).clear();
        } else {
            session.tracePath.add(step);
        }
    }

    public void renderNormalModeOverlay(GuiGraphics context, int mouseX, int mouseY) {
        if (this.renderer != null) this.renderer.renderNormalModeOverlay(context, mouseX, mouseY);

        // Only render the floating status message while the main editor panel is closed.
        if (session.currentState == EditorState.HIDDEN && session.statusMessage != null) {
            long elapsed = System.currentTimeMillis() - session.statusMessageTime;

            if (elapsed < 2000) {
                int alpha = 255;
                if (elapsed > 1500) {
                    alpha = (int) (255 * (1.0f - (elapsed - 1500) / 500.0f));
                }

                if (alpha > 10) {
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getMinecraft();
                    int color = (alpha << 24) | 0xFFFFFF;
                    context.drawCenteredString(
                            client.fontRenderer, session.statusMessage, screen.width / 2, screen.height - 40, color);
                }
            } else {
                session.statusMessage = null;
            }
        }
    }

    public void renderSavedLinesLayer(GuiGraphics context) {
        if (this.renderer != null) this.renderer.renderSavedLinesLayer(context);
    }

    public boolean keyPressed(io.github.marcsanzdev.chestseparators.client.input.KeyEvent input) {
        boolean isSearchFocused = (searchBox != null && searchBox.isFocused());
        boolean isWhitelistSearchFocused = (whitelistSearchBox != null && whitelistSearchBox.isFocused());

        if (isSearchFocused) {
            // 1.12.2 GuiTextField has no split keyPressed/charTyped; editing keys route through
            // textboxKeyTyped(char, keyCode). Typed characters arrive via charTyped() below.
            if (searchBox.textboxKeyTyped('\0', input.key())) {
                onSearchTextChanged();
                return true;
            }
            if (input.key() == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
                searchBox.setFocused(false);
                return true;
            }
        }
        if (session.isColorPickerOpen && screenColorPicker.keyPressed(input)) return true;
        if (this.inputHandler != null) return this.inputHandler.keyPressed(input);
        return false;
    }

    public boolean charTyped(io.github.marcsanzdev.chestseparators.client.input.CharacterEvent input) {
        // Route typed characters into a focused search box (the modern split-model charTyped path); the
        // whitelist search box is driven the same way through the input handler.
        if (searchBox != null && searchBox.isFocused()) {
            if (searchBox.textboxKeyTyped((char) input.codepoint(), 0)) {
                onSearchTextChanged();
                return true;
            }
        }
        if (whitelistSearchBox != null && whitelistSearchBox.isFocused()) {
            if (whitelistSearchBox.textboxKeyTyped((char) input.codepoint(), 0)) {
                onWhitelistSearchChanged();
                return true;
            }
        }
        if (session.isColorPickerOpen && screenColorPicker.charTyped(input)) return true;
        if (this.inputHandler != null) return this.inputHandler.charTyped(input);
        return false;
    }

    /** Runs the editor's click handling directly (used by the creative-inventory mixin to block tabs). */
    public void handleEditorClick(double mouseX, double mouseY, int button) {
        if (this.inputHandler != null) this.inputHandler.handleClick(mouseX, mouseY, button);
    }

    public List<String> extractItemsFromSelection() {
        Set<String> extracted = new LinkedHashSet<>();
        if (accessor.getHandler() != null && accessor.getHandler().inventorySlots != null) {
            // selectedSlots holds slot KEYS, not raw handler indices: player-inventory keys carry the
            // PLAYER_KEY_OFFSET, so indexing the handler directly missed every inventory slot (the offset
            // key is out of range) and the import button did nothing for inventory filters. Resolve each
            // key back to its Slot, which works for both the chest (offset 0) and the inventory.
            for (int key : session.selectedSlots) {
                Slot slot = slotForKey(key);
                if (slot == null) continue;
                ItemStack stack = slot.getStack();
                if (!stack.isEmpty()) {
                    String id = io.github.marcsanzdev.chestseparators.util.ItemKey.of(stack);
                    if (!session.currentAllowedItems.contains(id)) extracted.add(id);
                }
            }
        }
        return new ArrayList<>(extracted);
    }

    public void buildCustomChestTab() {
        session.customChestItems.clear();
        java.util.Set<Item> uniqueItems = new java.util.LinkedHashSet<>();

        if (accessor.getHandler() != null && accessor.getHandler().inventorySlots != null) {
            for (Slot slot : accessor.getHandler().inventorySlots) {
                if (!(slot.inventory instanceof net.minecraft.entity.player.InventoryPlayer) && slot.getHasStack()) {
                    uniqueItems.add(slot.getStack().getItem());
                }
            }
            for (Slot slot : accessor.getHandler().inventorySlots) {
                if (slot.inventory instanceof net.minecraft.entity.player.InventoryPlayer && slot.getHasStack()) {
                    uniqueItems.add(slot.getStack().getItem());
                }
            }
        }
        session.customChestItems.addAll(uniqueItems);
    }

    /**
     * Resets the creative-item browser to its default tab and first page whenever a filter menu is
     * opened, so the last-used tab (e.g. "Chest & Inventory") and any pending search text never carry
     * over between filters. The default is the first available tab, matching the initial open.
     */
    private void resetToDefaultCreativeTab() {
        session.currentTabPage = 0;
        for (int i = 0; i < session.availableTabs.size(); i++) {
            if (session.availableTabs.get(i) != null) {
                switchCreativeTab(i);
                return;
            }
        }
    }

    /** 1.19.2 lacks {@code ItemStack.copyWithCount(int)} (E4/1.20.1+); replicate it. */
    private static ItemStack copyWithCount(ItemStack stack, int count) {
        ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }

    /** 1.19.2 tab item enumeration: E5's getDisplayItems()/buildContents() is replaced by fillItemList(). */
    private static java.util.List<ItemStack> displayStacksOf(CreativeTabs group) {
        net.minecraft.util.NonNullList<ItemStack> list = net.minecraft.util.NonNullList.create();
        group.displayAllRelevantItems(list);
        return list;
    }

    public void switchCreativeTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= session.availableTabs.size()) return;
        session.currentCreativeTabIndex = tabIndex;
        session.gridScrollY = 0f;
        session.isPreviewing = false;

        EditorSessionData.CreativeTabInfo activeTab = session.availableTabs.get(tabIndex);
        if (activeTab == null) return;

        if (!activeTab.isSearchTab && this.searchBox != null) this.searchBox.setText("");
        session.filteredItems.clear();

        if (activeTab.isCustomChestTab) {
            buildCustomChestTab();
            for (Item item : session.customChestItems) {
                if (isItemAllowedForFilter(item)) session.filteredItems.add(item);
            }
        } else if (activeTab.isSearchTab) {
            boolean isSearching =
                    this.searchBox != null && !this.searchBox.getText().isEmpty();
            String lowerQuery = isSearching ? this.searchBox.getText().toLowerCase() : "";

            for (EditorSessionData.CreativeTabInfo tab : session.availableTabs) {
                if (tab == null || tab.isSearchTab || tab.isCustomChestTab || tab.group == null) continue;
                java.util.List<ItemStack> displayStacks = displayStacksOf(tab.group);

                if (displayStacks != null) {
                    for (ItemStack stack : displayStacks) {
                        Item item = stack.getItem();
                        if (!session.filteredItems.contains(item)
                                && isItemAllowedInMenu(item)
                                && isItemAllowedForFilter(item)) {
                            if (isSearching) {
                                boolean matches = false;
                                if (lowerQuery.startsWith("#")) {
                                    // TODO(1.12.2 port): E1 has no item-tag system (net.minecraft.tags); the
                                    // "#tag" search cannot resolve tags, so tag queries match nothing here.
                                    matches = false;
                                } else {
                                    matches = new ItemStack(item)
                                            .getDisplayName()
                                            .toLowerCase()
                                            .contains(lowerQuery);
                                }
                                if (matches) session.filteredItems.add(item);
                            } else {
                                session.filteredItems.add(item);
                            }
                        }
                    }
                }
            }
        } else if (activeTab.group != null) {
            java.util.List<ItemStack> displayStacks = displayStacksOf(activeTab.group);

            if (displayStacks != null && !displayStacks.isEmpty()) {
                for (ItemStack stack : displayStacks) {
                    Item item = stack.getItem();
                    if (!session.filteredItems.contains(item)
                            && isItemAllowedInMenu(item)
                            && isItemAllowedForFilter(item)) {
                        session.filteredItems.add(item);
                    }
                }
            }
        }
    }

    public boolean isItemAllowedInMenu(Item item) {
        if (item instanceof net.minecraft.item.ItemMonsterPlacer) return false;
        net.minecraft.util.ResourceLocation id = item.getRegistryName();
        if (id == null) return false;

        String path = id.getResourcePath();
        if (path.contains("infested")) return false;
        if (path.contains("command_block")) return false;

        java.util.Set<String> blacklist = new java.util.HashSet<>(java.util.Arrays.asList(
                "air",
                "bedrock",
                "spawner",
                "reinforced_deepslate",
                "end_portal_frame",
                "knowledge_book",
                "trial_spawner",
                "vault",
                "barrier",
                "light",
                "debug_stick",
                "structure_block",
                "structure_void",
                "jigsaw",
                "test_block",
                "test_instance_block",
                "player_head",
                "farmland",
                "suspicious_sand",
                "suspicious_gravel",
                "budding_amethyst",
                "dirt_path",
                "chorus_plant",
                "frogspawn"));

        return !blacklist.contains(path);
    }

    public void updateWhitelistSearchCache() {
        session.visibleLeftListItems.clear();
        String wlSearch = this.whitelistSearchBox != null
                ? this.whitelistSearchBox.getText().toLowerCase()
                : "";

        for (String id : session.currentAllowedItems) {
            Item item = io.github.marcsanzdev.chestseparators.util.ItemKey.item(id);
            if (item != null) {
                boolean matches = false;
                if (wlSearch.isEmpty()) {
                    matches = true;
                } else if (wlSearch.startsWith("#")) {
                    // TODO(1.12.2 port): E1 has no item-tag system (net.minecraft.tags); "#tag" whitelist
                    // filtering cannot resolve tags, so tag queries match nothing here.
                    matches = false;
                } else {
                    matches = io.github.marcsanzdev.chestseparators.util.ItemKey.stack(id)
                            .getDisplayName()
                            .toLowerCase()
                            .contains(wlSearch);
                }

                if (matches) session.visibleLeftListItems.add(id);
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isEditMode()) {
            return screenViewGroups.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        } else {
            if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
                return screenViewGroups.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            } else if (session.currentState == EditorState.EDIT_FILTER) {
                return screenEditFilter.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
        }
        return false;
    }

    public void syncClientInventoryWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        if (Minecraft.getMinecraft().player == null) return;
        Container handler = Minecraft.getMinecraft().player.openContainer;
        if (handler == null || handler.inventorySlots.isEmpty()) return;

        // Iterate all non-player slots and push whitelists to every unique IWhitelistProvider found.
        // This handles chest minecarts (entity IS the inventory), chest boats (same), and
        // donkeys/llamas (cargo SimpleInventory is a separate instance from the saddle slot inventory).
        java.util.Set<IWhitelistProvider> visited = new java.util.HashSet<>();
        for (Slot slot : handler.inventorySlots) {
            if (slot.inventory instanceof net.minecraft.entity.player.InventoryPlayer) continue;
            if (slot.inventory instanceof IWhitelistProvider) {
                IWhitelistProvider provider = (IWhitelistProvider) slot.inventory;
                if (visited.add(provider)) {
                    provider.setWhitelists(whitelists);
                }
            }
        }
    }

    public void executeDeposit(boolean shift) {
        if (accessor.getHandler() == null || net.minecraft.client.Minecraft.getMinecraft().player == null) return;
        if (!net.minecraft.client.Minecraft.getMinecraft().player.inventory.getItemStack().isEmpty()) return;

        Map<Integer, SlotWhitelist> whitelists = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                .getCurrentWhitelists();
        net.minecraft.client.multiplayer.PlayerControllerMP interactionManager =
                net.minecraft.client.Minecraft.getMinecraft().playerController;
        int syncId = accessor.getHandler().windowId;
        net.minecraft.entity.player.EntityPlayer player = net.minecraft.client.Minecraft.getMinecraft().player;

        int totalMovedCount = 0;

        for (net.minecraft.inventory.Slot playerSlot : accessor.getHandler().inventorySlots) {
            if (playerSlot.inventory instanceof net.minecraft.entity.player.InventoryPlayer && playerSlot.getHasStack()) {
                net.minecraft.item.ItemStack stackToMove = playerSlot.getStack().copy();
                String itemId = stackToMove.getItem().getRegistryName().toString();
                int remainingOnCursor = stackToMove.getCount();

                // Build the target slot list using the 4-pass priority system.
                List<net.minecraft.inventory.Slot> prioritizedSlots = new ArrayList<>();

                // Pass 1: Filtered slots with a matching existing stack (stacking).
                collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, true, false);
                // Pass 2: Empty filtered slots (filling).
                collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, false, false);

                if (shift) {
                    // Pass 3: Unfiltered slots with a matching existing stack (stacking).
                    collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, true, true);
                    // Pass 4: Empty unfiltered slots (filling).
                    collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, false, true);
                }

                if (!prioritizedSlots.isEmpty()) {
                    boolean pickedUp = false;
                    for (net.minecraft.inventory.Slot targetSlot : prioritizedSlots) {
                        if (remainingOnCursor <= 0) break;

                        int spaceLeft = stackToMove.getMaxStackSize()
                                - (targetSlot.getHasStack() ? targetSlot.getStack().getCount() : 0);
                        if (spaceLeft > 0) {
                            if (!pickedUp) {
                                interactionManager.windowClick(
                                        syncId,
                                        playerSlot.slotNumber,
                                        0,
                                        net.minecraft.inventory.ClickType.PICKUP,
                                        player);
                                pickedUp = true;
                            }
                            interactionManager.windowClick(
                                    syncId, targetSlot.slotNumber, 0, net.minecraft.inventory.ClickType.PICKUP, player);
                            int moved = Math.min(spaceLeft, remainingOnCursor);
                            remainingOnCursor -= moved;
                            totalMovedCount += moved;
                        }
                    }
                    if (pickedUp && remainingOnCursor > 0) {
                        interactionManager.windowClick(
                                syncId, playerSlot.slotNumber, 0, net.minecraft.inventory.ClickType.PICKUP, player);
                    }
                }
            }
        }

        if (totalMovedCount > 0) {
            showStatus(
                    new net.minecraft.util.text.TextComponentTranslation("message.chestseparators.deposited", totalMovedCount),
                    TextFormatting.GREEN);
            this.suspendDepositPreview = true;
            this.previewSourceRemaining.clear();
            this.previewTargetIncoming.clear();

            // Apply the server's post-deposit re-sort to the CLIENT's predicted container right now, so the
            // items show their final packed order ([64,1]) from the first frame. Without this the client
            // briefly renders its raw deposit prediction ([1,64]) and then jumps when the server's re-sort
            // syncs back — a visible flicker. Mirrors reorderFilteredGroups exactly.
            applyClientDepositReorder(whitelists);

            // Ask the server to re-sort the container's filtered groups by priority order, so a
            // just-deposited higher-priority item ends up ahead of lower-priority ones already stored
            // (the deposit above only fills slots; it never relocates existing stacks).
            io.github.marcsanzdev.chestseparators.network.ModNet.sendToServer(
                    new io.github.marcsanzdev.chestseparators.network.SortOpenFiltersPayload());
        } else {
            // Be specific: "no filters at all" is a different situation from "filters exist but none has
            // room / matches", and reads far clearer to the player. Both use the same muted grey as the
            // pull/grab empty notices so every "nothing happened" message looks the same across the mod.
            boolean anyContainerFilter = false;
            for (net.minecraft.inventory.Slot s : accessor.getHandler().inventorySlots) {
                if (!(s.inventory instanceof net.minecraft.entity.player.InventoryPlayer)
                        && whitelists != null
                        && whitelists.containsKey(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(s))) {
                    anyContainerFilter = true;
                    break;
                }
            }
            showStatus(
                    new net.minecraft.util.text.TextComponentTranslation(
                            anyContainerFilter
                                    ? "message.chestseparators.push_no_slot"
                                    : "message.chestseparators.push_no_filters"),
                    TextFormatting.GRAY);
        }
    }

    /**
     * Client-side mirror of {@code ModNetworking.reorderFilteredGroups}: packs each filtered group's stacks
     * by the filter's priority order (fuller stack first for a same-item tie) into its lowest slots, applied
     * to the open container's predicted slots so the deposit's final layout is shown with no flicker.
     */
    private void applyClientDepositReorder(
            Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists) {
        if (whitelists == null || whitelists.isEmpty()) return;

        java.util.Map<java.util.UUID, List<net.minecraft.inventory.Slot>> groups =
                new java.util.LinkedHashMap<>();
        for (net.minecraft.inventory.Slot s : accessor.getHandler().inventorySlots) {
            if (s.inventory instanceof net.minecraft.entity.player.InventoryPlayer) continue;
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl = whitelists.get(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(s));
            if (wl == null || wl.groupId() == null) continue;
            groups.computeIfAbsent(wl.groupId(), g -> new ArrayList<>()).add(s);
        }

        for (List<net.minecraft.inventory.Slot> gslots : groups.values()) {
            if (gslots.size() < 2) continue;
            gslots.sort(java.util.Comparator.comparingInt(io.github.marcsanzdev.chestseparators.util.SlotIndex::of));
            List<String> order = whitelists
                    .get(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(gslots.get(0)))
                    .allowedItems();

            List<net.minecraft.item.ItemStack> stacks = new ArrayList<>();
            for (net.minecraft.inventory.Slot s : gslots) {
                if (!s.getStack().isEmpty()) stacks.add(s.getStack());
            }
            if (stacks.isEmpty()) continue;
            stacks.sort(java.util.Comparator.<net.minecraft.item.ItemStack>comparingInt(st -> {
                        int r = io.github.marcsanzdev.chestseparators.util.ItemKey.rank(order, st);
                        return r < 0 ? Integer.MAX_VALUE : r;
                    })
                    .thenComparing(java.util.Comparator.comparingInt(
                                    net.minecraft.item.ItemStack::getCount)
                            .reversed()));

            for (int i = 0; i < gslots.size(); i++) {
                gslots.get(i)
                        .putStack(i < stacks.size() ? stacks.get(i) : net.minecraft.item.ItemStack.EMPTY);
            }
        }
    }

    private void collectTargetSlots(
            List<net.minecraft.inventory.Slot> list,
            Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists,
            String itemId,
            net.minecraft.item.ItemStack stack,
            boolean checkExisting,
            boolean unfilteredOnly) {
        List<net.minecraft.inventory.Slot> collected = new ArrayList<>();
        for (net.minecraft.inventory.Slot chestSlot : accessor.getHandler().inventorySlots) {
            if (chestSlot.inventory instanceof net.minecraft.entity.player.InventoryPlayer) continue;
            if (list.contains(chestSlot)) continue;

            boolean hasFilter = whitelists != null && whitelists.containsKey(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(chestSlot));
            boolean matchesFilter = hasFilter
                    && io.github.marcsanzdev.chestseparators.util.ItemKey.matches(
                            whitelists.get(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(chestSlot)).allowedItems(),
                            stack);

            if (unfilteredOnly) {
                if (hasFilter) continue;
            } else {
                if (!matchesFilter) continue;
            }

            if (checkExisting) {
                if (!chestSlot.getHasStack()) continue;
                if (!net.minecraft.item.ItemStack.areItemsEqual(stack, chestSlot.getStack())
                        || !io.github.marcsanzdev.chestseparators.util.ItemMatch.sameItemSameTags(stack, chestSlot.getStack()))
                    continue;
                if (chestSlot.getStack().getCount() >= chestSlot.getStack().getMaxStackSize()) continue;
            } else {
                if (chestSlot.getHasStack()) continue;
            }
            collected.add(chestSlot);
        }

        // Empty filtered slots are filled in the filter's own order: the item first in the list heads for
        // the group's first slot, and so on. Ties (and every other pass) keep the handler's slot order.
        if (!checkExisting && !unfilteredOnly) {
            collected.sort(java.util.Comparator.comparingInt(
                    s -> io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(
                            whitelists, io.github.marcsanzdev.chestseparators.util.SlotIndex.of(s), stack)));
        }
        list.addAll(collected);
    }

    /**
     * Client-side simulation of the Pull button for its hover preview: marks each container slot whose
     * item would be pulled into the inventory (via the inventory filters' keep-limits, or — with Shift —
     * any unfiltered item), storing the amount that would remain afterwards in {@link #previewSourceRemaining}.
     * Mirrors {@code performFillFromOpenContainer} on the server.
     */
    public void updateFillPreview(boolean shift) {
        previewSourceRemaining.clear();
        previewTargetIncoming.clear();
        if (accessor.getHandler() == null || net.minecraft.client.Minecraft.getMinecraft().player == null) return;

        java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> invFilters =
                ChestConfigManager.getInstance().getPlayerInventoryFilters();

        // Player inventory slots, sorted by inventory index (0-8 hotbar, then 9-35 main) — NOT the menu's
        // slot order, which lists the main inventory before the hotbar. The real pull fills via vanilla
        // Inventory#getFreeSlot, which scans items[0..35] (hotbar first), so the preview must simulate the
        // same order or the ghost lands in the wrong slot (e.g. main row 1 instead of the hotbar).
        java.util.List<net.minecraft.inventory.Slot> playerSlots = new java.util.ArrayList<>();
        for (net.minecraft.inventory.Slot s : accessor.getHandler().inventorySlots) {
            if (s.inventory instanceof net.minecraft.entity.player.InventoryPlayer) playerSlots.add(s);
        }
        playerSlots.sort(java.util.Comparator.comparingInt(io.github.marcsanzdev.chestseparators.util.SlotIndex::of));

        for (net.minecraft.inventory.Slot chestSlot : accessor.getHandler().inventorySlots) {
            if (chestSlot.inventory instanceof net.minecraft.entity.player.InventoryPlayer || !chestSlot.getHasStack())
                continue;
            net.minecraft.item.ItemStack stack = chestSlot.getStack();

            // Mirrors performFillFromOpenContainer: pull items the inventory filters list; with Shift, pull
            // everything into free space.
            if (!shift && !inventoryListsItemClient(invFilters, stack)) continue;

            int placed = placeIntoPlayerPreview(playerSlots, invFilters, stack, stack.getCount(), shift);
            if (placed <= 0) continue; // inventory full — nothing actually moves from this slot
            previewSourceRemaining.put(chestSlot.slotNumber, stack.getCount() - placed);
        }

        // Mirror the server's post-pull inventory group re-sort (honouring the hotbar lock) so the preview
        // shows the same final layout the real pull will produce.
        boolean lockHotbar = io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance == null
                || io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.lockHotbarOnReorder;
        int firstSortable = lockHotbar ? 9 : 0;
        java.util.List<net.minecraft.inventory.Slot> mainSlots = new java.util.ArrayList<>();
        for (net.minecraft.inventory.Slot s : playerSlots) {
            int idx = realIndex(s);
            if (idx >= firstSortable && idx < 36) {
                mainSlots.add(s);
            }
        }
        applyPreviewReorder(mainSlots, invFilters, ChestSeparatorsEditor::realIndex);
    }

    /**
     * Simulates PlayerInventory#insertStack (with the mod's inventory-filter routing) for the pull
     * preview: tops up matching partial stacks first, then places into empty slots honouring the filters —
     * an item prefers an empty slot whose active filter lists it, and never lands in a slot reserved for a
     * different item — exactly as {@code PlayerInventoryFilterMixin#getEmptySlot} does at pull time.
     * Accumulates the projected incoming amount per player slot in {@link #previewTargetIncoming}.
     * Returns how many of {@code amount} actually fit.
     */
    private int placeIntoPlayerPreview(
            java.util.List<net.minecraft.inventory.Slot> playerSlots,
            java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> invFilters,
            net.minecraft.item.ItemStack stack,
            int amount,
            boolean shift) {
        int maxC = stack.getMaxStackSize();
        int left = amount;

        // Pass 1: top up existing matching stacks (real contents or already-projected incoming). Without
        // Shift this is restricted to slots whose filter lists the item (mirrors insertRespectingFilter);
        // with Shift it tops up any matching stack (mirrors vanilla addItemStackToInventory).
        for (net.minecraft.inventory.Slot ps : playerSlots) {
            if (left <= 0) break;
            if (!shift) {
                io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl = invFilters != null
                        ? invFilters.get(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(ps))
                        : null;
                if (wl == null
                        || !io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack))
                    continue;
            }
            net.minecraft.item.ItemStack real = ps.getStack();
            net.minecraft.item.ItemStack proj = previewTargetIncoming.get(ps.slotNumber);
            int current;
            if (!real.isEmpty()) {
                if (!io.github.marcsanzdev.chestseparators.util.ItemMatch.sameItemSameTags(stack, real)) continue;
                current = real.getCount() + (proj != null ? proj.getCount() : 0);
            } else if (proj != null) {
                if (!io.github.marcsanzdev.chestseparators.util.ItemMatch.sameItemSameTags(stack, proj)) continue;
                current = proj.getCount();
            } else {
                continue; // empty slot handled below
            }
            int canAdd = maxC - current;
            if (canAdd <= 0) continue;
            int add = Math.min(left, canAdd);
            if (proj == null) previewTargetIncoming.put(ps.slotNumber, copyWithCount(stack, add));
            else proj.setCount(proj.getCount() + add);
            left -= add;
        }

        if (!shift) {
            // Pass 2 (no Shift): drop the remainder ONLY into empty filtered slots, in filter priority order —
            // never overflow into unfiltered slots. Mirrors insertRespectingFilter phase 2 exactly.
            java.util.List<net.minecraft.inventory.Slot> emptyFiltered = new java.util.ArrayList<>();
            for (net.minecraft.inventory.Slot ps : playerSlots) {
                if (!ps.getStack().isEmpty() || previewTargetIncoming.containsKey(ps.slotNumber)) continue;
                io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl = invFilters != null
                        ? invFilters.get(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(ps))
                        : null;
                if (wl == null
                        || !io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack))
                    continue;
                emptyFiltered.add(ps);
            }
            emptyFiltered.sort(java.util.Comparator.comparingInt(
                            (net.minecraft.inventory.Slot ps) ->
                                    io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(
                                            invFilters,
                                            io.github.marcsanzdev.chestseparators.util.SlotIndex.of(ps),
                                            stack))
                    .thenComparingInt(io.github.marcsanzdev.chestseparators.util.SlotIndex::of));
            for (net.minecraft.inventory.Slot ps : emptyFiltered) {
                if (left <= 0) break;
                int add = Math.min(left, maxC);
                previewTargetIncoming.put(ps.slotNumber, copyWithCount(stack, add));
                left -= add;
            }
        } else {
            // Pass 2 (Shift): empty slots one at a time via the filter-aware getEmptySlot equivalent, which
            // falls back to any unreserved empty slot (mirrors the vanilla spill of addItemStackToInventory).
            while (left > 0) {
                net.minecraft.inventory.Slot target = findPreviewEmptySlot(playerSlots, invFilters, stack);
                if (target == null) break;
                int add = Math.min(left, maxC);
                previewTargetIncoming.put(target.slotNumber, copyWithCount(stack, add));
                left -= add;
            }
        }
        return amount - left;
    }

    /** Mirror of PlayerInventoryFilterMixin#getEmptySlot: preferred filtered empty slot, else first unreserved. */
    private net.minecraft.inventory.Slot findPreviewEmptySlot(
            java.util.List<net.minecraft.inventory.Slot> playerSlots,
            java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> invFilters,
            net.minecraft.item.ItemStack stack) {
        // Preferred: the empty slot whose active filter lists this item and whose place in the filter's
        // order best fits it (item first in the list -> group's first slot, and so on; ties keep the
        // lowest index). Mirrors the same slotPreference choice getEmptySlot makes at pull time.
        net.minecraft.inventory.Slot best = null;
        int bestPreference = Integer.MAX_VALUE;
        for (net.minecraft.inventory.Slot ps : playerSlots) {
            if (!ps.getStack().isEmpty() || previewTargetIncoming.containsKey(ps.slotNumber)) continue;
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl =
                    invFilters != null ? invFilters.get(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(ps)) : null;
            if (wl == null
                    || !isSlotFilterActive(wl)
                    || !io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack))
                continue;
            int preference = io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(
                    invFilters, io.github.marcsanzdev.chestseparators.util.SlotIndex.of(ps), stack);
            if (preference < bestPreference) {
                bestPreference = preference;
                best = ps;
            }
        }
        if (best != null) return best;
        // Otherwise the first empty slot not reserved for a DIFFERENT item.
        for (net.minecraft.inventory.Slot ps : playerSlots) {
            if (!ps.getStack().isEmpty() || previewTargetIncoming.containsKey(ps.slotNumber)) continue;
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl =
                    invFilters != null ? invFilters.get(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(ps)) : null;
            boolean reservedForOther = wl != null
                    && isSlotFilterActive(wl)
                    && !io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack);
            if (!reservedForOther) return ps;
        }
        return null;
    }

    private static boolean isSlotFilterActive(io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl) {
        return wl.allowHopper() || wl.allowManual() || wl.allowShift();
    }

    /** Client mirror of the server's inventoryListsItem: does any inventory filter list this item? */
    private boolean inventoryListsItemClient(
            java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> invFilters,
            net.minecraft.item.ItemStack stack) {
        if (invFilters == null) return false;
        for (io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl : invFilters.values()) {
            if (io.github.marcsanzdev.chestseparators.util.ItemKey.matches(wl.allowedItems(), stack)) return true;
        }
        return false;
    }

    /**
     * True when a push/pull preview is active and this slot is part of it — the vanilla item render is
     * then suppressed for that slot so the preview draws a clean ghost with no real item (and its count)
     * bleeding through underneath.
     */
    public boolean isPreviewSlot(net.minecraft.inventory.Slot slot) {
        if (!isHoveringDeposit && !isHoveringFill) return false;
        return previewSourceRemaining.containsKey(slot.slotNumber) || previewTargetIncoming.containsKey(slot.slotNumber);
    }

    public void updateDepositPreview(boolean shift) {
        previewSourceRemaining.clear();
        previewTargetIncoming.clear();

        if (accessor.getHandler() == null || net.minecraft.client.Minecraft.getMinecraft().player == null) return;
        Map<Integer, SlotWhitelist> whitelists = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                .getCurrentWhitelists();

        for (net.minecraft.inventory.Slot playerSlot : accessor.getHandler().inventorySlots) {
            if (playerSlot.inventory instanceof net.minecraft.entity.player.InventoryPlayer && playerSlot.getHasStack()) {
                net.minecraft.item.ItemStack pristineStack = playerSlot.getStack();
                String itemId = pristineStack.getItem().getRegistryName().toString();

                int remaining = pristineStack.getCount();
                int initialCount = remaining;

                // 4-pass priority system mirrors the logic in executeDeposit.
                remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, true, false);

                if (remaining > 0)
                    remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, false, false);

                if (remaining > 0 && shift)
                    remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, true, true);

                if (remaining > 0 && shift)
                    remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, false, true);

                if (remaining < initialCount) {
                    previewSourceRemaining.put(playerSlot.slotNumber, remaining);
                }
            }
        }

        // Mirror the server's post-push per-group priority re-sort so the ghost preview lands items in the
        // exact slots the real deposit will after it re-organizes the chest.
        java.util.List<net.minecraft.inventory.Slot> chestSlots = new java.util.ArrayList<>();
        for (net.minecraft.inventory.Slot s : accessor.getHandler().inventorySlots) {
            if (!(s.inventory instanceof net.minecraft.entity.player.InventoryPlayer)) chestSlots.add(s);
        }
        applyPreviewReorder(chestSlots, whitelists, io.github.marcsanzdev.chestseparators.util.SlotIndex::of);
    }

    private int simulateDepositPass(
            Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists,
            net.minecraft.item.ItemStack stack,
            String itemId,
            int count,
            boolean checkExisting,
            boolean unfilteredOnly) {
        // Empty filtered slots are visited in the filter's order, exactly as executeDeposit fills them, so
        // the ghost preview lands items in the same slots the real deposit will.
        List<net.minecraft.inventory.Slot> orderedSlots = new ArrayList<>();
        for (net.minecraft.inventory.Slot chestSlot : accessor.getHandler().inventorySlots) {
            if (chestSlot.inventory instanceof net.minecraft.entity.player.InventoryPlayer) continue;
            orderedSlots.add(chestSlot);
        }
        if (!checkExisting && !unfilteredOnly) {
            orderedSlots.sort(java.util.Comparator.comparingInt(
                    s -> io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(
                            whitelists, io.github.marcsanzdev.chestseparators.util.SlotIndex.of(s), stack)));
        }

        for (net.minecraft.inventory.Slot chestSlot : orderedSlots) {
            if (count <= 0) break;

            boolean hasFilter = whitelists != null && whitelists.containsKey(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(chestSlot));
            boolean matchesFilter = hasFilter
                    && io.github.marcsanzdev.chestseparators.util.ItemKey.matches(
                            whitelists.get(io.github.marcsanzdev.chestseparators.util.SlotIndex.of(chestSlot)).allowedItems(),
                            stack);

            if (unfilteredOnly) {
                if (hasFilter) continue;
            } else {
                if (!matchesFilter) continue;
            }

            // If a slot already has a virtual preview stack, only continue if the incoming item type matches.
            net.minecraft.item.ItemStack incoming = previewTargetIncoming.get(chestSlot.slotNumber);
            if (incoming != null) {
                if (!net.minecraft.item.ItemStack.areItemsEqual(stack, incoming)
                        || !io.github.marcsanzdev.chestseparators.util.ItemMatch.sameItemSameTags(stack, incoming)) {
                    continue;
                }
            }

            if (checkExisting) {
                if (!chestSlot.getHasStack()) continue;
                if (!net.minecraft.item.ItemStack.areItemsEqual(stack, chestSlot.getStack())
                        || !io.github.marcsanzdev.chestseparators.util.ItemMatch.sameItemSameTags(stack, chestSlot.getStack()))
                    continue;
            } else {
                if (chestSlot.getHasStack()) continue;
            }

            int currentInChest = chestSlot.getHasStack() ? chestSlot.getStack().getCount() : 0;
            if (incoming != null) {
                currentInChest += incoming.getCount();
            }

            int spaceLeft = stack.getMaxStackSize() - currentInChest;
            if (spaceLeft > 0) {
                int toMove = Math.min(spaceLeft, count);
                count -= toMove;

                if (incoming == null) {
                    incoming = stack.copy();
                    incoming.setCount(toMove);
                } else {
                    incoming.grow(toMove);
                }
                previewTargetIncoming.put(chestSlot.slotNumber, incoming);
            }
        }
        return count;
    }

    /**
     * Rewrites {@link #previewTargetIncoming} so the preview shows the SAME final arrangement the real
     * push/pull produces — including the server's per-group priority re-sort. Programming rule of this mod:
     * a preview must ALWAYS render exactly what the action will do, so any change to the placement/sort
     * logic must be mirrored here. Two steps: (1) turn every projected entry from an "incoming delta" into
     * the slot's FINAL full stack, then (2) for each filtered group among {@code destSlots}, re-lay the
     * group's final stacks in priority order packed to the first slot — mirroring
     * {@code ChestSeparatorsMain#reorderFilteredGroups}. Slots that end unchanged are dropped from the map
     * (so they render normally); slots emptied by the sort are stored as an empty stack.
     *
     * @param destSlots  the slots the action re-sorts (chest slots for push; player MAIN slots, minus the
     *                   hotbar when locked, for pull)
     * @param whitelists filters keyed the way {@code indexOf} keys the slots
     * @param indexOf    maps a slot to its whitelist key (raw index for a chest, realIndex for the inventory)
     */
    private void applyPreviewReorder(
            java.util.List<net.minecraft.inventory.Slot> destSlots,
            java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists,
            java.util.function.ToIntFunction<net.minecraft.inventory.Slot> indexOf) {
        if (whitelists == null || whitelists.isEmpty() || previewTargetIncoming.isEmpty()) return;

        // Step 1: incoming delta -> final full stack, for every currently projected slot.
        for (Integer slotId : new java.util.ArrayList<>(previewTargetIncoming.keySet())) {
            net.minecraft.inventory.Slot s = accessor.getHandler().getSlot(slotId);
            net.minecraft.item.ItemStack inc = previewTargetIncoming.get(slotId);
            net.minecraft.item.ItemStack fin = inc.copy();
            if (!s.getStack().isEmpty() && io.github.marcsanzdev.chestseparators.util.ItemMatch.sameItemSameTags(s.getStack(), inc)) {
                fin.setCount(s.getStack().getCount() + inc.getCount());
            }
            previewTargetIncoming.put(slotId, fin);
        }

        // Step 2: re-sort each filtered group by priority order, packed to its first slot.
        java.util.Map<java.util.UUID, java.util.List<net.minecraft.inventory.Slot>> groups =
                new java.util.LinkedHashMap<>();
        for (net.minecraft.inventory.Slot s : destSlots) {
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl = whitelists.get(indexOf.applyAsInt(s));
            if (wl == null || wl.groupId() == null) continue;
            groups.computeIfAbsent(wl.groupId(), g -> new java.util.ArrayList<>())
                    .add(s);
        }
        for (java.util.List<net.minecraft.inventory.Slot> gslots : groups.values()) {
            if (gslots.size() < 2) continue;
            gslots.sort(java.util.Comparator.comparingInt(indexOf));
            java.util.List<String> order =
                    whitelists.get(indexOf.applyAsInt(gslots.get(0))).allowedItems();

            // Final content of each group slot: the projected override if present, else its real stack.
            java.util.List<net.minecraft.item.ItemStack> finals = new java.util.ArrayList<>();
            for (net.minecraft.inventory.Slot s : gslots) {
                net.minecraft.item.ItemStack fin =
                        previewTargetIncoming.containsKey(s.slotNumber) ? previewTargetIncoming.get(s.slotNumber) : s.getStack();
                if (!fin.isEmpty()) finals.add(fin);
            }
            finals.sort(java.util.Comparator.<net.minecraft.item.ItemStack>comparingInt(st -> {
                        int r = io.github.marcsanzdev.chestseparators.util.ItemKey.rank(order, st);
                        return r < 0 ? Integer.MAX_VALUE : r;
                    })
                    // Match reorderFilteredGroups: same-item overflow packs the fuller stack first, so the
                    // preview shows exactly what the deposit + server re-sort will produce ([64,1] not [1,64]).
                    .thenComparing(java.util.Comparator.comparingInt(
                                    net.minecraft.item.ItemStack::getCount)
                            .reversed()));

            for (int i = 0; i < gslots.size(); i++) {
                net.minecraft.inventory.Slot s = gslots.get(i);
                net.minecraft.item.ItemStack fin =
                        i < finals.size() ? finals.get(i) : net.minecraft.item.ItemStack.EMPTY;
                net.minecraft.item.ItemStack real = s.getStack();
                boolean unchanged = io.github.marcsanzdev.chestseparators.util.ItemMatch.sameItemSameTags(fin, real)
                        && fin.getCount() == real.getCount();
                if (unchanged) previewTargetIncoming.remove(s.slotNumber);
                else previewTargetIncoming.put(s.slotNumber, fin);
            }
        }
    }
}
