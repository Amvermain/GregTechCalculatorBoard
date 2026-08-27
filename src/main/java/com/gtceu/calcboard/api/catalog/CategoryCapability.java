package com.gtceu.calcboard.api.catalog;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Immutable metadata record representing the deducted mechanical capabilities,
 * workstations, and valid addon categories for a specific GT/mod recipe category.
 */
public record CategoryCapability(
        ResourceLocation categoryId,
        List<ResourceLocation> availableWorkstations,
        ResourceLocation defaultWorkstation,
        boolean hasSingleblockOption,
        boolean hasMultiblockOption,
        boolean canUseCoils,
        boolean isTurbine,
        boolean isThermal,
        boolean hasLowPressureSteamOption,
        boolean hasHighPressureSteamOption,
        ResourceLocation lowPressureWorkstation,
        ResourceLocation highPressureWorkstation,
        GTVoltageTier turbineBaseTier,
        double turbineBaseProduction,
        Set<AddonCategory> supportedAddonCategories
) {

    public static final CategoryCapability DEFAULT = new CategoryCapability(
            null,
            Collections.emptyList(),
            null,
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            null,
            null,
            null,
            0.0,
            Set.of(AddonCategory.CUSTOM)
    );

    /**
     * Resolves the active addon categories to display for a specific node in this category.
     */
    public List<AddonCategory> getActiveCategoriesForNode(RecipeNode node) {
        List<AddonCategory> cats = new ArrayList<>();
        if (node == null) {
            cats.addAll(AddonCategory.values());
            return cats;
        }

        if (isThermal) {
            cats.add(AddonCategory.THERMAL_AUGMENT);
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        if (isTurbine || node.isTurbine()) {
            if (node.isMultiblock() || node.hasMultiblockOption() || hasMultiblockOption) {
                cats.add(AddonCategory.ROTOR);
                cats.add(AddonCategory.MAINTENANCE);
                if (com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.isPlasmaTurbine(node)) {
                    cats.add(AddonCategory.MULTIBLOCK_TRAIT);
                }
                cats.add(AddonCategory.CUSTOM);
                return cats;
            }
            return List.of(AddonCategory.CUSTOM);
        }

        boolean isMb = node.isMultiblock() || node.hasMultiblockOption() || hasMultiblockOption;
        boolean isFusion = node.isFusion() || node.getRequiredReflectorTier() > 0 || (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion"));

        if (isFusion) {
            cats.add(AddonCategory.REFLECTOR);
            cats.add(AddonCategory.ENERGY_HATCH);
            cats.add(AddonCategory.HATCH_BUS);
            cats.add(AddonCategory.PARALLEL);
            cats.add(AddonCategory.MAINTENANCE);
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        if (isMb) {
            ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
            var def = mbId != null ? com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.getStructure(mbId) : null;
            boolean isSteamMb = (node.getSteamMode() != null && node.getSteamMode().isSteam()) || (mbId != null && MultiblockDetector.isSteamMultiblock(mbId));

            if (!isSteamMb && !node.isGenerator() && node.getEnergyType() != EnergyType.NONE && node.getEnergyType() != EnergyType.KINETIC_SU
                    && (def == null || def.supportsAbility("INPUT_ENERGY") || def.supportsAbility("SUBSTATION_INPUT_ENERGY") || def.supportsAbility("INPUT_LASER") || def.energyHatchSlotCount() > 0 || def.allowedAbilities().isEmpty() || node.getEnergyType() != EnergyType.NONE)) {
                cats.add(AddonCategory.ENERGY_HATCH);
            }
            if (def == null || !def.allowedAbilities().isEmpty() || def.inputBusSlotCount() > 0 || def.outputBusSlotCount() > 0 || def.inputHatchSlotCount() > 0 || def.outputHatchSlotCount() > 0 || !node.getInputs().isEmpty() || !node.getOutputs().isEmpty() || isMb) {
                cats.add(AddonCategory.HATCH_BUS);
            }
            boolean supportsCoil = false;
            if (def != null) {
                supportsCoil = def.coilSlotCount() > 0 || def.supportsAbility("HEATING_COILS") || MultiblockDetector.isCoilMultiblock(mbId);
            } else {
                supportsCoil = canUseCoils || node.canUseCoils() || MultiblockDetector.isCoilMultiblock(mbId);
            }
            if (supportsCoil) {
                cats.add(AddonCategory.COIL);
            }
            if (!isSteamMb && (MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations(), node.getRecipeCategoryId()) || (def != null && def.supportsAbility("PARALLEL_HATCH")))) {
                cats.add(AddonCategory.PARALLEL);
            }
            if (!isSteamMb && (def == null || def.supportsAbility("MAINTENANCE") || def.maintenanceSlotCount() > 0 || node.getEnergyType() != EnergyType.NONE)) {
                cats.add(AddonCategory.MAINTENANCE);
            }
            if (node.hasThreading()) {
                cats.add(AddonCategory.THREADING);
            }
            cats.add(AddonCategory.MULTIBLOCK_TRAIT);
        } else if (node.hasThreading()) {
            cats.add(AddonCategory.THREADING);
        }

        cats.add(AddonCategory.CUSTOM);
        return cats;
    }

    public boolean supportsSteamMode() {
        return hasLowPressureSteamOption || hasHighPressureSteamOption;
    }
}


