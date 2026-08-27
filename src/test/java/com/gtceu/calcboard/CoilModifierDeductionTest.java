package com.gtceu.calcboard;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CoilModifierDeductionTest {

    @Test
    @DisplayName("Test EBF Excess Temperature Discount (5% per 900K)")
    public void testEbfExcessTemperatureDiscount() {
        RecipeNode node = new RecipeNode("node-ebf", "EBF Node", 10.0, 120.0, GTVoltageTier.MV);
        node.setMachineIcon(new ResourceLocation("gtceu", "electric_blast_furnace"));
        node.setRecipeTemperature(1800);

        CoilHelper.CoilStats stats = new CoilHelper.CoilStats(3600, 150, 70, 125, 80, 64);
        GTCoilAddon coil = new GTCoilAddon("gtceu:naquadah_coil", "Naquadah Coil", "3600K", null, stats);

        MachineAddon tailored = CoilHelper.tailorCoilAddon(coil, node);
        Assertions.assertEquals(Math.pow(0.95, 2), tailored.getEutMultiplier(), 0.001);
        Assertions.assertEquals(1.0, tailored.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(1, tailored.getParallelMultiplier());
    }

    @Test
    @DisplayName("Test Pyrolyse Oven Speed Bonus")
    public void testPyrolyseOvenSpeedBonus() {
        RecipeNode node = new RecipeNode("node-pyro", "Pyro Node", 10.0, 120.0, GTVoltageTier.MV);
        node.setMachineIcon(new ResourceLocation("gtceu", "pyrolyse_oven"));

        CoilHelper.CoilStats stats = new CoilHelper.CoilStats(2700, 200, 80, 125, 80, 32);
        GTCoilAddon coil = new GTCoilAddon("gtceu:kanthal_coil", "Kanthal Coil", "2700K", null, stats);

        MachineAddon tailored = CoilHelper.tailorCoilAddon(coil, node);
        Assertions.assertEquals(0.5, tailored.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(1.0, tailored.getEutMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Test Cracking Unit Energy Discount")
    public void testCrackingUnitEnergyDiscount() {
        RecipeNode node = new RecipeNode("node-crack", "Crack Node", 10.0, 120.0, GTVoltageTier.MV);
        node.setMachineIcon(new ResourceLocation("gtceu", "cracking_unit"));

        CoilHelper.CoilStats stats = new CoilHelper.CoilStats(2700, 200, 80, 125, 80, 32);
        GTCoilAddon coil = new GTCoilAddon("gtceu:kanthal_coil", "Kanthal Coil", "2700K", null, stats);

        MachineAddon tailored = CoilHelper.tailorCoilAddon(coil, node);
        Assertions.assertEquals(1.0, tailored.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(0.8, tailored.getEutMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Test Chemical Reactor Speed and Energy Multipliers")
    public void testChemicalReactorMultipliers() {
        RecipeNode node = new RecipeNode("node-chem", "Chem Node", 10.0, 120.0, GTVoltageTier.MV);
        node.setMachineIcon(new ResourceLocation("gtceu", "large_chemical_reactor"));

        CoilHelper.CoilStats stats = new CoilHelper.CoilStats(2700, 200, 80, 125, 80, 32);
        GTCoilAddon coil = new GTCoilAddon("gtceu:kanthal_coil", "Kanthal Coil", "2700K", null, stats);

        MachineAddon tailored = CoilHelper.tailorCoilAddon(coil, node);
        Assertions.assertEquals(0.8, tailored.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(0.8, tailored.getEutMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Test Multi Smelter Parallel Multiplier")
    public void testMultiSmelterParallelMultiplier() {
        RecipeNode node = new RecipeNode("node-smelt", "Smelt Node", 10.0, 120.0, GTVoltageTier.MV);
        node.setMachineIcon(new ResourceLocation("gtceu", "multi_smelter"));

        CoilHelper.CoilStats stats = new CoilHelper.CoilStats(3600, 150, 70, 125, 80, 64);
        GTCoilAddon coil = new GTCoilAddon("gtceu:naquadah_coil", "Naquadah Coil", "3600K", null, stats);

        MachineAddon tailored = CoilHelper.tailorCoilAddon(coil, node);
        Assertions.assertEquals(64, tailored.getParallelMultiplier());
        Assertions.assertEquals(1.0, tailored.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(1.0, tailored.getEutMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Test Custom Coil Machine Spec Deduction")
    public void testCustomCoilMachineSpecDeduction() {
        GTCEuCoilModifierHelper.CustomCoilMultiplier custom = new GTCEuCoilModifierHelper.CustomCoilMultiplier(0.1, 0.05, 4, 2);
        GTCEuCoilModifierHelper.CoilMachineSpec spec = new GTCEuCoilModifierHelper.CoilMachineSpec(
                GTCEuCoilModifierHelper.CoilMachineKind.CUSTOM_COIL_MULTIBLOCK,
                custom
        );

        Assertions.assertEquals(GTCEuCoilModifierHelper.CoilMachineKind.CUSTOM_COIL_MULTIBLOCK, spec.kind());
        Assertions.assertEquals(0.1, spec.customMultiplier().durationMultiplier(), 0.001);
        Assertions.assertEquals(0.05, spec.customMultiplier().energyMultiplier(), 0.001);
        Assertions.assertEquals(4, spec.customMultiplier().parallelMultiplier());
        Assertions.assertEquals(2, spec.customMultiplier().baseParallel());
    }
}

