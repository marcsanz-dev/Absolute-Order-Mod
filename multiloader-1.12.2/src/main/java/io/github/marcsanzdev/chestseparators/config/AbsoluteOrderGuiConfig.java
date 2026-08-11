package io.github.marcsanzdev.chestseparators.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

/**
 * Forge's stock {@link GuiConfig} screen, fed one navigable sub-group per {@link Configuration}
 * category from {@link ForgeConfigBridge}.
 */
public class AbsoluteOrderGuiConfig extends GuiConfig {

    public AbsoluteOrderGuiConfig(GuiScreen parent) {
        super(parent,
                collectElements(),
                "chestseparators", // modID
                false, // allRequireWorldRestart
                false, // allRequireMcRestart
                "Absolute Order"); // title
    }

    private static List<IConfigElement> collectElements() {
        List<IConfigElement> list = new ArrayList<IConfigElement>();
        Configuration cfg = ForgeConfigBridge.getConfig();
        if (cfg != null) {
            for (String cat : cfg.getCategoryNames()) {
                list.add(new ConfigElement(cfg.getCategory(cat)));
            }
        }
        return list;
    }
}
