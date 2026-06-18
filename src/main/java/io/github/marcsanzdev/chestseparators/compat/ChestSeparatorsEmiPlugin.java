package io.github.marcsanzdev.chestseparators.compat;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;

@EmiEntrypoint
public class ChestSeparatorsEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea((screen, consumer) -> {
            ChestSeparatorsEditor editor = ChestSeparatorsEditor.getInstance();

            // While the editor is active, claim the entire screen to prevent EMI from rendering over it.
            if (editor != null && editor.getSession() != null && editor.getSession().currentState != EditorState.HIDDEN) {
                consumer.accept(new Bounds(0, 0, 10000, 10000));
            }
        });
    }
}