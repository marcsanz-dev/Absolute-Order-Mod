package io.github.marcsanzdev.chestseparators.util;

import net.minecraft.util.math.BlockPos;
import java.util.UUID;

/**
 * Functions as a transient state container for context propagation across disjoint system boundaries.
 * <p>
 * Specifically designed to bridge the gap between the initial player interaction event
 * and the subsequent GUI initialization, where direct parameter passing is restricted.
 */
public class ChestPosStorage {

    private static BlockPos lastClickedPos;
    private static String lastClickedDimension;
    private static UUID lastClickedEntityUUID;
    private static boolean isEntityOpened = false;
    private static UUID lastOpenedShulkerUUID = null;

    /**
     * Resets all transient fields to their default values.
     * <p>
     * This should be called whenever a container GUI is closed to prevent
     * state leaking between different interaction sessions.
     */
    public static void clear() {
        lastClickedPos = null;
        lastClickedDimension = null;
        lastClickedEntityUUID = null;
        isEntityOpened = false;
        lastOpenedShulkerUUID = null;
    }

    // --- GETTERS & SETTERS ---

    public static BlockPos getLastClickedPos() {
        return lastClickedPos;
    }

    public static void setLastClickedPos(BlockPos pos) {
        lastClickedPos = pos;
    }

    public static String getLastClickedDimension() {
        return lastClickedDimension;
    }

    public static void setLastClickedDimension(String dimension) {
        lastClickedDimension = dimension;
    }

    public static UUID getLastClickedEntityUUID() {
        return lastClickedEntityUUID;
    }

    public static void setLastClickedEntityUUID(UUID uuid) {
        lastClickedEntityUUID = uuid;
    }

    public static boolean isEntityContext() {
        return isEntityOpened;
    }

    public static void setEntityOpened(boolean opened) {
        isEntityOpened = opened;
    }

    public static boolean getEntityOpened() {
        return isEntityOpened;
    }

    public static UUID getLastOpenedShulkerUUID() {
        return lastOpenedShulkerUUID;
    }

    public static void setLastOpenedShulkerUUID(UUID uuid) {
        lastOpenedShulkerUUID = uuid;
    }
}