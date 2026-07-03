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
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@Environment(EnvType.CLIENT)
public class ChestSeparatorsEditor {

    private static ChestSeparatorsEditor activeInstance;
    private static boolean networkLockRegistered = false;

    public static ChestSeparatorsEditor getInstance() {
        return activeInstance;
    }

    public EditorSessionData getSession() {
        return this.session;
    }

    public EditorLayout getLayout() {
        return this.layout;
    }

    public final HandledScreen<?> screen;
    public final HandledScreenAccessor accessor;

    public static EditorSessionData activeSession = new EditorSessionData();
    public final EditorSessionData session = activeSession;
    public EditorGeometry geometry;
    public EditorRenderer renderer;
    public EditorInputHandler inputHandler;
    public EditorLayout layout;

    // SCREENS
    public ScreenViewGroups screenViewGroups;
    public ScreenDrawLines screenDrawLines;
    public ScreenColorPicker screenColorPicker;
    public ScreenEditFilter screenEditFilter;

    // --- BOTONES Y BUSCADOR ---
    public ToolButtonWidget entryButton;
    public ToolButtonWidget whitelistButton;
    public ToolButtonWidget depositButton;
    public TextFieldWidget searchBox;
    public TextFieldWidget whitelistSearchBox;

    public boolean isHoveringDeposit = false;
    public boolean depositPreviewShift = false;
    public boolean suspendDepositPreview = false; // <--- AÑADE ESTA VARIABLE
    public long depositClickTime = 0;
    public final Map<Integer, Integer> previewSourceRemaining = new HashMap<>();
    public final Map<Integer, ItemStack> previewTargetIncoming = new HashMap<>();

    public static final int TOOL_ERASER_ID = -1;

    // --- NUEVO: Control de bloqueo de edición ---
    public boolean hasEditorLock = false;

    public boolean colorPickerModified = false;
    public int restoreColorIndex = -1;

    public ChestSeparatorsEditor(HandledScreen<?> screen) {
        this.screen = screen;
        this.accessor = (HandledScreenAccessor) screen;
        activeInstance = this;
    }

    public void init() {
        // Registrar el receptor del servidor UNA sola vez para el cliente
        if (!networkLockRegistered) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                    io.github.marcsanzdev.chestseparators.network.EditorLockResponsePayload.ID,
                    (payload, context) -> {
                        context.client().execute(() -> {
                            ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
                            if (editor != null && editor.session.currentChestPos != null && editor.session.currentChestPos.equals(payload.pos())) {
                                if (payload.granted()) {
                                    editor.hasEditorLock = true;
                                    editor.toggleState(EditorState.VIEW_GROUPS); // ¡Permiso concedido! Abrimos el menú.
                                    editor.playClickSound(1.0f);
                                } else {
                                    // Permiso denegado. Mostramos un mensaje de error rojo.
                                    editor.showStatus(Text.translatable("message.chestseparators.locked"), Formatting.RED);
                                    editor.playClickSound(0.5f);
                                }
                            }
                        });
                    }
            );
            networkLockRegistered = true;
        }

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

        session.currentChestPos = ChestPosStorage.getLastClickedPos();
        session.currentDimension = ChestPosStorage.getLastClickedDimension();
        session.isEntityChest = ChestPosStorage.getEntityOpened();
        session.currentEntityUUID = ChestPosStorage.getLastClickedEntityUUID();
        session.isEnderChest = false;

        session.isShulkerBox = this.screen instanceof ShulkerBoxScreen;
        session.currentShulkerUUID = ChestPosStorage.getLastOpenedShulkerUUID();

        ChestConfigManager.getInstance().loadWorldPalette();

        if (session.isShulkerBox && session.currentShulkerUUID != null) {
            ChestConfigManager.getInstance().loadShulkerConfig(session.currentShulkerUUID);
        } else if (session.isEntityChest && session.currentEntityUUID != null) {
            ChestConfigManager.getInstance().loadEntityConfig(session.currentEntityUUID);
        } else if (session.currentChestPos != null && MinecraftClient.getInstance().world != null) {
            if (MinecraftClient.getInstance().world.getBlockState(session.currentChestPos).getBlock() == Blocks.ENDER_CHEST) {
                session.isEnderChest = true;
                ChestConfigManager.getInstance().loadEnderConfig();
            } else {
                ChestConfigManager.getInstance().loadConfig(session.currentChestPos, session.currentDimension);
            }
        }

        // --- SERVER FILTER REQUEST ---
        if (session.currentChestPos != null && !session.isEntityChest && !session.isShulkerBox) {
            if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(io.github.marcsanzdev.chestseparators.network.WhitelistRequestPayload.ID)) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new io.github.marcsanzdev.chestseparators.network.WhitelistRequestPayload(session.currentChestPos)
                );
            }
        }

        int x = accessor.getX();
        int y = accessor.getY();
        int bgWidth = accessor.getBackgroundWidth();

        // --- 1. LÓGICA DE BOTÓN LÁPIZ (AHORA ES UN TOOLBUTTONWIDGET) ---
        this.entryButton = new io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget(
                x + bgWidth - 22, y - 22, io.github.marcsanzdev.chestseparators.client.ModTextures.BTN_EDIT_LINES,
                Text.translatable("tooltip.chestseparators.edit_mode").getString(),
                () -> {
                    toggleState(EditorState.DRAW_LINES);
                    playClickSound(1.0f);
                }
        );

        // --- 2. LÓGICA DE BOTÓN W (AHORA ES UN TOOLBUTTONWIDGET SILENCIADO) ---
        this.whitelistButton = new io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget(
                x + bgWidth - 44, y - 22, io.github.marcsanzdev.chestseparators.client.ModTextures.BTN_WHITELIST,
                Text.translatable("tooltip.chestseparators.whitelist_mode").getString(),
                () -> {
                    boolean isFilterState = (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS || session.currentState == EditorState.EDIT_FILTER);

                    if (isFilterState) {
                        toggleState(EditorState.HIDDEN);
                        playClickSound(1.0f);
                    } else {
                        if (session.currentChestPos != null && !session.isEntityChest && !session.isShulkerBox) {
                            if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(io.github.marcsanzdev.chestseparators.network.EditorLockRequestPayload.ID)) {
                                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                                        new io.github.marcsanzdev.chestseparators.network.EditorLockRequestPayload(session.currentChestPos, true)
                                );
                            } else {
                                toggleState(EditorState.VIEW_GROUPS);
                                playClickSound(1.0f);
                            }
                        } else {
                            toggleState(EditorState.VIEW_GROUPS);
                            playClickSound(1.0f);
                        }
                    }
                }
        );

        // --- 3. LÓGICA DE BOTÓN DEPOSITAR (Limpia y estándar) ---
        // --- 3. LÓGICA DE BOTÓN DEPOSITAR ---
        this.depositButton = new ToolButtonWidget(
                0, 0, ModTextures.BTN_DEPOSIT,
                "",
                () -> {
                    if (this.isEditMode()) return;

                    // --- NUEVO: Registramos el momento del clic para la animación ---
                    this.depositClickTime = System.currentTimeMillis();

                    long window = MinecraftClient.getInstance().getWindow().getHandle();
                    boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                            GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                    executeDeposit(shift);
                    playClickSound(1.2f);
                }
        );

        // --- POSICIONAMIENTO DINÁMICO ---
        int baseX = x + bgWidth;
        if (GlobalChestConfig.instance.showEditButtons) {
            // Todos visibles: 3 botones alineados (Modificamos directamente x e y)
            this.entryButton.x = baseX - 22;
            this.entryButton.y = y - 22;

            this.whitelistButton.x = baseX - 44;
            this.whitelistButton.y = y - 22;

            this.depositButton.x = baseX - 66;
            this.depositButton.y = y - 22;
        } else {
            // Si los de edición están ocultos, el de depositar se va a la derecha del todo
            this.depositButton.x = baseX - 22;
            this.depositButton.y = y - 22;
        }

        // --- POBLAMIENTO MAESTRO DE ÍTEMS EN ORDEN CREATIVO ---
        session.allGameItems.clear();
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world != null) {
            ItemGroup searchGroup = ItemGroups.getSearchGroup();
            net.minecraft.resource.featuretoggle.FeatureSet features = client.world.getEnabledFeatures();
            ItemGroup.DisplayContext context = new ItemGroup.DisplayContext(features, false, client.world.getRegistryManager());

            if (searchGroup != null) {
                searchGroup.updateEntries(context);
                java.util.Collection<ItemStack> vanillaOrderedStacks = searchGroup.getDisplayStacks();
                if (vanillaOrderedStacks != null) {
                    vanillaOrderedStacks.forEach(stack -> {
                        Item item = stack.getItem();
                        if (item != Items.AIR && !session.allGameItems.contains(item)) {
                            session.allGameItems.add(item);
                        }
                    });
                }
            }
        }

        if (session.allGameItems.isEmpty()) {
            net.minecraft.registry.Registries.ITEM.forEach(item -> {
                if(item != Items.AIR && client.world != null && item.isEnabled(client.world.getEnabledFeatures())) {
                    session.allGameItems.add(item);
                }
            });
        }

        session.filteredItems.clear();
        session.filteredItems.addAll(session.allGameItems);

        int w = 240; int h = 200;
        int guiX = (screen.width - w) / 2;
        int guiY = (screen.height - h) / 2;

        session.availableTabs.clear();
        for (int i = 0; i < 16; i++) {
            session.availableTabs.add(null);
        }

        EditorSessionData.CreativeTabInfo chestTab = new EditorSessionData.CreativeTabInfo(Text.translatable("itemGroup.chestseparators.chest_and_inventory"), new ItemStack(net.minecraft.block.Blocks.CHEST), null, false, true);
        session.availableTabs.set(12, chestTab);

        java.util.List<EditorSessionData.CreativeTabInfo> modTabs = new java.util.ArrayList<>();
        net.minecraft.registry.Registries.ITEM_GROUP.forEach(group -> {
            net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ITEM_GROUP.getId(group);
            if (id == null) return;
            String path = id.getPath();

            if (path.equals("spawn_eggs") || path.equals("inventory") || path.equals("hotbar") || path.equals("op_blocks")) return;
            boolean isSearch = path.equals("search");
            EditorSessionData.CreativeTabInfo tab = new EditorSessionData.CreativeTabInfo(group.getDisplayName(), group.getIcon(), group, isSearch, false);

            if (path.equals("building_blocks")) session.availableTabs.set(0, tab);
            else if (path.equals("colored_blocks")) session.availableTabs.set(1, tab);
            else if (path.equals("natural_blocks")) session.availableTabs.set(2, tab);
            else if (path.equals("functional_blocks")) session.availableTabs.set(3, tab);
            else if (path.equals("redstone_blocks")) session.availableTabs.set(4, tab);
            else if (isSearch) session.availableTabs.set(7, tab);
            else if (path.equals("tools") || path.equals("tools_and_utilities")) session.availableTabs.set(8, tab);
            else if (path.equals("combat")) session.availableTabs.set(9, tab);
            else if (path.equals("food_and_drinks") || path.equals("food_and_drink")) session.availableTabs.set(10, tab);
            else if (path.equals("ingredients")) session.availableTabs.set(11, tab);
            else { modTabs.add(tab); }
        });
        session.availableTabs.addAll(modTabs);

        int firstValidTab = 0;
        for (int i = 0; i < session.availableTabs.size(); i++) {
            if (session.availableTabs.get(i) != null) {
                firstValidTab = i;
                break;
            }
        }
        switchCreativeTab(firstValidTab);

        this.searchBox = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, guiX + 10, guiY + 10, 100, 12, Text.translatable("gui.chestseparators.search"));
        this.searchBox.setDrawsBackground(false);
        this.searchBox.setMaxLength(50);
        this.searchBox.setChangedListener(text -> {
            if (!session.availableTabs.get(session.currentCreativeTabIndex).isSearchTab) return;
            switchCreativeTab(session.currentCreativeTabIndex);
        });

        this.whitelistSearchBox = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, guiX, guiY, 100, 12, Text.translatable("gui.chestseparators.search"));
        this.whitelistSearchBox.setDrawsBackground(false);
        this.whitelistSearchBox.setMaxLength(50);
        this.whitelistSearchBox.setChangedListener(text -> {
            session.listScrollY = 0f;
            updateWhitelistSearchCache();
        });

        this.inputHandler.setupInputHandlers();
    }

    public void onClose() {
        releaseLock(); // ¡IMPORTANTE! Soltar la llave al cerrar el cofre
        ChestConfigManager.getInstance().clearCurrentConfig();
        ChestConfigManager.getInstance().getCurrentWhitelists().clear();


        ChestPosStorage.setLastOpenedShulkerUUID(null);
        session.isColorPickerOpen = false;
        activeSession = new EditorSessionData();
        org.lwjgl.glfw.GLFW.glfwSetInputMode(MinecraftClient.getInstance().getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (renderer != null) renderer.render(context, mouseX, mouseY, delta);
    }

    public int getSidebarYOffset() {
        return geometry.getContainerSlotCount() > 27 ? 0 : -18;
    }

    public boolean isEditMode() {
        return session.currentState != EditorState.HIDDEN;
    }

    // --- NUEVO: Libera el bloqueo del servidor ---
    public void releaseLock() {
        if (hasEditorLock && session.currentChestPos != null) {
            if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(io.github.marcsanzdev.chestseparators.network.EditorLockRequestPayload.ID)) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.marcsanzdev.chestseparators.network.EditorLockRequestPayload(session.currentChestPos, false));
            }
            hasEditorLock = false;
        }
    }

    public void toggleState(EditorState targetState) {
        EditorState previousState = session.currentState;

        // 1. Resolver el nuevo estado
        if (session.currentState == targetState) {
            session.currentState = EditorState.HIDDEN;
        } else {
            session.currentState = targetState;
        }

        // --- SISTEMA DE LIBERACIÓN DE SEMÁFORO ---
        // Si estábamos en un menú de filtros y hemos salido a oculto o a dibujar líneas, soltamos la llave.
        boolean wasFilterState = (previousState == EditorState.VIEW_GROUPS || previousState == EditorState.SELECT_SLOTS || previousState == EditorState.EDIT_FILTER);
        boolean isFilterState = (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS || session.currentState == EditorState.EDIT_FILTER);

        if (wasFilterState && !isFilterState) {
            releaseLock();
        }

        // 2. Limpieza estándar
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
            session.selectedSlots.add(initialSlot.getIndex());
            session.lastClickedSlotIndex = initialSlot.getIndex();
            session.lastSlotClickTime = System.currentTimeMillis();
            session.isSelecting = true;
        }
        playClickSound(1.0f);
    }

    public void prepareFilterMenu() {
        session.currentAllowedItems.clear();
        session.ruleManual = GlobalChestConfig.instance.defaultRuleManual;
        session.ruleShift = GlobalChestConfig.instance.defaultRuleShift;
        session.ruleHopper = GlobalChestConfig.instance.defaultRuleHopper;
        session.gridScrollY = 0f;
        session.listScrollY = 0f;

        if (session.selectedGroupId != null) {
            var whitelists = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance().getCurrentWhitelists();
            boolean isExistingGroup = false;
            if (whitelists != null) {
                isExistingGroup = whitelists.values().stream().anyMatch(wl -> session.selectedGroupId.equals(wl.groupId()));
            }

            if (isExistingGroup) {
                session.selectedSlots.clear();
                boolean loadedRules = false;

                for (java.util.Map.Entry<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> entry : whitelists.entrySet()) {
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
        updateWhitelistSearchCache();
    }

    public void saveCurrentGroup() {
        var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
        if (whitelists == null) whitelists = new java.util.HashMap<>();

        for (int slotIndex : session.selectedSlots) {
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl = new io.github.marcsanzdev.chestseparators.data.SlotWhitelist(
                    session.selectedGroupId,
                    new ArrayList<>(session.currentAllowedItems),
                    session.ruleManual,
                    session.ruleShift,
                    session.ruleHopper
            );
            whitelists.put(slotIndex, wl);
        }
        ChestConfigManager.getInstance().setCurrentWhitelists(whitelists);
        saveSmart();
        sendWhitelistToServer();

        syncClientInventoryWhitelists(whitelists);
    }

    public void deleteCurrentGroup() {
        if (session.selectedGroupId == null) return;
        var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
        if (whitelists != null) {
            whitelists.entrySet().removeIf(entry -> session.selectedGroupId.equals(entry.getValue().groupId()));
            ChestConfigManager.getInstance().setCurrentWhitelists(whitelists);
            saveSmart();
            sendWhitelistToServer();

            syncClientInventoryWhitelists(whitelists);
        }
    }

    public void sendWhitelistToServer() {
        if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(io.github.marcsanzdev.chestseparators.network.WhitelistPayload.ID)) {
            BlockPos posToSend = session.currentChestPos != null ? session.currentChestPos : BlockPos.ORIGIN;
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new io.github.marcsanzdev.chestseparators.network.WhitelistPayload(
                            posToSend,
                            io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance().getCurrentWhitelists()
                    )
            );
        }
    }

    public void playClickSound(float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(
                net.minecraft.client.sound.PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch)
        );
    }

    public void saveSmart() {
        if (session.isShulkerBox && session.currentShulkerUUID != null) {
            ChestConfigManager.getInstance().saveShulkerConfig(session.currentShulkerUUID);
        } else if (session.isEntityChest && session.currentEntityUUID != null) {
            ChestConfigManager.getInstance().saveEntityConfig(session.currentEntityUUID);
        } else if (session.isEnderChest) {
            ChestConfigManager.getInstance().saveEnderConfig();
        } else {
            ChestConfigManager.getInstance().saveConfig(session.currentChestPos, session.currentDimension);
        }
    }

    public void triggerActionAnimation(int actionId) {
        session.clickedActionId = actionId;
        session.clickedActionTime = System.currentTimeMillis();
    }

    public void showStatus(Text message, Formatting color) {
        session.statusMessage = message.copy().formatted(color);
        session.statusMessageTime = System.currentTimeMillis();
    }

    public boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public boolean isInsidePickerWindow(double mx, double my) {
        int w = 220; int h = 185;
        int x = (screen.width - w) / 2; int y = (screen.height - h) / 2;
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
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

        // --- RESETEO DE POSICIÓN VISUAL (Punto inicial fijo) ---
        // Esto asegura que los cursores aparezcan siempre en el mismo sitio
        session.pickerHue = 0.0f; // Rojo (arriba en la barra)
        session.pickerSat = 1.0f; // Saturación máxima (derecha del cuadro)
        session.pickerVal = 1.0f; // Brillo máximo (arriba del cuadro)

        if (initialColor == 0) {
            // Si el hueco está vacío, mantenemos el RGB en 0 para mostrar el ajedrezado
            session.pickerCurrentRGB = 0;
        } else {
            // Si el hueco YA tiene un color, sobreescribimos los valores por defecto
            // para que los cursores se muevan a la posición de ese color real.
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

    public void renderNormalModeOverlay(DrawContext context, int mouseX, int mouseY) {
        if (this.renderer != null) this.renderer.renderNormalModeOverlay(context, mouseX, mouseY);

        // Solo dibujamos el mensaje flotante si el editor principal del mod está cerrado
        if (session.currentState == EditorState.HIDDEN && session.statusMessage != null) {
            long elapsed = System.currentTimeMillis() - session.statusMessageTime;

            // Usamos EXACTAMENTE el mismo timing que en EditorRenderer.java
            if (elapsed < 2000) {
                int alpha = 255;
                if (elapsed > 1500) {
                    alpha = (int) (255 * (1.0f - (elapsed - 1500) / 500.0f));
                }

                if (alpha > 10) {
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    int color = (alpha << 24) | 0xFFFFFF;

                    // Copiamos la posición exacta de EditorRenderer: Abajo del todo y centrado en pantalla
                    context.drawCenteredTextWithShadow(client.textRenderer, session.statusMessage, screen.width / 2, screen.height - 40, color);
                }
            } else {
                session.statusMessage = null; // Clears memory after it finishes
            }
        }
    }

    public void renderSavedLinesLayer(DrawContext context) {
        if (this.renderer != null) this.renderer.renderSavedLinesLayer(context);
    }

    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        boolean isSearchFocused = (searchBox != null && searchBox.isFocused());
        boolean isWhitelistSearchFocused = (whitelistSearchBox != null && whitelistSearchBox.isFocused());

        if (isSearchFocused) {
            if (searchBox.keyPressed(input)) return true;
            if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                return true;
            }
        }
        if (session.isColorPickerOpen && screenColorPicker.keyPressed(input)) return true;
        if (this.inputHandler != null) return this.inputHandler.keyPressed(input);
        return false;
    }

    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (session.isColorPickerOpen && screenColorPicker.charTyped(input)) return true;
        if (this.inputHandler != null) return this.inputHandler.charTyped(input);
        return false;
    }

    public List<String> extractItemsFromSelection() {
        Set<String> extracted = new LinkedHashSet<>();
        if (accessor.getHandler() != null && accessor.getHandler().slots != null) {
            for (int slotIdx : session.selectedSlots) {
                if (slotIdx >= 0 && slotIdx < accessor.getHandler().slots.size()) {
                    ItemStack stack = accessor.getHandler().getSlot(slotIdx).getStack();
                    if (!stack.isEmpty()) {
                        String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
                        if (!session.currentAllowedItems.contains(id)) extracted.add(id);
                    }
                }
            }
        }
        return new ArrayList<>(extracted);
    }

    public void buildCustomChestTab() {
        session.customChestItems.clear();
        java.util.Set<Item> uniqueItems = new java.util.LinkedHashSet<>();

        if (accessor.getHandler() != null && accessor.getHandler().slots != null) {
            for (Slot slot : accessor.getHandler().slots) {
                if (!(slot.inventory instanceof net.minecraft.entity.player.PlayerInventory) && slot.hasStack()) {
                    uniqueItems.add(slot.getStack().getItem());
                }
            }
            for (Slot slot : accessor.getHandler().slots) {
                if (slot.inventory instanceof net.minecraft.entity.player.PlayerInventory && slot.hasStack()) {
                    uniqueItems.add(slot.getStack().getItem());
                }
            }
        }
        session.customChestItems.addAll(uniqueItems);
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

        MinecraftClient client = MinecraftClient.getInstance();
        net.minecraft.resource.featuretoggle.FeatureSet features = client.world != null ? client.world.getEnabledFeatures() : null;
        ItemGroup.DisplayContext context = features != null ? new ItemGroup.DisplayContext(features, false, client.world.getRegistryManager()) : null;

        if (activeTab.isCustomChestTab) {
            buildCustomChestTab();
            session.filteredItems.addAll(session.customChestItems);
        } else if (activeTab.isSearchTab) {
            boolean isSearching = this.searchBox != null && !this.searchBox.getText().isEmpty();
            String lowerQuery = isSearching ? this.searchBox.getText().toLowerCase() : "";

            for (EditorSessionData.CreativeTabInfo tab : session.availableTabs) {
                if (tab == null || tab.isSearchTab || tab.isCustomChestTab || tab.group == null) continue;
                java.util.Collection<ItemStack> displayStacks = tab.group.getDisplayStacks();

                if ((displayStacks == null || displayStacks.isEmpty()) && context != null) {
                    tab.group.updateEntries(context);
                    displayStacks = tab.group.getDisplayStacks();
                }

                if (displayStacks != null) {
                    for (ItemStack stack : displayStacks) {
                        Item item = stack.getItem();
                        if (!session.filteredItems.contains(item) && isItemAllowedInMenu(item)) {
                            if (isSearching) {
                                boolean matches = false;
                                if (lowerQuery.startsWith("#")) {
                                    String searchTag = lowerQuery.substring(1);
                                    matches = item.getRegistryEntry().streamTags().anyMatch(t -> t.id().getPath().contains(searchTag));
                                } else {
                                    matches = item.getName().getString().toLowerCase().contains(lowerQuery);
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
            java.util.Collection<ItemStack> displayStacks = activeTab.group.getDisplayStacks();
            if ((displayStacks == null || displayStacks.isEmpty()) && context != null) {
                activeTab.group.updateEntries(context);
                displayStacks = activeTab.group.getDisplayStacks();
            }

            if (displayStacks != null && !displayStacks.isEmpty()) {
                for (ItemStack stack : displayStacks) {
                    Item item = stack.getItem();
                    if (!session.filteredItems.contains(item) && isItemAllowedInMenu(item)) {
                        session.filteredItems.add(item);
                    }
                }
            }
        }
    }

    public boolean isItemAllowedInMenu(Item item) {
        if (item instanceof SpawnEggItem) return false;
        net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ITEM.getId(item);
        if (id == null) return false;

        String path = id.getPath();
        if (path.contains("infested")) return false;
        if (path.contains("command_block")) return false;

        java.util.Set<String> blacklist = java.util.Set.of(
                "air", "bedrock", "spawner", "reinforced_deepslate", "end_portal_frame",
                "knowledge_book", "trial_spawner", "vault", "barrier", "light",
                "debug_stick", "structure_block", "structure_void", "jigsaw",
                "test_block", "test_instance_block", "player_head", "farmland",
                "suspicious_sand", "suspicious_gravel", "budding_amethyst",
                "dirt_path", "chorus_plant", "frogspawn"
        );

        return !blacklist.contains(path);
    }

    public void updateWhitelistSearchCache() {
        session.visibleLeftListItems.clear();
        String wlSearch = this.whitelistSearchBox != null ? this.whitelistSearchBox.getText().toLowerCase() : "";

        for (String id : session.currentAllowedItems) {
            Item item = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.tryParse(id));
            if (item != null) {
                boolean matches = false;
                if (wlSearch.isEmpty()) {
                    matches = true;
                } else if (wlSearch.startsWith("#")) {
                    String searchTag = wlSearch.substring(1);
                    matches = item.getRegistryEntry().streamTags().anyMatch(t -> t.id().getPath().contains(searchTag));
                } else {
                    matches = item.getName().getString().toLowerCase().contains(wlSearch);
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
        if (MinecraftClient.getInstance().player != null) {
            ScreenHandler handler = MinecraftClient.getInstance().player.currentScreenHandler;
            // We get the inventory from the very first slot, which universally points to the container's inventory
            if (handler != null && !handler.slots.isEmpty()) {
                Inventory inv = handler.slots.get(0).inventory;
                if (inv instanceof IWhitelistProvider provider) {
                    provider.setWhitelists(whitelists);
                }
            }
        }
    }

    public void executeDeposit(boolean shift) {
        if (accessor.getHandler() == null || net.minecraft.client.MinecraftClient.getInstance().player == null) return;
        if (!accessor.getHandler().getCursorStack().isEmpty()) return;

        var whitelists = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance().getCurrentWhitelists();
        net.minecraft.client.network.ClientPlayerInteractionManager interactionManager = net.minecraft.client.MinecraftClient.getInstance().interactionManager;
        int syncId = accessor.getHandler().syncId;
        net.minecraft.entity.player.PlayerEntity player = net.minecraft.client.MinecraftClient.getInstance().player;

        int totalMovedCount = 0;

        for (net.minecraft.screen.slot.Slot playerSlot : accessor.getHandler().slots) {
            if (playerSlot.inventory instanceof net.minecraft.entity.player.PlayerInventory && playerSlot.hasStack()) {
                net.minecraft.item.ItemStack stackToMove = playerSlot.getStack().copy();
                String itemId = net.minecraft.registry.Registries.ITEM.getId(stackToMove.getItem()).toString();
                int remainingOnCursor = stackToMove.getCount();

                // Order matters here! We build the target list following the 4-pass priority
                List<net.minecraft.screen.slot.Slot> prioritizedSlots = new ArrayList<>();

                // 1. Filtered Stacking
                collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, true, false);
                // 2. Filtered Filling
                collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, false, false);

                if (shift) {
                    // 3. Unfiltered Stacking
                    collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, true, true);
                    // 4. Unfiltered Filling
                    collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, false, true);
                }

                if (!prioritizedSlots.isEmpty()) {
                    boolean pickedUp = false;
                    for (net.minecraft.screen.slot.Slot targetSlot : prioritizedSlots) {
                        if (remainingOnCursor <= 0) break;

                        int spaceLeft = stackToMove.getMaxCount() - (targetSlot.hasStack() ? targetSlot.getStack().getCount() : 0);
                        if (spaceLeft > 0) {
                            if (!pickedUp) {
                                interactionManager.clickSlot(syncId, playerSlot.id, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, player);
                                pickedUp = true;
                            }
                            interactionManager.clickSlot(syncId, targetSlot.id, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, player);
                            int moved = Math.min(spaceLeft, remainingOnCursor);
                            remainingOnCursor -= moved;
                            totalMovedCount += moved;
                        }
                    }
                    if (pickedUp && remainingOnCursor > 0) {
                        interactionManager.clickSlot(syncId, playerSlot.id, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, player);
                    }
                }
            }
        }

        if (totalMovedCount > 0) {
            showStatus(net.minecraft.text.Text.translatable("message.chestseparators.deposited", totalMovedCount), net.minecraft.util.Formatting.GREEN);
            this.suspendDepositPreview = true;
            this.previewSourceRemaining.clear();
            this.previewTargetIncoming.clear();
        } else {
            showStatus(net.minecraft.text.Text.translatable("message.chestseparators.deposit_failed"), net.minecraft.util.Formatting.RED);
        }
    }

    private void collectTargetSlots(List<net.minecraft.screen.slot.Slot> list, Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists, String itemId, net.minecraft.item.ItemStack stack, boolean checkExisting, boolean unfilteredOnly) {
        for (net.minecraft.screen.slot.Slot chestSlot : accessor.getHandler().slots) {
            if (chestSlot.inventory instanceof net.minecraft.entity.player.PlayerInventory) continue;
            if (list.contains(chestSlot)) continue;

            boolean hasFilter = whitelists != null && whitelists.containsKey(chestSlot.getIndex());
            boolean matchesFilter = hasFilter && whitelists.get(chestSlot.getIndex()).allowedItems().contains(itemId);

            if (unfilteredOnly) {
                if (hasFilter) continue;
            } else {
                if (!matchesFilter) continue;
            }

            if (checkExisting) {
                if (!chestSlot.hasStack()) continue;
                if (!net.minecraft.item.ItemStack.areItemsEqual(stack, chestSlot.getStack()) || !net.minecraft.item.ItemStack.areItemsAndComponentsEqual(stack, chestSlot.getStack())) continue;
                if (chestSlot.getStack().getCount() >= chestSlot.getStack().getMaxCount()) continue;
            } else {
                if (chestSlot.hasStack()) continue;
            }
            list.add(chestSlot);
        }
    }

    public void updateDepositPreview(boolean shift) {
        previewSourceRemaining.clear();
        previewTargetIncoming.clear();

        if (accessor.getHandler() == null || net.minecraft.client.MinecraftClient.getInstance().player == null) return;
        var whitelists = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance().getCurrentWhitelists();

        for (net.minecraft.screen.slot.Slot playerSlot : accessor.getHandler().slots) {
            if (playerSlot.inventory instanceof net.minecraft.entity.player.PlayerInventory && playerSlot.hasStack()) {
                net.minecraft.item.ItemStack pristineStack = playerSlot.getStack();
                String itemId = net.minecraft.registry.Registries.ITEM.getId(pristineStack.getItem()).toString();

                int remaining = pristineStack.getCount();
                int initialCount = remaining;

                // --- 4-PASS PRIORITY SYSTEM ---

                // PASS 1: Filtered slots with matching stacks (Stacking)
                remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, true, false);

                // PASS 2: Filtered slots that are empty (Filling)
                if (remaining > 0)
                    remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, false, false);

                // PASS 3: Unfiltered slots with matching stacks (Only if Shift)
                if (remaining > 0 && shift)
                    remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, true, true);

                // PASS 4: Unfiltered slots that are empty (Only if Shift)
                if (remaining > 0 && shift)
                    remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, false, true);

                if (remaining < initialCount) {
                    previewSourceRemaining.put(playerSlot.id, remaining);
                }
            }
        }
    }

    // Helper method to keep simulate logic clean and DRY (Don't Repeat Yourself)
    // Helper method to keep simulate logic clean and DRY (Don't Repeat Yourself)
    private int simulateDepositPass(Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists, net.minecraft.item.ItemStack stack, String itemId, int count, boolean checkExisting, boolean unfilteredOnly) {
        for (net.minecraft.screen.slot.Slot chestSlot : accessor.getHandler().slots) {
            if (chestSlot.inventory instanceof net.minecraft.entity.player.PlayerInventory || count <= 0) continue;

            boolean hasFilter = whitelists != null && whitelists.containsKey(chestSlot.getIndex());
            boolean matchesFilter = hasFilter && whitelists.get(chestSlot.getIndex()).allowedItems().contains(itemId);

            // Logic gate for priority tiers
            if (unfilteredOnly) {
                if (hasFilter) continue; // Skip filtered slots in unfiltered pass
            } else {
                if (!matchesFilter) continue; // Skip non-matching or unfiltered slots in filtered pass
            }

            // ¡NUEVO! Y VITAL:
            // Si el slot ya tiene un holograma proyectado, DEBEMOS comprobar que sea exactamente del mismo tipo de ítem.
            // Si es un ítem distinto (ej. intentas meter tierra donde ya hay un Shulker virtual), salta este slot.
            net.minecraft.item.ItemStack incoming = previewTargetIncoming.get(chestSlot.id);
            if (incoming != null) {
                if (!net.minecraft.item.ItemStack.areItemsEqual(stack, incoming) || !net.minecraft.item.ItemStack.areItemsAndComponentsEqual(stack, incoming)) {
                    continue;
                }
            }

            if (checkExisting) {
                if (!chestSlot.hasStack()) continue;
                if (!net.minecraft.item.ItemStack.areItemsEqual(stack, chestSlot.getStack()) || !net.minecraft.item.ItemStack.areItemsAndComponentsEqual(stack, chestSlot.getStack())) continue;
            } else {
                if (chestSlot.hasStack()) continue;
            }

            // Calculation of space
            int currentInChest = chestSlot.hasStack() ? chestSlot.getStack().getCount() : 0;
            if (incoming != null) {
                currentInChest += incoming.getCount();
            }

            int spaceLeft = stack.getItem().getMaxCount() - currentInChest;
            if (spaceLeft > 0) {
                int toMove = Math.min(spaceLeft, count);
                count -= toMove;

                if (incoming == null) {
                    incoming = stack.copy();
                    incoming.setCount(toMove);
                } else {
                    incoming.increment(toMove);
                }
                previewTargetIncoming.put(chestSlot.id, incoming);
            }
        }
        return count;
    }
}