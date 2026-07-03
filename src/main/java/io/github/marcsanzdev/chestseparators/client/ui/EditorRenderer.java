package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class EditorRenderer {

    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final HandledScreen<?> screen;
    private final HandledScreenAccessor accessor;

    private final EditorLayout layout;

    public EditorRenderer(ChestSeparatorsEditor editor, EditorSessionData session, EditorLayout layout, HandledScreen<?> screen, HandledScreenAccessor accessor) {
        this.editor = editor;
        this.session = session;
        this.layout = layout;
        this.screen = screen;
        this.accessor = accessor;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        layout.update(screen, accessor, editor.getSidebarYOffset());
        editor.syncClientInventoryWhitelists(ChestConfigManager.getInstance().getCurrentWhitelists());

        boolean showButton = GlobalChestConfig.instance.showEditButtons;
        boolean showDeposit = GlobalChestConfig.instance.showDepositButton;

        boolean hideInFilter = (session.currentState == EditorState.EDIT_FILTER);
        boolean hideVanilla = session.isColorPickerOpen || hideInFilter || session.hasSelectionConflict;
        boolean isEditorClosed = (session.currentState == EditorState.HIDDEN);

        // --- NUEVO: ESTADOS VISUALES DE LOS BOTONES (PRESIONADO/SUELTO) ---
        if (editor.entryButton != null) {
            // El lápiz se queda presionado si estamos dibujando líneas
            editor.entryButton.isActive = (session.currentState == EditorState.DRAW_LINES);
        }
        if (editor.whitelistButton != null) {
            // El filtro se queda presionado si estamos en cualquiera de sus submenús
            editor.whitelistButton.isActive = (session.currentState == EditorState.VIEW_GROUPS ||
                    session.currentState == EditorState.SELECT_SLOTS ||
                    session.currentState == EditorState.EDIT_FILTER);
        }
        if (editor.depositButton != null) {
            // El botón de depósito se presiona durante 150 milisegundos al hacerle clic
            editor.depositButton.isActive = (System.currentTimeMillis() - editor.depositClickTime < 150);
        }

        int bgMouseX = session.hasSelectionConflict ? -1 : mouseX;
        int bgMouseY = session.hasSelectionConflict ? -1 : mouseY;

        // --- 1. CAPA DE FONDO (Detrás de la sombra) ---
        if (showButton) {
            // Si estamos en un menú (Filter o ColorPicker), los pintamos SIN HOVER (-1)
            // para que se vean de fondo pero no reaccionen al ratón visualmente.
            int bMouseX = hideVanilla ? -1 : bgMouseX;
            int bMouseY = hideVanilla ? -1 : bgMouseY;
            if (editor.entryButton != null) editor.entryButton.render(context, bMouseX, bMouseY, delta);
            if (editor.whitelistButton != null) editor.whitelistButton.render(context, bMouseX, bMouseY, delta);
        }

        // El botón de depósito solo existe en la vista normal del cofre
        if (showDeposit && isEditorClosed) {
            if (editor.depositButton != null) editor.depositButton.render(context, bgMouseX, bgMouseY, delta);
        }

        if (session.currentState != EditorState.HIDDEN) {
            // Sombra oscura
            if (!session.isEyedropperActive) {
                context.fill(0, 0, layout.screenWidth, layout.screenHeight, 0x66000000);
            }

            // --- 2. CAPA DE PRIMER PLANO (Encima de la sombra) ---
            // Solo los dibujamos aquí si NO estamos en un sub-menú (Filter o ColorPicker).
            // Esto hace que el clic "atraviese" la sombra y llegue a la pantalla de abajo.
            if (showButton && !hideVanilla) {
                if (editor.entryButton != null) editor.entryButton.render(context, bgMouseX, bgMouseY, delta);
                if (editor.whitelistButton != null) editor.whitelistButton.render(context, bgMouseX, bgMouseY, delta);
            }

            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float)accessor.getX(), (float)accessor.getY());
            renderSavedLinesLayer(context);
            context.getMatrices().popMatrix();

            switch (session.currentState) {
                case DRAW_LINES -> editor.screenDrawLines.render(context, mouseX, mouseY, delta);
                case VIEW_GROUPS, SELECT_SLOTS -> editor.screenViewGroups.render(context, mouseX, mouseY, delta);
                case EDIT_FILTER -> editor.screenEditFilter.render(context, mouseX, mouseY, delta);
                default -> {}
            }

            renderStatusMessage(context);

            if (session.isColorPickerOpen) {
                editor.screenColorPicker.render(context, mouseX, mouseY, delta);
            }
        }
    }

    public void renderSavedLinesLayer(DrawContext context) {
        ChestConfigManager manager = ChestConfigManager.getInstance();

        // Calculamos el valor alfa (0-255) a partir del porcentaje (0-100)
        int bgAlpha = (GlobalChestConfig.instance.bgTransparency * 255 / 100) << 24;
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;

        for (Slot s : accessor.getHandler().slots) {
            if (s.inventory instanceof PlayerInventory) continue;

            int bgColor = manager.getColor(s.getIndex(), ChestConfigManager.ACTION_BG);
            if (bgColor != 0) context.fill(s.x, s.y, s.x + 16, s.y + 16, (bgColor & 0xFFFFFF) | bgAlpha);

            renderLineRaw(context, s.x, s.y, manager.getColor(s.getIndex(), ChestConfigManager.ACTION_TOP), ChestConfigManager.ACTION_TOP, lineAlpha);
            renderLineRaw(context, s.x, s.y, manager.getColor(s.getIndex(), ChestConfigManager.ACTION_BOTTOM), ChestConfigManager.ACTION_BOTTOM, lineAlpha);
            renderLineRaw(context, s.x, s.y, manager.getColor(s.getIndex(), ChestConfigManager.ACTION_LEFT), ChestConfigManager.ACTION_LEFT, lineAlpha);
            renderLineRaw(context, s.x, s.y, manager.getColor(s.getIndex(), ChestConfigManager.ACTION_RIGHT), ChestConfigManager.ACTION_RIGHT, lineAlpha);
        }
    }

    private void renderLineRaw(DrawContext context, int x, int y, int color, int action, int alpha) {
        if (color == 0) return;
        int renderColor = (color & 0x00FFFFFF) | alpha;

        if (action == ChestConfigManager.ACTION_TOP) context.fill(x - 1, y - 1, x + 17, y, renderColor);
        else if (action == ChestConfigManager.ACTION_BOTTOM) context.fill(x - 1, y + 16, x + 17, y + 17, renderColor);
        else if (action == ChestConfigManager.ACTION_LEFT) context.fill(x - 1, y - 1, x, y + 17, renderColor);
        else if (action == ChestConfigManager.ACTION_RIGHT) context.fill(x + 16, y - 1, x + 17, y + 17, renderColor);
    }

    private void renderStatusMessage(DrawContext context) {
        if (session.statusMessage != null) {
            long elapsed = System.currentTimeMillis() - session.statusMessageTime;
            if (elapsed < 2000) {
                int alpha = 255;
                if (elapsed > 1500) alpha = (int) (255 * (1.0f - (elapsed - 1500) / 500.0f));
                int color = (alpha << 24) | 0xFFFFFF;
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, session.statusMessage, screen.width / 2, screen.height - 40, color);
            } else {
                session.statusMessage = null;
            }
        }
    }

    public void renderNormalModeOverlay(DrawContext context, int mouseX, int mouseY) {
        if (!editor.isEditMode()) {
            if (io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.showLeftPanel ||
                    io.github.marcsanzdev.chestseparators.event.KeyInputHandler.isModifierPressed()) {
                editor.screenViewGroups.renderWhitelistPreviewPanel(context, mouseX, mouseY);
            }
        }

        long window = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
        boolean shift = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        boolean hover = false;
        if (!editor.isEditMode() && io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.showDepositButton && editor.depositButton != null) {
            editor.depositButton.tooltipText = shift ?
                    net.minecraft.text.Text.translatable("key.chestseparators.deposit_all").getString() :
                    net.minecraft.text.Text.translatable("key.chestseparators.deposit_filter").getString();

            hover = editor.isHovering(editor.depositButton.x, editor.depositButton.y, 20, 20, mouseX, mouseY);
        }

        if (hover) {
            // --- NUEVO: Si la preview está suspendida (por haber hecho click), no mostramos nada ---
            if (editor.suspendDepositPreview) {
                editor.isHoveringDeposit = false;
                return;
            }

            editor.isHoveringDeposit = true;
            if (editor.depositPreviewShift != shift || editor.previewSourceRemaining.isEmpty()) {
                editor.depositPreviewShift = shift;
                editor.updateDepositPreview(shift);
            }
            renderDepositPreview(context);
        } else {
            editor.isHoveringDeposit = false;
            // --- NUEVO: Al quitar el ratón del botón, quitamos el bloqueo ---
            editor.suspendDepositPreview = false;

            editor.previewSourceRemaining.clear();
            editor.previewTargetIncoming.clear();
        }
    }

    private void renderDepositPreview(DrawContext context) {
        if (!editor.isHoveringDeposit || editor.previewTargetIncoming.isEmpty()) return;

        int guiX = accessor.getX();
        int guiY = accessor.getY();

        // --- 1. INVENTARIO (Ítems salientes) ---
        for (java.util.Map.Entry<Integer, Integer> entry : editor.previewSourceRemaining.entrySet()) {
            net.minecraft.screen.slot.Slot slot = accessor.getHandler().getSlot(entry.getKey());
            int remaining = entry.getValue();
            int x = guiX + slot.x;
            int y = guiY + slot.y;

            // Capa A: Fondo y bordes (Bevel)
            drawVanillaSlotBevel(context, x, y);

            if (remaining > 0) {
                net.minecraft.item.ItemStack visualStack = slot.getStack().copy();
                visualStack.setCount(remaining);
                context.drawItem(visualStack, x, y);

                // ¡NUEVO! Dibujamos la barra de durabilidad si el ítem está gastado
                drawDurabilityBar(context, visualStack, x, y);

                // Capa B: Lavado gris (Afectará al ítem y a la barra de durabilidad)
                context.fill(x, y, x + 16, y + 16, 0xAA8B8B8B);

                // Capa C: Número proyectado
                if (remaining > 1) {
                    drawProjectedCount(context, x, y, remaining, 0xAAFFFFFF);
                }
            } else {
                context.drawItem(slot.getStack(), x, y);
                drawDurabilityBar(context, slot.getStack(), x, y); // ¡NUEVO!
                context.fill(x, y, x + 16, y + 16, 0xAA8B8B8B);
            }
        }

        // --- 2. COFRE (Ítems entrantes) ---
        for (java.util.Map.Entry<Integer, net.minecraft.item.ItemStack> entry : editor.previewTargetIncoming.entrySet()) {
            net.minecraft.screen.slot.Slot slot = accessor.getHandler().getSlot(entry.getKey());
            net.minecraft.item.ItemStack incoming = entry.getValue();
            int x = guiX + slot.x;
            int y = guiY + slot.y;

            int total = incoming.getCount();
            if (slot.hasStack()) total += slot.getStack().getCount();

            drawVanillaSlotBevel(context, x, y);
            context.drawItem(incoming, x, y);
            drawDurabilityBar(context, incoming, x, y);

            // Llamada al método actualizado que ahora gestiona las 4 líneas y respeta el estado vanilla
            drawCustomPreviewGlow(context, x, y, slot.getIndex());

            if (total > 1) {
                drawProjectedCount(context, x, y, total, 0xAAFFFFFF);
            }
        }
    }

    // --- NUEVO: Dibuja la barra de durabilidad clonando la lógica exacta de Vanilla ---
    private void drawDurabilityBar(DrawContext context, net.minecraft.item.ItemStack stack, int x, int y) {
        if (stack.isItemBarVisible()) {
            int step = stack.getItemBarStep();
            int color = stack.getItemBarColor();
            int barX = x + 2;
            int barY = y + 13;
            // Fondo negro de la barra (13x2 píxeles)
            context.fill(barX, barY, barX + 13, barY + 2, 0xFF000000);
            // Color de la durabilidad (Forzamos opacidad total con | 0xFF000000)
            context.fill(barX, barY, barX + step, barY + 1, color | 0xFF000000);
        }
    }

    private void drawProjectedCount(DrawContext context, int x, int y, int count, int color) {
        String text = String.valueOf(count);
        int textW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(text);
        context.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, text, x + 17 - textW, y + 9, color, true);
    }

    private void drawVanillaSlotBevel(DrawContext context, int x, int y) {
        context.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        context.fill(x - 1, y - 1, x + 16, y, 0xFF373737);
        context.fill(x - 1, y, x, y + 16, 0xFF373737);
        context.fill(x, y + 16, x + 17, y + 17, 0xFFFFFFFF);
        context.fill(x + 16, y, x + 17, y + 16, 0xFFFFFFFF);
    }

    // --- NUEVO: Dibuja el brillo del preview respetando la personalización o manteniendo el estilo vanilla ---
    private void drawCustomPreviewGlow(DrawContext context, int x, int y, int slotIndex) {
        ChestConfigManager manager = ChestConfigManager.getInstance();
        int previewAlpha = 0x88000000; // Transparencia para el efecto fantasma del fondo

        // 1. Fondo: Si tiene color personalizado, lo usamos. Si NO, aplicamos el lavado gris fantasma.
        int customBg = manager.getColor(slotIndex, ChestConfigManager.ACTION_BG);
        if (customBg != 0) {
            context.fill(x, y, x + 16, y + 16, (customBg & 0x00FFFFFF) | previewAlpha);
        } else {
            context.fill(x, y, x + 16, y + 16, 0xAA8B8B8B);
        }

        // --- NUEVO: Extraemos la opacidad real de las líneas configurada por el usuario ---
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;

        // 2. Líneas: Las dibujamos con su opacidad real (lineAlpha) para que cubran el bisel gris Vanilla
        renderLineRaw(context, x, y, manager.getColor(slotIndex, ChestConfigManager.ACTION_TOP), ChestConfigManager.ACTION_TOP, lineAlpha);
        renderLineRaw(context, x, y, manager.getColor(slotIndex, ChestConfigManager.ACTION_BOTTOM), ChestConfigManager.ACTION_BOTTOM, lineAlpha);
        renderLineRaw(context, x, y, manager.getColor(slotIndex, ChestConfigManager.ACTION_LEFT), ChestConfigManager.ACTION_LEFT, lineAlpha);
        renderLineRaw(context, x, y, manager.getColor(slotIndex, ChestConfigManager.ACTION_RIGHT), ChestConfigManager.ACTION_RIGHT, lineAlpha);
    }
}