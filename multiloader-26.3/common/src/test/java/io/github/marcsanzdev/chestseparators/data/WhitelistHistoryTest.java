package io.github.marcsanzdev.chestseparators.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the whitelist undo/redo stack and the whitelist clipboard in
 * {@link ChestConfigManager}, exercising the {@link UndoRedoHistory} wiring for filter data.
 */
class WhitelistHistoryTest {

    private final ChestConfigManager manager = ChestConfigManager.getInstance();

    private static SlotWhitelist wl(String... items) {
        return new SlotWhitelist(UUID.randomUUID(), List.of(items), true, true, true);
    }

    @BeforeEach
    void reset() {
        manager.clearCurrentConfig();
        manager.clearHistory();
        manager.setCurrentWhitelists(Map.of());
    }

    @Test
    void undoRestoresEmptyWhitelist() {
        manager.saveWhitelistSnapshot();
        manager.setCurrentWhitelists(Map.of(0, wl("minecraft:apple")));

        assertTrue(manager.canUndoWhitelist());
        manager.undoWhitelist();

        Map<Integer, SlotWhitelist> restored = manager.getCurrentWhitelists();
        assertTrue(restored == null || restored.isEmpty());
    }

    @Test
    void redoReappliesWhitelistAfterUndo() {
        manager.saveWhitelistSnapshot();
        SlotWhitelist apple = wl("minecraft:apple");
        manager.setCurrentWhitelists(Map.of(0, apple));

        manager.undoWhitelist();
        assertTrue(manager.canRedoWhitelist());
        manager.redoWhitelist();

        Map<Integer, SlotWhitelist> current = manager.getCurrentWhitelists();
        assertNotNull(current);
        assertEquals(1, current.size());
        assertEquals(List.of("minecraft:apple"), current.get(0).allowedItems());
    }

    @Test
    void multipleSnapshotsUndoInOrder() {
        // snapshot A: empty
        manager.saveWhitelistSnapshot();
        manager.setCurrentWhitelists(Map.of(1, wl("minecraft:stone")));

        // snapshot B: one entry
        manager.saveWhitelistSnapshot();
        manager.setCurrentWhitelists(Map.of(1, wl("minecraft:stone"), 2, wl("minecraft:dirt")));

        // undo to B
        manager.undoWhitelist();
        assertEquals(1, manager.getCurrentWhitelists().size());

        // undo to A
        manager.undoWhitelist();
        assertTrue(manager.getCurrentWhitelists() == null
                || manager.getCurrentWhitelists().isEmpty());

        assertFalse(manager.canUndoWhitelist());
    }

    @Test
    void whitelistClipboardCopyAndPaste() {
        manager.setCurrentWhitelists(Map.of(5, wl("minecraft:oak_log")));
        manager.copyWhitelistsToClipboard();
        assertTrue(manager.hasWhitelistClipboardData());

        // Clear and paste
        manager.setCurrentWhitelists(Map.of());
        manager.pasteWhitelistsFromClipboard();

        Map<Integer, SlotWhitelist> result = manager.getCurrentWhitelists();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(List.of("minecraft:oak_log"), result.get(5).allowedItems());
    }
}
