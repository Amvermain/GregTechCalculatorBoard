package com.gtceu.calcboard.client.gui.render;

/** Render-thread-local presentation override; never changes the live board or its selection. */
public final class ExportRenderScope implements AutoCloseable {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);
    private final boolean previous = ACTIVE.get();

    public ExportRenderScope() { ACTIVE.set(true); }
    public static boolean isActive() { return ACTIVE.get(); }
    @Override public void close() {
        if (previous) ACTIVE.set(true);
        else ACTIVE.remove();
    }
}
