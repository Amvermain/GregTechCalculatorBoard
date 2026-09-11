package com.gtceu.calcboard.api.model;

import java.util.UUID;

/**
 * Represents an output boundary pin inside a compound module sub-page.
 * Collects ingredients produced inside the module to be exposed to outer parent pages.
 */
public class ModuleOutputPin extends BoundaryPinNode {

    public ModuleOutputPin(String id, String name, IngredientStack ingredient) {
        super(id != null ? id : UUID.randomUUID().toString(), name != null ? name : "Output Pin", PinDirection.OUTPUT);
        setBoundIngredient(ingredient);
        if (ingredient != null) {
            addInput(ingredient.copy());
        }
    }

    public IngredientStack getCollectedIngredient() {
        return getBoundIngredient();
    }
}
