package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// RecipeBookWidget#setOpen is protected; this invoker lets the editor close the book (setOpen(false)) while
// a mod sub-screen is open. See GenericContainerScreenMixin and RecipeBookScreenAccessor.
@Mixin(RecipeBookWidget.class)
public interface RecipeBookWidgetInvoker {

    @Invoker("setOpen")
    void chestseparators$setOpen(boolean open);
}
