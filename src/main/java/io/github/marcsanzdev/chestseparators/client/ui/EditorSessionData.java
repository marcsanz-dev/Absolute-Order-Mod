package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class EditorSessionData {
    // State UI
    public EditorState currentState = EditorState.HIDDEN;

    // Tab State
    public static final int TAB_LINES = 0;
    public static final int TAB_BG = 1;
    public static final int TAB_COMBO = 2;
    public int currentTab = TAB_LINES;

    // Whitelist Data
    public UUID selectedGroupId = null;
    public Set<Integer> selectedSlots = new HashSet<>();

    // Item Filter UI
    public List<String> currentAllowedItems = new ArrayList<>();
    public boolean ruleManual = true;
    public boolean ruleShift = true;
    public boolean ruleHopper = true;
    public List<Item> allGameItems = new ArrayList<>();
    public List<Item> filteredItems = new ArrayList<>();
    public List<String> visibleLeftListItems = new ArrayList<>();

    public long lastSlotClickTime = 0;
    public int lastClickedSlotIndex = -1;
    public boolean isSelecting = true;

    // Editing
    public int clickedActionId = -1;
    public long clickedActionTime = 0;

    public int lineColorIndex = 0;
    public int bgColorIndex = 0;
    public int comboColorIndex = 0;

    public int editingLineCustomIndex = -1;
    public int editingBgCustomIndex = -1;
    public int editingComboCustomIndex = -1;

    // Last non-eraser color (and custom-slot index) per tab, restored when switching from the
    // eraser back to a paint tool so the previous color is not lost.
    public int lineColorBeforeErase = 0;
    public int bgColorBeforeErase = 0;
    public int comboColorBeforeErase = 0;
    public int lineCustomBeforeErase = -1;
    public int bgCustomBeforeErase = -1;
    public int comboCustomBeforeErase = -1;

    public int lineToolMode = 0;
    public int bgToolMode = 0;
    public int comboToolMode = 0;

    // Which tab's Clear button is currently hovered (-1 = none); drives the fade-to-empty preview of
    // the layers that would be erased. Only honored while in the DRAW_LINES state.
    public int clearPreviewTab = -1;

    // Picker
    public boolean isColorPickerOpen = false;
    public int pickerTargetMode = 0;
    public float pickerHue = 0.0f;
    public float pickerSat = 1.0f;
    public float pickerVal = 1.0f;
    public int pickerCurrentRGB = 0xFFFF0000;
    public int copiedColorRGB = 0; // -1 indicates no color is currently copied
    public boolean isEyedropperActive = false;
    public boolean isDraggingSatVal = false;
    public boolean isDraggingHue = false;
    public boolean lastClickedHue = false;

    // Dragging
    public boolean isDraggingLine = false;
    public int currentDragAction = 0;
    public Slot dragStartSlot = null;
    public Slot dragCurrentSlot = null;
    public boolean isDragModeErasing = false;
    public List<String> tracePath = new ArrayList<>();

    public int lockedTraceAxis = 0;
    public int lockedTraceAction = 0;
    public int lockedTraceRowCol = -1;
    public double lockedLineCoord = -1;

    // Context
    public BlockPos currentChestPos;
    public String currentDimension;
    // True when the editor targets the player's own inventory slots (decorations + filters) instead of
    // a container's slots. Stored as a global/per-world profile rather than per-position.
    public boolean isPlayerInventory = false;
    public boolean isEnderChest = false;
    public boolean isEntityChest = false;
    public UUID currentEntityUUID;
    public boolean isShulkerBox = false;
    public UUID currentShulkerUUID;
    public Text statusMessage = null;
    public long statusMessageTime = 0;

    // Whitelist Tools
    public int wlToolMode = 0; // 0 = Area, 1 = Trace
    public boolean hasSelectionConflict = false;

    // Phase 4: Scroll and List states
    // Scroll and List states (Pixel-based)
    public float listScrollY = 0f;
    public float gridScrollY = 0f;
    public boolean isDraggingMainScroll = false;
    public boolean isDraggingListScroll = false;
    public boolean lastInteractedWasList = false;

    // Phase 4.B: Import Preview and Auto-Scroll Engine
    public List<String> previewItems = new ArrayList<>();
    public boolean isPreviewing = false;
    public int previewType = 0; // 0 = none, 1 = import, 2 = allow all, 3 = clear all
    public float previewScrollY = 0f;
    public boolean previewScrollDirectionDown = true;
    public long lastPreviewTime = 0;
    public long previewStartTime = 0;
    public boolean userOverrodePreviewScroll = false;

    public boolean forceStopPreview = false;

    // Tab pagination and selection
    public int currentCreativeTabIndex = 0;
    public int currentTabPage = 0;
    public List<CreativeTabInfo> availableTabs = new ArrayList<>();
    public List<Item> customChestItems = new ArrayList<>();

    // Unsaved Changes State
    public boolean isUnsavedPopupOpen = false;
    public boolean isExpelPopupOpen = false;
    public List<String> originalItemsSnapshot = new ArrayList<>();
    public boolean originalRuleManual = true;
    public boolean originalRuleShift = true;
    public boolean originalRuleHopper = true;

    // Inner class to hold tab data cleanly
    public static class CreativeTabInfo {
        public Text name;
        public ItemStack icon;
        public ItemGroup group;
        public boolean isSearchTab;
        public boolean isCustomChestTab;

        public CreativeTabInfo(
                Text name, ItemStack icon, ItemGroup group, boolean isSearchTab, boolean isCustomChestTab) {
            this.name = name;
            this.icon = icon;
            this.group = group;
            this.isSearchTab = isSearchTab;
            this.isCustomChestTab = isCustomChestTab;
        }
    }

    // Tag Dropdown Menu State
    public List<String> activeDropdownTags = new ArrayList<>();
    public int dropdownX = 0;
    public int dropdownY = 0;

    public UUID lastHoveredGroupId = null;
}
