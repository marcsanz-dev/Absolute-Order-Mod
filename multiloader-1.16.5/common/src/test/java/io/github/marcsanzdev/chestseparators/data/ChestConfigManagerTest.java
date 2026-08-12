package io.github.marcsanzdev.chestseparators.data;

import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_BG;
import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_BOTTOM;
import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_LEFT;
import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_RIGHT;
import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_TOP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the in-memory separator logic of ChestConfigManager — painting, erasing, the
 * snapshot-backed undo/redo wired to {@link UndoRedoHistory}, and the layout clipboard.
 *
 * <p>Only methods that operate on the in-memory config are touched; file I/O and the
 * client-only world/palette helpers are out of scope here.
 */
class ChestConfigManagerTest {

    private static final int RED = 0xFFAA0000;
    private static final int BLUE = 0xFF0000AA;

    private final ChestConfigManager manager = ChestConfigManager.getInstance();

    @BeforeEach
    void reset() {
        manager.clearCurrentConfig();
    }

    @Test
    void paintSetsOnlyTheTargetedChannels() {
        manager.paintAction(0, ACTION_TOP | ACTION_LEFT, RED);

        assertEquals(RED, manager.getColor(0, ACTION_TOP));
        assertEquals(RED, manager.getColor(0, ACTION_LEFT));
        assertEquals(0, manager.getColor(0, ACTION_BOTTOM));
        assertEquals(0, manager.getColor(0, ACTION_RIGHT));
        assertEquals(0, manager.getColor(0, ACTION_BG));
    }

    @Test
    void removeClearsChannelAndDropsEmptySlots() {
        manager.paintAction(3, ACTION_TOP, RED);
        manager.removeAction(3, ACTION_TOP);

        assertEquals(0, manager.getColor(3, ACTION_TOP));
        // With every channel back to zero, the slot entry is discarded entirely.
        assertEquals(0, manager.getColor(3, ACTION_BG));
    }

    @Test
    void clearAllLinesKeepsBackgrounds() {
        manager.paintAction(5, ACTION_TOP, RED);
        manager.paintAction(5, ACTION_BG, BLUE);

        manager.clearAllLines();

        assertEquals(0, manager.getColor(5, ACTION_TOP));
        assertEquals(BLUE, manager.getColor(5, ACTION_BG));
    }

    @Test
    void clearAllBackgroundsKeepsLines() {
        manager.paintAction(5, ACTION_TOP, RED);
        manager.paintAction(5, ACTION_BG, BLUE);

        manager.clearAllBackgrounds();

        assertEquals(RED, manager.getColor(5, ACTION_TOP));
        assertEquals(0, manager.getColor(5, ACTION_BG));
    }

    @Test
    void undoRestoresThePreSnapshotStateAndRedoReapplies() {
        manager.saveSnapshot(); // restore point: empty
        manager.paintAction(1, ACTION_TOP, RED);
        assertEquals(RED, manager.getColor(1, ACTION_TOP));

        assertTrue(manager.canUndo());
        manager.undo();
        assertEquals(0, manager.getColor(1, ACTION_TOP));

        assertTrue(manager.canRedo());
        manager.redo();
        assertEquals(RED, manager.getColor(1, ACTION_TOP));
    }

    @Test
    void clipboardCopyAndPasteRoundTripsTheLayout() {
        manager.paintAction(2, ACTION_TOP | ACTION_BG, RED);
        manager.copyToClipboard();
        assertTrue(manager.hasClipboardData());

        manager.clearCurrentConfig();
        assertEquals(0, manager.getColor(2, ACTION_TOP));

        manager.pasteFromClipboard();
        assertEquals(RED, manager.getColor(2, ACTION_TOP));
        assertEquals(RED, manager.getColor(2, ACTION_BG));
    }

    @Test
    void linesClipboardCopiesOnlyEdgeChannels() {
        manager.paintAction(4, ACTION_TOP, RED);
        manager.paintAction(4, ACTION_BG, BLUE);
        manager.copyLinesToClipboard();

        manager.clearCurrentConfig();
        manager.pasteLinesFromClipboard();

        assertEquals(RED, manager.getColor(4, ACTION_TOP));
        // The background channel is not part of the lines clipboard.
        assertEquals(0, manager.getColor(4, ACTION_BG));
        assertFalse(manager.hasBackgroundsClipboardData());
    }
}
