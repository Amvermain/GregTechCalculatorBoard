package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.GregTechCalcBoard;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guarantees that all recipe indexing and catalog baking operations strictly occur
 * after EMI recipes are 100% loaded and baked into memory, without ever blocking Minecraft's ForkJoinPool.
 */
public class EmiLifecycleHook {

    private static final List<Runnable> PENDING_ACTIONS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean WATCHER_RUNNING = new AtomicBoolean(false);
    private static ScheduledExecutorService SCHEDULER = null;
    private static ScheduledFuture<?> CURRENT_POLL_TASK = null;

    private static synchronized ScheduledExecutorService getScheduler() {
        if (SCHEDULER == null || SCHEDULER.isShutdown()) {
            SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "GTCalcBoard-EmiWatcher");
                t.setDaemon(true);
                return t;
            });
        }
        return SCHEDULER;
    }

    /**
     * Checks whether EMI recipe manager has completed baking and has non-empty recipes.
     */
    public static boolean isEmiRecipeBakingComplete() {
        try {
            if (!EmiReloadManager.isLoaded()) {
                return false;
            }
            EmiRecipeManager rm = EmiApi.getRecipeManager();
            return rm != null && rm.getRecipes() != null && !rm.getRecipes().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Executes the given action strictly after EMI recipes are fully baked.
     * If EMI is already ready, executes the action immediately on background executor.
     * If EMI is still loading, queues the action until baking completes.
     */
    public static void runWhenEmiReady(Runnable action) {
        if (action == null) return;
        if (isEmiRecipeBakingComplete()) {
            CompletableFuture.runAsync(action, Util.backgroundExecutor());
            return;
        }

        PENDING_ACTIONS.add(action);
        ensureWatcherRunning();
    }

    public static synchronized void reset() {
        if (CURRENT_POLL_TASK != null) {
            CURRENT_POLL_TASK.cancel(true);
            CURRENT_POLL_TASK = null;
        }
        WATCHER_RUNNING.set(false);
        PENDING_ACTIONS.clear();
    }

    private static void ensureWatcherRunning() {
        if (WATCHER_RUNNING.compareAndSet(false, true)) {
            final AtomicInteger attempts = new AtomicInteger(0);
            final int maxAttempts = 240; // 240 * 250ms = 60s max wait

            CURRENT_POLL_TASK = getScheduler().scheduleAtFixedRate(() -> {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    // If client is logged out or world is tearing down, abort watcher
                    if (mc == null || mc.level == null) {
                        if (attempts.incrementAndGet() > 20) { // Give 5 seconds before cancelling outside world
                            reset();
                            return;
                        }
                    }

                    if (isEmiRecipeBakingComplete()) {
                        GregTechCalcBoard.LOGGER.info(
                                "[GTCalcBoard] [Lifecycle] EMI recipe baking verified complete. Dispatching deferred indexing actions ({} tasks)...",
                                PENDING_ACTIONS.size()
                        );
                        List<Runnable> actions = new ArrayList<>(PENDING_ACTIONS);
                        reset();
                        for (Runnable act : actions) {
                            CompletableFuture.runAsync(() -> {
                                try {
                                    act.run();
                                } catch (Throwable t) {
                                    GregTechCalcBoard.LOGGER.error("[GTCalcBoard] [Lifecycle] Error executing post-EMI action: ", t);
                                }
                            }, Util.backgroundExecutor());
                        }
                        return;
                    }

                    if (attempts.incrementAndGet() >= maxAttempts) {
                        GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] [Lifecycle] EMI recipe baking wait timed out (60s). Discarding {} pending actions.", PENDING_ACTIONS.size());
                        reset();
                    }
                } catch (Throwable t) {
                    reset();
                }
            }, 100, 250, TimeUnit.MILLISECONDS);
        }
    }
}
