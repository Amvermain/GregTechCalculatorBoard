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
        Set<MachineAddon.Category> supportedAddonCategories
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
            Set.of(MachineAddon.Category.CUSTOM)
    );

    /**
     * Resolves the active addon categories to display for a specific node in this category.
     */
    public List<MachineAddon.Category> getActiveCategoriesForNode(RecipeNode node) {
        List<MachineAddon.Category> cats = new ArrayList<>();
        if (node == null) {
            cats.addAll(List.of(MachineAddon.Category.values()));
            return cats;
        }

        if (isThermal) {
            cats.add(MachineAddon.Category.THERMAL_AUGMENT);
            cats.add(MachineAddon.Category.CUSTOM);
            return cats;
        }

        if (isTurbine || node.isTurbine()) {
            cats.add(MachineAddon.Category.ROTOR);
            if (node.isMultiblock() || node.hasMultiblockOption() || hasMultiblockOption) {
                cats.add(MachineAddon.Category.MAINTENANCE);
            }
            cats.add(MachineAddon.Category.CUSTOM);
            return cats;
        }

        boolean isMb = node.isMultiblock() || node.hasMultiblockOption() || hasMultiblockOption;

        if (isMb) {
            if (canUseCoils || node.canUseCoils()) {
                cats.add(MachineAddon.Category.COIL);
            }
            cats.add(MachineAddon.Category.PARALLEL);
            cats.add(MachineAddon.Category.MAINTENANCE);
            cats.add(MachineAddon.Category.MULTIBLOCK_TRAIT);
        }

        cats.add(MachineAddon.Category.CUSTOM);
        return cats;
    }
}
