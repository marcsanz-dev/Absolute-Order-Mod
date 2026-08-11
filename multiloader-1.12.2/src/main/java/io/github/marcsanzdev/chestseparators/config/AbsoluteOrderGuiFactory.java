package io.github.marcsanzdev.chestseparators.config;

import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

/**
 * Wires the "Config" button on the mods list to {@link AbsoluteOrderGuiConfig}. Referenced by the
 * {@code guiFactory} attribute of the {@code @Mod} annotation.
 */
public class AbsoluteOrderGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft mc) {}

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        return new AbsoluteOrderGuiConfig(parent);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}
