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
        syncCompoundGroupIfApplicable(node);
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
                syncCompoundGroupIfApplicable(node);
                break;
            }
        }
    }

    private static void syncCompoundGroupIfApplicable(RecipeNode node) {
        if (node != null && node.isCompoundNode() && node.getParentGraph() != null) {
            node.getParentGraph().syncCompoundParameters(node);
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
        for (int i = 0; i < addons.size(); i++) {
            MachineAddon a = addons.get(i);
            if (isDuplicateTrait(addons, i, a)) continue;
            mult *= a.getDurationMultiplier();
        }
        return mult;
    }

    public static double getCombinedEutMultiplier(List<MachineAddon> addons) {
        double mult = 1.0;
        for (int i = 0; i < addons.size(); i++) {
            MachineAddon a = addons.get(i);
            if (isDuplicateTrait(addons, i, a)) continue;
            mult *= a.getEutMultiplier();
        }
        return mult;
    }

    public static int getCombinedParallelMultiplier(List<MachineAddon> addons) {
        int mult = 1;
        for (int i = 0; i < addons.size(); i++) {
            MachineAddon a = addons.get(i);
            if (isDuplicateTrait(addons, i, a)) continue;
            mult *= a.getParallelMultiplier();
        }
        return mult;
    }

    public static int getPowerConsumingParallelMultiplier(List<MachineAddon> addons) {
        int mult = 1;
        for (int i = 0; i < addons.size(); i++) {
            MachineAddon a = addons.get(i);
            if (isDuplicateTrait(addons, i, a)) continue;
            if (!a.isPowerConstant()) {
                mult *= a.getParallelMultiplier();
            }
        }
        return mult;
    }

    public static int getPowerConstantParallelMultiplier(List<MachineAddon> addons) {
        if (addons == null || addons.isEmpty()) return 1;
        int mult = 1;
        for (int i = 0; i < addons.size(); i++) {
            MachineAddon a = addons.get(i);
            if (isDuplicateTrait(addons, i, a)) continue;
            if (a.isPowerConstant() && a.getParallelMultiplier() > 1) {
                mult *= a.getParallelMultiplier();
            }
        }
        return mult;
    }

    private static boolean isDuplicateTrait(List<MachineAddon> addons, int index, MachineAddon addon) {
        if (addon == null || addon.getCategory() != MachineAddon.Category.MULTIBLOCK_TRAIT) {
            return false;
        }
        String id = addon.getId();
        if (id == null) return false;
        for (int j = 0; j < index; j++) {
            MachineAddon prev = addons.get(j);
            if (prev != null && id.equals(prev.getId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasPowerConstantAddon(List<MachineAddon> addons) {
        for (MachineAddon a : addons) {
            if (a.isPowerConstant()) return true;
        }
        return false;
    }
}
