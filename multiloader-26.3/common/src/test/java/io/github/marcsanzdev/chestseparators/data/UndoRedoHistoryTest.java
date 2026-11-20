package io.github.marcsanzdev.chestseparators.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the generic undo/redo history that backs both the separator config and the whitelists. */
class UndoRedoHistoryTest {

    private static UndoRedoHistory<List<Integer>> newHistory(int maxSteps) {
        return new UndoRedoHistory<>(ArrayList::new, maxSteps);
    }

    @Test
    void emptyHistoryHasNothingToUndoOrRedo() {
        UndoRedoHistory<List<Integer>> history = newHistory(10);
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
        assertNull(history.undo(new ArrayList<>()));
        assertNull(history.redo(new ArrayList<>()));
    }

    @Test
    void undoReturnsThePreviousSnapshotAndEnablesRedo() {
        UndoRedoHistory<List<Integer>> history = newHistory(10);

        List<Integer> state = new ArrayList<>(List.of(1));
        history.push(state); // restore point: [1]
        state.add(2); // live state is now [1, 2]

        List<Integer> undone = history.undo(state);
        assertEquals(List.of(1), undone);
        assertTrue(history.canRedo());
        assertFalse(history.canUndo());

        List<Integer> redone = history.redo(undone);
        assertEquals(List.of(1, 2), redone);
    }

    @Test
    void pushDiscardsRedoHistory() {
        UndoRedoHistory<List<Integer>> history = newHistory(10);
        history.push(new ArrayList<>(List.of(1)));
        history.undo(new ArrayList<>(List.of(1, 2)));
        assertTrue(history.canRedo());

        history.push(new ArrayList<>(List.of(9)));
        assertFalse(history.canRedo());
    }

    @Test
    void snapshotsAreDeepCopiesIndependentOfLaterMutation() {
        UndoRedoHistory<List<Integer>> history = newHistory(10);
        List<Integer> state = new ArrayList<>(List.of(1));

        history.push(state);
        state.add(99); // must not leak into the stored snapshot

        assertEquals(List.of(1), history.undo(state));
    }

    @Test
    void undoStackIsBoundedByMaxSteps() {
        UndoRedoHistory<List<Integer>> history = newHistory(2);
        history.push(new ArrayList<>(List.of(1)));
        history.push(new ArrayList<>(List.of(2)));
        history.push(new ArrayList<>(List.of(3)));

        // Only the two most recent restore points survive.
        assertEquals(List.of(3), history.undo(new ArrayList<>(List.of(4))));
        assertEquals(List.of(2), history.undo(new ArrayList<>(List.of(3))));
        assertFalse(history.canUndo());
    }

    @Test
    void clearEmptiesBothStacks() {
        UndoRedoHistory<List<Integer>> history = newHistory(10);
        history.push(new ArrayList<>(List.of(1)));
        history.undo(new ArrayList<>(List.of(1, 2)));

        history.clear();
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }
}
