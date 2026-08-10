package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enruta el scroll del ratón al editor. En 1.12.2 no existe {@code mouseScrolled}: la rueda llega por
 * {@link GuiScreen#handleMouseInput()} vía {@link Mouse#getEventDWheel()}. {@code GuiContainer} NO
 * sobreescribe {@code handleMouseInput} (lo hereda de {@code GuiScreen}), por lo que el inject debe vivir
 * aquí, en {@code GuiScreen} — pero sólo actúa cuando la pantalla es un {@code GuiContainer} con editor
 * activo, así que ninguna otra pantalla se ve afectada. El editor es un singleton
 * ({@link ChestSeparatorsEditor#getInstance()}), fijado en su constructor.
 */
@Mixin(GuiScreen.class)
public abstract class GuiScreenScrollMixin {

    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void chestseparators$routeScroll(CallbackInfo ci) {
        if (!((Object) this instanceof GuiContainer)) return;
        ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();
        if (editor == null) return;

        int dw = Mouse.getEventDWheel();
        if (dw == 0) return;

        GuiScreen self = (GuiScreen) (Object) this;
        Minecraft mc = Minecraft.getMinecraft();
        int mx = Mouse.getEventX() * self.width / mc.displayWidth;
        int my = self.height - Mouse.getEventY() * self.height / mc.displayHeight - 1;
        double amount = Math.signum((double) dw);

        // onMouseScrolled devuelve true = permitir (no consumido); false = consumido → cancela vanilla.
        if (!editor.inputHandler.onMouseScrolled(mx, my, 0.0, amount)) {
            ci.cancel();
        }
    }
}
