package com.gtceu.calcboard.compat.gtceu.physics;

import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuBOMHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuMultiblockStructureScanner;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Encapsulates multiblock structure scanning, part categorization, and BOM part resolution for GTCEu.
 */
public final class GTMultiblockBOMResolver {

    private GTMultiblockBOMResolver() {}

    public static MultiblockStructureDef scanMultiblockStructure(ResourceLocation machineId) {
        return GTCEuMultiblockStructureScanner.scanSingle(machineId);
    }

    public static PartCategory classifyBOMPart(ResourceLocation itemId) {
        if (itemId == null) return null;
        if (CoilHelper.isHeatingCoil(itemId)) {
            return PartCategory.COIL;
        }
        String ns = itemId.getNamespace().toLowerCase(Locale.ROOT);
        if (!ns.equals("gtceu") && !ns.equals("gtcalcboard") && !ns.contains("start")) {
            return null;
        }
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        if ((path.contains("energy") && path.contains("hatch")) || (path.contains("power") && path.contains("hatch"))
                || path.contains("laser_target") || path.contains("laser_source") || path.contains("input_bus")
                || path.contains("output_bus") || path.contains("input_hatch") || path.contains("output_hatch")
                || path.contains("maintenance") || path.contains("parallel") || path.contains("hatch")
                || path.contains("bus") || path.contains("target") || path.contains("source")
                || path.contains("import") || path.contains("export") || path.contains("augment")
                || path.contains("upgrade")) {
            return PartCategory.HATCH_BUS;
        } else if (path.contains("casing") || path.contains("pipe") || path.contains("glass") || path.contains("wall")
                || path.contains("grate") || path.contains("frame") || path.contains("coil") || path.contains("magnet")) {
            return PartCategory.CASING;
        }
        return null;
    }

    public static List<MultiblockStructurePart> resolveStructureParts(RecipeNode node, boolean dualLowerTierEnergyHatches) {
        if (node == null) return Collections.emptyList();
        return GTCEuBOMHelper.resolveGTMultiblockParts(node, dualLowerTierEnergyHatches);
    }

    public static void accumulateStructureSlots(
            ResourceLocation itemId,
            PartCategory category,
            int amount,
            com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.StructureSlotCounts slots
    ) {
        if (itemId == null || slots == null) return;
        if (category == PartCategory.COIL) {
            slots.coilSlots = Math.max(slots.coilSlots, amount);
            return;
        }
        String path = itemId.getPath().toLowerCase(Locale.ROOT);
        if ((path.contains("energy") && path.contains("hatch")) || (path.contains("power") && path.contains("hatch")) || path.contains("laser_target") || path.contains("laser_source")) {
            slots.energyHatchSlots = Math.max(slots.energyHatchSlots, amount);
        } else if (path.contains("input_bus") || path.contains("import_bus")) {
            slots.inputBusSlots = Math.max(slots.inputBusSlots, amount);
        } else if (path.contains("output_bus") || path.contains("export_bus")) {
            slots.outputBusSlots = Math.max(slots.outputBusSlots, amount);
        } else if (path.contains("input_hatch") || path.contains("fluid_import")) {
            slots.inputHatchSlots = Math.max(slots.inputHatchSlots, amount);
        } else if (path.contains("output_hatch") || path.contains("fluid_export")) {
            slots.outputHatchSlots = Math.max(slots.outputHatchSlots, amount);
        } else if (path.contains("maintenance")) {
            slots.maintenanceSlots = Math.max(slots.maintenanceSlots, amount);
        }
    }
}
