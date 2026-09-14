package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.model.role.BoundaryPinNodeRole;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import java.util.Objects;

public abstract class BoundaryPinNode extends RecipeNode {

    public enum PinDirection {
        INPUT,
        OUTPUT
    }

    public BoundaryPinNode(String id, String name, PinDirection direction) {
        super(id, name, 20.0, 0.0, GTVoltageTier.LV);
        BoundaryPinNodeRole pinRole = new BoundaryPinNodeRole(direction, name != null ? name : "", 0, null);
        setRole(pinRole);
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
        return asBoundaryPin().getDirection();
    }

    public void setDirection(PinDirection direction) {
        asBoundaryPin().setDirection(direction);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        if (isBoundaryPin() && !Objects.equals(asBoundaryPin().getPinLabel(), name)) {
            asBoundaryPin().setPinLabel(name != null ? name : "");
        }
    }

    public String getPinLabel() {
        return asBoundaryPin().getPinLabel();
    }

    public void setPinLabel(String pinLabel) {
        asBoundaryPin().setPinLabel(pinLabel);
    }

    public IngredientStack getBoundIngredient() {
        return asBoundaryPin().getBoundIngredient();
    }

    public void setBoundIngredient(IngredientStack boundIngredient) {
        asBoundaryPin().setBoundIngredient(boundIngredient);
    }

    public int getTargetPortIndex() {
        return asBoundaryPin().getTargetPortIndex();
    }

    public void setTargetPortIndex(int targetPortIndex) {
        asBoundaryPin().setTargetPortIndex(targetPortIndex);
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
