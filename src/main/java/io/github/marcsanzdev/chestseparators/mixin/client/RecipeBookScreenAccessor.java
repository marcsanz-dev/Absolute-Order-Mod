package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.client.gui.screen.ingame.RecipeBookScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the vanilla recipe book widget of recipe-book-bearing screens (the survival inventory, crafting
// table, etc.) so the editor can force it closed while a mod sub-screen is open: an open book covers the
// editor's panels and shoves the whole GUI aside, which is only in the way there.
@Mixin(RecipeBookScreen.class)
public interface RecipeBookScreenAccessor {

    @Accessor("recipeBook")
    RecipeBookWidget<?> chestseparators$getRecipeBook();
}
