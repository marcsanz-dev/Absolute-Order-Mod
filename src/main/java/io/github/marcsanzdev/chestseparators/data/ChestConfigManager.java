package io.github.marcsanzdev.chestseparators.data;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

/**
 * Core client-side manager for handling custom UI visual states and persistence.
 * <p>
 * This Singleton class is responsible for managing the active editing session, including:
 * <ul>
 * <li>I/O operations for saving/loading visual data via compressed NBT files.</li>
 * <li>Managing an undo/redo stack using the Memento pattern.</li>
 * <li>Handling clipboard operations for layouts and whitelist configurations.</li>
 * <li>Executing complex grid calculations, such as Flood Fill for contiguous slot detection.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class ChestConfigManager {

    private static final String MOD_ID = "chestseparators";
    private static final String FOLDER_NAME = "separators";
    private static final String ENDER_FILE_NAME = "ender_chest.dat";
    private static final String PALETTE_FILE_NAME = "world_palette.dat";

    // Bitwise action flags for targeted rendering updates
    public static final int ACTION_TOP = 1;
    public static final int ACTION_BOTTOM = 2;
    public static final int ACTION_LEFT = 4;
    public static final int ACTION_RIGHT = 8;
    public static final int ACTION_BG = 16;

    // Array indices for the data structure: [Top, Bottom, Left, Right, Background]
    private static final int IDX_TOP = 0;
    private static final int IDX_BOTTOM = 1;
    private static final int IDX_LEFT = 2;
    private static final int IDX_RIGHT = 3;
    private static final int IDX_BG = 4;

    /** Active visual configuration map: Slot Index -> Color Array [T, B, L, R, BG] */
    private final Map<Integer, int[]> currentChestConfig = new HashMap<>();

    // Clipboard states
    private Map<Integer, int[]> clipboardConfig = null;
    private Map<Integer, int[]> linesClipboard = null;
    private Map<Integer, int[]> bgClipboard = null;
    private Map<Integer, SlotWhitelist> whitelistClipboard = null;

    // Persistent color palettes
    private int[] worldCustomLineColors = new int[8];
    private int[] worldCustomBgColors = new int[8];
    private int[] worldCustomComboColors = new int[8];

    private static final ChestConfigManager INSTANCE = new ChestConfigManager();

    public static ChestConfigManager getInstance() {
        return INSTANCE;
    }

    /**
     * Clears the current active configuration and purges the history stacks.
     */
    public void clearCurrentConfig() {
        currentChestConfig.clear();
        clearHistory();
    }

    // ==========================================
    // UNDO / REDO SYSTEM (MEMENTO PATTERN)
    // ==========================================

    private static class Snapshot {
        final Map<Integer, int[]> configState;
        Snapshot(Map<Integer, int[]> config) {
            this.configState = new HashMap<>();
            for (Map.Entry<Integer, int[]> entry : config.entrySet()) {
                this.configState.put(entry.getKey(), entry.getValue().clone());
            }
        }
    }

    private final java.util.LinkedList<Snapshot> undoStack = new java.util.LinkedList<>();
    private final java.util.LinkedList<Snapshot> redoStack = new java.util.LinkedList<>();
    private static final int MAX_UNDO_STEPS = 50;

    public void saveSnapshot() {
        undoStack.addLast(new Snapshot(currentChestConfig));
        if (undoStack.size() > MAX_UNDO_STEPS) undoStack.removeFirst();
        redoStack.clear();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void undo() {
        if (!canUndo()) return;
        redoStack.addLast(new Snapshot(currentChestConfig));
        restoreSnapshot(undoStack.removeLast());
    }

    public void redo() {
        if (!canRedo()) return;
        undoStack.addLast(new Snapshot(currentChestConfig));
        restoreSnapshot(redoStack.removeLast());
    }

    private void restoreSnapshot(Snapshot snapshot) {
        this.currentChestConfig.clear();
        for (Map.Entry<Integer, int[]> entry : snapshot.configState.entrySet()) {
            this.currentChestConfig.put(entry.getKey(), entry.getValue().clone());
        }
    }

    private static class WhitelistSnapshot {
        final Map<Integer, SlotWhitelist> whitelistState;
        WhitelistSnapshot(Map<Integer, SlotWhitelist> whitelists) {
            this.whitelistState = new HashMap<>();
            for (Map.Entry<Integer, SlotWhitelist> entry : whitelists.entrySet()) {
                SlotWhitelist orig = entry.getValue();
                this.whitelistState.put(entry.getKey(), new SlotWhitelist(
                        orig.groupId(), new ArrayList<>(orig.allowedItems()),
                        orig.allowManual(), orig.allowShift(), orig.allowHopper()
                ));
            }
        }
    }

    private final java.util.LinkedList<WhitelistSnapshot> wlUndoStack = new java.util.LinkedList<>();
    private final java.util.LinkedList<WhitelistSnapshot> wlRedoStack = new java.util.LinkedList<>();

    public void saveWhitelistSnapshot() {
        wlUndoStack.addLast(new WhitelistSnapshot(currentWhitelists));
        if (wlUndoStack.size() > MAX_UNDO_STEPS) wlUndoStack.removeFirst();
        wlRedoStack.clear();
    }

    public boolean canUndoWhitelist() { return !wlUndoStack.isEmpty(); }
    public boolean canRedoWhitelist() { return !wlRedoStack.isEmpty(); }

    public void undoWhitelist() {
        if (!canUndoWhitelist()) return;
        wlRedoStack.addLast(new WhitelistSnapshot(currentWhitelists));
        restoreWhitelistSnapshot(wlUndoStack.removeLast());
    }

    public void redoWhitelist() {
        if (!canRedoWhitelist()) return;
        wlUndoStack.addLast(new WhitelistSnapshot(currentWhitelists));
        restoreWhitelistSnapshot(wlRedoStack.removeLast());
    }

    private void restoreWhitelistSnapshot(WhitelistSnapshot snapshot) {
        this.currentWhitelists.clear();
        for (Map.Entry<Integer, SlotWhitelist> entry : snapshot.whitelistState.entrySet()) {
            SlotWhitelist orig = entry.getValue();
            this.currentWhitelists.put(entry.getKey(), new SlotWhitelist(
                    orig.groupId(), new ArrayList<>(orig.allowedItems()),
                    orig.allowManual(), orig.allowShift(), orig.allowHopper()
            ));
        }
    }

    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        wlUndoStack.clear();
        wlRedoStack.clear();
    }

    // ==========================================
    // PATH MANAGEMENT (NIO)
    // ==========================================

    /**
     * Resolves the appropriate folder name based on the active game session (Singleplayer vs Multiplayer).
     */
    private String getWorldFolderName() {
        MinecraftClient client = MinecraftClient.getInstance();
        String name = "unknown_world";

        if (client.isInSingleplayer() && client.getServer() != null) {
            name = "sp_" + client.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).getFileName().toString();
        } else if (client.getCurrentServerEntry() != null) {
            name = "mp_" + client.getCurrentServerEntry().address;
        }

        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private Path getWorldConfigDir() {
        Path runDir = MinecraftClient.getInstance().runDirectory.toPath();
        Path baseModDir = runDir.resolve("config/" + MOD_ID);
        Path worldDir = baseModDir.resolve(getWorldFolderName());

        try {
            if (!Files.exists(worldDir)) {
                Files.createDirectories(worldDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return worldDir;
    }

    private Path getSeparatorsDir() {
        Path sepDir = getWorldConfigDir().resolve(FOLDER_NAME);
        try {
            if (!Files.exists(sepDir)) {
                Files.createDirectories(sepDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sepDir;
    }

    private Path getFileForPos(BlockPos pos, String dimensionId) {
        if (pos == null) return null;
        Path dir = getSeparatorsDir();
        String safeDim = dimensionId.replace(":", "_");
        String fileName = String.format("%s_%d_%d_%d.dat", safeDim, pos.getX(), pos.getY(), pos.getZ());
        return dir.resolve(fileName);
    }

    private Path getEnderChestFile() {
        return getWorldConfigDir().resolve(ENDER_FILE_NAME);
    }

    private Path getFileForEntity(UUID uuid) {
        if (uuid == null) return null;
        Path dir = getSeparatorsDir();
        return dir.resolve("entity_" + uuid.toString() + ".dat");
    }

    private Path getFileForShulker(UUID uuid) {
        if (uuid == null) return null;
        Path dir = getSeparatorsDir();
        return dir.resolve("shulker_" + uuid.toString() + ".dat");
    }

    // ==========================================
    // CHEST LOGIC & VISUAL OPERATIONS
    // ==========================================

    private int[] getSlotColors(int slotIndex) {
        return currentChestConfig.computeIfAbsent(slotIndex, k -> new int[5]);
    }

    public int getColor(int slotIndex, int actionFlag) {
        if (!currentChestConfig.containsKey(slotIndex)) return 0;
        int[] colors = currentChestConfig.get(slotIndex);
        if (actionFlag == ACTION_TOP) return colors[IDX_TOP];
        if (actionFlag == ACTION_BOTTOM) return colors[IDX_BOTTOM];
        if (actionFlag == ACTION_LEFT) return colors[IDX_LEFT];
        if (actionFlag == ACTION_RIGHT) return colors[IDX_RIGHT];
        if (actionFlag == ACTION_BG) return colors[IDX_BG];
        return 0;
    }

    public void paintAction(int slotIndex, int actionFlags, int argbColor) {
        int[] colors = getSlotColors(slotIndex);
        if ((actionFlags & ACTION_TOP) != 0) colors[IDX_TOP] = argbColor;
        if ((actionFlags & ACTION_BOTTOM) != 0) colors[IDX_BOTTOM] = argbColor;
        if ((actionFlags & ACTION_LEFT) != 0) colors[IDX_LEFT] = argbColor;
        if ((actionFlags & ACTION_RIGHT) != 0) colors[IDX_RIGHT] = argbColor;
        if ((actionFlags & ACTION_BG) != 0) colors[IDX_BG] = argbColor;
    }

    public void removeAction(int slotIndex, int actionFlags) {
        if (!currentChestConfig.containsKey(slotIndex)) return;
        int[] colors = currentChestConfig.get(slotIndex);
        if ((actionFlags & ACTION_TOP) != 0) colors[IDX_TOP] = 0;
        if ((actionFlags & ACTION_BOTTOM) != 0) colors[IDX_BOTTOM] = 0;
        if ((actionFlags & ACTION_LEFT) != 0) colors[IDX_LEFT] = 0;
        if ((actionFlags & ACTION_RIGHT) != 0) colors[IDX_RIGHT] = 0;
        if ((actionFlags & ACTION_BG) != 0) colors[IDX_BG] = 0;

        if (colors[0] == 0 && colors[1] == 0 && colors[2] == 0 && colors[3] == 0 && colors[4] == 0) {
            currentChestConfig.remove(slotIndex);
        }
    }

    public void clearAllBackgrounds() {
        for (Map.Entry<Integer, int[]> entry : currentChestConfig.entrySet()) entry.getValue()[IDX_BG] = 0;
        currentChestConfig.entrySet().removeIf(entry -> {
            int[] c = entry.getValue(); return c[0] == 0 && c[1] == 0 && c[2] == 0 && c[3] == 0 && c[4] == 0;
        });
    }

    public void clearAllLines() {
        for (Map.Entry<Integer, int[]> entry : currentChestConfig.entrySet()) {
            entry.getValue()[IDX_TOP] = 0; entry.getValue()[IDX_BOTTOM] = 0; entry.getValue()[IDX_LEFT] = 0; entry.getValue()[IDX_RIGHT] = 0;
        }
        currentChestConfig.entrySet().removeIf(entry -> {
            int[] c = entry.getValue(); return c[0] == 0 && c[1] == 0 && c[2] == 0 && c[3] == 0 && c[4] == 0;
        });
    }

    // ==========================================
    // CLIPBOARD SYSTEM
    // ==========================================

    public void copyToClipboard() {
        this.clipboardConfig = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : this.currentChestConfig.entrySet()) {
            this.clipboardConfig.put(entry.getKey(), entry.getValue().clone());
        }
    }

    public void pasteFromClipboard() {
        if (this.clipboardConfig != null && !this.clipboardConfig.isEmpty()) {
            this.currentChestConfig.clear();
            for (Map.Entry<Integer, int[]> entry : this.clipboardConfig.entrySet()) {
                this.currentChestConfig.put(entry.getKey(), entry.getValue().clone());
            }
        }
    }

    public boolean hasClipboardData() {
        return this.clipboardConfig != null && !this.clipboardConfig.isEmpty();
    }

    public void copyLinesToClipboard() {
        this.linesClipboard = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : this.currentChestConfig.entrySet()) {
            int[] c = entry.getValue();
            if (c[0] != 0 || c[1] != 0 || c[2] != 0 || c[3] != 0) {
                this.linesClipboard.put(entry.getKey(), new int[]{c[0], c[1], c[2], c[3], 0});
            }
        }
    }

    public void pasteLinesFromClipboard() {
        if (this.linesClipboard != null && !this.linesClipboard.isEmpty()) {
            for (Map.Entry<Integer, int[]> entry : this.linesClipboard.entrySet()) {
                int[] current = getSlotColors(entry.getKey());
                int[] clip = entry.getValue();
                current[0] = clip[0]; current[1] = clip[1]; current[2] = clip[2]; current[3] = clip[3];
            }
        }
    }

    public boolean hasLinesClipboardData() {
        return this.linesClipboard != null && !this.linesClipboard.isEmpty();
    }

    public void copyBackgroundsToClipboard() {
        this.bgClipboard = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : this.currentChestConfig.entrySet()) {
            if (entry.getValue()[4] != 0) {
                this.bgClipboard.put(entry.getKey(), new int[]{0, 0, 0, 0, entry.getValue()[4]});
            }
        }
    }

    public void pasteBackgroundsFromClipboard() {
        if (this.bgClipboard != null && !this.bgClipboard.isEmpty()) {
            for (Map.Entry<Integer, int[]> entry : this.bgClipboard.entrySet()) {
                getSlotColors(entry.getKey())[4] = entry.getValue()[4];
            }
        }
    }

    public boolean hasBackgroundsClipboardData() {
        return this.bgClipboard != null && !this.bgClipboard.isEmpty();
    }

    public void copyAllToClipboard() {
        copyToClipboard();
        copyLinesToClipboard();
        copyBackgroundsToClipboard();
    }

    // ==========================================
    // I/O OPERATIONS (NBT SERIALIZATION)
    // ==========================================

    /**
     * Deletes the persistent NBT file associated with a specific physical block.
     *
     * @param pos         The location of the block entity.
     * @param dimensionId The registry key of the dimension.
     */
    public void clearChest(BlockPos pos, String dimensionId) {
        Path path = getFileForPos(pos, dimensionId);
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Loads visual configuration data from disk into memory.
     * <p>
     * Implements specific logic for Double Chests: vanilla handles double chests
     * as an assembled 54-slot inventory, but they are stored physically as two separate blocks.
     * This method reads both block files and maps their data correctly to indices 0-26 and 27-53.
     */
    public void loadConfig(BlockPos pos, String dimensionId) {
        clearCurrentConfig();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        BlockState state = client.world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.get(ChestBlock.FACING);
                Direction neighborDir = type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
                BlockPos neighborPos = pos.offset(neighborDir);

                BlockPos primaryPos = pos.compareTo(neighborPos) < 0 ? pos : neighborPos;
                BlockPos secondaryPos = pos.compareTo(neighborPos) < 0 ? neighborPos : pos;

                // Load top half (0-26)
                Map<Integer, int[]> firstData = readRawConfig(getFileForPos(primaryPos, dimensionId));
                currentChestConfig.putAll(firstData);

                // Load bottom half and offset indices to 27-53
                Map<Integer, int[]> secondData = readRawConfig(getFileForPos(secondaryPos, dimensionId));
                for(Map.Entry<Integer, int[]> entry : secondData.entrySet()) {
                    currentChestConfig.put(entry.getKey() + 27, entry.getValue());
                }
                return;
            }
        }

        // Single containers (Barrel, Shulker, Ender Chest...)
        Map<Integer, int[]> rawData = readRawConfig(getFileForPos(pos, dimensionId));
        currentChestConfig.putAll(rawData);
    }

    public void loadEnderConfig() {
        clearCurrentConfig();
        currentChestConfig.putAll(readRawConfig(getEnderChestFile()));
    }

    public void loadEntityConfig(UUID uuid) {
        clearCurrentConfig();
        currentChestConfig.putAll(readRawConfig(getFileForEntity(uuid)));
    }

    public void loadShulkerConfig(UUID uuid) {
        clearCurrentConfig();
        currentChestConfig.putAll(readRawConfig(getFileForShulker(uuid)));
    }

    /**
     * Serializes the current visual configuration from memory to disk.
     * <p>
     * For Double Chests, splits the 54-slot map back into two independent 27-slot maps
     * and saves them to their respective block coordinates.
     */
    public void saveConfig(BlockPos pos, String dimensionId) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        BlockState state = client.world.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.get(ChestBlock.CHEST_TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.get(ChestBlock.FACING);
                Direction neighborDir = type == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
                BlockPos neighborPos = pos.offset(neighborDir);

                BlockPos primaryPos = pos.compareTo(neighborPos) < 0 ? pos : neighborPos;
                BlockPos secondaryPos = pos.compareTo(neighborPos) < 0 ? neighborPos : pos;

                Map<Integer, int[]> firstMap = new HashMap<>();
                Map<Integer, int[]> secondMap = new HashMap<>();

                for (Map.Entry<Integer, int[]> entry : currentChestConfig.entrySet()) {
                    int slot = entry.getKey();
                    if (slot < 27) {
                        firstMap.put(slot, entry.getValue());
                    } else {
                        secondMap.put(slot - 27, entry.getValue());
                    }
                }
                writeRawConfig(firstMap, getFileForPos(primaryPos, dimensionId));
                writeRawConfig(secondMap, getFileForPos(secondaryPos, dimensionId));
                return;
            }
        }

        writeRawConfig(currentChestConfig, getFileForPos(pos, dimensionId));
    }

    public void saveEnderConfig() {
        writeRawConfig(currentChestConfig, getEnderChestFile());
    }

    public void saveEntityConfig(UUID uuid) {
        writeRawConfig(currentChestConfig, getFileForEntity(uuid));
    }

    public void saveShulkerConfig(UUID uuid) {
        writeRawConfig(currentChestConfig, getFileForShulker(uuid));
    }

    /**
     * Internal method to decode NBT files into map structures.
     */
    private Map<Integer, int[]> readRawConfig(Path path) {
        Map<Integer, int[]> config = new HashMap<>();
        if (path == null || !Files.exists(path)) return config;
        try {
            NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());

            if (root.contains("Separators")) {
                root.getCompound("Separators").ifPresent(separatorsTag -> {
                    for (String key : separatorsTag.getKeys()) {
                        try {
                            int slot = Integer.parseInt(key);
                            separatorsTag.getIntArray(key).ifPresent(data -> {
                                if (data.length == 4) {
                                    config.put(slot, new int[]{data[0], data[1], data[2], data[3], 0});
                                } else if (data.length >= 5) {
                                    config.put(slot, data);
                                }
                            });
                        } catch (NumberFormatException ignored) {}
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return config;
    }

    /**
     * Internal method to encode map structures into compressed NBT files.
     */
    private void writeRawConfig(Map<Integer, int[]> config, Path path) {
        if (path == null) return;

        if (config.isEmpty()) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            return;
        }

        NbtCompound root = new NbtCompound();
        NbtCompound separatorsTag = new NbtCompound();
        for (Map.Entry<Integer, int[]> entry : config.entrySet()) {
            separatorsTag.putIntArray(String.valueOf(entry.getKey()), entry.getValue());
        }
        root.put("Separators", separatorsTag);

        try {
            NbtIo.writeCompressed(root, path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    // WHITELIST DATA MANAGEMENT
    // ==========================================

    private Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> currentWhitelists = new HashMap<>();

    public Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> getCurrentWhitelists() {
        return currentWhitelists;
    }

    public void setCurrentWhitelists(Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists) {
        this.currentWhitelists = new HashMap<>(whitelists);
    }

    // ==========================================
    // FLOOD FILL ALGORITHM (BFS)
    // ==========================================

    /**
     * Performs a Breadth-First Search (Flood Fill) to find all contiguous slots
     * bounded by the drawn separator lines.
     * <p>
     * This is used for grouping visual zones and applying batch operations to an enclosed area.
     *
     * @param startSlot         The index where the search originates.
     * @param maxContainerSlots The maximum number of slots in the current container layout.
     * @return A set of all slot indices included in the contiguous area.
     */
    public Set<Integer> getContiguousSlots(int startSlot, int maxContainerSlots) {
        Set<Integer> result = new HashSet<>();

        if (startSlot < 0 || startSlot >= maxContainerSlots) return result;

        java.util.Queue<Integer> queue = new java.util.LinkedList<>();
        queue.add(startSlot);
        result.add(startSlot);

        while (!queue.isEmpty()) {
            int current = queue.poll();

            int row = current / 9;
            int col = current % 9;

            // Check UP
            int upIndex = current - 9;
            if (row > 0 && !result.contains(upIndex)) {
                if (getColor(current, ACTION_TOP) == 0 && getColor(upIndex, ACTION_BOTTOM) == 0) {
                    result.add(upIndex);
                    queue.add(upIndex);
                }
            }

            // Check DOWN
            int downIndex = current + 9;
            if (row < (maxContainerSlots / 9) - 1 && !result.contains(downIndex)) {
                if (getColor(current, ACTION_BOTTOM) == 0 && getColor(downIndex, ACTION_TOP) == 0) {
                    result.add(downIndex);
                    queue.add(downIndex);
                }
            }

            // Check LEFT
            int leftIndex = current - 1;
            if (col > 0 && !result.contains(leftIndex)) {
                if (getColor(current, ACTION_LEFT) == 0 && getColor(leftIndex, ACTION_RIGHT) == 0) {
                    result.add(leftIndex);
                    queue.add(leftIndex);
                }
            }

            // Check RIGHT
            int rightIndex = current + 1;
            if (col < 8 && !result.contains(rightIndex)) {
                if (getColor(current, ACTION_RIGHT) == 0 && getColor(rightIndex, ACTION_LEFT) == 0) {
                    result.add(rightIndex);
                    queue.add(rightIndex);
                }
            }
        }
        return result;
    }

    // ==========================================
    // COLOR PALETTE MANAGEMENT
    // ==========================================

    public int[] getCustomColors(int tabMode) {
        if (tabMode == 1) return worldCustomBgColors;
        if (tabMode == 2) return worldCustomComboColors;
        return worldCustomLineColors;
    }

    public void setCustomColor(int index, int color, int tabMode) {
        if (index >= 0 && index < 8) {
            if (tabMode == 1) worldCustomBgColors[index] = color;
            else if (tabMode == 2) worldCustomComboColors[index] = color;
            else worldCustomLineColors[index] = color;
        }
    }

    public void loadWorldPalette() {
        worldCustomLineColors = new int[8];
        worldCustomBgColors = new int[8];
        worldCustomComboColors = new int[8];
        Path path = getWorldConfigDir().resolve(PALETTE_FILE_NAME);

        if (!Files.exists(path)) return;

        try {
            NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());

            if (root.contains("PaletteLines")) {
                root.getIntArray("PaletteLines").ifPresent(loaded -> System.arraycopy(loaded, 0, worldCustomLineColors, 0, Math.min(loaded.length, 8)));
            }
            if (root.contains("PaletteBg")) {
                root.getIntArray("PaletteBg").ifPresent(loaded -> System.arraycopy(loaded, 0, worldCustomBgColors, 0, Math.min(loaded.length, 8)));
            }
            if (root.contains("PaletteCombo")) {
                root.getIntArray("PaletteCombo").ifPresent(loaded -> System.arraycopy(loaded, 0, worldCustomComboColors, 0, Math.min(loaded.length, 8)));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveWorldPalette() {
        Path path = getWorldConfigDir().resolve(PALETTE_FILE_NAME);
        NbtCompound root = new NbtCompound();
        root.putIntArray("PaletteLines", worldCustomLineColors);
        root.putIntArray("PaletteBg", worldCustomBgColors);
        root.putIntArray("PaletteCombo", worldCustomComboColors);
        try {
            NbtIo.writeCompressed(root, path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==========================================
    // WHITELIST CLIPBOARD
    // ==========================================

    public void copyWhitelistsToClipboard() {
        if (currentWhitelists != null) {
            whitelistClipboard = new HashMap<>();
            for (Map.Entry<Integer, SlotWhitelist> entry : currentWhitelists.entrySet()) {
                whitelistClipboard.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public boolean hasWhitelistClipboardData() {
        return whitelistClipboard != null && !whitelistClipboard.isEmpty();
    }

    /**
     * Pastes whitelist data from the clipboard into the active configuration.
     * <p>
     * This method maps copied group UUIDs to fresh, newly generated UUIDs. This prevents
     * logic overlaps where pasting a layout onto a second chest would accidentally link
     * its inventory groups to the original chest's groups.
     */
    public void pasteWhitelistsFromClipboard() {
        if (hasWhitelistClipboardData()) {
            if (currentWhitelists == null) currentWhitelists = new HashMap<>();

            Map<UUID, UUID> uuidMapping = new HashMap<>();

            for (Map.Entry<Integer, SlotWhitelist> entry : whitelistClipboard.entrySet()) {
                SlotWhitelist clipboardItem = entry.getValue();

                // Assign a new UUID if this group hasn't been mapped yet in the current paste operation
                UUID newGroupId = uuidMapping.computeIfAbsent(clipboardItem.groupId(), k -> UUID.randomUUID());

                SlotWhitelist independentCopy = new SlotWhitelist(
                        newGroupId,
                        new ArrayList<>(clipboardItem.allowedItems()),
                        clipboardItem.allowManual(),
                        clipboardItem.allowShift(),
                        clipboardItem.allowHopper()
                );
                currentWhitelists.put(entry.getKey(), independentCopy);
            }
        }
    }
}