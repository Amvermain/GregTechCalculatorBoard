package com.gtceu.calcboard.compat.gtceu.handler;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTCEuAddonCrawler;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks compatibility and structure support for muffler hatches and maintenance addons.
 */
public final class GTMufflerMaintenanceHelper {

    private GTMufflerMaintenanceHelper() {}

    private static final Map<ResourceLocation, Boolean> MUFFLER_SUPPORT_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MUFFLER_ADDON_IDS;

    static {
        Set<String> set = new HashSet<>();
        set.add("gtceu:muffler_hatch");
        for (GTVoltageTier tier : GTVoltageTier.values()) {
            set.add("gtceu:" + tier.name().toLowerCase(Locale.ROOT) + "_muffler_hatch");
        }
        MUFFLER_ADDON_IDS = Collections.unmodifiableSet(set);
    }

    public static boolean isMufflerAddon(MachineAddon addon) {
        if (addon == null || addon.getId() == null) return false;
        String id = addon.getId();
        if (MUFFLER_ADDON_IDS.contains(id)) return true;
        return addon.getItemIcon() != null && GTCEuAddonCrawler.isMufflerHatchItem(null, addon.getItemIcon());
    }

    public static boolean supportsMuffler(com.gtceu.calcboard.api.bom.MultiblockStructureDef def, ResourceLocation mbId) {
        if (def == null) return false;
        if (mbId != null) {
            Boolean cached = MUFFLER_SUPPORT_CACHE.get(mbId);
            if (cached != null) return cached;
        }
        boolean has = def.supportsAbility("MUFFLER") || def.parts().stream().anyMatch(p -> p != null && p.itemId() != null && GTCEuAddonCrawler.isMufflerHatchItem(null, p.itemId()));
        if (mbId != null) {
            MUFFLER_SUPPORT_CACHE.put(mbId, has);
        }
        return has;
    }

    public static boolean isMaintenanceAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (!node.isMultiblock()) return false;
        ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
        if (mbId == null) return true;
        var def = MultiblockStructureCatalog.getStructure(mbId);
        if (def == null) return true;
        if (isMufflerAddon(addon)) {
            return supportsMuffler(def, mbId);
        }
        return def.maintenanceSlotCount() > 0 || def.supportsAbility("MAINTENANCE");
    }
}
