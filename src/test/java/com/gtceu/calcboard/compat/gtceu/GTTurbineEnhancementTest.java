package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.compat.gtceu.physics.GTTurbinePhysics;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying RFC-006 decoupled turbine rotor holder tiers, dynamo hatch amperage,
 * lubricant/coolant boost factors, rotor wear/lifespan physics, and dynamic Pmax calculation.
 */
class GTTurbineEnhancementTest {

    @BeforeAll
    static void init() {
        com.gtceu.calcboard.compat.ModAdapterRegistry.init();
    }

    private RecipeNode createLargeSteamTurbineNode() {
        RecipeNode node = new RecipeNode("test_turbine", "Large Steam Turbine", 20.0, 4096.0, GTVoltageTier.EV);
        node.setGenerator(true);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_steam_turbine"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_turbine"));
        node.setMultiblock(true);
        node.setBaseEUt(-4096.0);
        node.setTargetTier(GTVoltageTier.EV);
        node.setRotorEfficiency(100);
        node.setRotorPower(100);
        node.setRotorName("Standard (100%)");
        return node;
    }

    @Test
    @DisplayName("RFC-006: Decoupled Rotor Holder Tier and Dynamo Hatch Tier")
    void testDecoupledRotorHolderAndDynamoTier() {
        RecipeNode node = createLargeSteamTurbineNode();

        // Default: matches node targetTier (EV)
        assertEquals(GTVoltageTier.EV, GTTurbineHelper.getRotorHolderTier(node));
        assertEquals(GTVoltageTier.EV, GTTurbineHelper.getDynamoTier(node));
        assertEquals(16, GTTurbineHelper.getDynamoAmperage(node));

        // Decouple: Set Holder to UV (Tier 7) and Dynamo to LuV (Tier 5), 4A
        GTTurbineHelper.setRotorHolderTier(node, GTVoltageTier.UV);
        GTTurbineHelper.setDynamoTier(node, GTVoltageTier.LuV);
        GTTurbineHelper.setDynamoAmperage(node, 4);

        assertEquals(GTVoltageTier.UV, GTTurbineHelper.getRotorHolderTier(node));
        assertEquals(GTVoltageTier.LuV, GTTurbineHelper.getDynamoTier(node));
        assertEquals(4, GTTurbineHelper.getDynamoAmperage(node));

        // Base tier for Large Steam Turbine is HV (Tier 3, index 3)
        // Delta between Holder (UV=8) and Base (HV=3) = 5 tiers -> +50% efficiency bonus
        int holderBonus = GTTurbineHelper.getTurbineHolderEfficiencyBonus(node);
        assertEquals(50, holderBonus);

        // Total efficiency with 100% standard rotor = 100 * (100 + 50) / 100 = 150%
        int totalEff = GTTurbineHelper.getTotalTurbineEfficiency(node);
        assertEquals(150, totalEff);
    }

    @Test
    @DisplayName("RFC-006: Dynamo Capacity Bottlenecking min(HolderCap, DynamoCap)")
    void testDynamoCapacityBottlenecking() {
        RecipeNode node = createLargeSteamTurbineNode();

        // Holder: UV (Capacity = 4096 * 2^(8-4) = 4096 * 16 = 65,536 EU/t at 100% power)
        GTTurbineHelper.setRotorHolderTier(node, GTVoltageTier.UV);
        GTTurbineHelper.setDynamoTier(node, GTVoltageTier.LV);
        GTTurbineHelper.setDynamoAmperage(node, 1);
        node.setRotorPower(100);

        double dynamoCap = GTTurbineHelper.getDynamoMaxCapacity(node);
        assertEquals(32.0, dynamoCap, 0.001);

        double holderCap = GTTurbineHelper.getNodeRotorHolderMaxEUt(node, GTVoltageTier.UV, 100);
        assertTrue(holderCap > dynamoCap, "UV Holder cap should be much higher than LV Dynamo cap");

        double genMax = GTTurbineHelper.getGeneratorMaxEUt(node);
        assertEquals(dynamoCap, genMax, 0.001, "Final generator cap should be bottlenecked by Dynamo capacity");
    }

    @Test
    @DisplayName("RFC-006: Lubricant and Coolant Boost Multipliers")
    void testTurbineBoostMultipliers() {
        RecipeNode node = new RecipeNode("plasma_turbine", "Large Plasma Turbine", 20.0, 16384.0, GTVoltageTier.IV);
        node.setGenerator(true);
        // Standard LPT: No StarT boost
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_plasma_turbine"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_generator"));
        node.setMultiblock(true);
        node.setBaseEUt(16384.0);
        node.setTargetTier(GTVoltageTier.IV);
        assertFalse(GTTurbineHelper.supportsTurbineBoost(node));
        assertEquals(1.0, GTTurbineHelper.getTurbineBoostMultiplier(node), 1e-6);

        // Star Technology SPT (Supreme Plasma Turbine)
        RecipeNode spt = new RecipeNode("spt", "Supreme Plasma Turbine", 20.0, 98304.0, GTVoltageTier.UHV);
        spt.setMachineIcon(ResourceLocation.tryParse("start_core:supreme_plasma_turbine"));
        spt.setRecipeCategoryId(ResourceLocation.tryParse("start_core:plasma_generator"));
        spt.setMultiblock(true);
        assertTrue(GTTurbineHelper.supportsTurbineBoost(spt));

        // Default: 0.9x (Unboosted penalty due to missing WS2)
        assertEquals(0.9, GTTurbineHelper.getTurbineBoostMultiplier(spt), 1e-6);

        // Passive boost: WS2 Only -> 1.25x (+25%)
        GTTurbineHelper.setLubricantBoost(spt, true);
        GTTurbineHelper.setCoolantBoost(spt, false);
        assertEquals(1.25, GTTurbineHelper.getTurbineBoostMultiplier(spt), 1e-6);

        // Full Active boost: WS2 + SS-He3 -> 2.0x (+100%)
        GTTurbineHelper.setCoolantBoost(spt, true);
        assertEquals(2.0, GTTurbineHelper.getTurbineBoostMultiplier(spt), 1e-6);

        // Star Technology NPT (Nyinsane Plasma Turbine)
        RecipeNode npt = new RecipeNode("npt", "Nyinsane Plasma Turbine", 20.0, 196608.0, GTVoltageTier.UIV);
        npt.setMachineIcon(ResourceLocation.tryParse("start_core:nyinsane_plasma_turbine"));
        npt.setRecipeCategoryId(ResourceLocation.tryParse("start_core:plasma_generator"));
        npt.setMultiblock(true);
        assertTrue(GTTurbineHelper.supportsTurbineBoost(npt));

        // Default: 0.8x (Unboosted penalty due to missing WS2)
        assertEquals(0.8, GTTurbineHelper.getTurbineBoostMultiplier(npt), 1e-6);

        // Passive boost: WS2 Only -> 1.50x (+50%)
        GTTurbineHelper.setLubricantBoost(npt, true);
        GTTurbineHelper.setCoolantBoost(npt, false);
        assertEquals(1.50, GTTurbineHelper.getTurbineBoostMultiplier(npt), 1e-6);

        // Full Active boost: WS2 + BEC-Og -> 3.0x (+200%)
        GTTurbineHelper.setCoolantBoost(npt, true);
        assertEquals(3.0, GTTurbineHelper.getTurbineBoostMultiplier(npt), 1e-6);
    }

    @Test
    @DisplayName("RFC-006: Rotor Wear Rate and Lifespan Modeling")
    void testRotorWearAndLifespan() {
        RecipeNode node = createLargeSteamTurbineNode();
        GTTurbineHelper.setRotorHolderTier(node, GTVoltageTier.EV);
        GTTurbineHelper.setDynamoTier(node, GTVoltageTier.EV);
        node.setRotorPower(100);
        node.setRotorEfficiency(100);
        node.setRotorName("Stainless Steel"); // Durability = 300,000 in TurbineRotorHelper

        // Running at 100% capacity utilization -> 20 damage/sec
        double wearPerSec = GTTurbineHelper.calculateRotorWearPerSecond(node);
        assertEquals(20.0, wearPerSec, 1.0);

        // Lifespan = Durability / (20 * 3600)
        double lifespanHours = GTTurbineHelper.calculateRotorLifespanHours(node);
        assertTrue(lifespanHours > 0.0 && !Double.isInfinite(lifespanHours));

        // Hourly replacement rate for 2 machines
        node.setMachineCount(2.0);
        double replPerHour = GTTurbineHelper.calculateRotorReplacementRatePerHour(node);
        assertEquals((1.0 / lifespanHours) * 2.0, replPerHour, 1e-6);
    }

    @Test
    @DisplayName("RFC-006: Dynamic Max Parallel Pmax Calculation and Auto-fill")
    void testDynamicMaxParallelCalculation() {
        RecipeNode node = createLargeSteamTurbineNode();
        GTTurbineHelper.setRotorHolderTier(node, GTVoltageTier.EV);
        GTTurbineHelper.setDynamoTier(node, GTVoltageTier.EV);
        node.setBaseEUt(-512.0); // 512 EU/t per recipe batch

        int pmax = GTPowerCalculator.getMaxParallelCapacity(node);
        assertTrue(pmax >= 1);

        // Auto-calculate parallel tuning
        GTTurbineHelper.autoCalculateTurbineParallel(node);
        assertEquals(pmax, node.getParallel());
    }

    @Test
    @DisplayName("RFC-006: Tier and Amperage Cycling Controls (Wheel / Click)")
    void testCyclingControls() {
        RecipeNode node = createLargeSteamTurbineNode();
        GTTurbineHelper.setRotorHolderTier(node, GTVoltageTier.EV);
        GTTurbineHelper.setDynamoTier(node, GTVoltageTier.EV);
        GTTurbineHelper.setDynamoAmperage(node, 1);

        // Holder tier cycling
        GTTurbineHelper.cycleRotorHolderTier(node, 1); // EV -> IV
        assertEquals(GTVoltageTier.IV, GTTurbineHelper.getRotorHolderTier(node));
        GTTurbineHelper.cycleRotorHolderTier(node, -1); // IV -> EV
        assertEquals(GTVoltageTier.EV, GTTurbineHelper.getRotorHolderTier(node));

        // Dynamo tier cycling
        GTTurbineHelper.cycleDynamoTier(node, 1); // EV -> IV
        assertEquals(GTVoltageTier.IV, GTTurbineHelper.getDynamoTier(node));
        GTTurbineHelper.cycleDynamoTier(node, -1); // IV -> EV
        assertEquals(GTVoltageTier.EV, GTTurbineHelper.getDynamoTier(node));

        // Dynamo amperage cycling: 1 -> 2 -> 4 -> 16 -> 64 -> 1
        GTTurbineHelper.cycleDynamoAmperage(node, 1);
        assertEquals(2, GTTurbineHelper.getDynamoAmperage(node));
        GTTurbineHelper.cycleDynamoAmperage(node, 1);
        assertEquals(4, GTTurbineHelper.getDynamoAmperage(node));
        GTTurbineHelper.cycleDynamoAmperage(node, -1);
        assertEquals(2, GTTurbineHelper.getDynamoAmperage(node));
    }

    @Test
    @DisplayName("RFC-006: Turbine Boost Applicability and Cycling")
    void testTurbineBoostApplicability() {
        RecipeNode steamTurbine = createLargeSteamTurbineNode();
        assertFalse(GTTurbineHelper.supportsTurbineBoost(steamTurbine));
        assertEquals(1.0, GTTurbineHelper.getTurbineBoostMultiplier(steamTurbine));

        RecipeNode gasTurbine = new RecipeNode("gas_turbine", "Large Gas Turbine", 20.0, 4096.0, GTVoltageTier.EV);
        gasTurbine.setMachineIcon(ResourceLocation.tryParse("gtceu:large_gas_turbine"));
        gasTurbine.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        gasTurbine.setMultiblock(true);
        assertFalse(GTTurbineHelper.supportsTurbineBoost(gasTurbine));
        assertEquals(1.0, GTTurbineHelper.getTurbineBoostMultiplier(gasTurbine));

        RecipeNode spt = new RecipeNode("spt", "Supreme Plasma Turbine", 20.0, 98304.0, GTVoltageTier.UHV);
        spt.setMachineIcon(ResourceLocation.tryParse("start_core:supreme_plasma_turbine"));
        spt.setRecipeCategoryId(ResourceLocation.tryParse("start_core:plasma_generator"));
        spt.setMultiblock(true);
        assertTrue(GTTurbineHelper.supportsTurbineBoost(spt));
        assertEquals(0.9, GTTurbineHelper.getTurbineBoostMultiplier(spt), 1e-6);

        // Test cycleTurbineBoost for SPT: Unboosted (0.9x) -> Passive (1.25x) -> Full (2.0x) -> Unboosted (0.9x)
        GTTurbineHelper.cycleTurbineBoost(spt, 1);
        assertTrue(GTTurbineHelper.isLubricantBoost(spt));
        assertFalse(GTTurbineHelper.isCoolantBoost(spt));
        assertEquals(1.25, GTTurbineHelper.getTurbineBoostMultiplier(spt), 1e-6);

        GTTurbineHelper.cycleTurbineBoost(spt, 1);
        assertTrue(GTTurbineHelper.isLubricantBoost(spt));
        assertTrue(GTTurbineHelper.isCoolantBoost(spt));
        assertEquals(2.0, GTTurbineHelper.getTurbineBoostMultiplier(spt), 1e-6);

        GTTurbineHelper.cycleTurbineBoost(spt, 1);
        assertFalse(GTTurbineHelper.isLubricantBoost(spt));
        assertFalse(GTTurbineHelper.isCoolantBoost(spt));
        assertEquals(0.9, GTTurbineHelper.getTurbineBoostMultiplier(spt), 1e-6);
    }

    @Test
    @DisplayName("RFC-006: Star Technology Turbine ID Variants Base Tier and Production Deduction")
    void testStarTechnologyTurbineIdDeduction() {
        RecipeNode starSteam = new RecipeNode("star_steam", "Large Steam Turbine", 20.0, 1024.0, GTVoltageTier.HV);
        starSteam.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_large_turbine"));
        starSteam.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_turbine"));
        starSteam.setMultiblock(true);
        assertEquals(GTVoltageTier.HV, GTTurbineHelper.getTurbineBaseTier(starSteam));
        assertEquals(1024.0, GTTurbineHelper.getTurbineBaseProduction(starSteam), 0.001);

        // Holder cycling on Star Technology Steam Turbine: HV -> EV -> IV -> ... -> MAX -> HV
        GTTurbineHelper.cycleRotorHolderTier(starSteam, 1); // HV -> EV
        assertEquals(GTVoltageTier.EV, GTTurbineHelper.getRotorHolderTier(starSteam));
        GTTurbineHelper.cycleRotorHolderTier(starSteam, -1); // EV -> HV
        assertEquals(GTVoltageTier.HV, GTTurbineHelper.getRotorHolderTier(starSteam));
        GTTurbineHelper.cycleRotorHolderTier(starSteam, -1); // HV -> MAX
        assertEquals(GTVoltageTier.MAX, GTTurbineHelper.getRotorHolderTier(starSteam));
        GTTurbineHelper.cycleRotorHolderTier(starSteam, 1); // MAX -> HV
        assertEquals(GTVoltageTier.HV, GTTurbineHelper.getRotorHolderTier(starSteam));

        // Dynamo cycling: LV -> MV -> HV -> EV -> ... -> MAX -> LV
        GTTurbineHelper.setDynamoTier(starSteam, GTVoltageTier.HV);
        GTTurbineHelper.cycleDynamoTier(starSteam, 1); // HV -> EV
        GTTurbineHelper.cycleDynamoTier(starSteam, 1); // EV -> IV
        assertEquals(GTVoltageTier.IV, GTTurbineHelper.getDynamoTier(starSteam));
    }

    @Test
    @DisplayName("RFC-006: Dynamo Bottleneck Detection and Pmax Limitation")
    void testDynamoBottleneckDetection() {
        RecipeNode node = createLargeSteamTurbineNode();
        // Set Large Steam Turbine with Enderium Rotor (300% power)
        node.setRotorName("Enderium Turbine Rotor");
        node.setRotorPower(300);
        node.setBaseEUt(32.0); // 32 EU/t per recipe batch

        // Holder: HV (Base 1024 EU/t * 300% power = 3072 EU/t -> 96 parallels)
        GTTurbineHelper.setRotorHolderTier(node, GTVoltageTier.HV);
        assertEquals(3072.0, GTTurbineHelper.getRotorHolderCapacity(node), 0.001);
        assertEquals(96, GTTurbineHelper.getRotorHolderMaxParallel(node));

        // Dynamo: LV 1A (32 EU/t -> 1 parallel). Bottlenecked!
        GTTurbineHelper.setDynamoTier(node, GTVoltageTier.LV);
        GTTurbineHelper.setDynamoAmperage(node, 1);
        assertTrue(GTTurbineHelper.isDynamoBottleneck(node));
        assertEquals(1, GTPowerCalculator.getMaxParallelCapacity(node));

        // Dynamo: MV 1A (128 EU/t -> 4 parallels). Bottlenecked!
        GTTurbineHelper.setDynamoTier(node, GTVoltageTier.MV);
        GTTurbineHelper.setDynamoAmperage(node, 1);
        assertTrue(GTTurbineHelper.isDynamoBottleneck(node));
        assertEquals(4, GTPowerCalculator.getMaxParallelCapacity(node));

        // Dynamo: EV 1A (2048 EU/t -> 64 parallels). Bottlenecked!
        GTTurbineHelper.setDynamoTier(node, GTVoltageTier.EV);
        GTTurbineHelper.setDynamoAmperage(node, 1);
        assertTrue(GTTurbineHelper.isDynamoBottleneck(node));
        assertEquals(64, GTPowerCalculator.getMaxParallelCapacity(node));

        // Dynamo: EV 2A (4096 EU/t >= 3072 EU/t). NOT bottlenecked! Full 96 parallels achieved.
        GTTurbineHelper.setDynamoAmperage(node, 2);
        assertFalse(GTTurbineHelper.isDynamoBottleneck(node));
        assertEquals(96, GTPowerCalculator.getMaxParallelCapacity(node));
    }

    @Test
    @DisplayName("Verify minimum rotor holder tier clamping per turbine type")
    void testMinimumRotorHolderTierClamping() {
        // 1. Large Steam Turbine (Base: HV)
        RecipeNode steamNode = createLargeSteamTurbineNode();
        steamNode.setTargetTier(GTVoltageTier.ULV);
        assertEquals(GTVoltageTier.HV, GTTurbineHelper.getRotorHolderTier(steamNode), "Steam turbine must start at minimum HV");
        GTTurbineHelper.setRotorHolderTier(steamNode, GTVoltageTier.LV);
        assertEquals(GTVoltageTier.HV, GTTurbineHelper.getRotorHolderTier(steamNode), "Setting LV must clamp to HV");

        // 2. Large Gas Turbine (Base: EV)
        RecipeNode gasNode = new RecipeNode("gas_turbine", "Large Gas Turbine", 20.0, 4096.0, GTVoltageTier.ULV);
        gasNode.setMachineIcon(ResourceLocation.tryParse("gtceu:large_gas_turbine"));
        gasNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        gasNode.setMultiblock(true);
        gasNode.setTargetTier(GTVoltageTier.ULV);
        assertEquals(GTVoltageTier.EV, GTTurbineHelper.getRotorHolderTier(gasNode), "Gas turbine must start at minimum EV");
        GTTurbineHelper.setRotorHolderTier(gasNode, GTVoltageTier.HV);
        assertEquals(GTVoltageTier.EV, GTTurbineHelper.getRotorHolderTier(gasNode), "Setting HV must clamp to EV");

        // 3. Large Plasma Turbine / Plasma Generator (Base: IV)
        RecipeNode plasmaNode = new RecipeNode("plasma_turbine", "Large Plasma Turbine", 20.0, 16384.0, GTVoltageTier.ULV);
        plasmaNode.setMachineIcon(ResourceLocation.tryParse("gtceu:large_plasma_turbine"));
        plasmaNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_generator"));
        plasmaNode.setMultiblock(true);
        plasmaNode.setTargetTier(GTVoltageTier.ULV);
        assertEquals(GTVoltageTier.IV, GTTurbineHelper.getRotorHolderTier(plasmaNode), "Plasma turbine must start at minimum IV");
        GTTurbineHelper.setRotorHolderTier(plasmaNode, GTVoltageTier.EV);
        assertEquals(GTVoltageTier.IV, GTTurbineHelper.getRotorHolderTier(plasmaNode), "Setting EV must clamp to IV");
    }

    @Test
    @DisplayName("RFC-006: Plasma Turbine Holder Efficiency Bonus (Base: IV)")
    void testPlasmaTurbineHolderBonus() {
        RecipeNode node = new RecipeNode("plasma_turbine", "Plasma Generator", 20.0, 16384.0, GTVoltageTier.IV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:plasma_generator"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_generator"));
        node.setMultiblock(true);
        node.setGenerator(true);

        assertEquals(GTVoltageTier.IV, GTTurbineHelper.getTurbineBaseTier(node));

        // IV Holder -> +0%
        GTTurbineHelper.setRotorHolderTier(node, GTVoltageTier.IV);
        assertEquals(0, GTTurbineHelper.getTurbineHolderEfficiencyBonus(node));
        assertEquals(100, GTTurbineHelper.getTotalTurbineEfficiency(node));

        // LuV Holder (1 tier above IV) -> +10%
        GTTurbineHelper.setRotorHolderTier(node, GTVoltageTier.LuV);
        assertEquals(10, GTTurbineHelper.getTurbineHolderEfficiencyBonus(node));
        assertEquals(110, GTTurbineHelper.getTotalTurbineEfficiency(node));

        // UV Holder (3 tiers above IV) -> +30%
        GTTurbineHelper.setRotorHolderTier(node, GTVoltageTier.UV);
        assertEquals(30, GTTurbineHelper.getTurbineHolderEfficiencyBonus(node));
        assertEquals(130, GTTurbineHelper.getTotalTurbineEfficiency(node));
    }
}
