package com.gtceu.calcboard.compat.tfg;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TFGBoilerPhysicsTest {

    @Test
    public void testBaseBoilerTiers() {
        RecipeNode bronzeNode = createBoilerNode(TFGBoilerPhysics.LARGE_BRONZE_BOILER);
        Assertions.assertTrue(TFGBoilerPhysics.isTFGLargeBoiler(bronzeNode));
        Assertions.assertFalse(TFGBoilerPhysics.isSteelBoiler(bronzeNode));
        Assertions.assertEquals(480, TFGBoilerPhysics.getBasePressure(bronzeNode));
        Assertions.assertEquals(480, TFGBoilerPhysics.getEffectivePressure(bronzeNode));
        Assertions.assertEquals(480.0, TFGBoilerPhysics.getSteamRatePerTick(bronzeNode), 0.001);
        Assertions.assertEquals(9600.0, TFGBoilerPhysics.getSteamRatePerSec(bronzeNode), 0.001);

        RecipeNode steelNode = createBoilerNode(TFGBoilerPhysics.LARGE_STEEL_BOILER);
        Assertions.assertTrue(TFGBoilerPhysics.isTFGLargeBoiler(steelNode));
        Assertions.assertTrue(TFGBoilerPhysics.isSteelBoiler(steelNode));
        Assertions.assertEquals(1280, TFGBoilerPhysics.getBasePressure(steelNode));
        Assertions.assertEquals(1280, TFGBoilerPhysics.getEffectivePressure(steelNode));
        Assertions.assertEquals(1280.0, TFGBoilerPhysics.getSteamRatePerTick(steelNode), 0.001);
        Assertions.assertEquals(25600.0, TFGBoilerPhysics.getSteamRatePerSec(steelNode), 0.001);

        RecipeNode nonTfgNode = createBoilerNode(ResourceLocation.tryParse("gtceu:large_bronze_boiler"));
        Assertions.assertFalse(TFGBoilerPhysics.isTFGLargeBoiler(nonTfgNode));
    }

    @Test
    public void testBoosterFluidsAndConstraints() {
        List<TFGBoilerPhysics.BoosterFluid> boosters = TFGBoilerPhysics.getAllBoosters();
        Assertions.assertEquals(10, boosters.size());

        TFGBoilerPhysics.BoosterFluid creosote = TFGBoilerPhysics.getBooster(1);
        Assertions.assertEquals("block.gtceu.creosote", creosote.translationKey());
        Assertions.assertEquals(32.0, creosote.consumptionMbPerSec(), 0.001);
        Assertions.assertEquals(1.6, creosote.consumptionMbPerTick(), 0.001);
        Assertions.assertEquals(300, creosote.pressureBonus());
        Assertions.assertEquals(0, creosote.minBoilerPressure());

        TFGBoilerPhysics.BoosterFluid oliveOil = TFGBoilerPhysics.getBooster(6);
        Assertions.assertEquals(1.0, oliveOil.consumptionMbPerSec(), 0.001);
        Assertions.assertEquals(0.05, oliveOil.consumptionMbPerTick(), 0.001);
        Assertions.assertEquals(600, oliveOil.pressureBonus());
        Assertions.assertEquals(0, oliveOil.minBoilerPressure());

        TFGBoilerPhysics.BoosterFluid aromatic = TFGBoilerPhysics.getBooster(7);
        Assertions.assertEquals(300.0, aromatic.consumptionMbPerSec(), 0.001);
        Assertions.assertEquals(1200, aromatic.pressureBonus());
        Assertions.assertEquals(1280, aromatic.minBoilerPressure());

        TFGBoilerPhysics.BoosterFluid rocketFuel = TFGBoilerPhysics.getBooster(8);
        Assertions.assertEquals(200.0, rocketFuel.consumptionMbPerSec(), 0.001);
        Assertions.assertEquals(5000, rocketFuel.pressureBonus());
        Assertions.assertEquals(1280, rocketFuel.minBoilerPressure());

        TFGBoilerPhysics.BoosterFluid effluent = TFGBoilerPhysics.getBooster(9);
        Assertions.assertEquals(2.0, effluent.consumptionMbPerSec(), 0.001);
        Assertions.assertEquals(16000, effluent.pressureBonus());
        Assertions.assertEquals(1280, effluent.minBoilerPressure());

        RecipeNode bronzeNode = createBoilerNode(TFGBoilerPhysics.LARGE_BRONZE_BOILER);
        for (int i = 0; i < 6; i++) {
            TFGBoilerPhysics.cycleBooster(bronzeNode, 1);
        }
        Assertions.assertEquals(6, bronzeNode.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX));
        TFGBoilerPhysics.cycleBooster(bronzeNode, 1);
        Assertions.assertEquals(0, bronzeNode.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX));

        RecipeNode steelNode = createBoilerNode(TFGBoilerPhysics.LARGE_STEEL_BOILER);
        for (int i = 0; i < 9; i++) {
            TFGBoilerPhysics.cycleBooster(steelNode, 1);
        }
        Assertions.assertEquals(9, steelNode.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX));
        TFGBoilerPhysics.cycleBooster(steelNode, 1);
        Assertions.assertEquals(0, steelNode.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX));
        TFGBoilerPhysics.cycleBooster(steelNode, -1);
        Assertions.assertEquals(9, steelNode.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX));
    }

    @Test
    public void testNonLinearWaterTempFactor() {
        Assertions.assertEquals(1.0, TFGBoilerPhysics.getWaterTempFactor(480), 0.0001);
        Assertions.assertEquals(1.0, TFGBoilerPhysics.getWaterTempFactor(300), 0.0001);

        double factorCreosote = TFGBoilerPhysics.getWaterTempFactor(780);
        Assertions.assertEquals(1.0 + 0.035 * Math.pow(3.0, 1.5), factorCreosote, 0.0001);
        Assertions.assertEquals(1.181865, factorCreosote, 0.0001);

        double factorSteel = TFGBoilerPhysics.getWaterTempFactor(1280);
        Assertions.assertEquals(1.0 + 0.035 * Math.pow(8.0, 1.5), factorSteel, 0.0001);
        Assertions.assertEquals(1.791918, factorSteel, 0.0001);

        double factorRocket = TFGBoilerPhysics.getWaterTempFactor(6280);
        Assertions.assertEquals(1.0 + 0.035 * Math.pow(58.0, 1.5), factorRocket, 0.01);
        Assertions.assertEquals(16.4602, factorRocket, 0.01);

        double factorEffluent = TFGBoilerPhysics.getWaterTempFactor(17280);
        Assertions.assertEquals(1.0 + 0.035 * Math.pow(168.0, 1.5), factorEffluent, 0.01);
        Assertions.assertEquals(77.2111, factorEffluent, 0.01);

        RecipeNode bronzeNode = createBoilerNode(TFGBoilerPhysics.LARGE_BRONZE_BOILER);
        Assertions.assertEquals(60.0, TFGBoilerPhysics.getWaterConsumptionPerSec(bronzeNode), 0.001);

        bronzeNode.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, 1);
        Assertions.assertEquals(115.231, TFGBoilerPhysics.getWaterConsumptionPerSec(bronzeNode), 0.01);

        RecipeNode steelNode = createBoilerNode(TFGBoilerPhysics.LARGE_STEEL_BOILER);
        Assertions.assertEquals(286.706, TFGBoilerPhysics.getWaterConsumptionPerSec(steelNode), 0.01);
    }

    @Test
    public void testFuelEfficiencyAndBurnAcceleration() {
        RecipeNode bronzeNode = createBoilerNode(TFGBoilerPhysics.LARGE_BRONZE_BOILER);
        Assertions.assertEquals(100.0, TFGBoilerPhysics.getFuelEfficiency(bronzeNode), 0.001);
        Assertions.assertEquals(1.0, TFGBoilerPhysics.getBoilerSpeedMultiplier(bronzeNode), 0.001);

        bronzeNode.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, 1);
        double expectedReduction = 0.6 * (1.0 - Math.exp(-0.8 * 300.0 / 1000.0));
        double expectedMu = 1.0 - expectedReduction;
        Assertions.assertEquals(expectedMu * 100.0, TFGBoilerPhysics.getFuelEfficiency(bronzeNode), 0.01);
        Assertions.assertEquals(1.0 / expectedMu, TFGBoilerPhysics.getBoilerSpeedMultiplier(bronzeNode), 0.01);

        RecipeNode steelNode = createBoilerNode(TFGBoilerPhysics.LARGE_STEEL_BOILER);
        steelNode.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, 9);
        Assertions.assertTrue(TFGBoilerPhysics.getFuelEfficiency(steelNode) >= 40.0);
        Assertions.assertTrue(TFGBoilerPhysics.getBoilerSpeedMultiplier(steelNode) <= 2.5);

        steelNode.setBoilerThrottle(50);
        Assertions.assertEquals(0.5 / (TFGBoilerPhysics.getFuelEfficiency(steelNode) / 100.0),
                TFGBoilerPhysics.getBoilerSpeedMultiplier(steelNode), 0.01);
    }

    @Test
    public void testWaterTierScaling() {
        RecipeNode bronzeNode = createBoilerNode(TFGBoilerPhysics.LARGE_BRONZE_BOILER);
        Assertions.assertEquals(9600.0, TFGBoilerPhysics.getSteamRatePerSec(bronzeNode), 0.001);
        Assertions.assertEquals(60.0, TFGBoilerPhysics.getWaterConsumptionPerSec(bronzeNode), 0.001);

        bronzeNode.getProperties().set(TFGBoilerProperties.WATER_TIER, 1);
        Assertions.assertEquals(14400.0, TFGBoilerPhysics.getSteamRatePerSec(bronzeNode), 0.001);
        Assertions.assertEquals(60.0, TFGBoilerPhysics.getWaterConsumptionPerSec(bronzeNode), 0.001);

        TFGBoilerPhysics.cycleWaterTier(bronzeNode);
        Assertions.assertEquals(0, bronzeNode.getProperties().get(TFGBoilerProperties.WATER_TIER));
        Assertions.assertEquals(9600.0, TFGBoilerPhysics.getSteamRatePerSec(bronzeNode), 0.001);
    }

    @Test
    public void testSuperBoilerMode() {
        RecipeNode steelNode = createBoilerNode(TFGBoilerPhysics.LARGE_STEEL_BOILER);
        steelNode.getProperties().set(TFGBoilerProperties.BOILER_MODE, 1);
        steelNode.setRecipeCategoryId(TFGBoilerPhysics.SUPER_BOILER_RECIPE);
        Assertions.assertTrue(TFGBoilerPhysics.isSuperBoilerMode(steelNode));

        RecipeNode bronzeNode = createBoilerNode(TFGBoilerPhysics.LARGE_BRONZE_BOILER);
        bronzeNode.getProperties().set(TFGBoilerProperties.BOILER_MODE, 1);
        Assertions.assertFalse(TFGBoilerPhysics.isSuperBoilerMode(bronzeNode));
    }

    @Test
    public void testDynamicPortProjection() {
        RecipeNode bronzeNode = createBoilerNode(TFGBoilerPhysics.LARGE_BRONZE_BOILER);
        RecipeSpec spec = new RecipeSpec(
                "coal_boiler_recipe",
                ResourceLocation.tryParse("gtceu:large_boiler"),
                100.0,
                0.0,
                List.of(
                        IngredientStack.item(ResourceLocation.tryParse("minecraft:coal"), "Coal", 1.0),
                        IngredientStack.fluid(TFGBoilerPhysics.STANDARD_WATER, "Water", 300.0)
                ),
                List.of(
                        IngredientStack.fluid(TFGBoilerPhysics.STEAM, "Steam", 48000.0)
                )
        );
        bronzeNode.setBaseSpec(spec);
        bronzeNode.syncProjectedPorts();

        Assertions.assertEquals(2, bronzeNode.getInputs().size());
        Assertions.assertEquals(ResourceLocation.tryParse("minecraft:coal"), bronzeNode.getInputs().get(0).getId());
        Assertions.assertEquals(TFGBoilerPhysics.STANDARD_WATER, bronzeNode.getInputs().get(1).getId());
        Assertions.assertEquals(60.0, bronzeNode.getInputSlotRate(1, false), 0.001);
        Assertions.assertEquals(9600.0, bronzeNode.getOutputSlotRate(0, false), 0.001);

        bronzeNode.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, 1);
        bronzeNode.syncProjectedPorts();
        Assertions.assertEquals(3, bronzeNode.getInputs().size());
        IngredientStack boosterSlot = bronzeNode.getInputs().get(2);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:creosote"), boosterSlot.getId());
        Assertions.assertEquals(32.0, bronzeNode.getInputSlotRate(2, false), 0.001);
        Assertions.assertEquals(115.231, bronzeNode.getInputSlotRate(1, false), 0.01);
        Assertions.assertEquals(15600.0, bronzeNode.getOutputSlotRate(0, false), 0.001);

        bronzeNode.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, 0);
        bronzeNode.syncProjectedPorts();
        Assertions.assertEquals(2, bronzeNode.getInputs().size());
        Assertions.assertEquals(60.0, bronzeNode.getInputSlotRate(1, false), 0.001);
        Assertions.assertEquals(9600.0, bronzeNode.getOutputSlotRate(0, false), 0.001);

        bronzeNode.getProperties().set(TFGBoilerProperties.WATER_TIER, 1);
        bronzeNode.syncProjectedPorts();
        Assertions.assertEquals(TFGBoilerPhysics.DISTILLED_WATER, bronzeNode.getInputs().get(1).getId());
        Assertions.assertEquals(60.0, bronzeNode.getInputSlotRate(1, false), 0.001);
        Assertions.assertEquals(14400.0, bronzeNode.getOutputSlotRate(0, false), 0.001);
    }

    @Test
    public void testTierToggleAndModeReset() {
        RecipeNode steelNode = createBoilerNode(TFGBoilerPhysics.LARGE_STEEL_BOILER);
        TFGBoilerPhysics.cycleBoilerMode(steelNode);
        steelNode.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, 8); // Rocket Fuel (LSB only)
        Assertions.assertTrue(TFGBoilerPhysics.isSteelBoiler(steelNode));
        Assertions.assertTrue(TFGBoilerPhysics.isSuperBoilerMode(steelNode));
        Assertions.assertEquals(TFGBoilerPhysics.SUPER_BOILER_RECIPE, steelNode.getRecipeCategoryId());
        Assertions.assertEquals(6280, TFGBoilerPhysics.getEffectivePressure(steelNode));

        TFGBoilerPhysics.toggleBoilerTier(steelNode);
        Assertions.assertFalse(TFGBoilerPhysics.isSteelBoiler(steelNode));
        Assertions.assertFalse(TFGBoilerPhysics.isSuperBoilerMode(steelNode));
        Assertions.assertEquals(TFGBoilerPhysics.LARGE_BRONZE_BOILER, steelNode.getMachineIcon());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:large_boiler"), steelNode.getRecipeCategoryId());
        Assertions.assertEquals(0, steelNode.getProperties().get(TFGBoilerProperties.BOILER_MODE));
        Assertions.assertEquals(0, steelNode.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX));
        Assertions.assertEquals(480, TFGBoilerPhysics.getBasePressure(steelNode));
        Assertions.assertEquals(480, TFGBoilerPhysics.getEffectivePressure(steelNode));
    }

    @Test
    public void testModAdapterIsolation() {
        com.gtceu.calcboard.api.spi.ModAdapterRegistry.init();

        RecipeNode tfgBoiler = createBoilerNode(TFGBoilerPhysics.LARGE_BRONZE_BOILER);
        var tfgAdapter = com.gtceu.calcboard.api.spi.ModAdapterRegistry.getAdapterForNode(tfgBoiler);
        Assertions.assertInstanceOf(TFGModAdapter.class, tfgAdapter);

        RecipeNode gtBoiler = createBoilerNode(ResourceLocation.tryParse("gtceu:large_bronze_boiler"));
        var gtAdapter = com.gtceu.calcboard.api.spi.ModAdapterRegistry.getAdapterForNode(gtBoiler);
        Assertions.assertInstanceOf(com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.class, gtAdapter);
        Assertions.assertFalse(gtAdapter instanceof TFGModAdapter);

        RecipeNode greateNode = RecipeNode.create("Greate Compacting", 200.0, 128.0, GTVoltageTier.MV);
        greateNode.setEnergyType(com.gtceu.calcboard.api.type.EnergyType.KINETIC_SU);
        greateNode.getProperties().set(com.gtceu.calcboard.compat.greate.GreateProperties.IS_GREATE, true);
        var greateAdapter = com.gtceu.calcboard.api.spi.ModAdapterRegistry.getAdapterForNode(greateNode);
        Assertions.assertInstanceOf(com.gtceu.calcboard.compat.greate.GreateModAdapter.class, greateAdapter);
    }

    @Test
    public void testBoosterTooltipGeneration() {
        RecipeNode node = createBoilerNode(TFGBoilerPhysics.LARGE_STEEL_BOILER);
        node.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, 8); // Rocket fuel
        node.getProperties().set(TFGBoilerProperties.WATER_TIER, 1); // Distilled

        java.util.List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
        TFGBoilerPhysics.buildBoosterTooltip(node, tooltip);

        Assertions.assertFalse(tooltip.isEmpty());
        String fullText = String.join("\n", tooltip.stream().map(net.minecraft.network.chat.Component::getString).toList());
        Assertions.assertTrue(fullText.contains("Thermodynamic Simulation"));
        Assertions.assertTrue(fullText.contains("6,280PU"));
        Assertions.assertTrue(fullText.contains("Rocket Fuel"));
        Assertions.assertTrue(fullText.contains("1.5x Boost"));
        Assertions.assertTrue(fullText.contains("Water Usage"));
        Assertions.assertTrue(fullText.contains("Fuel Efficiency"));
    }

    @Test
    public void testNbtSerializationAndRestoration() {
        RecipeNode node = createBoilerNode(TFGBoilerPhysics.LARGE_STEEL_BOILER);
        node.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, 5);
        node.getProperties().set(TFGBoilerProperties.WATER_TIER, 1);
        node.getProperties().set(TFGBoilerProperties.BOILER_MODE, 1);

        net.minecraft.nbt.CompoundTag tag = node.serializeNBT();
        RecipeNode restored = RecipeNode.deserializeNBT(tag);
        Assertions.assertEquals(5, restored.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX));
        Assertions.assertEquals(1, restored.getProperties().get(TFGBoilerProperties.WATER_TIER));
        Assertions.assertEquals(1, restored.getProperties().get(TFGBoilerProperties.BOILER_MODE));
        Assertions.assertTrue(TFGBoilerPhysics.isTFGLargeBoiler(restored));
        Assertions.assertTrue(TFGBoilerPhysics.isSteelBoiler(restored));
    }

    private RecipeNode createBoilerNode(ResourceLocation machineIcon) {
        RecipeNode node = RecipeNode.create("Boiler Test Node", 100.0, 0.0, GTVoltageTier.ULV);
        node.setMachineIcon(machineIcon);
        node.setMultiblock(true);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:large_boiler"));
        node.setBoilerThrottle(100);
        node.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, 0);
        node.getProperties().set(TFGBoilerProperties.WATER_TIER, 0);
        node.getProperties().set(TFGBoilerProperties.BOILER_MODE, 0);
        return node;
    }
}
