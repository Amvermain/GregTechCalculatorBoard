package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.type.GTVoltageTier;

/**
 * Pure domain node representing an explicit interface boundary pin (input or output)
 * for a dedicated compound module sub-page.
 *
 * @see ModuleInputPin
 * @see ModuleOutputPin
 */
public abstract class BoundaryPinNode extends RecipeNode {

    /**
     * Represents the flow orientation of the boundary pin relative to the module sub-page.
     */
    public enum PinDirection {
        INPUT,
        OUTPUT
    }

    private PinDirection direction;
    private String pinLabel;
    private IngredientStack boundIngredient;
    private int targetPortIndex;

    public BoundaryPinNode(String id, String name, PinDirection direction) {
        super(id, name, 20.0, 0.0, GTVoltageTier.LV);
        this.direction = direction;
        this.pinLabel = name != null ? name : "";
        this.targetPortIndex = 0;
        this.setCardWidth(32);
        this.setCardHeight(32);
    }

    @Override
    public int getCardWidth() {
        return 32;
    }

    @Override
    public int getCardHeight() {
        return 32;
    }

    public PinDirection getDirection() {
        return direction;
    }

    public void setDirection(PinDirection direction) {
        this.direction = direction;
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        this.pinLabel = name != null ? name : "";
    }

    public String getPinLabel() {
        return pinLabel;
    }

    public void setPinLabel(String pinLabel) {
        this.pinLabel = pinLabel != null ? pinLabel : "";
        super.setName(this.pinLabel);
    }

    public IngredientStack getBoundIngredient() {
        return boundIngredient;
    }

    public void setBoundIngredient(IngredientStack boundIngredient) {
        this.boundIngredient = boundIngredient;
    }

    public int getTargetPortIndex() {
        return targetPortIndex;
    }

    public void setTargetPortIndex(int targetPortIndex) {
        this.targetPortIndex = targetPortIndex;
    }

    @Override
    public boolean isBoundaryPin() {
        return true;
    }

    @Override
    public boolean isOperational(FlowGraph graph) {
        return true;
    }
}
