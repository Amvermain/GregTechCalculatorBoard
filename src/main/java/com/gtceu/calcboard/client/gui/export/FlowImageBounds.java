package com.gtceu.calcboard.client.gui.export;

/** Pure geometry and allocation policy, independent of the window's GUI scale. */
public final class FlowImageBounds {
    public static final int SCALE = 2;
    public static final int PADDING = 24;
    public static final long MAX_PIXELS = 16_777_216L;
    private double left = Double.POSITIVE_INFINITY;
    private double top = Double.POSITIVE_INFINITY;
    private double right = Double.NEGATIVE_INFINITY;
    private double bottom = Double.NEGATIVE_INFINITY;

    public void include(double x, double y, double width, double height) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width)
                || !Double.isFinite(height) || width < 0 || height < 0
                || !Double.isFinite(x + width) || !Double.isFinite(y + height)) {
            throw new IllegalArgumentException("Invalid flow geometry");
        }
        left = Math.min(left, x);
        top = Math.min(top, y);
        right = Math.max(right, x + width);
        bottom = Math.max(bottom, y + height);
    }

    public Plan plan(int maxTextureSize) {
        if (left == Double.POSITIVE_INFINITY) throw new IllegalArgumentException("empty");
        double x = Math.floor(left) - PADDING;
        double y = Math.floor(top) - PADDING;
        double w = (Math.ceil(right) + PADDING - x) * SCALE;
        double h = (Math.ceil(bottom) + PADDING - y) * SCALE;
        // Never silently shrink labels to fit an allocation limit.
        if (w > maxTextureSize || h > maxTextureSize || w * h > MAX_PIXELS
                || w < 1 || h < 1) throw new IllegalArgumentException("too_large");
        return new Plan(x, y, (int) w, (int) h);
    }

    public record Plan(double left, double top, int width, int height) {}
}
