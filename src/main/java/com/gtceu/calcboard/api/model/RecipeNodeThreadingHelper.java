package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.type.NodeThreadingConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * Encapsulates multiblock threading and helix configuration logic for RecipeNode.
 */
public final class RecipeNodeThreadingHelper {

    private RecipeNodeThreadingHelper() {}

    public static boolean isThreadingAvailable(RecipeNode node) {
        return MultiblockDetector.getMaxHelixCount(node) > 0;
    }

    public static boolean isExplicitThreadingMachine(RecipeNode node) {
        ResourceLocation icon = node.getMachineIcon();
        return icon != null && MultiblockDetector.isThreadingMultiblock(icon);
    }

    public static boolean hasThreading(RecipeNode node) {
        if (isExplicitThreadingMachine(node)) return true;
        NodeThreadingConfig cfg = node.getThreadingConfig();
        return cfg != null && cfg.isActive();
    }

    public static void setThreadingActive(RecipeNode node, boolean active) {
        if (active) {
            node.getThreadingConfig().setActive(true);
            for (ResourceLocation ws : node.getAvailableWorkstations()) {
                if (ws != null && MultiblockDetector.isThreadingMultiblock(ws)) {
                    node.setMachineIcon(ws);
                    break;
                }
            }
        } else {
            NodeThreadingConfig cfg = node.getThreadingConfig();
            if (cfg != null) {
                cfg.reset();
                cfg.setActive(false);
            }
            node.getAddons().removeIf(a -> a.getCategory() == AddonCategory.THREADING);
            ResourceLocation icon = node.getMachineIcon();
            if (icon != null && MultiblockDetector.isThreadingMultiblock(icon)) {
                ResourceLocation mbWs = node.getMultiblockWorkstation();
                if (mbWs != null && !MultiblockDetector.isThreadingMultiblock(mbWs)) {
                    node.setMachineIcon(mbWs);
                }
            }
        }
    }
}
