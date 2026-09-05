package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.NodeRateCalculator;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for input consumption chance and tier chance boost (including negative reductions).
 */
public class InputConsumptionChanceTest {

    @Test
    @DisplayName("Negative tierChanceBoost in IngredientStack must be preserved and calculated correctly")
    public void testIngredientStackNegativeTierChanceBoost() {
        IngredientStack mesh = IngredientStack.item(ResourceLocation.tryParse("start:netherite_reinforced_mesh"), "Netherite Reinforced Mesh", 1, 0.03f);
        mesh.setTierChanceBoost(-0.002); // -0.2% per tier above recipe tier

        assertEquals(-0.002, mesh.getTierChanceBoost(), 1e-6, "Negative tier chance boost must not be clamped to zero");
        assertEquals(0.03, mesh.getEffectiveChance(0), 1e-6, "Base tier (delta=0) must be 3.0%");
        assertEquals(0.028, mesh.getEffectiveChance(1), 1e-6, "1 tier above (UV) must decrease chance to 2.8%");
        assertEquals(0.026, mesh.getEffectiveChance(2), 1e-6, "2 tiers above (UHV) must decrease chance to 2.6%");

        // NBT round-trip check
        CompoundTag tag = mesh.serializeNBT();
        IngredientStack restored = IngredientStack.deserializeNBT(tag);
        assertEquals(-0.002, restored.getTierChanceBoost(), 1e-6, "NBT deserialization must restore negative tierChanceBoost");
        assertEquals(0.028, restored.getEffectiveChance(1), 1e-6);
    }

    @Test
    @DisplayName("Cyclonic Sifter input rate at base tier (ZPM) must account for 3% consumption chance")
    public void testInputConsumptionRateAtBaseTier() {
        RecipeNode node = new RecipeNode("sifter-1", "Cyclonic Sifter", 240, 131072, GTVoltageTier.ZPM);
        node.setTargetTier(GTVoltageTier.ZPM);

        IngredientStack mesh = IngredientStack.item(ResourceLocation.tryParse("start:netherite_reinforced_mesh"), "Netherite Reinforced Mesh", 1, 0.03f);
        mesh.setTierChanceBoost(-0.002);
        node.addInput(mesh);

        // Input consumption chance check
        assertEquals(0.03, NodeRateCalculator.getEffectiveInputChance(node, 0), 1e-6, "Effective input chance at base tier must be 0.03");

        // Rate calculation check: 1 item * 0.03 * (1/12 cps) = 0.0025 items/s
        Map<IngredientStack, Double> inputRates = NodeRateCalculator.calculateInputRates(node);
        assertEquals(0.0025, inputRates.get(mesh), 1e-6, "Input rate must be 0.0025/s (0.25%/s) instead of 0.0833/s");

        assertEquals(0.0025, NodeRateCalculator.getInputSlotRate(node, 0, false), 1e-6);
        assertEquals(0.0025, NodeRateCalculator.getInputSlotRate(node, 0, true), 1e-6);
        assertEquals(0.0025, NodeRateCalculator.calculateSingleMachineInputRate(node, mesh), 1e-6);
    }

    @Test
    @DisplayName("Cyclonic Sifter input rate at UV tier (+1 delta) must account for 2.8% reduced chance and overclocked duration")
    public void testInputConsumptionRateOverclockedTier() {
        RecipeNode node = new RecipeNode("sifter-2", "Cyclonic Sifter", 240, 131072, GTVoltageTier.ZPM);
        node.setTargetTier(GTVoltageTier.UV); // +1 tier overclock -> duration halves to 120 ticks (6.0s) -> 0.16667 cycles/s

        IngredientStack mesh = IngredientStack.item(ResourceLocation.tryParse("start:netherite_reinforced_mesh"), "Netherite Reinforced Mesh", 1, 0.03f);
        mesh.setTierChanceBoost(-0.002);
        node.addInput(mesh);

        // Effective chance: 0.03 + 1 * (-0.002) = 0.028 (2.8%)
        assertEquals(0.028, NodeRateCalculator.getEffectiveInputChance(node, 0), 1e-6, "Effective input chance at UV must be 0.028");

        // Overclocked duration is 6.0 seconds -> CPS = 1 / 6 = 0.1666667
        // Rate: 1 item * 0.028 * (1/6) = 0.00466667 items/s
        double expectedRate = 1.0 * 0.028 * (20.0 / 120.0);
        Map<IngredientStack, Double> inputRates = NodeRateCalculator.calculateInputRates(node);
        assertEquals(expectedRate, inputRates.get(mesh), 1e-6, "Input rate at UV must be ~0.004667/s");

        assertEquals(expectedRate, NodeRateCalculator.getInputSlotRate(node, 0, false), 1e-6);
        assertEquals(expectedRate, NodeRateCalculator.getInputSlotRate(node, 0, true), 1e-6);
        assertEquals(expectedRate, NodeRateCalculator.calculateSingleMachineInputRate(node, mesh), 1e-6);
    }
}
