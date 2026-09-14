package com.gtceu.calcboard.api.model;

/**
 * Functional classification of a projected port.
 * Distinguishes core recipe inputs/outputs from dynamic hardware-injected sidecar ports.
 */
public enum PortRole {
    /**
     * Core ingredient defined by the base recipe specification.
     * Guaranteed to maintain stable 0-based ordering and indices.
     */
    CORE_RECIPE,

    /**
     * Auxiliary input injected by hardware configuration (e.g., steam, coolant, lubricant, oxidizer).
     */
    AUXILIARY_INPUT,

    /**
     * Auxiliary byproduct output injected by hardware configuration (e.g., condensate, ash).
     */
    AUXILIARY_OUTPUT
}
