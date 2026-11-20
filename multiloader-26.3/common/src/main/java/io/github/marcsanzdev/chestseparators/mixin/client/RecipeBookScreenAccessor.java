package io.github.marcsanzdev.chestseparators.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the vanilla recipe book component of recipe-book-bearing screens (the survival inventory, crafting
// table, etc.) so the editor can force it closed while a mod sub-screen is open: an open book covers the
// editor's panels and shoves the whole GUI aside, which is only in the way there.
@Mixin(AbstractRecipeBookScreen.class)
public interface RecipeBookScreenAccessor {

    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> chestseparators$getRecipeBook();
}
