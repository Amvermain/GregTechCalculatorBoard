package com.gtceu.calcboard.api.event;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.neoforged.bus.api.Event;

import java.util.Collections;
import java.util.Map;

/**
 * Base NeoForge event for lifecycle hooks and calculation events of RecipeNode instances.
 */
public abstract class RecipeNodeEvent extends Event {

    private final RecipeNode node;

    public RecipeNodeEvent() {
        this(null);
    }

    public RecipeNodeEvent(RecipeNode node) {
        this.node = node;
    }

    public RecipeNode getNode() {
        return node;
    }

    public static void clearListeners() {
        // No-op in NeoForge event system
    }

    /**
     * Fired when a RecipeNode is created or initialized from EMI/Search.
     */
    public static class Created extends RecipeNodeEvent {
        public Created() {
            super(null);
        }

        public Created(RecipeNode node) {
            super(node);
        }
    }

    /**
     * Fired before overclocking, duration, and energy calculations are computed.
     */
    public static class PreCalculation extends RecipeNodeEvent {
        public PreCalculation() {
            super(null);
        }

        public PreCalculation(RecipeNode node) {
            super(node);
        }
    }

    /**
     * Fired after effective input/output rates are calculated.
     * Listeners can read and modify the effective rate mappings.
     */
    public static class PostCalculation extends RecipeNodeEvent {
        private final Map<IngredientStack, Double> inputRates;
        private final Map<IngredientStack, Double> outputRates;

        public PostCalculation() {
            this(null, Collections.emptyMap(), Collections.emptyMap());
        }

        public PostCalculation(RecipeNode node, Map<IngredientStack, Double> inputRates, Map<IngredientStack, Double> outputRates) {
            super(node);
            this.inputRates = inputRates;
            this.outputRates = outputRates;
        }

        public Map<IngredientStack, Double> getInputRates() {
            return inputRates;
        }

        public Map<IngredientStack, Double> getOutputRates() {
            return outputRates;
        }
    }
}

