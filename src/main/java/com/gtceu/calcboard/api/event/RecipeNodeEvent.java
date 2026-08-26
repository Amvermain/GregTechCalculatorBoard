package com.gtceu.calcboard.api.event;

import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.Event;

import java.util.Collections;
import java.util.Map;

/**
 * Base Forge event for lifecycle hooks and calculation events of RecipeNode instances.
 */
public abstract class RecipeNodeEvent extends Event {

    private static ListenerList LISTENER_LIST = new ListenerList();
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

    @Override
    public ListenerList getListenerList() {
        return LISTENER_LIST;
    }

    public static void clearListeners() {
        LISTENER_LIST = new ListenerList();
        Created.LISTENER_LIST = new ListenerList(LISTENER_LIST);
        PreCalculation.LISTENER_LIST = new ListenerList(LISTENER_LIST);
        PostCalculation.LISTENER_LIST = new ListenerList(LISTENER_LIST);
    }

    /**
     * Fired when a RecipeNode is created or initialized from EMI/Search.
     */
    public static class Created extends RecipeNodeEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(RecipeNodeEvent.LISTENER_LIST);

        public Created() {
            super(null);
        }

        public Created(RecipeNode node) {
            super(node);
        }

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }

    /**
     * Fired before overclocking, duration, and energy calculations are computed.
     */
    public static class PreCalculation extends RecipeNodeEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(RecipeNodeEvent.LISTENER_LIST);

        public PreCalculation() {
            super(null);
        }

        public PreCalculation(RecipeNode node) {
            super(node);
        }

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }

    /**
     * Fired after effective input/output rates are calculated.
     * Listeners can read and modify the effective rate mappings.
     */
    public static class PostCalculation extends RecipeNodeEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(RecipeNodeEvent.LISTENER_LIST);
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

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }
}
