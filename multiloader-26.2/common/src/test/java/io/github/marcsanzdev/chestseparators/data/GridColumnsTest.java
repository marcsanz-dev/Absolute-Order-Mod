package io.github.marcsanzdev.chestseparators.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the container grid-width helpers in {@link ChestConfigManager}: per-namespace columns, row/column
 * of a key, grid detection from slot positions, and that the player-key offset clears every chest size.
 */
class GridColumnsTest {

    @AfterEach
    void restoreColumns() {
        ChestConfigManager.setContainerColumns(9);
    }

    @Test
    void defaultsToNineColumns() {
        assertEquals(9, ChestConfigManager.gridColumns(0));
        ChestConfigManager.setContainerColumns(0);
        assertEquals(9, ChestConfigManager.gridColumns(0));
    }

    @Test
    void containerWidthNeverAppliesToInventoryKeys() {
        ChestConfigManager.setContainerColumns(14);
        int inv = ChestConfigManager.PLAYER_KEY_OFFSET;
        assertEquals(14, ChestConfigManager.gridColumns(125));
        assertEquals(9, ChestConfigManager.gridColumns(inv));
        assertEquals(9, ChestConfigManager.gridColumns(inv + 40));
    }

    @Test
    void rowAndColumnAreRelativeToTheNamespace() {
        ChestConfigManager.setContainerColumns(14);
        assertEquals(1, ChestConfigManager.gridRow(14));
        assertEquals(0, ChestConfigManager.gridCol(14));
        assertEquals(8, ChestConfigManager.gridRow(125));
        assertEquals(13, ChestConfigManager.gridCol(125));

        int inv = ChestConfigManager.PLAYER_KEY_OFFSET;
        assertEquals(0, ChestConfigManager.gridRow(inv + 8));
        assertEquals(8, ChestConfigManager.gridCol(inv + 8));
        assertEquals(3, ChestConfigManager.gridRow(inv + 35));
        assertEquals(8, ChestConfigManager.gridCol(inv + 35));
    }

    @Test
    void playerKeyOffsetClearsTheLargestChestAndKeepsTheNineWideGridAligned() {
        // Netherite (Iron Chests) is the largest known container at 126 slots.
        assertFalse(ChestConfigManager.isInventoryKey(125));
        assertTrue(ChestConfigManager.isInventoryKey(ChestConfigManager.PLAYER_KEY_OFFSET));
        assertEquals(0, ChestConfigManager.PLAYER_KEY_OFFSET % 9);
    }

    @Test
    void detectsRowMajorGrids() {
        assertEquals(9, ChestConfigManager.detectGridColumns(xs(9, 3, 8), ys(9, 3, 18)));
        assertEquals(12, ChestConfigManager.detectGridColumns(xs(12, 9, 8), ys(12, 9, 18)));
        assertEquals(14, ChestConfigManager.detectGridColumns(xs(14, 9, 8), ys(14, 9, 18)));
    }

    @Test
    void rejectsLayoutsThatAreNotOneGrid() {
        // Ragged last row.
        int[] x = xs(9, 3, 8);
        int[] y = ys(9, 3, 18);
        assertEquals(0, ChestConfigManager.detectGridColumns(
                java.util.Arrays.copyOf(x, 26), java.util.Arrays.copyOf(y, 26)));

        // A gap between two columns.
        int[] gapX = xs(9, 3, 8);
        for (int i = 0; i < gapX.length; i++) if (i % 9 >= 5) gapX[i] += 4;
        assertEquals(0, ChestConfigManager.detectGridColumns(gapX, y));

        // Slots stored column-major instead of row-major.
        int[] colX = new int[27];
        int[] colY = new int[27];
        for (int i = 0; i < 27; i++) {
            colX[i] = 8 + (i / 3) * 18;
            colY[i] = 18 + (i % 3) * 18;
        }
        assertEquals(0, ChestConfigManager.detectGridColumns(colX, colY));

        assertEquals(0, ChestConfigManager.detectGridColumns(new int[0], new int[0]));
    }

    private static int[] xs(int cols, int rows, int x0) {
        int[] out = new int[cols * rows];
        for (int i = 0; i < out.length; i++) out[i] = x0 + (i % cols) * 18;
        return out;
    }

    private static int[] ys(int cols, int rows, int y0) {
        int[] out = new int[cols * rows];
        for (int i = 0; i < out.length; i++) out[i] = y0 + (i / cols) * 18;
        return out;
    }
}
