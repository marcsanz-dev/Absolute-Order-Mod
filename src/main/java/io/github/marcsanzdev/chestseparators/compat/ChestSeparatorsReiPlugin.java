package io.github.marcsanzdev.chestseparators.compat;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

import java.util.Collections;

public class ChestSeparatorsReiPlugin implements REIClientPlugin {

    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(HandledScreen.class, screen -> {
            ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();

            // Si el editor está activo (no está oculto), reclamamos toda la pantalla
            if (editor != null && editor.getSession() != null && editor.getSession().currentState != EditorState.HIDDEN) {
                return Collections.singletonList(new Rectangle(0, 0, 10000, 10000));
            }

            return Collections.emptyList();
        });
    }
}