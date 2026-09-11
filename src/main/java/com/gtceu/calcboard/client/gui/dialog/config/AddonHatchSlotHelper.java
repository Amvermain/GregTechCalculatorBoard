package com.gtceu.calcboard.client.gui.dialog.config;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Helper providing hatch slot capacity checks, installed counts, and hatch type resolution.
 */
public final class AddonHatchSlotHelper {

    private AddonHatchSlotHelper() {}

    public static int getMaxHatchSlotsAllowed(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return 1;
        GTHatchAddon.HatchType type = getHatchType(addon);

        int reqCount = switch (type) {
            case FLUID_OUTPUT -> (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
            case FLUID_INPUT -> (int) node.getInputs().stream().filter(IngredientStack::isFluid).count();
            case ITEM_OUTPUT -> (int) node.getOutputs().stream().filter(IngredientStack::isItem).count();
            case ITEM_INPUT -> (int) node.getInputs().stream().filter(IngredientStack::isItem).count();
            case DUAL_INPUT -> Math.max(
                    (int) node.getInputs().stream().filter(IngredientStack::isFluid).count(),
                    (int) node.getInputs().stream().filter(IngredientStack::isItem).count()
            );
            case DUAL_OUTPUT -> Math.max(
                    (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count(),
                    (int) node.getOutputs().stream().filter(IngredientStack::isItem).count()
            );
            default -> 1;
        };

        ResourceLocation mbId = node.getMachineIcon();
        if (mbId == null || !MultiblockDetector.isMultiblock(mbId)) {
            mbId = node.getMultiblockWorkstation();
        }

        if (mbId != null) {
            int rFluidOut = (int) node.getOutputs().stream().filter(IngredientStack::isFluid).count();
            int rItemOut = (int) node.getOutputs().stream().filter(IngredientStack::isItem).count();
            int rFluidIn = (int) node.getInputs().stream().filter(IngredientStack::isFluid).count();
            int rItemIn = (int) node.getInputs().stream().filter(IngredientStack::isItem).count();
            var def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getMatchingStructure(mbId, rFluidOut, rItemOut, rFluidIn, rItemIn);
            if (def == null) {
                def = com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(mbId);
            }
            if (def != null) {
                int defSlots = switch (type) {
                    case FLUID_OUTPUT -> def.outputHatchSlotCount();
                    case FLUID_INPUT -> def.inputHatchSlotCount();
                    case ITEM_OUTPUT -> def.outputBusSlotCount();
                    case ITEM_INPUT -> def.inputBusSlotCount();
                    default -> 1;
                };
                reqCount = Math.max(reqCount, defSlots);
            }
        }

        return Math.max(1, reqCount);
    }

    public static int getTotalInstalledHatchesOfSameType(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return 0;
        GTHatchAddon.HatchType type = getHatchType(addon);
        int total = 0;
        for (MachineAddon a : node.getAddons()) {
            if (a.getCategory() == MachineAddon.Category.HATCH_BUS) {
                if (getHatchType(a) == type) {
                    total++;
                }
            }
        }
        return total;
    }

    public static GTHatchAddon.HatchType getHatchType(MachineAddon addon) {
        if (addon instanceof GTHatchAddon gh) return gh.getHatchType();
        var stats = com.gtceu.calcboard.compat.gtceu.helper.GTHatchHelper.extractStatsFromMachineDef(null, addon.getItemIcon());
        if (stats != null) return stats.hatchType();
        String path = addon.getId().toLowerCase(Locale.ROOT);
        if (path.contains("input_hatch") || path.contains("fluid_import")) return GTHatchAddon.HatchType.FLUID_INPUT;
        if (path.contains("output_bus") || path.contains("export_bus")) return GTHatchAddon.HatchType.ITEM_OUTPUT;
        if (path.contains("input_bus") || path.contains("import_bus")) return GTHatchAddon.HatchType.ITEM_INPUT;
        return GTHatchAddon.HatchType.FLUID_OUTPUT;
    }
}
