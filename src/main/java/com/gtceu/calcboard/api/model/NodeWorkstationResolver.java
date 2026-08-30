package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Dedicated resolver for recipe node workstations (singleblock and multiblock)
 * across voltage tiers, coils, and trait capabilities.
 */
public final class NodeWorkstationResolver {

    private NodeWorkstationResolver() {}

    public static boolean isMultiblockWorkstation(ResourceLocation ws) {
        if (ws == null) return false;
        return MultiblockDetector.isMultiblock(ws);
    }

    public static ResourceLocation getWorkstationForTier(RecipeNode node, GTVoltageTier tier) {
        if (node == null || tier == null) return null;
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            ResourceLocation ws = adapter.getWorkstationForTier(node, tier);
            if (ws != null) return ws;
        }
        return getWorkstationForTierFromList(node, tier);
    }

    public static ResourceLocation getWorkstationForTierFromList(RecipeNode node, GTVoltageTier tier) {
        if (node == null || tier == null) return null;
        String prefix = tier.name().toLowerCase(Locale.ROOT) + "_";
        String catName = node.getRecipeCategoryId() != null ? node.getRecipeCategoryId().getPath().toLowerCase(Locale.ROOT) : null;
        ResourceLocation bestMatch = null;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && !MultiblockDetector.isMultiblock(ws)) {
                String path = ws.getPath().toLowerCase(Locale.ROOT);
                if (path.startsWith(prefix) || path.contains("_" + prefix)) {
                    if (catName != null && path.contains(catName)) {
                        return ws;
                    }
                    if (bestMatch == null) {
                        bestMatch = ws;
                    }
                }
            }
        }
        return bestMatch;
    }

    public static boolean hasMultiblockOption(RecipeNode node) {
        if (node == null || node.isModule()) return false;
        if (node.isMultiblock() || canUseCoils(node)) return true;
        if (node.getRecipeCategoryId() != null && CategoryCapabilityMatrix.getInstance().getCapability(node.getRecipeCategoryId()).hasMultiblockOption()) {
            return true;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (MultiblockDetector.isMultiblock(ws)) {
                return true;
            }
        }
        return false;
    }

    public static List<ResourceLocation> getMultiblockWorkstations(RecipeNode node) {
        List<ResourceLocation> list = new ArrayList<>();
        if (node == null) return list;

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            List<ResourceLocation> adapterList = adapter.getMultiblockWorkstations(node);
            if (adapterList != null && !adapterList.isEmpty()) return adapterList;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && MultiblockDetector.isMultiblock(ws) && !list.contains(ws)) {
                list.add(ws);
            }
        }
        return list;
    }

    public static ResourceLocation getMultiblockWorkstation(RecipeNode node) {
        if (node == null) return null;
        List<ResourceLocation> mbList = getMultiblockWorkstations(node);
        if (!mbList.isEmpty()) {
            return mbList.get(0);
        }
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            ResourceLocation preferred = adapter.getPreferredMultiblockWorkstation(node, node.getAvailableWorkstations());
            if (preferred != null) return preferred;
        }
        return null;
    }

    public static ResourceLocation getSingleblockWorkstation(RecipeNode node) {
        if (node == null) return null;
        ResourceLocation catId = node.getRecipeCategoryId();
        String catName = catId != null ? catId.getPath().toLowerCase(Locale.ROOT) : null;
        ResourceLocation bestMatch = null;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && !MultiblockDetector.isMultiblock(ws)) {
                if (catName != null && ws.getPath().toLowerCase(Locale.ROOT).contains(catName)) {
                    return ws;
                }
                if (bestMatch == null) {
                    bestMatch = ws;
                }
            }
        }
        if (bestMatch != null) return bestMatch;
        if (catId != null && !MultiblockDetector.isMultiblock(catId)) {
            return catId;
        }
        CategoryCapability cap = catId != null ? CategoryCapabilityMatrix.getInstance().getCapability(catId) : null;
        if (cap != null && cap.defaultWorkstation() != null && !MultiblockDetector.isMultiblock(cap.defaultWorkstation())) {
            return cap.defaultWorkstation();
        }
        return (node.getMachineIcon() != null && !MultiblockDetector.isMultiblock(node.getMachineIcon())) ? node.getMachineIcon() : null;
    }

    public static boolean canUseCoils(RecipeNode node) {
        if (node == null) return false;
        if (node.getRecipeTemperature() > 0) return true;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null && CategoryCapabilityMatrix.getInstance().getCapability(catId).canUseCoils()) return true;
        if (node.getMachineIcon() != null && MultiblockDetector.isCoilMultiblock(node.getMachineIcon())) return true;
        if (catId != null && MultiblockDetector.isCoilRecipeCategory(catId)) return true;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (MultiblockDetector.isCoilMultiblock(ws)) return true;
        }
        return false;
    }

    public static boolean canUseMultiblockTraits(RecipeNode node) {
        if (node == null) return false;
        if (node.isMultiblock() || hasMultiblockOption(node) || canUseCoils(node)) return true;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (MultiblockDetector.isMultiblock(ws)) return true;
        }
        return false;
    }
}
