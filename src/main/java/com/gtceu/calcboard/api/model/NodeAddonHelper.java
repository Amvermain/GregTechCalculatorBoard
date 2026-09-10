package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;

import java.util.List;

/**
 * Encapsulates machine addon mutation, adapter lifecycle delegation, and aggregate multipliers.
 */
public final class NodeAddonHelper {

    private NodeAddonHelper() {}

    public static void addAddon(RecipeNode node, List<MachineAddon> addons, MachineAddon addon) {
        if (addon == null) return;
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            if (!adapter.canInstallAddon(node, addon)) {
                return;
            }
            adapter.onAddonInstalled(node, addon);
        } else {
            addons.add(addon);
        }
    }

    public static void removeSingleAddon(RecipeNode node, List<MachineAddon> addons, String addonId) {
        if (addonId == null) return;
        for (int i = 0; i < addons.size(); i++) {
            if (addons.get(i).getId().equals(addonId)) {
                MachineAddon removed = addons.remove(i);
                IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
                if (adapter != null) {
                    adapter.onAddonRemoved(node, removed);
                }
                break;
            }
        }
    }

    public static boolean removeOneAddon(List<MachineAddon> addons, String addonId) {
        if (addonId == null) return false;
        for (int i = 0; i < addons.size(); i++) {
            if (addons.get(i).getId().equals(addonId)) {
                addons.remove(i);
                return true;
            }
        }
        return false;
    }

    public static double getCombinedDurationMultiplier(List<MachineAddon> addons) {
        double mult = 1.0;
        for (MachineAddon a : addons) {
            mult *= a.getDurationMultiplier();
        }
        return mult;
    }

    public static double getCombinedEutMultiplier(List<MachineAddon> addons) {
        double mult = 1.0;
        for (MachineAddon a : addons) {
            mult *= a.getEutMultiplier();
        }
        return mult;
    }

    public static int getCombinedParallelMultiplier(List<MachineAddon> addons) {
        int mult = 1;
        for (MachineAddon a : addons) {
            mult *= a.getParallelMultiplier();
        }
        return mult;
    }

    public static boolean hasPowerConstantAddon(List<MachineAddon> addons) {
        for (MachineAddon a : addons) {
            if (a.isPowerConstant()) return true;
        }
        return false;
    }
}
