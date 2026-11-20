package io.github.marcsanzdev.chestseparators.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class InventoryTransferPolicyTest {
    @Test void legacyZeroProtectsTheEntireMatchingStack() {
        assertEquals(0, InventoryTransferPolicy.movableCount(64, 0, 0));
    }

    @Test void aSingleArrowIsKeptAndSurplusIsReleased() {
        assertEquals(63, InventoryTransferPolicy.movableCount(64, 1, 0));
        assertEquals(0, InventoryTransferPolicy.movableCount(1, 1, 0));
    }

    @Test void groupTargetIsSharedAcrossSeveralSlots() {
        assertEquals(0, InventoryTransferPolicy.movableCount(32, 64, 0));
        assertEquals(32, InventoryTransferPolicy.movableCount(64, 64, 32));
        assertEquals(64, InventoryTransferPolicy.movableCount(64, 1, 1));
    }

    @Test void pullRespectsTargetExceptWhenUnlimited() {
        assertEquals(1, InventoryTransferPolicy.inboundRoom(1, 0));
        assertEquals(0, InventoryTransferPolicy.inboundRoom(1, 1));
        assertEquals(0, InventoryTransferPolicy.inboundRoom(16, 20));
        assertEquals(12, InventoryTransferPolicy.inboundRoom(16, 4));
        assertEquals(Integer.MAX_VALUE, InventoryTransferPolicy.inboundRoom(0, 64));
    }
}
