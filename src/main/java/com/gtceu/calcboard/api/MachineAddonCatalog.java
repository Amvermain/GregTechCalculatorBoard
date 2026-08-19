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
    private volatile boolean isDirty = true;
    private String lastLanguageCode = "";

    private MachineAddonCatalog() {
        // Lazy loaded on first GUI open
    }

    public static synchronized MachineAddonCatalog getInstance() {
        if (instance == null) {
            instance = new MachineAddonCatalog();
        }
        return instance;
    }

    public void markDirty() {
        this.isDirty = true;
    }

    public synchronized void refresh() {
        allAddons.clear();
        allAddons.addAll(DynamicAddonCrawler.crawlAllAddons());
        allAddons.addAll(customAddons);
        this.isDirty = false;
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
        } catch (Throwable ignored) {}

        if (isDirty || allAddons.isEmpty()) {
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
     * Categorizes accurately between Thermal Expansion machines/dynamos, GregTech Turbines,
     * and GregTech Multiblock/Standard processing machines.
     */
    public List<MachineAddon> getRecommendedAddons(RecipeNode node) {
        List<MachineAddon> rec = new ArrayList<>();
        if (node == null) return rec;

        String nodeName = node.getName().toLowerCase();
        boolean isGen = node.isGenerator();

        boolean isThermal = nodeName.contains("thermal") || nodeName.contains("fuel") || nodeName.contains("dynamo")
                || nodeName.contains("lapidary") || nodeName.contains("compression") || nodeName.contains("magmatic")
                || nodeName.contains("gourmand") || nodeName.contains("numismatic") || nodeName.contains("stirling")
                || nodeName.contains("disenchantment")
                || (node.getMachineIcon() != null && (node.getMachineIcon().getNamespace().equals("thermal")
                    || node.getMachineIcon().getNamespace().equals("thermal_expansion")
                    || node.getMachineIcon().getNamespace().equals("systeams")));

        List<MachineAddon> all = getAllAddons();

        if (isThermal) {
            // Thermal Expansion Dynamos & Processing Machines:
            // Recommend Upgrade Kits (EV, IV, LuV, etc.) & Thermal Augments
            for (MachineAddon addon : all) {
                if (addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT
                        || addon.getId().contains("upgrade_kit")
                        || addon.getId().contains("augment")
                        || addon.getId().contains("thermal")) {
                    rec.add(addon);
                }
            }
        } else if (isGen) {
            // GregTech Turbines & Power Generators:
            // Recommend Turbine Rotors
            for (MachineAddon addon : all) {
                if (addon.getCategory() == MachineAddon.Category.ROTOR || addon.getId().contains("rotor")) {
                    rec.add(addon);
                }
            }
        } else {
            // GregTech Multiblocks & Standard Processing Machines:
            for (MachineAddon addon : all) {
                String aId = addon.getId().toLowerCase();
                if (nodeName.contains("pyrolyse") && aId.contains("throughput")) {
                    rec.add(addon);
                } else if (nodeName.contains("autoclave") && aId.contains("overpressure")) {
                    rec.add(addon);
                } else if (addon.getCategory() == MachineAddon.Category.PARALLEL || aId.contains("configurable_maintenance")) {
                    rec.add(addon);
                }
            }

            // Fallback general multiblocks
            for (MachineAddon addon : all) {
                if ((addon.getId().contains("throughput") || addon.getId().contains("configurable_maintenance")) && !rec.contains(addon)) {
                    rec.add(addon);
                }
            }
        }

        return rec;
    }
}
