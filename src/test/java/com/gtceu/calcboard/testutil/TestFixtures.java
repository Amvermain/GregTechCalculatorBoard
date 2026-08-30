package com.gtceu.calcboard.testutil;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

/**
 * Common test fixtures and factory helpers for creating mock nodes, ingredients, and graphs.
 */
public final class TestFixtures {

    static {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    private TestFixtures() {}

    public static RecipeNode createNode(String name, double durationTicks, double eut, GTVoltageTier tier) {
        return RecipeNode.create(name, durationTicks, eut, tier);
    }

    public static RecipeNode createSimpleMachine(String name, double durationSeconds, double eut, GTVoltageTier tier) {
        return RecipeNode.create(name, durationSeconds * 20.0, eut, tier);
    }

    public static IngredientStack item(String id, String label, double amount) {
        return IngredientStack.item(ResourceLocation.tryParse(id), label, amount, 1.0);
    }

    public static IngredientStack item(String id, String label, double amount, double chance) {
        return IngredientStack.item(ResourceLocation.tryParse(id), label, amount, chance);
    }

    public static IngredientStack fluid(String id, String label, double amount) {
        return IngredientStack.fluid(ResourceLocation.tryParse(id), label, amount, 1.0);
    }

    public static IngredientStack fluid(String id, String label, double amount, double chance) {
        return IngredientStack.fluid(ResourceLocation.tryParse(id), label, amount, chance);
    }

    public static MachineAddon createParallelHatch(String id, String name, int multiplier, boolean powerConstant) {
        MachineAddon addon = new MachineAddon(id, name, MachineAddon.Category.PARALLEL, multiplier + "x Parallel", null);
        addon.setParallelMultiplier(multiplier);
        addon.setPowerConstant(powerConstant);
        return addon;
    }

    public static FlowGraph createEmptyGraph() {
        return new FlowGraph();
    }
}
