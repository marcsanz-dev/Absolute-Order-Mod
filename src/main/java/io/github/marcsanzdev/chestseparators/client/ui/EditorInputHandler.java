package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.MinecraftClient;

public class EditorInputHandler {

    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorGeometry geometry;
    private final HandledScreen<?> screen;
    private final HandledScreenAccessor accessor;

    private final EditorLayout layout;

    public EditorInputHandler(ChestSeparatorsEditor editor, EditorSessionData session, EditorGeometry geometry, EditorLayout layout, HandledScreen<?> screen, HandledScreenAccessor accessor) {
        this.editor = editor;
        this.session = session;
        this.geometry = geometry;
        this.layout = layout;
        this.screen = screen;
        this.accessor = accessor;
    }

    public void setupInputHandlers() {
        ScreenMouseEvents.allowMouseClick(screen).register(this::allowMouseClick);

        ScreenMouseEvents.allowMouseRelease(screen).register((_screen, context) -> {
            session.isDraggingMainScroll = false;
            session.isDraggingListScroll = false;

            int button = context.button();

            // --- NUEVO: Pasar el evento al Color Picker si está abierto ---
            if (session.isColorPickerOpen) {
                editor.screenColorPicker.mouseReleased(context.x(), context.y(), button);
                return true; // Continuamos normal porque no queremos bloquear a Vanilla al soltar
            }

            if (button == 0 && session.isDraggingLine) {
                if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {

                    editor.screenViewGroups.mouseReleased(context.x(), context.y(), button);

                } else if (session.currentState == EditorState.DRAW_LINES) {
                    editor.screenDrawLines.commitDrag(context.x(), context.y());
                    session.isDraggingLine = false;
                    session.currentDragAction = 0;
                    session.dragStartSlot = null;
                    session.dragCurrentSlot = null;
                    session.isDragModeErasing = false;
                }
            }
            return true;
        });

        ScreenMouseEvents.allowMouseDrag(screen).register((_screen, context, deltaX, deltaY) -> {
            double mouseX = context.x();
            double mouseY = context.y();

            if (session.currentState == EditorState.HIDDEN) return true;

            if (editor.screenColorPicker.mouseDragged(mouseX, mouseY, 0, deltaX, deltaY)) {
                return false;
            }

            if (session.currentState == EditorState.EDIT_FILTER) {
                layout.update(screen, accessor, editor.getSidebarYOffset());

                int mainX = layout.mainX;

                session.lastInteractedWasList = (mouseX < mainX);

                int gridViewH = layout.gridViewH;
                int msbY = layout.msbY;
                int msbH = layout.msbH;

                int listViewH = layout.listViewH;
                int lsbY = layout.lsbY;
                int lsbH = layout.lsbH;

                if (session.isDraggingMainScroll) {
                    int totalGridHeight = (int)Math.ceil(session.filteredItems.size() / 9.0) * 18;
                    float maxGridScroll = Math.max(0, totalGridHeight - gridViewH);
                    int thumbH = maxGridScroll > 0 ? Math.max(10, (int)((gridViewH / (float)Math.max(1, totalGridHeight)) * msbH)) : msbH;
                    float percent = (float)(mouseY - msbY - (thumbH / 2.0f)) / (msbH - thumbH);
                    session.gridScrollY = MathHelper.clamp(percent * maxGridScroll, 0, maxGridScroll);
                    return false;
                }
                if (session.isDraggingListScroll) {
                    java.util.List<String> displayedAllowedItems = new java.util.ArrayList<>();
                    String wlSearch = editor.whitelistSearchBox != null ? editor.whitelistSearchBox.getText().toLowerCase() : "";
                    for (String id : session.currentAllowedItems) {
                        net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.tryParse(id));
                        if (item != null && (wlSearch.isEmpty() || item.getName().getString().toLowerCase().contains(wlSearch))) displayedAllowedItems.add(id);
                    }

                    int totalListHeight = displayedAllowedItems.size() * 18;
                    float maxListScroll = Math.max(0, totalListHeight - listViewH);
                    int thumbH = maxListScroll > 0 ? Math.max(10, (int)((listViewH / (float)Math.max(1, totalListHeight)) * lsbH)) : lsbH;
                    float percent = (float)(mouseY - lsbY - (thumbH / 2.0f)) / (lsbH - thumbH);
                    session.listScrollY = MathHelper.clamp(percent * maxListScroll, 0, maxListScroll);
                    return false;
                }
                return false;
            }

            if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
                if (editor.screenViewGroups.mouseDragged(mouseX, mouseY, 0, deltaX, deltaY)) {
                    return false;
                }
            }

            if (session.currentState == EditorState.DRAW_LINES) {
                editor.screenDrawLines.mouseDragged(mouseX, mouseY, 0, deltaX, deltaY);
                return false;
            }
            return true;
        });

        registerScrollEvent();
    }

    private void registerScrollEvent() {
        net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen).register((_screen, mouseX, mouseY, horizontalAmount, verticalAmount) -> {

            if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
                if (editor.screenViewGroups.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return false;
            }
            if (session.currentState == EditorState.EDIT_FILTER) {
                if (editor.screenEditFilter.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return false;
            }

            return true;
        });
    }

    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        // 1. Detectamos si se pulsa Control (Seguro contra crasheos usando GLFW)
        boolean isControlDown = org.lwjgl.glfw.GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                org.lwjgl.glfw.GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        // --- LÓGICA DE CAJAS DE TEXTO Y SCROLL (EDIT FILTER) ---
        if (session.currentState == EditorState.EDIT_FILTER) {
            if (editor.screenEditFilter.keyPressed(input)) {
                return true;
            }
        }

        // --- ATAJOS DE TECLADO (Ctrl + Z, Y, C, V) ---
        if (isControlDown && session.currentState != EditorState.HIDDEN) {
            boolean isTyping = (session.currentState == EditorState.EDIT_FILTER) &&
                    ((editor.searchBox != null && editor.searchBox.isFocused()) ||
                            (editor.whitelistSearchBox != null && editor.whitelistSearchBox.isFocused()));

            if (!isTyping) {
                // Ctrl + Z (Deshacer)
                if (input.key() == GLFW.GLFW_KEY_Z) {
                    if (ChestConfigManager.getInstance().canUndo()) {
                        ChestConfigManager.getInstance().undo();
                        editor.saveSmart();
                        editor.playClickSound(0.8f);
                    } else { editor.playClickSound(0.5f); }
                    return true;
                }
                // Ctrl + Y (Rehacer)
                else if (input.key() == GLFW.GLFW_KEY_Y) {
                    if (ChestConfigManager.getInstance().canRedo()) {
                        ChestConfigManager.getInstance().redo();
                        editor.saveSmart();
                        editor.playClickSound(0.8f);
                    } else { editor.playClickSound(0.5f); }
                    return true;
                }
                // Ctrl + C (Copiar)
                else if (input.key() == GLFW.GLFW_KEY_C) {
                    if (session.currentState == EditorState.VIEW_GROUPS) {
                        ChestConfigManager.getInstance().copyWhitelistsToClipboard();
                        editor.showStatus(Text.literal("Filters Copied!"), Formatting.GRAY);
                        editor.playClickSound(1.0f);
                    } else if (session.currentState == EditorState.DRAW_LINES) {
                        int tabMode = session.currentTab;
                        if (tabMode == 0) ChestConfigManager.getInstance().copyLinesToClipboard();
                        else if (tabMode == 1) ChestConfigManager.getInstance().copyBackgroundsToClipboard();
                        else ChestConfigManager.getInstance().copyAllToClipboard();
                        editor.showStatus(Text.literal("Copied!"), Formatting.GRAY);
                        editor.playClickSound(1.0f);
                    }
                    return true;
                }
                // Ctrl + V (Pegar)
                else if (input.key() == GLFW.GLFW_KEY_V) {
                    if (session.currentState == EditorState.VIEW_GROUPS) {
                        if (ChestConfigManager.getInstance().hasWhitelistClipboardData()) {
                            ChestConfigManager.getInstance().saveSnapshot(); // Gatillo de la instantánea
                            ChestConfigManager.getInstance().pasteWhitelistsFromClipboard();
                            editor.saveSmart();
                            editor.sendWhitelistToServer();

                            if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.currentScreenHandler instanceof net.minecraft.screen.GenericContainerScreenHandler genericHandler) {
                                if (genericHandler.getInventory() instanceof io.github.marcsanzdev.chestseparators.access.IWhitelistProvider provider) {
                                    provider.setWhitelists(ChestConfigManager.getInstance().getCurrentWhitelists());
                                }
                            }
                            editor.showStatus(Text.literal("Filters Pasted!"), Formatting.GREEN);
                            editor.playClickSound(1.0f);
                        } else { editor.playClickSound(0.5f); }
                    } else if (session.currentState == EditorState.DRAW_LINES) {
                        int tabMode = session.currentTab;
                        boolean pasted = false;

                        if (tabMode == 0 && ChestConfigManager.getInstance().hasLinesClipboardData()) {
                            ChestConfigManager.getInstance().saveSnapshot(); // Gatillo de la instantánea
                            ChestConfigManager.getInstance().pasteLinesFromClipboard();
                            pasted = true;
                        } else if (tabMode == 1 && ChestConfigManager.getInstance().hasBackgroundsClipboardData()) {
                            ChestConfigManager.getInstance().saveSnapshot(); // Gatillo de la instantánea
                            ChestConfigManager.getInstance().pasteBackgroundsFromClipboard();
                            pasted = true;
                        } else if (tabMode == 2 && ChestConfigManager.getInstance().hasClipboardData()) {
                            ChestConfigManager.getInstance().saveSnapshot(); // Gatillo de la instantánea
                            ChestConfigManager.getInstance().pasteFromClipboard();
                            pasted = true;
                        }

                        if (pasted) {
                            editor.saveSmart();
                            editor.showStatus(Text.literal("Pasted!"), Formatting.GREEN);
                            editor.playClickSound(1.0f);
                        } else { editor.playClickSound(0.5f); }
                    }
                    return true;
                }
            }
        }

        // --- TECLA ESCAPE ---
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && session.currentState != EditorState.HIDDEN) {
            if (session.isColorPickerOpen) {
                session.isColorPickerOpen = false;
            } else {
                editor.toggleState(EditorState.HIDDEN);
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (session.currentState == EditorState.EDIT_FILTER) {
            return editor.screenEditFilter.charTyped(input);
        }
        return false;
    }

    private boolean allowMouseClick(Screen _screen, Click context) {
        layout.update(screen, accessor, editor.getSidebarYOffset());
        double mouseX = context.x();
        double mouseY = context.y();
        int button = context.button();

        // --- EL MURO: Bloqueo absoluto si el Color Picker está abierto ---
        if (session.isColorPickerOpen) {
            editor.screenColorPicker.mouseClicked(mouseX, mouseY, button);
            return false; // Al devolver false, matamos el clic. Nada más se enterará.
        }

        boolean isFilterMenuOpen = (session.currentState == EditorState.EDIT_FILTER);
        boolean isEditorClosed = (session.currentState == EditorState.HIDDEN);

        // --- 1. Botones Lápiz y Whitelist ---
        // Se pueden clicar a menos que estemos dentro del menú de editar un filtro concreto
        if (!isFilterMenuOpen) {
            if (GlobalChestConfig.isShowEditButton()) {
                if (editor.entryButton != null && editor.entryButton.mouseClicked(mouseX, mouseY, button)) {
                    return false; // Consumimos el clic
                }
                if (editor.whitelistButton != null && editor.whitelistButton.mouseClicked(mouseX, mouseY, button)) {
                    return false; // Consumimos el clic
                }
            }
        }

        // --- 2. Botón de Depositar (EL ARREGLO) ---
        // SOLO procesa el clic si TODOS los menús de edición están completamente cerrados
        if (isEditorClosed && GlobalChestConfig.instance.showDepositButton) {
            if (editor.depositButton != null && editor.depositButton.mouseClicked(mouseX, mouseY, button)) {
                return false;
            }
        }

        // Si el editor está cerrado y no hemos clicado nuestros botones, dejamos que Vanilla haga su magia
        if (!GlobalChestConfig.isShowEditButton() || isEditorClosed) {
            return true;
        }

        // --- RUTAS DE CLICS INTERNOS DEL EDITOR ---
        if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
            if (editor.screenViewGroups.mouseClicked(mouseX, mouseY, button)) return false;
            return false; // El clic llegó al fondo de ViewGroups
        }

        if (session.currentState == EditorState.EDIT_FILTER) {
            if (editor.screenEditFilter.mouseClicked(mouseX, mouseY, button)) return false;
            return false; // El clic llegó al fondo de EditFilter
        }

        if (editor.screenDrawLines.mouseClicked(mouseX, mouseY, button)) return false;

        return false;
    }
}