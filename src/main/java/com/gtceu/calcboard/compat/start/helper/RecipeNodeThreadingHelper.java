package com.gtceu.calcboard.compat.start.helper;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.start.model.NodeThreadingConfig;
import com.gtceu.calcboard.compat.start.model.StarTProperties;
import net.minecraft.resources.ResourceLocation;

/**
 * Encapsulates multiblock threading and helix configuration logic for RecipeNode.
 */
public final class RecipeNodeThreadingHelper {

    private RecipeNodeThreadingHelper() {}

    public static NodeThreadingConfig getThreadingConfig(RecipeNode node) {
        if (node == null) return new NodeThreadingConfig();
        NodeThreadingConfig cfg = node.getProperties().get(StarTProperties.THREADING_CONFIG);
        if (cfg == null) {
            cfg = new NodeThreadingConfig();
            node.getProperties().set(StarTProperties.THREADING_CONFIG, cfg);
        }
        int max = node.getMachineIcon() != null ? MultiblockDetector.getMaxHelixCount(node.getMachineIcon()) : 0;
        if (max > 0 && cfg.getMaxHelixCapacity() <= 0) {
            cfg.setMaxHelixCapacity(max);
        }
        return cfg;
    }

    public static NodeThreadingConfig getOrCreateThreadingConfig(RecipeNode node) {
        return getThreadingConfig(node);
    }

    public static void setThreadingConfig(RecipeNode node, NodeThreadingConfig cfg) {
        if (node == null) return;
        node.getProperties().set(StarTProperties.THREADING_CONFIG, cfg);
    }

    public static boolean isThreadingAvailable(RecipeNode node) {
        if (node == null) return false;
        return MultiblockDetector.getMaxHelixCount(node) > 0;
    }

    public static boolean isExplicitThreadingMachine(RecipeNode node) {
        if (node == null) return false;
        ResourceLocation icon = node.getMachineIcon();
        return icon != null && MultiblockDetector.isThreadingMultiblock(icon);
    }

    public static boolean hasThreading(RecipeNode node) {
        if (node == null) return false;
        if (isExplicitThreadingMachine(node)) return true;
        NodeThreadingConfig cfg = node.getProperties().get(StarTProperties.THREADING_CONFIG);
        return cfg != null && cfg.isActive();
    }

    public static void setThreadingActive(RecipeNode node, boolean active) {
        if (node == null) return;
        if (active) {
            getThreadingConfig(node).setActive(true);
            for (ResourceLocation ws : node.getAvailableWorkstations()) {
                if (ws != null && MultiblockDetector.isThreadingMultiblock(ws)) {
                    node.setMachineIcon(ws);
                    break;
                }
            }
        } else {
            NodeThreadingConfig cfg = node.getProperties().get(StarTProperties.THREADING_CONFIG);
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
