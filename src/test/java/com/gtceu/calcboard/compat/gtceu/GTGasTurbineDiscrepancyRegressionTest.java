package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.physics.GTTurbinePhysics;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GTGasTurbineDiscrepancyRegressionTest {

    @BeforeAll
    public static void setup() {
        ModAdapterRegistry.init();
        com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix.getInstance().reset();
        MultiblockDetector.reinitialize();
        com.gtceu.calcboard.testutil.TestMultiblockFixtures.initTestEnvironmentDefaults();
    }

    @Test
    @DisplayName("Verify Large Gas Turbine Nitrobenzene Enderium EV Holder matches GTCEu and Web Calculator")
    void testNitrobenzeneLargeGasTurbineEnderiumEVHolder() {
        RecipeNode node = RecipeNode.create("Gas Turbine (Nitrobenzene)", 40.0, 32.0, GTVoltageTier.EV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_gas_turbine"));
        node.setGenerator(true);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.EV);

        MachineAddon enderiumRotor = new MachineAddon(
                "gtceu:rotor_enderium",
                "Enderium Turbine Rotor",
                MachineAddon.Category.ROTOR,
                "180%",
                null
        );
        enderiumRotor.setDurationMultiplier(1.80);
        enderiumRotor.setRotorEfficiency(180);
        enderiumRotor.setRotorPower(300);
        enderiumRotor.setRotorMaxEUt(264000.0);
        node.addAddon(enderiumRotor);

        assertEquals(GTVoltageTier.EV, GTTurbineHelper.getTurbineBaseTier(node));
        assertEquals(4096.0, GTTurbineHelper.getTurbineBaseProduction(node), 0.001);
        assertEquals(0, GTTurbineHelper.getTurbineHolderEfficiencyBonus(node));
        assertEquals(180, GTTurbineHelper.getTotalTurbineEfficiency(node));
        assertEquals(12288.0, GTTurbineHelper.getNodeRotorHolderMaxEUt(node, GTVoltageTier.EV, 300), 0.001);

        node.autoCalculateTurbineParallel();
        assertEquals(384, node.getTotalParallel());
        assertEquals(12288.0, node.getSingleMachineEUt(), 0.001);
        assertEquals(3.60, node.getEffectiveDurationSeconds(), 0.001);
        assertEquals(72.0, node.getEffectiveDurationSeconds() * 20.0, 0.001);

        double fuelConsumptionPerTick = 384.0 / 72.0;
        assertEquals(5.333, fuelConsumptionPerTick, 0.005);
        assertEquals(106.666, fuelConsumptionPerTick * 20.0, 0.05);
    }

    @Test
    @DisplayName("Verify EMI-imported singleblock gas turbine automatically syncs to multiblock on rotor equip")
    void testEmiImportedGasTurbinePromotesToMultiblock() {
        RecipeNode node = RecipeNode.create("Gas Turbine (Nitrobenzene)", 40.0, 32.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:ev_gas_turbine"));
        node.setGenerator(true);
        node.setMultiblock(false);

        MachineAddon enderiumRotor = new MachineAddon(
                "gtceu:rotor_enderium",
                "Enderium Turbine Rotor",
                MachineAddon.Category.ROTOR,
                "180%",
                null
        );
        enderiumRotor.setDurationMultiplier(1.80);
        enderiumRotor.setRotorEfficiency(180);
        enderiumRotor.setRotorPower(300);
        enderiumRotor.setRotorMaxEUt(264000.0);
        node.addAddon(enderiumRotor);

        GTTurbinePhysics.syncTurbineMachineIcon(node);
        assertTrue(node.isMultiblock(), "Node with rotor addon must become multiblock");
        assertTrue(MultiblockDetector.isTurbineMachine(node.getMachineIcon()), "Machine icon must be a turbine controller");
        assertTrue(MultiblockDetector.isMultiblock(node.getMachineIcon()), "Machine icon must be a multiblock controller");

        node.setTargetTier(GTVoltageTier.EV);
        assertEquals(GTVoltageTier.EV, GTTurbineHelper.getTurbineBaseTier(node));
        assertEquals(384, node.getTotalParallel());
        assertEquals(12288.0, node.getSingleMachineEUt(), 0.001);
        assertEquals(3.60, node.getEffectiveDurationSeconds(), 0.001);
    }

    @Test
    @DisplayName("Verify IV Holder scaling: 24,576 EU/t, 768 parallel, 198% efficiency")
    void testNitrobenzeneLargeGasTurbineIvHolderScaling() {
        RecipeNode node = RecipeNode.create("Gas Turbine (Nitrobenzene)", 40.0, 32.0, GTVoltageTier.EV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_gas_turbine"));
        node.setGenerator(true);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.IV);

        MachineAddon enderiumRotor = new MachineAddon(
                "gtceu:rotor_enderium",
                "Enderium Turbine Rotor",
                MachineAddon.Category.ROTOR,
                "180%",
                null
        );
        enderiumRotor.setDurationMultiplier(1.80);
        enderiumRotor.setRotorEfficiency(180);
        enderiumRotor.setRotorPower(300);
        enderiumRotor.setRotorMaxEUt(264000.0);
        node.addAddon(enderiumRotor);

        assertEquals(10, GTTurbineHelper.getTurbineHolderEfficiencyBonus(node));
        assertEquals(198, GTTurbineHelper.getTotalTurbineEfficiency(node));
        assertEquals(24576.0, GTTurbineHelper.getNodeRotorHolderMaxEUt(node, GTVoltageTier.IV, 300), 0.001);

        node.autoCalculateTurbineParallel();
        assertEquals(768, node.getTotalParallel());
        assertEquals(24576.0, node.getSingleMachineEUt(), 0.001);
        assertEquals(3.96, node.getEffectiveDurationSeconds(), 0.001);
        assertEquals(79.2, node.getEffectiveDurationSeconds() * 20.0, 0.001);
    }
}
