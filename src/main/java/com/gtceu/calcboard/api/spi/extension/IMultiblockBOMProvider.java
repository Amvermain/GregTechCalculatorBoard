package com.gtceu.calcboard.api.spi.extension;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provider interface for multiblock structure detection, 3D structure scanning, and Bill of Materials (BOM) resolution.
 */
public interface IMultiblockBOMProvider extends IModExtension {

    default void scanMultiblocks(Object emiRecipeManager) {
    }

    default void scanMultiblockStructures() {
    }

    default MultiblockStructureDef scanMultiblockStructure(ResourceLocation machineId) {
        return null;
    }

    default PartCategory classifyBOMPart(ResourceLocation itemId) {
        return null;
    }

    default void accumulateStructureSlots(ResourceLocation itemId, PartCategory category, int amount, MultiblockStructureCatalog.StructureSlotCounts slots) {
    }

    default void populateExtraBOMParts(RecipeNode node, List<MultiblockStructurePart> parts) {
    }

    default List<MultiblockStructurePart> resolveStructureParts(RecipeNode node, boolean dualLowerTierEnergyHatches) {
        List<MultiblockStructurePart> list = new ArrayList<>();
        if (node == null) return list;

        ResourceLocation machineId = node.getMachineIcon();
        if (machineId == null && !node.getAvailableWorkstations().isEmpty()) {
            machineId = node.getAvailableWorkstations().get(0);
        }

        GTVoltageTier targetTier = node.getTargetTier() != null ? node.getTargetTier() : node.getRecipeTier();
        if (targetTier != null) {
            ResourceLocation tieredWs = node.getWorkstationForTierFromList(targetTier);
            if (tieredWs != null) {
                machineId = tieredWs;
            }
        }

        if (machineId != null && isLikelyMachineOrStructure(node, machineId)) {
            String displayName = MultiblockStructureCatalog.formatMachineName(machineId.getPath());
            list.add(new MultiblockStructurePart(
                    machineId,
                    displayName,
                    1,
                    PartCategory.CONTROLLER
            ));
        }

        Map<ResourceLocation, Integer> addonCounts = new LinkedHashMap<>();
        Map<ResourceLocation, String> addonNames = new LinkedHashMap<>();
        for (MachineAddon addon : node.getAddons()) {
            if (addon != null && addon.getItemIcon() != null) {
                ResourceLocation icon = addon.getItemIcon();
                addonCounts.merge(icon, 1, Integer::sum);
                addonNames.put(icon, addon.getName());
            }
        }
        for (var entry : addonCounts.entrySet()) {
            ResourceLocation icon = entry.getKey();
            int count = entry.getValue();
            String partName = addonNames.getOrDefault(icon, icon.getPath());
            PartCategory pCat = MultiblockStructureCatalog.classifyPart(icon);
            list.add(new MultiblockStructurePart(icon, partName, count, pCat));
        }

        populateExtraBOMParts(node, list);
        return list;
    }

    default boolean isLikelyMachineOrStructure(RecipeNode node, ResourceLocation machineId) {
        if (machineId == null) return false;
        String path = machineId.getPath().toLowerCase(Locale.ROOT);
        if (path.equals("air") || path.equals("barrier") || path.equals("structure_void")) return false;

        if (node != null) {
            if (node.isMultiblock() || node.isGenerator()) return true;
            if (node.getEnergyType() != EnergyType.NONE) return true;
            if (!node.getAddons().isEmpty()) return true;
            if (node.getSteamMode() != null && node.getSteamMode().isSteam()) return true;
        }

        if (MultiblockDetector.isMultiblock(machineId)) return true;
        if (MultiblockStructureCatalog.getStructure(machineId) != null) return true;

        return false;
    }

    default List<ResourceLocation> getMultiblockWorkstations(RecipeNode node) {
        return List.of();
    }

    default ResourceLocation getPreferredMultiblockWorkstation(RecipeNode node, List<ResourceLocation> availableWorkstations) {
        if (availableWorkstations == null || availableWorkstations.isEmpty()) return null;
        return availableWorkstations.get(0);
    }
}
