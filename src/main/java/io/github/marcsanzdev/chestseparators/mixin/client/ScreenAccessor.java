package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Screen.class)
public interface ScreenAccessor {
    @Invoker("addDrawableChild")
    <T extends Element & net.minecraft.client.gui.Drawable & net.minecraft.client.gui.Selectable>
            T invokeAddDrawableChild(T drawableElement);
}
