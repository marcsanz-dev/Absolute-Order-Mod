package io.github.marcsanzdev.chestseparators.data;

import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_BG;
import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_TOP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the background-only and copyAll clipboard paths in {@link ChestConfigManager}. The
 * lines-only clipboard is already covered by {@link ChestConfigManagerTest}.
 */
class ClipboardTest {

    private static final int RED = 0xFFAA0000;
    private static final int BLUE = 0xFF0000AA;

    private final ChestConfigManager manager = ChestConfigManager.getInstance();

    @BeforeEach
    void reset() {
        manager.clearCurrentConfig();
    }

    @Test
    void backgroundsClipboardCopiesOnlyBgChannel() {
        manager.paintAction(3, ACTION_TOP, RED);
        manager.paintAction(3, ACTION_BG, BLUE);
        manager.copyBackgroundsToClipboard();
        assertTrue(manager.hasBackgroundsClipboardData());

        manager.clearCurrentConfig();
        manager.pasteBackgroundsFromClipboard();

        // Background must be present
        assertEquals(BLUE, manager.getColor(3, ACTION_BG));
        // The line channel must not have been copied to the backgrounds clipboard
        assertEquals(0, manager.getColor(3, ACTION_TOP));
    }

    @Test
    void copyAllPopulatesAllThreeClipboards() {
        manager.paintAction(0, ACTION_TOP, RED);
        manager.paintAction(0, ACTION_BG, BLUE);
        manager.copyAllToClipboard();

        assertTrue(manager.hasClipboardData());
        assertTrue(manager.hasLinesClipboardData());
        assertTrue(manager.hasBackgroundsClipboardData());
    }

    @Test
    void pasteDoesNotAffectUnrelatedSlots() {
        manager.paintAction(7, ACTION_BG, RED);
        manager.copyToClipboard();

        manager.clearCurrentConfig();
        manager.paintAction(2, ACTION_TOP, BLUE);
        manager.pasteFromClipboard();

        // Slot 7 restored from clipboard
        assertEquals(RED, manager.getColor(7, ACTION_BG));
        // Paste replaces the whole config, so slot 2 overwritten
        assertEquals(0, manager.getColor(2, ACTION_TOP));
    }

    @Test
    void clearHistoryResetsUndoRedoAvailability() {
        manager.saveSnapshot();
        manager.paintAction(1, ACTION_TOP, RED);
        assertTrue(manager.canUndo());

        manager.clearHistory();

        assertFalse(manager.canUndo());
        assertFalse(manager.canRedo());
    }

    @Test
    void customColors_setAndGetRoundTrip() {
        manager.setCustomColor(0, 0xFF112233, 0); // tab=0 (lines)
        manager.setCustomColor(3, 0xFF445566, 1); // tab=1 (backgrounds)
        manager.setCustomColor(7, 0xFF778899, 2); // tab=2 (combo)

        assertEquals(0xFF112233, manager.getCustomColors(0)[0]);
        assertEquals(0xFF445566, manager.getCustomColors(1)[3]);
        assertEquals(0xFF778899, manager.getCustomColors(2)[7]);
    }
}
