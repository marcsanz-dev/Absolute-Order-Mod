package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Compact draggable Keep slider: All, 64, 32, 16, 1 marks with exact in-between values. */
public final class InventoryRetentionSlider extends CustomWidget {
    private final IntSupplier current;
    private final IntConsumer update;
    private boolean dragging;
    private int heldDetent = -1;

    // A short magnetic pull onto stack-size marks, followed by a slightly wider release
    // threshold, makes 16/32/64 easy to hit without changing their saved numeric values.
    private static final double DETENT_ENTER_PX = 2.0;
    private static final double DETENT_RELEASE_PX = 4.0;

    public InventoryRetentionSlider(int x, int y, int width, int height,
            IntSupplier current, IntConsumer update) {
        super(x, y, width, height, null);
        this.current = current;
        this.update = update;
        this.tooltipText = "Keep: drag for 1-64 (marks: All / 64 / 32 / 16 / 1); scroll for exact +/-1";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || isDisabled || !isHovering(mouseX, mouseY)) return false;
        dragging = true;
        heldDetent = -1;
        updateFromMouse(mouseX);
        return true;
    }

    public boolean mouseDragged(double mouseX, int button) {
        if (!dragging || button != 0) return false;
        updateFromMouse(mouseX);
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isDisabled || !isHovering(mouseX, mouseY) || delta == 0) return false;
        update.accept(io.github.marcsanzdev.chestseparators.util.InventoryRetentionSteps.adjustByWheel(
                current.getAsInt(), delta > 0 ? 1 : -1));
        return true;
    }

    public void stopDrag() {
        dragging = false;
        heldDetent = -1;
    }

    private int trackStart() {
        return x + 6;
    }

    private int trackEnd() {
        return x + width - 6;
    }

    private void updateFromMouse(double mouseX) {
        int span = Math.max(1, trackEnd() - trackStart());
        double fraction = Math.max(0, Math.min(1, (mouseX - trackStart()) / span));
        // Stay on an engaged detent until the pointer moves farther away than the
        // entrance radius; this avoids jitter around 16/32/64 while dragging.
        if (heldDetent >= 0) {
            int stillHeld = io.github.marcsanzdev.chestseparators.util.InventoryRetentionSteps
                    .detentNearFraction(fraction, span, DETENT_RELEASE_PX);
            if (stillHeld == heldDetent) {
                update.accept(heldDetent);
                return;
            }
            heldDetent = -1;
        }
        heldDetent = io.github.marcsanzdev.chestseparators.util.InventoryRetentionSteps
                .detentNearFraction(fraction, span, DETENT_ENTER_PX);
        update.accept(heldDetent >= 0
                ? heldDetent
                : io.github.marcsanzdev.chestseparators.util.InventoryRetentionSteps.valueAtFraction(fraction));
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (isDisabled) return;
        int value = current.getAsInt();
        boolean hover = isHovering(mouseX, mouseY);
        io.github.marcsanzdev.chestseparators.client.ui.UiTheme.button(
                context, x, y, width, height, hover, false);
        String label = value <= 0 ? "Keep: All" : "Keep: " + value;
        context.pose().pushMatrix();
        context.pose().translate(x + 3.0f, y + 1.0f);
        context.pose().scale(0.62f, 0.62f);
        context.text(Minecraft.getInstance().font, label, 0, 0,
                io.github.marcsanzdev.chestseparators.client.ui.UiTheme.TEXT, false);
        context.pose().popMatrix();

        // Use virtually the entire 108px widget as a track; the label sits ABOVE it.
        int start = trackStart();
        int end = trackEnd();
        int lineY = y + height - 2;
        context.fill(start, lineY, end + 1, lineY + 1, 0xFF626262);
        int marks = io.github.marcsanzdev.chestseparators.util.InventoryRetentionSteps.count();
        for (int i = 0; i < marks; i++) {
            int px = start + (end - start) * i / (marks - 1);
            context.fill(px, lineY - 2, px + 1, lineY + 2, 0xFF989898);
        }
        int knob = start + (int) Math.round((end - start) *
                io.github.marcsanzdev.chestseparators.util.InventoryRetentionSteps.fractionForValue(value));
        context.fill(knob - 2, lineY - 3, knob + 3, lineY + 3, 0xFFDEDEDE);
        if (hover && tooltipText != null) {
            context.setTooltipForNextFrame(Minecraft.getInstance().font,
                    Component.literal(tooltipText), mouseX, mouseY);
        }
    }
}
