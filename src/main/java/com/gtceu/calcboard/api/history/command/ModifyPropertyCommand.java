package com.gtceu.calcboard.api.history.command;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Node property modification (Count, Tier, OC, Parallel, Rotor, Title, Anchor).
 */
public class ModifyPropertyCommand<T> implements BoardCommand {
    public enum Property {
        MACHINE_COUNT,
        TARGET_TIER,
        OVERCLOCK_MODE,
        PARALLEL,
        CUSTOM_NAME,
        BASE_ANCHOR,
        ROTOR_EFFICIENCY,
        ROTOR_POWER,
        ROTOR_NAME
    }

    private final String nodeId;
    private final Property property;
    private final T oldValue;
    private final T newValue;

    public ModifyPropertyCommand(String nodeId, Property property, T oldValue, T newValue) {
        this.nodeId = nodeId;
        this.property = property;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public static ModifyPropertyCommand<Double> machineCount(String nodeId, double oldVal, double newVal) {
        return new ModifyPropertyCommand<>(nodeId, Property.MACHINE_COUNT, oldVal, newVal);
    }

    public static ModifyPropertyCommand<com.gtceu.calcboard.api.type.GTVoltageTier> targetTier(String nodeId, com.gtceu.calcboard.api.type.GTVoltageTier oldVal, com.gtceu.calcboard.api.type.GTVoltageTier newVal) {
        return new ModifyPropertyCommand<>(nodeId, Property.TARGET_TIER, oldVal, newVal);
    }

    public static ModifyPropertyCommand<com.gtceu.calcboard.api.type.OverclockMode> overclockMode(String nodeId, com.gtceu.calcboard.api.type.OverclockMode oldVal, com.gtceu.calcboard.api.type.OverclockMode newVal) {
        return new ModifyPropertyCommand<>(nodeId, Property.OVERCLOCK_MODE, oldVal, newVal);
    }

    public static ModifyPropertyCommand<Integer> parallel(String nodeId, int oldVal, int newVal) {
        return new ModifyPropertyCommand<>(nodeId, Property.PARALLEL, oldVal, newVal);
    }

    public static ModifyPropertyCommand<String> customName(String nodeId, String oldVal, String newVal) {
        return new ModifyPropertyCommand<>(nodeId, Property.CUSTOM_NAME, oldVal, newVal);
    }

    public static ModifyPropertyCommand<Boolean> baseAnchor(String nodeId, boolean oldVal, boolean newVal) {
        return new ModifyPropertyCommand<>(nodeId, Property.BASE_ANCHOR, oldVal, newVal);
    }

    public static ModifyPropertyCommand<Integer> rotorEfficiency(String nodeId, int oldVal, int newVal) {
        return new ModifyPropertyCommand<>(nodeId, Property.ROTOR_EFFICIENCY, oldVal, newVal);
    }

    public static ModifyPropertyCommand<Integer> rotorPower(String nodeId, int oldVal, int newVal) {
        return new ModifyPropertyCommand<>(nodeId, Property.ROTOR_POWER, oldVal, newVal);
    }

    public static ModifyPropertyCommand<String> rotorName(String nodeId, String oldVal, String newVal) {
        return new ModifyPropertyCommand<>(nodeId, Property.ROTOR_NAME, oldVal, newVal);
    }

    @Override
    public void undo(FlowGraph graph) {
        applyValue(graph, oldValue);
    }

    @Override
    public void redo(FlowGraph graph) {
        applyValue(graph, newValue);
    }

    private void applyValue(FlowGraph graph, T val) {
        RecipeNode node = graph.findNodeById(nodeId);
        if (node == null || val == null) return;

        switch (property) {
            case MACHINE_COUNT -> {
                if (val instanceof Number n) node.setMachineCount(n.doubleValue());
            }
            case TARGET_TIER -> {
                if (val instanceof com.gtceu.calcboard.api.type.GTVoltageTier t) node.setTargetTier(t);
            }
            case OVERCLOCK_MODE -> {
                if (val instanceof com.gtceu.calcboard.api.type.OverclockMode m) node.setOverclockMode(m);
            }
            case PARALLEL -> {
                if (val instanceof Number n) node.setParallel(n.intValue());
            }
            case CUSTOM_NAME -> {
                if (val instanceof String s) node.setName(s);
            }
            case BASE_ANCHOR -> updateBaseAnchor(graph, node, val);
            case ROTOR_EFFICIENCY -> {
                if (val instanceof Number n) node.setRotorEfficiency(n.intValue());
            }
            case ROTOR_POWER -> {
                if (val instanceof Number n) node.setRotorPower(n.intValue());
            }
            case ROTOR_NAME -> {
                if (val instanceof String s) node.setRotorName(s);
            }
        }
    }

    private void updateBaseAnchor(FlowGraph graph, RecipeNode node, T val) {
        if (!(val instanceof Boolean b)) return;
        if (!b) {
            node.setBaseNode(false);
            return;
        }
        for (RecipeNode n : graph.getNodes()) {
            n.setBaseNode(n.getId().equals(nodeId));
        }
    }

    @Override
    public String getDescription() {
        return "Modify " + property.name().toLowerCase().replace('_', ' ');
    }
}
