package io.github.marcsanzdev.chestseparators.data;

import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_BOTTOM;
import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_LEFT;
import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_RIGHT;
import static io.github.marcsanzdev.chestseparators.data.ChestConfigManager.ACTION_TOP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ChestConfigManager#getContiguousSlots} — the BFS that finds all slots reachable
 * from a starting slot without crossing a painted separator line.
 *
 * <p>Uses a 27-slot (3×9) single chest layout for all cases.
 */
class ContiguousSlotsTest {

    private static final int MAX_SLOTS = 27;
    private static final int RED = 0xFFAA0000;

    private final ChestConfigManager manager = ChestConfigManager.getInstance();

    @BeforeEach
    void reset() {
        manager.clearCurrentConfig();
    }

    @Test
    void withNoSeparators_allSlotsAreContiguous() {
        Set<Integer> result = manager.getContiguousSlots(0, 0, MAX_SLOTS);
        assertEquals(MAX_SLOTS, result.size());
    }

    @Test
    void topLineOnSlot9_splitsTopAndBottomRows() {
        // Draw a TOP separator on every slot of the second row (slots 9–17).
        // This creates a horizontal wall between row 0 and row 1.
        for (int col = 0; col < 9; col++) {
            manager.paintAction(9 + col, ACTION_TOP, RED);
        }

        Set<Integer> topRegion = manager.getContiguousSlots(0, 0, MAX_SLOTS);
        Set<Integer> bottomRegion = manager.getContiguousSlots(9, 0, MAX_SLOTS);

        // Top region (row 0, slots 0–8) must not reach row 1+
        assertEquals(9, topRegion.size());
        for (int i = 0; i < 9; i++) assertTrue(topRegion.contains(i));

        // Bottom region (rows 1–2, slots 9–26) must not reach row 0
        assertEquals(18, bottomRegion.size());
        assertFalse(bottomRegion.contains(0));
    }

    @Test
    void rightLineOnSlot4_splitsColumnRegions() {
        // A RIGHT separator on slot 4 (middle of row 0) and its mirror LEFT on slot 5
        // together form a vertical wall between columns 4 and 5 in row 0.
        // Only apply within row 0 to isolate the effect.
        manager.paintAction(4, ACTION_RIGHT, RED);
        manager.paintAction(5, ACTION_LEFT, RED);

        Set<Integer> leftRegion = manager.getContiguousSlots(0, 0, MAX_SLOTS);
        // The wall only covers row 0; rows 1–2 reconnect the two sides via the cells below.
        // So starting from slot 0 we expect to reach ALL slots (the region wraps around rows 1–2).
        // This verifies the BFS does NOT stop at the wall when there is a path around it.
        assertTrue(leftRegion.contains(8));
    }

    @Test
    void fullyEnclosedRegion_returnsOnlyItsSlots() {
        // Enclose slot 13 (centre of a 3×9 chest: row 1, col 4) on all four sides.
        manager.paintAction(13, ACTION_TOP, RED);
        manager.paintAction(13, ACTION_BOTTOM, RED);
        manager.paintAction(13, ACTION_LEFT, RED);
        manager.paintAction(13, ACTION_RIGHT, RED);

        Set<Integer> isolated = manager.getContiguousSlots(13, 0, MAX_SLOTS);
        assertEquals(1, isolated.size());
        assertTrue(isolated.contains(13));
    }

    @Test
    void outOfBoundsStart_returnsEmptySet() {
        Set<Integer> result = manager.getContiguousSlots(-1, 0, MAX_SLOTS);
        assertTrue(result.isEmpty());

        result = manager.getContiguousSlots(MAX_SLOTS, 0, MAX_SLOTS);
        assertTrue(result.isEmpty());
    }
}
