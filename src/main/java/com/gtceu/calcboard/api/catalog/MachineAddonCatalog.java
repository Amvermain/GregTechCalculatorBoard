package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.event.CatalogLifecycleEvent;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;

import net.minecraft.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manages the global catalog of crawled and custom MachineAddons with asynchronous background preloading.
 */
public class MachineAddonCatalog {

    private static MachineAddonCatalog instance;

    private final List<MachineAddon> allAddons = Collections.synchronizedList(new ArrayList<>());
    private final List<MachineAddon> customAddons = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean isDirty = true;
    private volatile boolean isFastLoaded = false;
    private volatile boolean isExhaustiveScanRunning = false;
    private volatile boolean isExhaustiveScanComplete = false;
    private volatile double exhaustiveProgress = 0.0;
    private volatile CompletableFuture<Void> exhaustiveFuture = null;
    private String lastLanguageCode = "";

    public record LoadingProgress(int currentPhase, int totalPhases, String phaseKey, String detail) {}
    private volatile LoadingProgress currentProgress = new LoadingProgress(1, 4, "gui.gtcalcboard.loading_phase.1", "");

    private MachineAddonCatalog() {
        // Initial state
    }

    public static synchronized MachineAddonCatalog getInstance() {
        if (instance == null) {
            instance = new MachineAddonCatalog();
        }
        return instance;
    }

    public boolean isLoading() {
        return !isFastLoaded || isExhaustiveScanRunning;
    }

    public boolean isReady() {
        return isFastLoaded;
    }

    public boolean isExhaustiveScanRunning() {
        return isExhaustiveScanRunning;
    }

    public boolean isExhaustiveScanComplete() {
        return isExhaustiveScanComplete;
    }

    public double getExhaustiveProgress() {
        return exhaustiveProgress;
    }

    public LoadingProgress getProgress() {
        return currentProgress;
    }

    public void setProgress(int currentPhase, int totalPhases, String phaseKey, String detail) {
        this.currentProgress = new LoadingProgress(currentPhase, totalPhases, phaseKey, detail != null ? detail : "");
    }

    public void markDirty() {
        this.isDirty = true;
    }

    public synchronized void reset() {
        synchronized (allAddons) {
            allAddons.clear();
            isFastLoaded = false;
            isExhaustiveScanRunning = false;
            isExhaustiveScanComplete = false;
            exhaustiveProgress = 0.0;
            isDirty = true;
            this.currentProgress = new LoadingProgress(1, 4, "gui.gtcalcboard.loading_phase.1", "");
        }
    }

    private volatile long catalogVersion = 0;

    public long getVersion() {
        return catalogVersion;
    }

    /**
     * Fast Track 1: Synchronously or asynchronously loads 100% of registry-backed addons in ~5ms.
     */
    public synchronized void ensureFastLoaded() {
        if (isFastLoaded && !isDirty) {
            return;
        }

        try {
            String currentLang = DynamicAddonCrawler.getLevelRecipeProvider().getSelectedLanguage();
            if (currentLang != null && !currentLang.isEmpty()) {
                lastLanguageCode = currentLang;
            }
        } catch (Throwable ignored) {}

        List<MachineAddon> fastList = DynamicAddonCrawler.crawlFastRegistries();
        synchronized (allAddons) {
            allAddons.clear();
            allAddons.addAll(fastList);
            allAddons.addAll(customAddons);
            isFastLoaded = true;
            isDirty = false;
            catalogVersion++;
        }
    }

    public CompletableFuture<Void> ensureFastLoadedAsync() {
        if (isFastLoaded && !isDirty) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(this::ensureFastLoaded, Util.backgroundExecutor());
    }

    /**
     * Track 2: Multi-threaded background deep scan across all recipes for custom NBT outputs.
     */
    public synchronized CompletableFuture<Void> startExhaustiveScanAsync() {
        if (isExhaustiveScanRunning && exhaustiveFuture != null && !exhaustiveFuture.isDone()) {
            return exhaustiveFuture;
        }
        if (isExhaustiveScanComplete && !isDirty) {
            return CompletableFuture.completedFuture(null);
        }

        isExhaustiveScanRunning = true;
        exhaustiveProgress = 0.0;

        exhaustiveFuture = CompletableFuture.supplyAsync(() -> {
            return DynamicAddonCrawler.crawlExhaustiveRecipesParallel(p -> {
                this.exhaustiveProgress = p;
            });
        }, Util.backgroundExecutor())
        .thenAccept(extraList -> {
            if (extraList != null && !extraList.isEmpty()) {
                synchronized (allAddons) {
                    allAddons.clear();
                    allAddons.addAll(extraList);
                    allAddons.addAll(customAddons);
                    catalogVersion++;
                }
            }
            this.isExhaustiveScanRunning = false;
            this.isExhaustiveScanComplete = true;
            this.exhaustiveProgress = 1.0;
            catalogVersion++;
            com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                    "[GTCalcBoard] [Catalog] 2-Track indexing complete. Total Addons: {}", allAddons.size()
            );
            try {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                    new com.gtceu.calcboard.api.event.CatalogLifecycleEvent.AddonsReady(allAddons.size())
                );
            } catch (Throwable ignored) {}
        }).exceptionally(ex -> {
            this.isExhaustiveScanRunning = false;
            return null;
        });

        return exhaustiveFuture;
    }

    /**
     * Triggers asynchronous background preloading of both Track 1 (Fast) and Track 2 (Exhaustive).
     */
    public synchronized CompletableFuture<Void> preloadAsync() {
        try {
            boolean recipeReady = DynamicAddonCrawler.isRecipeBakingComplete();
            if (recipeReady && !wasRecipeReady) {
                wasRecipeReady = true;
                isDirty = true;
                isExhaustiveScanComplete = false;
            }
        } catch (Throwable ignored) {}

        ensureFastLoaded();
        if (!isExhaustiveScanComplete && !isExhaustiveScanRunning) {
            startExhaustiveScanAsync();
        }
        return exhaustiveFuture != null ? exhaustiveFuture : CompletableFuture.completedFuture(null);
    }

    public synchronized void refresh() {
        isDirty = true;
        ensureFastLoaded();
    }
    private boolean wasRecipeReady = false;

    public List<MachineAddon> getAllAddons() {
        try {
            String currentLang = DynamicAddonCrawler.getLevelRecipeProvider().getSelectedLanguage();
            if (currentLang != null && !currentLang.isEmpty()) {
                if (lastLanguageCode.isEmpty()) {
                    lastLanguageCode = currentLang;
                } else if (!currentLang.equals(lastLanguageCode)) {
                    lastLanguageCode = currentLang;
                    isDirty = true;
                }
            }
        } catch (Throwable ignored) {}

        if (isDirty || !isFastLoaded) {
            ensureFastLoaded();
        }

        synchronized (allAddons) {
            if (allAddons.isEmpty()) {
                allAddons.addAll(DynamicAddonCrawler.getBuiltinTraits());
                allAddons.addAll(customAddons);
            }
            return new ArrayList<>(allAddons);
        }
    }

    public List<MachineAddon> getAddonsByCategory(AddonCategory category) {
        if (category == null) return getAllAddons();
        return getAllAddons().stream()
                .filter(a -> a.getCategory().equals(category))
                .collect(Collectors.toList());
    }

    public void registerCustomAddon(MachineAddon customAddon) {
        if (customAddon != null && !customAddons.contains(customAddon)) {
            customAddons.add(customAddon);
            allAddons.add(customAddon);
        }
    }

    /**
     * Recommends the best matching addons based on the target RecipeNode.
     * Categorizes accurately between Thermal Expansion machines/dynamos, GregTech Turbines,
     * and GregTech Multiblock/Standard processing machines.
     */
    public List<MachineAddon> getRecommendedAddons(RecipeNode node) {
        List<MachineAddon> rec = new ArrayList<>();
        if (node == null) return rec;

        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        List<MachineAddon> all = getAllAddons();

        for (MachineAddon addon : all) {
            if (adapter.isAddonCompatible(node, addon)) {
                rec.add(addon);
            }
        }

        return rec;
    }
}


