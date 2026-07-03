package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import net.minecraft.client.gui.screen.Screen;

public class EditorLayout {
    // Base Screen Info
    public int screenWidth;
    public int screenHeight;
    public int guiX;
    public int guiY;
    public int bgWidth;
    public int bgHeight;

    // Universal Symmetrical Layout metrics
    public int gap = 6;
    public int mainW = 190;
    public int mainH = 200;
    public int listW = 120;
    public int btnW = 108;
    public int bH = 22;
    public int swatchSize = 12;

    // Calculated X/Y positions for main panels
    public int mainX;
    public int mainY;
    public int listX;
    public int listY;
    public int listH;
    public int rightX;

    // Edit Filter Grid
    public int cols = 9;
    public int rows = 9;
    public int itemSize = 18;
    public int gridX;
    public int gridViewY;
    public int gridViewH;

    // Main Grid Scrollbar
    public int msbX;
    public int msbY;
    public int msbH;

    // List Panel Scrollbar
    public int listViewY;
    public int listViewH;
    public int lsbX;
    public int lsbY;
    public int lsbH;

    // View Groups & Tools
    public int sidebarWidth = 76;
    public int sidebarHeight;
    public int sidebarX;
    public int paletteBoxY;
    public int paletteY;
    public int paletteCol1X;
    public int paletteCol2X;
    public int paletteCol3X;

    // Color Picker
    public int pickerBoxSize = 100;
    public int pickerHueWidth = 20;
    public int popupW = 240;
    public int popupH = 185;
    public int popupX;
    public int popupY;

    // Conflict Popup
    public int conflictPopupW = 260;
    public int conflictPopupH = 110;
    public int conflictPopupX;
    public int conflictPopupY;

    public void update(Screen screen, HandledScreenAccessor accessor, int customSidebarYOffset) {
        this.screenWidth = screen.width;
        this.screenHeight = screen.height;
        this.guiX = accessor.getX();
        this.guiY = accessor.getY();
        this.bgWidth = accessor.getBackgroundWidth();
        this.bgHeight = accessor.getBackgroundHeight();

        // 1. Center panels (Edit Filter / View Groups)
        this.mainY = this.guiY + (this.bgHeight - this.mainH) / 2;
        this.mainX = this.guiX + (this.bgWidth - this.mainW) / 2;

        // 2. Side panels
        this.listX = this.mainX - this.listW - this.gap;
        this.listY = this.mainY;
        this.listH = this.mainH;
        this.rightX = this.mainX + this.mainW + this.gap;

        // 3. Grid area
        this.gridX = this.mainX + 10;
        this.gridViewY = this.mainY + 25;
        this.gridViewH = this.rows * this.itemSize;

        // 4. Main Grid Scrollbar
        this.msbX = this.gridX + (this.cols * this.itemSize) + 3;
        this.msbY = this.gridViewY - 2;
        this.msbH = this.gridViewH + 4;

        // 5. Left List area & Scrollbar
        this.listViewY = this.listY + 42;
        this.listViewH = this.listH - 48;
        this.lsbX = this.listX + this.listW - 13;
        this.lsbY = this.listViewY;
        this.lsbH = this.listViewH - 1;

        // 6. Draw Lines Sidebar
        this.sidebarX = this.guiX - this.sidebarWidth - 4;
        this.paletteBoxY = this.guiY + customSidebarYOffset + 48;
        int contentX = this.sidebarX + 7;
        this.paletteY = this.paletteBoxY + 3;
        this.paletteCol1X = contentX + 5;
        this.paletteCol2X = contentX + 23;
        this.paletteCol3X = contentX + 47;

        // Altura del panel lateral (ahora es dinámica)
        this.sidebarHeight = this.bgHeight - 16;

        // Posiciones del Color Picker Popup
        this.popupX = ((this.screenWidth - this.popupW) / 2) + 40;
        this.popupY = (this.screenHeight - this.popupH) / 2;

        this.conflictPopupX = (this.screenWidth - this.conflictPopupW) / 2;
        this.conflictPopupY = (this.screenHeight - this.conflictPopupH) / 2;
    }
}