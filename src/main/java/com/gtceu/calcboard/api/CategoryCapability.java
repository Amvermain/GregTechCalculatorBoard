package com.gtceu.calcboard.api;

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
            cats.add(AddonCategory.ROTOR);
            if (node.isMultiblock() || node.hasMultiblockOption() || hasMultiblockOption) {
                cats.add(AddonCategory.MAINTENANCE);
            }
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        boolean isMb = node.isMultiblock() || node.hasMultiblockOption() || hasMultiblockOption;
        boolean isFusion = node.isFusion() || node.getRequiredReflectorTier() > 0 || (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion"));

        if (isFusion) {
            cats.add(AddonCategory.REFLECTOR);
            cats.add(AddonCategory.PARALLEL);
            cats.add(AddonCategory.MAINTENANCE);
            cats.add(AddonCategory.CUSTOM);
            return cats;
        }

        if (isMb) {
            if (canUseCoils || node.canUseCoils()) {
                cats.add(AddonCategory.COIL);
            }
            cats.add(AddonCategory.PARALLEL);
            cats.add(AddonCategory.MAINTENANCE);
            cats.add(AddonCategory.MULTIBLOCK_TRAIT);
        }

        cats.add(AddonCategory.CUSTOM);
        return cats;
    }
}
