package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class EditorInputHandler {

    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorGeometry geometry;
    private final HandledScreen<?> screen;
    private final HandledScreenAccessor accessor;

    private final EditorLayout layout;

    public EditorInputHandler(
            ChestSeparatorsEditor editor,
            EditorSessionData session,
            EditorGeometry geometry,
            EditorLayout layout,
            HandledScreen<?> screen,
            HandledScreenAccessor accessor) {
        this.editor = editor;
        this.session = session;
        this.geometry = geometry;
        this.layout = layout;
        this.screen = screen;
        this.accessor = accessor;
    }

    public void setupInputHandlers() {
        ScreenMouseEvents.allowMouseClick(screen).register(this::allowMouseClick);

        // The inventory screen (InventoryScreen -> RecipeBookScreen) bypasses HandledScreen#render and
        // calls renderMain directly, so the editor overlay mixin injected at HandledScreen#render TAIL
        // never fires there. Draw the overlay via a screen render event for the inventory editor only
        // (chests still use the mixin, so there is no double rendering).
        if (session.isPlayerInventory) {
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen)
                    .register((sc, context, mouseX, mouseY, tickDelta) -> {
                        editor.render(context, mouseX, mouseY, tickDelta);
                        editor.renderNormalModeOverlay(context, mouseX, mouseY);
                    });
        }

        ScreenMouseEvents.allowMouseRelease(screen).register((_screen, context) -> {
            session.isDraggingMainScroll = false;
            session.isDraggingListScroll = false;

            int button = context.button();

            if (session.isColorPickerOpen) {
                editor.screenColorPicker.mouseReleased(context.x(), context.y(), button);
                return true;
            }

            if (button == 0 && session.isDraggingLine) {
                if (session.currentState == EditorState.VIEW_GROUPS
                        || session.currentState == EditorState.SELECT_SLOTS) {

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
                    int totalGridHeight = (int) Math.ceil(session.filteredItems.size() / 9.0) * 18;
                    float maxGridScroll = Math.max(0, totalGridHeight - gridViewH);
                    int thumbH = maxGridScroll > 0
                            ? Math.max(10, (int) ((gridViewH / (float) Math.max(1, totalGridHeight)) * msbH))
                            : msbH;
                    float percent = (float) (mouseY - msbY - (thumbH / 2.0f)) / (msbH - thumbH);
                    session.gridScrollY = MathHelper.clamp(percent * maxGridScroll, 0, maxGridScroll);
                    return false;
                }
                if (session.isDraggingListScroll) {
                    List<String> displayedAllowedItems = new ArrayList<>();
                    String wlSearch = editor.whitelistSearchBox != null
                            ? editor.whitelistSearchBox.getText().toLowerCase()
                            : "";
                    for (String id : session.currentAllowedItems) {
                        Item item = Registries.ITEM.get(Identifier.tryParse(id));
                        if (item != null
                                && (wlSearch.isEmpty()
                                        || item.getName()
                                                .getString()
                                                .toLowerCase()
                                                .contains(wlSearch))) displayedAllowedItems.add(id);
                    }

                    int totalListHeight = displayedAllowedItems.size() * 18
                            + io.github.marcsanzdev.chestseparators.client.ui.EditorLayout.LIST_TRAILING_PADDING;
                    float maxListScroll = layout.maxListScroll(displayedAllowedItems.size());
                    int thumbH = maxListScroll > 0
                            ? Math.max(10, (int) ((listViewH / (float) Math.max(1, totalListHeight)) * lsbH))
                            : lsbH;
                    float percent = (float) (mouseY - lsbY - (thumbH / 2.0f)) / (lsbH - thumbH);
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
        ScreenMouseEvents.allowMouseScroll(screen)
                .register((_screen, mouseX, mouseY, horizontalAmount, verticalAmount) -> {
                    if (session.currentState == EditorState.VIEW_GROUPS
                            || session.currentState == EditorState.SELECT_SLOTS) {
                        if (editor.screenViewGroups.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount))
                            return false;
                    }
                    if (session.currentState == EditorState.EDIT_FILTER) {
                        if (editor.screenEditFilter.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount))
                            return false;
                    }

                    return true;
                });
    }

    public boolean keyPressed(KeyInput input) {
        boolean isControlDown = GLFW.glfwGetKey(
                                MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL)
                        == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL)
                        == GLFW.GLFW_PRESS;

        if (session.currentState == EditorState.EDIT_FILTER) {
            if (editor.screenEditFilter.keyPressed(input)) {
                return true;
            }
        }

        if (isControlDown && session.currentState != EditorState.HIDDEN) {
            boolean isTyping = (session.currentState == EditorState.EDIT_FILTER)
                    && ((editor.searchBox != null && editor.searchBox.isFocused())
                            || (editor.whitelistSearchBox != null && editor.whitelistSearchBox.isFocused()));

            if (!isTyping) {
                // Undo (unified: separators or filters, whichever happened last).
                if (input.key() == GLFW.GLFW_KEY_Z) {
                    editor.applyUndoRedo(ChestConfigManager.getInstance().undo(), false);
                    return true;
                }
                // Redo
                else if (input.key() == GLFW.GLFW_KEY_Y) {
                    editor.applyUndoRedo(ChestConfigManager.getInstance().redo(), true);
                    return true;
                }
                // Copy — whole-chest layout (lines + backgrounds) in DRAW_LINES, all filters in VIEW_GROUPS.
                else if (input.key() == GLFW.GLFW_KEY_C) {
                    if (session.currentState == EditorState.VIEW_GROUPS) {
                        ChestConfigManager.getInstance().copyWhitelistsToClipboard();
                        editor.showStatus(Text.translatable("message.chestseparators.filters_copied"), Formatting.GRAY);
                        editor.playClickSound(1.0f);
                    } else if (session.currentState == EditorState.DRAW_LINES) {
                        ChestConfigManager.getInstance().copyToClipboard();
                        editor.showStatus(Text.translatable("message.chestseparators.layout_copied"), Formatting.GRAY);
                        editor.playClickSound(1.0f);
                    }
                    return true;
                }
                // Paste
                else if (input.key() == GLFW.GLFW_KEY_V) {
                    if (session.currentState == EditorState.VIEW_GROUPS) {
                        if (ChestConfigManager.getInstance().hasWhitelistClipboardData()) {
                            ChestConfigManager.getInstance().saveWhitelistSnapshot();
                            ChestConfigManager.getInstance().pasteWhitelistsFromClipboard();
                            editor.saveSmart();
                            editor.sendWhitelistToServer();

                            if (MinecraftClient.getInstance().player != null
                                    && MinecraftClient.getInstance().player.currentScreenHandler
                                            instanceof GenericContainerScreenHandler genericHandler) {
                                if (genericHandler.getInventory() instanceof IWhitelistProvider provider) {
                                    provider.setWhitelists(
                                            ChestConfigManager.getInstance().getCurrentWhitelists());
                                }
                            }
                            editor.showStatus(
                                    Text.translatable("message.chestseparators.filters_pasted"), Formatting.GREEN);
                            editor.playClickSound(1.0f);
                        } else {
                            editor.playClickSound(0.5f);
                        }
                    } else if (session.currentState == EditorState.DRAW_LINES) {
                        // Paste the whole-chest layout (lines + backgrounds) regardless of the active tab.
                        if (ChestConfigManager.getInstance().hasClipboardData()) {
                            ChestConfigManager.getInstance().saveSnapshot();
                            ChestConfigManager.getInstance().pasteFromClipboard();
                            editor.saveSmart();
                            editor.showStatus(
                                    Text.translatable("message.chestseparators.layout_pasted"), Formatting.GREEN);
                            editor.playClickSound(1.0f);
                        } else {
                            editor.playClickSound(0.5f);
                        }
                    }
                    return true;
                }
            }
        }

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

    public boolean charTyped(CharInput input) {
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

        // While the color picker is open, all clicks outside it must be suppressed.
        if (session.isColorPickerOpen) {
            editor.screenColorPicker.mouseClicked(mouseX, mouseY, button);
            return false;
        }

        boolean isFilterMenuOpen = (session.currentState == EditorState.EDIT_FILTER);
        boolean isEditorClosed = (session.currentState == EditorState.HIDDEN);

        // Edit and whitelist buttons are clickable in all states except the filter edit sub-menu.
        if (!isFilterMenuOpen) {
            if (GlobalChestConfig.isShowEditButton()) {
                if (editor.entryButton != null && editor.entryButton.mouseClicked(mouseX, mouseY, button)) {
                    return false;
                }
                if (editor.whitelistButton != null && editor.whitelistButton.mouseClicked(mouseX, mouseY, button)) {
                    return false;
                }
            }
        }

        // The deposit button is only active when no editor sub-menu is open.
        if (isEditorClosed && GlobalChestConfig.instance.showDepositButton) {
            if (editor.depositButton != null && editor.depositButton.mouseClicked(mouseX, mouseY, button)) {
                return false;
            }
        }

        if (!GlobalChestConfig.isShowEditButton() || isEditorClosed) {
            return true;
        }

        if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
            if (editor.screenViewGroups.mouseClicked(mouseX, mouseY, button)) return false;
            return false;
        }

        if (session.currentState == EditorState.EDIT_FILTER) {
            if (editor.screenEditFilter.mouseClicked(mouseX, mouseY, button)) return false;
            return false;
        }

        if (editor.screenDrawLines.mouseClicked(mouseX, mouseY, button)) return false;

        return false;
    }
}
