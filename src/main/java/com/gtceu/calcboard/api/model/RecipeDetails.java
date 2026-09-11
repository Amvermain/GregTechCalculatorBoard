package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure domain intermediate representation (IR) holding extracted recipe execution details.
 * Shared across external recipe viewer converters and mod compatibility adapters.
 */
public class RecipeDetails {
    public double durationTicks = 20.0;
    public double eut = 0.0;
    public GTVoltageTier tier = GTVoltageTier.ULV;
    public boolean isGenerator = false;
    public EnergyType energyType = EnergyType.NONE;
    public int backingRecipeTemp = 0;
    public int circuitNumber = -1;
    public String heatCondition = "NONE";
    public List<IngredientStack> extraInputs = new ArrayList<>();
    public List<IngredientStack> extraOutputs = new ArrayList<>();
    public boolean overrideOutputs = false;
    public List<IngredientStack> customOutputs = new ArrayList<>();

    public RecipeDetails copy() {
        RecipeDetails copy = new RecipeDetails();
        copy.durationTicks = this.durationTicks;
        copy.eut = this.eut;
        copy.tier = this.tier;
        copy.isGenerator = this.isGenerator;
        copy.energyType = this.energyType;
        copy.backingRecipeTemp = this.backingRecipeTemp;
        copy.circuitNumber = this.circuitNumber;
        copy.heatCondition = this.heatCondition;
        copy.extraInputs = new ArrayList<>(this.extraInputs);
        copy.extraOutputs = new ArrayList<>(this.extraOutputs);
        copy.overrideOutputs = this.overrideOutputs;
        copy.customOutputs = new ArrayList<>(this.customOutputs);
        return copy;
    }
}
