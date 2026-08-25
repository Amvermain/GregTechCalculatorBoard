package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class SteamParallelTest {

    @BeforeEach
    public void setUp() {
        MultiblockDetector.reinitialize();
    }

    @Test
    public void testInnateSteamMultiblockParallelDetection() {
        Assertions.assertEquals(8, MultiblockDetector.getDefaultParallel(ResourceLocation.tryParse("gtceu:steam_grinder")));
        Assertions.assertEquals(8, MultiblockDetector.getDefaultParallel(ResourceLocation.tryParse("gtceu:steam_oven")));
        Assertions.assertEquals(8, MultiblockDetector.getDefaultParallel(ResourceLocation.tryParse("gtceu:steam_compressor")));
        Assertions.assertEquals(4, MultiblockDetector.getDefaultParallel(ResourceLocation.tryParse("gtceu:steam_ore_factory")));
        Assertions.assertEquals(8, MultiblockDetector.getDefaultParallel(ResourceLocation.tryParse("gtceu:steam_hammer")));
        Assertions.assertEquals(8, MultiblockDetector.getDefaultParallel(ResourceLocation.tryParse("gtceu:steam_alloy_smelter")));
        Assertions.assertEquals(1, MultiblockDetector.getDefaultParallel(ResourceLocation.tryParse("gtceu:lv_macerator")));
    }

    @Test
    public void testNodeAutoParallelInitializationForSteamMultiblock() {
        RecipeNode node = RecipeNode.create("Macerate Gravel", 100.0, 32.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_grinder"));

        // When machineIcon is set to steam_grinder, parallel should automatically become 8
        Assertions.assertEquals(8, node.getParallel());
    }

    @Test
    public void testSteamParallelsCalculationExactRate() {
        RecipeNode node = RecipeNode.create("Crushed Ore Processing", 100.0, 30.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_grinder"));
        node.setSteamMode(SteamMode.HIGH_PRESSURE); // HP Steam: 1.0x duration, consumes 2 mB Steam/EU

        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_ore"), "Iron Ore", 1.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2.0));

        // Effective parallel = 8
        GTCEuModAdapter adapter = (GTCEuModAdapter) ModAdapterRegistry.getAdapterForNode(node);
        Assertions.assertEquals(8, adapter.computeEffectiveParallel(node));

        // Duration: 100 ticks = 5.0 seconds
        // Steam amount per batch = 30 EU * 2 mB/EU * 20 ticks/sec * 5.0 sec = 6000 mB Steam per cycle
        // With parallel = 8, 8 recipes run per cycle:
        // Iron Ore rate: 1.0 * 8 / 5.0 = 1.6 items/sec
        // Crushed Iron Ore rate: 2.0 * 8 / 5.0 = 3.2 items/sec
        // Steam rate: 6000 mB * 8 / 5.0 = 9600 mB Steam/sec (480 mB/t)
        Map<IngredientStack, Double> inputRates = node.calculateEffectiveInputRates();
        Map<IngredientStack, Double> outputRates = node.calculateEffectiveOutputRates();

        for (Map.Entry<IngredientStack, Double> entry : inputRates.entrySet()) {
            if (entry.getKey().isFluid()) {
                Assertions.assertEquals(9600.0, entry.getValue(), 0.01, "Steam consumption rate must scale by 8x without time penalty");
            } else {
                Assertions.assertEquals(1.6, entry.getValue(), 0.01, "Iron Ore input rate must scale by 8x");
            }
        }

        for (Map.Entry<IngredientStack, Double> entry : outputRates.entrySet()) {
            Assertions.assertEquals(3.2, entry.getValue(), 0.01, "Crushed Iron Ore output rate must scale by 8x");
        }
    }
}
