package com.gtceu.calcboard.api.model;

import java.util.Objects;

/**
 * Projected port descriptor synthesized dynamically from a base recipe and current hardware configuration.
 */
public record ProjectedPort(
        IngredientStack stack,
        PortRole role,
        int coreIndex,
        String sourceAddonId
) {
    public ProjectedPort {
        Objects.requireNonNull(stack, "stack cannot be null");
        if (role == null) role = PortRole.CORE_RECIPE;
    }

    public static ProjectedPort ofCore(IngredientStack stack, int coreIndex) {
        return new ProjectedPort(stack, PortRole.CORE_RECIPE, coreIndex, null);
    }

    public static ProjectedPort ofAuxInput(IngredientStack stack, String sourceAddonId) {
        return new ProjectedPort(stack, PortRole.AUXILIARY_INPUT, -1, sourceAddonId);
    }

    public static ProjectedPort ofAuxOutput(IngredientStack stack, String sourceAddonId) {
        return new ProjectedPort(stack, PortRole.AUXILIARY_OUTPUT, -1, sourceAddonId);
    }

    public boolean isCore() {
        return role == PortRole.CORE_RECIPE;
    }

    public boolean isAuxiliary() {
        return role == PortRole.AUXILIARY_INPUT || role == PortRole.AUXILIARY_OUTPUT;
    }
}
