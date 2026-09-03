package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HighVoltageOverclockTest {

    @Test
    public void testSingleblockIntegerTickTruncationAndZPMOverclock() {
        RecipeNode barrel = RecipeNode.create("Industrial Barrel", 80.0, 15.0, GTVoltageTier.LV);
        barrel.setMachineIcon(ResourceLocation.tryParse("gtceu:industrial_barrel"));
        barrel.setMultiblock(false);

        IngredientStack water = IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000);
        IngredientStack greenFluid = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:chloroplatinic_acid"), "Acid", 1000);
        barrel.addInput(water);
        barrel.addOutput(greenFluid);

        barrel.setTargetTier(GTVoltageTier.ZPM);

        Assertions.assertEquals(1.0 / 20.0, barrel.getEffectiveDurationSeconds(), 1e-6);
        Assertions.assertEquals(20.0, barrel.getEffectiveCyclesPerSecond(), 1e-6);
        Assertions.assertEquals(61440.0, barrel.getSingleMachineEUt(), 1e-6);
        Assertions.assertEquals(20000.0, barrel.getInputSlotRate(0, true), 1e-6);
        Assertions.assertEquals(20000.0, barrel.getOutputSlotRate(0, true), 1e-6);
    }

    @Test
    public void testSingleblockEarlyBreakBelowOneTick() {
        RecipeNode barrel = RecipeNode.create("Industrial Barrel", 80.0, 15.0, GTVoltageTier.LV);
        barrel.setMachineIcon(ResourceLocation.tryParse("gtceu:industrial_barrel"));
        barrel.setMultiblock(false);

        barrel.setTargetTier(GTVoltageTier.UV);

        Assertions.assertEquals(1.0 / 20.0, barrel.getEffectiveDurationSeconds(), 1e-6);
        Assertions.assertEquals(20.0, barrel.getEffectiveCyclesPerSecond(), 1e-6);
        Assertions.assertEquals(61440.0, barrel.getSingleMachineEUt(), 1e-6);
    }

    @Test
    public void testMultiblockSubtickParallelOverclock() {
        RecipeNode multiblock = RecipeNode.create("Cracking Unit", 80.0, 15.0, GTVoltageTier.LV);
        multiblock.setMachineIcon(ResourceLocation.tryParse("gtceu:cracker"));
        multiblock.setMultiblock(true);

        IngredientStack fluidIn = IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000);
        IngredientStack fluidOut = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 1000);
        multiblock.addInput(fluidIn);
        multiblock.addOutput(fluidOut);

        multiblock.setTargetTier(GTVoltageTier.ZPM);
        Assertions.assertEquals(1.0 / 20.0, multiblock.getEffectiveDurationSeconds(), 1e-6);
        Assertions.assertEquals(20.0, multiblock.getEffectiveCyclesPerSecond(), 1e-6);
        Assertions.assertEquals(61440.0, multiblock.getSingleMachineEUt(), 1e-6);

        multiblock.setTargetTier(GTVoltageTier.UV);
        Assertions.assertEquals(1.0 / 20.0, multiblock.getEffectiveDurationSeconds(), 1e-6);
        Assertions.assertEquals(40.0, multiblock.getEffectiveCyclesPerSecond(), 1e-6);
        Assertions.assertEquals(245760.0, multiblock.getSingleMachineEUt(), 1e-6);
        Assertions.assertEquals(40000.0, multiblock.getInputSlotRate(0, true), 1e-6);
        Assertions.assertEquals(40000.0, multiblock.getOutputSlotRate(0, true), 1e-6);
    }

    @Test
    public void testOneTickRecipeSingleblockBehavior() {
        RecipeNode oneTickNode = RecipeNode.create("Fast Machine", 1.0, 30.0, GTVoltageTier.LV);
        oneTickNode.setMachineIcon(ResourceLocation.tryParse("gtceu:fast_machine"));
        oneTickNode.setMultiblock(false);

        oneTickNode.setTargetTier(GTVoltageTier.HV);
        Assertions.assertEquals(1.0 / 20.0, oneTickNode.getEffectiveDurationSeconds(), 1e-6);
        Assertions.assertEquals(20.0, oneTickNode.getEffectiveCyclesPerSecond(), 1e-6);
        Assertions.assertEquals(30.0, oneTickNode.getSingleMachineEUt(), 1e-6);
    }
}
