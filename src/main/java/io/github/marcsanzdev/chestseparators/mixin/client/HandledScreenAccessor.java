package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Mixin Interface implementing the Accessor Pattern.
// Bypasses Java access modifiers (protected/private) to expose internal layout coordinates
// and state data from the generic HandledScreen class.
// This creates a compile-time safe bridge for the rendering logic to align custom UI elements
// with the vanilla container GUI.
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

    // Exposes the absolute X coordinate of the GUI root on the screen.
    // Required for calculating relative offsets for custom buttons and overlays.
    @Accessor("x")
    int getX();

    // Recenters the GUI root. Used to undo the recipe book's horizontal shift while a mod sub-screen is
    // open, so the container returns to its normal position as if the book were closed.
    @Accessor("x")
    void setX(int x);

    // Exposes the absolute Y coordinate of the GUI root.
    @Accessor("y")
    int getY();

    // Exposes the dynamic width of the container background texture.
    // Used to anchor UI elements relative to the right edge of the container.
    @Accessor("backgroundWidth")
    int getBackgroundWidth();

    // Exposes the dynamic height of the container background texture.
    @Accessor("backgroundHeight")
    int getBackgroundHeight();

    // Retrieves the slot currently hovered by the mouse cursor.
    // Critical for the editor's hit-detection logic (determining where to draw lines).
    @Accessor("focusedSlot")
    Slot getFocusedSlot();

    // Exposes the underlying ScreenHandler (Container).
    // Essential for iterating through the complete list of slots to render
    // persisted separator lines during the draw cycle.
    @Accessor("handler")
    ScreenHandler getHandler();
}
