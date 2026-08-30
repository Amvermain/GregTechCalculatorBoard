package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;

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
        Assertions.assertEquals(6, MultiblockDetector.getDefaultParallel(ResourceLocation.tryParse("gtceu:steam_ore_factory")));
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

        // When machineIcon is changed to steam_ore_factory, parallel should automatically become 6
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_ore_factory"));
        Assertions.assertEquals(6, node.getParallel());
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
        // Steam Multiblocks consume a flat 64 mB/t for HP (1280 mB/sec) across all 8 parallels
        // With parallel = 8, 8 recipes run per cycle:
        // Iron Ore rate: 1.0 * 8 / 5.0 = 1.6 items/sec
        // Crushed Iron Ore rate: 2.0 * 8 / 5.0 = 3.2 items/sec
        // Steam rate: 64 mB/t * 20 ticks/sec = 1280.0 mB Steam/sec (64 mB/t)
        Map<IngredientStack, Double> inputRates = node.calculateEffectiveInputRates();
        Map<IngredientStack, Double> outputRates = node.calculateEffectiveOutputRates();

        for (Map.Entry<IngredientStack, Double> entry : inputRates.entrySet()) {
            if (entry.getKey().isFluid()) {
                Assertions.assertEquals(1280.0, entry.getValue(), 0.01, "Steam multiblock must consume flat 64 mB/t (1280 mB/s) in HP mode");
            } else {
                Assertions.assertEquals(1.6, entry.getValue(), 0.01, "Iron Ore input rate must scale by 8x");
            }
        }

        for (Map.Entry<IngredientStack, Double> entry : outputRates.entrySet()) {
            Assertions.assertEquals(3.2, entry.getValue(), 0.01, "Crushed Iron Ore output rate must scale by 8x");
        }

        // Test LP Steam multiblock
        node.setSteamMode(SteamMode.LOW_PRESSURE);
        Map<IngredientStack, Double> lpInputRates = node.calculateEffectiveInputRates();
        for (Map.Entry<IngredientStack, Double> entry : lpInputRates.entrySet()) {
            if (entry.getKey().isFluid()) {
                Assertions.assertEquals(640.0, entry.getValue(), 0.01, "Steam multiblock must consume flat 32 mB/t (640 mB/s) in LP mode");
            }
        }
    }

    @Test
    public void testSteamMultiblockAutoConfigAndValidation() {
        RecipeNode node = RecipeNode.create("Tin Dust", 72.2 * 20.0, 2.0, GTVoltageTier.ULV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:huge_restrictive_tin_item_pipe"), "Pipe", 1.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:tin_dust"), "Tin Dust", 1.0));

        // Set machine icon to steam_grinder
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_grinder"));

        Assertions.assertTrue(node.isMultiblock(), "Steam grinder must be multiblock");
        Assertions.assertEquals(SteamMode.HIGH_PRESSURE, node.getSteamMode(), "Steam grinder must auto-set HP steam mode");
        Assertions.assertEquals(8, node.getParallel(), "Steam grinder must have 8x default parallel");
        Assertions.assertEquals(0.0, node.getTotalEUt(), "Steam grinder consumes steam instead of EU");
        Assertions.assertTrue(node.isOperational(), "Steam grinder must be operational without energy hatch");

        // Verify steam fluid input was injected
        Assertions.assertTrue(node.getInputs().stream().anyMatch(in -> in.isFluid() && in.getId().equals(ResourceLocation.tryParse("gtceu:steam"))));
    }

    @Test
    public void testZeroChanceByproductSlotRequirement() {
        RecipeNode node = RecipeNode.create("Macerate Pipe", 100.0, 2.0, GTVoltageTier.ULV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:huge_restrictive_tin_item_pipe"), "Pipe", 1.0));

        IngredientStack mainOut = IngredientStack.item(ResourceLocation.tryParse("gtceu:tin_dust"), "Tin Dust", 12.0);
        IngredientStack byprod = IngredientStack.item(ResourceLocation.tryParse("gtceu:zinc_dust"), "Zinc Dust", 2.0, 0.0);
        byprod.setTierChanceBoost(0.1); // +10% per tier above ULV

        node.getOutputs().add(mainOut);
        node.getOutputs().add(byprod);

        // Equip 1-slot ULV Item Output Bus
        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon outBus = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
                "gtceu:ulv_item_output_bus", "ULV Output Bus", "", null,
                com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.ITEM_OUTPUT, GTVoltageTier.ULV, 1, 0L, false);
        node.getAddons().add(outBus);

        GTCEuModAdapter adapter = (GTCEuModAdapter) ModAdapterRegistry.getAdapterForNode(node);
        java.util.List<net.minecraft.network.chat.Component> warnings = new java.util.ArrayList<>();

        // 1. At ULV (tierDelta = 0), byproduct chance is 0.0 -> reqItemOut = 1, equipped = 1 -> VALID!
        boolean validAtUlv = adapter.validateNode(node, warnings);
        Assertions.assertTrue(validAtUlv, "At ULV, 0% byproduct should not trigger item slot deficit");
        Assertions.assertTrue(warnings.isEmpty());

        // 2. Overclock to HV (tierDelta = 3), byproduct chance is 0.3 (30%) -> reqItemOut = 2, equipped = 1 -> DEFICIT!
        node.setTargetTier(GTVoltageTier.HV);
        warnings.clear();
        boolean validAtHv = adapter.validateNode(node, warnings);
        Assertions.assertFalse(validAtHv, "At HV, active 30% byproduct must trigger item slot deficit for 1-slot bus");
        Assertions.assertFalse(warnings.isEmpty());
    }

    @Test
    public void testSteamKilnFixedDemandCalculation() {
        RecipeNode kiln = RecipeNode.create("Bake Bricks", 200.0, 30.0, GTVoltageTier.LV);
        kiln.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:kiln"));
        kiln.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_kiln"));

        Assertions.assertTrue(kiln.isMultiblock(), "Steam Kiln must be recognized as multiblock");
        Assertions.assertEquals(8, kiln.getParallel(), "Steam Kiln must have 8x default parallel");
        Assertions.assertEquals(SteamMode.HIGH_PRESSURE, kiln.getSteamMode(), "Steam Kiln must default to HP steam mode");

        // 1. Single machine steam demand must be exactly 64 mB/t (1280 mB/s)
        Assertions.assertEquals(1280.0, kiln.getInputSlotRate(0, true), 0.01, "Steam demand must be exactly 64 mB/t (1280 mB/s)");

        // 2. Double machine count -> 128 mB/t (2560 mB/s)
        kiln.setMachineCount(2.0);
        Assertions.assertEquals(2560.0, kiln.getInputSlotRate(0, true), 0.01, "2 Steam Kilns must demand 128 mB/t (2560 mB/s)");

        // 3. Single machine input rate query for solver must be 1280 mB/s
        IngredientStack steamStack = kiln.getInputs().get(0);
        Assertions.assertEquals(1280.0, kiln.calculateSingleMachineInputRate(steamStack), 0.01);
    }
}


