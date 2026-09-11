package com.gtceu.calcboard.client.gui.compat.gtceu;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Encapsulates scroll, drag, and paging state for GTCEu machine configuration dialog headers.
 */
@OnlyIn(Dist.CLIENT)
public class GTCEuMachineDialogState {

    private static int mbControllerScroll = 0;

    private double headerRow1ScrollX = 0;
    private double headerRow2ScrollX = 0;
    private double maxHeaderRow1ScrollX = 0;
    private double maxHeaderRow2ScrollX = 0;

    private boolean isDraggingHeader = false;
    private int draggingRow = 0;
    private double dragStartX = 0;
    private double dragStartScrollX = 0;
    private boolean hasDraggedHeader = false;

    public static void resetControllerScroll() {
        mbControllerScroll = 0;
    }

    public static int getMbControllerScroll() {
        return mbControllerScroll;
    }

    public static void setMbControllerScroll(int scroll) {
        mbControllerScroll = scroll;
    }

    public double getHeaderRow1ScrollX() {
        return headerRow1ScrollX;
    }

    public void setHeaderRow1ScrollX(double scrollX) {
        this.headerRow1ScrollX = scrollX;
    }

    public double getHeaderRow2ScrollX() {
        return headerRow2ScrollX;
    }

    public void setHeaderRow2ScrollX(double scrollX) {
        this.headerRow2ScrollX = scrollX;
    }

    public double getMaxHeaderRow1ScrollX() {
        return maxHeaderRow1ScrollX;
    }

    public void setMaxHeaderRow1ScrollX(double maxScrollX) {
        this.maxHeaderRow1ScrollX = maxScrollX;
    }

    public double getMaxHeaderRow2ScrollX() {
        return maxHeaderRow2ScrollX;
    }

    public void setMaxHeaderRow2ScrollX(double maxScrollX) {
        this.maxHeaderRow2ScrollX = maxScrollX;
    }

    public boolean isDraggingHeader() {
        return isDraggingHeader;
    }

    public void setDraggingHeader(boolean dragging) {
        this.isDraggingHeader = dragging;
    }

    public int getDraggingRow() {
        return draggingRow;
    }

    public void setDraggingRow(int row) {
        this.draggingRow = row;
    }

    public double getDragStartX() {
        return dragStartX;
    }

    public void setDragStartX(double x) {
        this.dragStartX = x;
    }

    public double getDragStartScrollX() {
        return dragStartScrollX;
    }

    public void setDragStartScrollX(double scrollX) {
        this.dragStartScrollX = scrollX;
    }

    public boolean hasDraggedHeader() {
        return hasDraggedHeader;
    }

    public void setHasDraggedHeader(boolean dragged) {
        this.hasDraggedHeader = dragged;
    }
}
