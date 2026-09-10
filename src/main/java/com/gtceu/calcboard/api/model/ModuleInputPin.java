package com.gtceu.calcboard.api.model;

import java.util.UUID;

/**
 * Represents an input boundary pin inside a compound module sub-page.
 * Supplies ingredients entering the module to downstream internal nodes.
 */
public class ModuleInputPin extends BoundaryPinNode {

    public ModuleInputPin(String id, String name, IngredientStack ingredient) {
        super(id != null ? id : UUID.randomUUID().toString(), name != null ? name : "Input Pin", PinDirection.INPUT);
        setBoundIngredient(ingredient);
        if (ingredient != null) {
            addOutput(ingredient.copy());
        }
    }

    public IngredientStack getSuppliedIngredient() {
        return getBoundIngredient();
    }
}
