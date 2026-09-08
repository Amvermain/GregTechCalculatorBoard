package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.property.NodePropertyStore;

import java.util.List;

/**
 * Helper for neutron reflector tier requirements and installed reflector validation on RecipeNode.
 */
public final class RecipeNodeReflectorHelper {

    private static final String REQUIRED_REFLECTOR_TIER_KEY = "required_reflector_tier";

    private RecipeNodeReflectorHelper() {}

    public static int getRequiredReflectorTier(NodePropertyStore properties) {
        return properties.getById(REQUIRED_REFLECTOR_TIER_KEY, 0);
    }

    public static void setRequiredReflectorTier(NodePropertyStore properties, int tier) {
        properties.setById(REQUIRED_REFLECTOR_TIER_KEY, Math.max(0, tier));
    }

    public static int getInstalledReflectorTier(List<MachineAddon> addons) {
        if (addons == null || addons.isEmpty()) return 0;
        for (MachineAddon addon : addons) {
            if (addon != null && addon.getCategory() == MachineAddon.Category.REFLECTOR) {
                return addon.getReflectorTier();
            }
        }
        return 0;
    }

    public static boolean hasValidReflector(NodePropertyStore properties, List<MachineAddon> addons) {
        int required = getRequiredReflectorTier(properties);
        if (required <= 0) return true;
        return getInstalledReflectorTier(addons) >= required;
    }
}
