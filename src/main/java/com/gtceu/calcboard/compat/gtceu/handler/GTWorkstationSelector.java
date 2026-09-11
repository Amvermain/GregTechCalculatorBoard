package com.gtceu.calcboard.compat.gtceu.handler;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Selects preferred multiblock workstations and controllers for recipes and nodes.
 */
public final class GTWorkstationSelector {

    private GTWorkstationSelector() {}

    public static ResourceLocation getPreferredMultiblockWorkstation(RecipeNode node, List<ResourceLocation> availableWorkstations) {
        if (node == null || availableWorkstations == null || availableWorkstations.isEmpty()) return null;

        if (GTFusionHelper.isFusion(node)) {
            ResourceLocation fusionWs = selectFusionWorkstation(node, availableWorkstations);
            if (fusionWs != null) return fusionWs;
        }

        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null) {
            for (ResourceLocation ws : availableWorkstations) {
                if (MultiblockDetector.isMultiblock(ws) && ws.getPath().equalsIgnoreCase(catId.getPath())) {
                    return ws;
                }
            }
        }

        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws) && ws.getPath().toLowerCase(Locale.ROOT).startsWith("large_")) {
                return ws;
            }
        }

        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) {
                String path = ws.getPath().toLowerCase(Locale.ROOT);
                if (!path.startsWith("mega_") && !path.startsWith("extreme_") && !path.startsWith("incomprehensible_")
                        && !path.startsWith("advanced_") && !path.startsWith("yielding_") && !path.startsWith("super_")
                        && !path.startsWith("supreme_") && !path.startsWith("nyinsane_")) {
                    return ws;
                }
            }
        }

        for (ResourceLocation ws : availableWorkstations) {
            if (MultiblockDetector.isMultiblock(ws)) return ws;
        }
        return null;
    }

    private static ResourceLocation selectFusionWorkstation(RecipeNode node, List<ResourceLocation> availableWorkstations) {
        GTVoltageTier minTier = GTFusionHelper.getMinFusionVoltageTier(node);
        for (ResourceLocation ws : availableWorkstations) {
            if (ws != null && GTCEuModAdapter.extractVoltageTierFromIcon(ws) == minTier) {
                return ws;
            }
        }
        ResourceLocation bestHigher = null;
        GTVoltageTier bestTier = null;
        for (ResourceLocation ws : availableWorkstations) {
            if (ws == null) continue;
            GTVoltageTier wsTier = GTCEuModAdapter.extractVoltageTierFromIcon(ws);
            if (wsTier != null && wsTier.ordinal() >= minTier.ordinal()) {
                if (bestTier == null || wsTier.ordinal() < bestTier.ordinal()) {
                    bestTier = wsTier;
                    bestHigher = ws;
                }
            }
        }
        return bestHigher;
    }
}
