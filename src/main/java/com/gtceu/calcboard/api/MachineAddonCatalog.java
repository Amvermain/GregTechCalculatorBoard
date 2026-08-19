package com.gtceu.calcboard.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the global catalog of crawled and custom MachineAddons.
 */
public class MachineAddonCatalog {

    private static MachineAddonCatalog instance;

    private final List<MachineAddon> allAddons = new ArrayList<>();
    private final List<MachineAddon> customAddons = new ArrayList<>();

    private MachineAddonCatalog() {
        refresh();
    }

    public static synchronized MachineAddonCatalog getInstance() {
        if (instance == null) {
            instance = new MachineAddonCatalog();
        }
        return instance;
    }

    public void refresh() {
        allAddons.clear();
        allAddons.addAll(DynamicAddonCrawler.crawlAllAddons());
        allAddons.addAll(customAddons);
    }

    public List<MachineAddon> getAllAddons() {
        if (allAddons.isEmpty()) {
            refresh();
        }
        return new ArrayList<>(allAddons);
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
     */
    public List<MachineAddon> getRecommendedAddons(RecipeNode node) {
        List<MachineAddon> rec = new ArrayList<>();
        if (node == null) return rec;

        String nodeName = node.getName().toLowerCase();
        boolean isGen = node.isGenerator();

        for (MachineAddon addon : getAllAddons()) {
            String aId = addon.getId().toLowerCase();
            String aName = addon.getName().toLowerCase();

            if (isGen) {
                if (addon.getCategory() == MachineAddon.Category.ROTOR || addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT) {
                    rec.add(addon);
                }
            } else {
                if (nodeName.contains("pyrolyse") && aId.contains("throughput")) {
                    rec.add(addon);
                } else if (nodeName.contains("autoclave") && aId.contains("overpressure")) {
                    rec.add(addon);
                } else if (addon.getCategory() == MachineAddon.Category.PARALLEL || aId.contains("configurable_maintenance")) {
                    rec.add(addon);
                }
            }
        }

        // Always include Throughput Boosting & CMH if non-generator and not already present
        if (!isGen) {
            for (MachineAddon addon : getAllAddons()) {
                if ((addon.getId().contains("throughput") || addon.getId().contains("configurable_maintenance")) && !rec.contains(addon)) {
                    rec.add(addon);
                }
            }
        }

        return rec;
    }
}
