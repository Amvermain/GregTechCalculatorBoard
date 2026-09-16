package com.gtceu.calcboard.api.model.role;

import com.gtceu.calcboard.api.model.BoundaryPinNode;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operational role representing a boundary input or output pin inside a subpage module.
 * <p>
 * Binds internal subgraph ingredient flows to external module node port interfaces.
 */
public class BoundaryPinNodeRole implements INodeRole {

    private RecipeNode owner;
    private BoundaryPinNode.PinDirection direction = BoundaryPinNode.PinDirection.INPUT;
    private String pinLabel = "";
    private int targetPortIndex = 0;
    private IngredientStack boundIngredient = null;

    public BoundaryPinNodeRole() {}

    public BoundaryPinNodeRole(BoundaryPinNode.PinDirection direction, String pinLabel, int targetPortIndex, IngredientStack boundIngredient) {
        this.direction = direction != null ? direction : BoundaryPinNode.PinDirection.INPUT;
        this.pinLabel = pinLabel != null ? pinLabel : "";
        this.targetPortIndex = targetPortIndex;
        this.boundIngredient = boundIngredient != null ? boundIngredient.copy() : null;
    }

    @Override
    public NodeRoleType getRoleType() {
        return NodeRoleType.BOUNDARY_PIN;
    }

    @Override
    public void attach(RecipeNode owner) {
        this.owner = owner;
        if (owner != null && !pinLabel.isEmpty() && (owner.getName() == null || owner.getName().isEmpty())) {
            owner.setName(pinLabel);
        }
    }

    @Override
    public void detach() {
        this.owner = null;
    }

    @Override
    public RecipeNode getOwner() {
        return owner;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isOperational(FlowGraph graph) {
        return true;
    }

    @Override
    public double getSingleMachinePower() {
        return 0.0;
    }

    @Override
    public double getTotalPower() {
        return 0.0;
    }

    @Override
    public double getEffectiveDurationSeconds() {
        return 1.0;
    }

    @Override
    public double getCyclesPerSecond() {
        return 1.0;
    }

    @Override
    public int getDefaultCardWidth() {
        return 32;
    }

    @Override
    public int getDefaultCardHeight() {
        return 32;
    }

    @Override
    public boolean isFixedSize() {
        return true;
    }

    public BoundaryPinNode.PinDirection getDirection() {
        return direction;
    }

    public void setDirection(BoundaryPinNode.PinDirection direction) {
        this.direction = direction != null ? direction : BoundaryPinNode.PinDirection.INPUT;
    }

    public String getPinLabel() {
        return pinLabel;
    }

    public void setPinLabel(String pinLabel) {
        this.pinLabel = pinLabel != null ? pinLabel : "";
        if (owner != null && !java.util.Objects.equals(owner.getName(), this.pinLabel)) {
            owner.setName(this.pinLabel);
        }
    }

    public int getTargetPortIndex() {
        return targetPortIndex;
    }

    public void setTargetPortIndex(int targetPortIndex) {
        this.targetPortIndex = Math.max(0, targetPortIndex);
    }

    public IngredientStack getBoundIngredient() {
        return boundIngredient;
    }

    public void setBoundIngredient(IngredientStack boundIngredient) {
        this.boundIngredient = boundIngredient != null ? boundIngredient.copy() : null;
    }

    @Override
    public void serializeRoleNBT(CompoundTag tag, Set<FlowGraph> visitedGraphs, int depth) {
        if (direction != null) {
            tag.putString("pinType", direction.name());
        }
        if (pinLabel != null && !pinLabel.isEmpty()) {
            tag.putString("pinLabel", pinLabel);
        }
        if (targetPortIndex > 0) {
            tag.putInt("targetPortIndex", targetPortIndex);
        }
        if (boundIngredient != null) {
            tag.put("boundIngredient", boundIngredient.serializeNBT());
        }
    }

    @Override
    public void deserializeRoleNBT(CompoundTag tag) {
        if (tag == null) return;
        if (tag.contains("pinType")) {
            try {
                this.direction = BoundaryPinNode.PinDirection.valueOf(tag.getString("pinType"));
            } catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains("pinLabel")) {
            this.pinLabel = tag.getString("pinLabel");
        }
        if (tag.contains("targetPortIndex")) {
            this.targetPortIndex = tag.getInt("targetPortIndex");
        }
        if (tag.contains("boundIngredient")) {
            this.boundIngredient = IngredientStack.deserializeNBT(tag.getCompound("boundIngredient"));
        }
    }

    @Override
    public NodeCalculationSnapshot captureSnapshot(FlowGraph graph) {
        if (owner == null) return NodeCalculationSnapshot.EMPTY;
        Map<Integer, Double> inRates = new HashMap<>();
        for (int i = 0; i < owner.getInputs().size(); i++) {
            inRates.put(i, owner.getInputSlotRate(i, true));
        }
        Map<Integer, Double> outRates = new HashMap<>();
        for (int i = 0; i < owner.getOutputs().size(); i++) {
            outRates.put(i, owner.getOutputSlotRate(i, true));
        }
        return new NodeCalculationSnapshot(
            owner.getId(),
            NodeRoleType.BOUNDARY_PIN,
            1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 1,
            EnergyType.NONE, true, false, List.of(),
            inRates, outRates, Map.of(), Map.of()
        );
    }

    @Override
    public BoundaryPinNodeRole copy(Set<FlowGraph> visitedGraphs, int depth) {
        BoundaryPinNodeRole cp = new BoundaryPinNodeRole(this.direction, this.pinLabel, this.targetPortIndex, this.boundIngredient != null ? this.boundIngredient.copy() : null);
        return cp;
    }
}
