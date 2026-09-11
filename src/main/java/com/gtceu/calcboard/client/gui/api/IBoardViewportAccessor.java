package com.gtceu.calcboard.client.gui.api;

import com.gtceu.calcboard.client.gui.util.BoardViewportTransform;

/**
 * Accessor interface for board viewport geometry, panning, zooming, and coordinate transformations.
 */
public interface IBoardViewportAccessor {

    int getScreenWidth();

    int getScreenHeight();

    double getPanX();

    double getPanY();

    void setPanX(double panX);

    void setPanY(double panY);

    double getZoom();

    void setZoom(double zoom);

    double toCanvasX(double screenX);

    double toCanvasY(double screenY);

    double toScreenX(double canvasX);

    double toScreenY(double canvasY);

    double getLastMouseX();

    double getLastMouseY();

    double[] getScreenCenterCanvasPosition();

    int getDynamicLeftMargin();

    int getPageTabY();

    int getToolbarY();

    int getHeaderBottomY();

    int getFavoritesDockY();

    int getSummaryRightOffset();

    BoardViewportTransform getViewportTransform();
}
