package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates multiblock transitions, hardware addon cleanup, and operational validation.
 */
public final class NodeMultiblockHelper {

    private NodeMultiblockHelper() {}

    public static void configureMultiblock(RecipeNode node, boolean multiblock) {
        if (multiblock) {
            if (node.getSteamMode().isSteam()) {
                node.setSteamMode(SteamMode.NONE);
            }
            if (node.getParallel() <= 1) {
                int defPar = ModAdapterRegistry.getAdapterForNode(node).getDefaultParallel(node);
                if (defPar > 1) {
                    node.setParallel(defPar);
                }
            }
            if (node.getMachineIcon() == null || !MultiblockDetector.isMultiblock(node.getMachineIcon())) {
                ResourceLocation mbWs = node.getMultiblockWorkstation();
                if (mbWs != null && !Objects.equals(node.getMachineIcon(), mbWs)) {
                    node.setMachineIcon(mbWs);
                }
            }
        } else {
            if (node.getMachineIcon() != null && MultiblockDetector.isMultiblock(node.getMachineIcon())) {
                ResourceLocation sbWs = node.getWorkstationForTier(node.getTargetTier());
                if (sbWs == null) {
                    sbWs = node.getSingleblockWorkstation();
                }
                if (sbWs != null && !Objects.equals(node.getMachineIcon(), sbWs)) {
                    node.setMachineIcon(sbWs);
                }
            }
            node.setParallel(1);
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL
                    || a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT
                    || a.getCategory() == MachineAddon.Category.THREADING
                    || a.getCategory() == MachineAddon.Category.PARALLEL);
        }
    }

    public static List<Component> getOperationalWarnings(RecipeNode node, FlowGraph graph) {
        List<Component> warnings = new ArrayList<>();
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null) {
            adapter.validateNode(node, graph, warnings);
        }
        if (!node.hasValidReflector() && warnings.isEmpty()) {
            int req = node.getRequiredReflectorTier();
            int inst = node.getInstalledReflectorTier();
            String instStr = inst > 0 ? ("Tier " + inst) : Component.translatable("gui.gtcalcboard.none_plain").getString();
            warnings.add(Component.translatable("gui.gtcalcboard.node_warning.reflector_detail", String.valueOf(req), instStr));
        }
        return warnings;
    }
}
