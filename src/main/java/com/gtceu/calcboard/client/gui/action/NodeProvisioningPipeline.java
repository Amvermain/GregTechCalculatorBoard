package com.gtceu.calcboard.client.gui.action;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.NodeWorkstationResolver;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.preset.CategoryMachinePreset;
import com.gtceu.calcboard.api.preset.CategoryMachinePresetManager;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Orchestrates automatic voltage tier and energy hatch provisioning when nodes are added to a board page.
 */
public final class NodeProvisioningPipeline {

    private NodeProvisioningPipeline() {}

    public static void provision(RecipeNode node, BoardPage page) {
        if (node == null || page == null) return;
        GTVoltageTier targetTier = page.getDefaultVoltageTier();
        if (targetTier == null) return;
        if (node.getEnergyType() != EnergyType.ELECTRIC_EU) return;

        GTVoltageTier recipeTier = node.getRecipeTier();
        GTVoltageTier effectiveTier = targetTier;
        if (recipeTier != null && recipeTier.ordinal() > targetTier.ordinal()) {
            effectiveTier = recipeTier;
        }

        if (node.isMultiblock()) {
            provisionMultiblock(node, page, effectiveTier);
        } else {
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            if (adapter != null) {
                effectiveTier = adapter.sanitizeTargetTier(node, effectiveTier);
            }
            provisionSingleblock(node, effectiveTier);
        }
    }

    private static void provisionMultiblock(RecipeNode node, BoardPage page, GTVoltageTier effectiveTier) {
        if (!page.isAutoEquipEnergyHatches()) return;

        boolean hasCustomHatch = node.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH);
        if (hasCustomHatch) return;

        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null && CategoryMachinePresetManager.getInstance().hasPreset(catId)) {
            CategoryMachinePreset preset = CategoryMachinePresetManager.getInstance().getPreset(catId);
            if (preset != null && preset.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH)) {
                return;
            }
        }

        EnergyHatchHelper.installDefaultEnergyHatch(node, effectiveTier);
    }

    private static void provisionSingleblock(RecipeNode node, GTVoltageTier effectiveTier) {
        node.setTargetTier(effectiveTier);
        GTVoltageTier actualTier = node.getTargetTier() != null ? node.getTargetTier() : effectiveTier;
        ResourceLocation ws = NodeWorkstationResolver.getWorkstationForTier(node, actualTier);
        if (ws != null) {
            node.setMachineIcon(ws);
        }
        node.markOverclockDirty();
    }
}
