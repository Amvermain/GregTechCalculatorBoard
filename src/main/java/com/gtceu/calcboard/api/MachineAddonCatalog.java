package com.gtceu.calcboard.api;

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
    private volatile boolean isLoading = false;
    private volatile boolean isDynamicDataLoaded = false;
    private volatile CompletableFuture<Void> preloadFuture = null;
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
        return isLoading;
    }

    public boolean isReady() {
        return isDynamicDataLoaded && !isDirty;
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
            isDynamicDataLoaded = false;
            isLoading = false;
            isDirty = true;
            this.currentProgress = new LoadingProgress(1, 4, "gui.gtcalcboard.loading_phase.1", "");
        }
    }

    /**
     * Triggers asynchronous background preloading of all dynamic addons and hatches.
     */
    public synchronized CompletableFuture<Void> preloadAsync() {
        if (!isDirty && isDynamicDataLoaded) {
            return CompletableFuture.completedFuture(null);
        }
        if (isLoading && preloadFuture != null && !preloadFuture.isDone()) {
            return preloadFuture;
        }

        isLoading = true;
        setProgress(1, 4, "gui.gtcalcboard.loading_phase.1", "Builtin Traits & Multiblock Capabilities");

        preloadFuture = CompletableFuture.supplyAsync(() -> {
            List<MachineAddon> list = new ArrayList<>();
            list.addAll(DynamicAddonCrawler.getBuiltinTraits());

            List<MachineAddon> dynamic = DynamicAddonCrawler.crawlDynamicAddons();
            if (dynamic != null && !dynamic.isEmpty()) {
                list.addAll(dynamic);
            }
            list.addAll(customAddons);
            return list;
        }, Util.backgroundExecutor())
        .thenAccept(completeList -> {
            setProgress(4, 4, "gui.gtcalcboard.loading_phase.4", completeList.size() + " Addons Ready");
            synchronized (allAddons) {
                allAddons.clear();
                allAddons.addAll(completeList);
                boolean hasThermal = completeList.stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT);
                if (hasThermal || DynamicAddonCrawler.isRecipeBakingComplete()) {
                    isDynamicDataLoaded = true;
                    isDirty = false;
                } else {
                    isDynamicDataLoaded = false;
                    isDirty = true;
                }
                com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                        "[GTCalcBoard] [Catalog] Preload completed. {} addons available (Thermal Augments present: {}, Recipe baking complete: {}).",
                        allAddons.size(), hasThermal, DynamicAddonCrawler.isRecipeBakingComplete()
                );
            }
            isLoading = false;
        }).exceptionally(ex -> {
            isLoading = false;
            return null;
        });

        return preloadFuture;
    }

    public synchronized void refresh() {
        isLoading = true;
        try {
            List<MachineAddon> list = new ArrayList<>();
            list.addAll(DynamicAddonCrawler.getBuiltinTraits());
            List<MachineAddon> dynamic = DynamicAddonCrawler.crawlDynamicAddons();
            if (dynamic != null && !dynamic.isEmpty()) {
                list.addAll(dynamic);
            }
            list.addAll(customAddons);

            synchronized (allAddons) {
                allAddons.clear();
                allAddons.addAll(list);
                boolean hasThermal = list.stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT);
                if (hasThermal || DynamicAddonCrawler.isRecipeBakingComplete()) {
                    isDynamicDataLoaded = true;
                    isDirty = false;
                } else {
                    isDynamicDataLoaded = false;
                    isDirty = true;
                }
            }
        } finally {
            isLoading = false;
        }
    }

    public List<MachineAddon> getAllAddons() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.getLanguageManager() != null) {
                String currentLang = mc.getLanguageManager().getSelected();
                if (currentLang != null && !currentLang.equals(lastLanguageCode)) {
                    lastLanguageCode = currentLang;
                    isDirty = true;
                }
            }
            if (mc != null && mc.level != null && !isDynamicDataLoaded) {
                isDirty = true;
            }
        } catch (Throwable ignored) {}

        if ((isDirty || !isDynamicDataLoaded) && !isLoading) {
            preloadAsync();
        }

        synchronized (allAddons) {
            if (allAddons.isEmpty()) {
                allAddons.addAll(DynamicAddonCrawler.getBuiltinTraits());
                allAddons.addAll(customAddons);
            }
            return new ArrayList<>(allAddons);
        }
    }

    public List<MachineAddon> getAddonsByCategory(MachineAddon.Category category) {
        return getAllAddons().stream()
                .filter(a -> a.getCategory() == category)
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

        boolean isThermal = MachineAddon.isThermalMachine(node);
        boolean isTurbine = MachineAddon.isTurbineMachine(node);
        List<MachineAddon> all = getAllAddons();

        if (isThermal) {
            for (MachineAddon addon : all) {
                if (addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT) {
                    rec.add(addon);
                }
            }
        } else if (isTurbine) {
            for (MachineAddon addon : all) {
                if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                    rec.add(addon);
                }
            }
        } else {
            for (MachineAddon addon : all) {
                if (addon.isCompatibleWith(node)) {
                    rec.add(addon);
                }
            }
        }

        return rec;
    }
}
