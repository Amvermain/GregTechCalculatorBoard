package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrapExtension.class)
public class CoilModifierDeductionTest {

    @Test
    @DisplayName("Test EBF Excess Temperature Discount (5% per 900K)")
    public void testEbfExcessTemperatureDiscount() {
        RecipeNode node = new RecipeNode("node-ebf", "EBF Node", 10.0, 120.0, GTVoltageTier.MV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
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
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:pyrolyse_oven"));

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
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:cracking_unit"));

        CoilHelper.CoilStats stats = new CoilHelper.CoilStats(2700, 200, 80, 125, 80, 32);
        GTCoilAddon coil = new GTCoilAddon("gtceu:kanthal_coil", "Kanthal Coil", "2700K", null, stats);

        MachineAddon tailored = CoilHelper.tailorCoilAddon(coil, node);
        Assertions.assertEquals(1.0, tailored.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(0.8, tailored.getEutMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Test Chemical Reactor Speed and Energy Multipliers Isolated to StarT")
    public void testChemicalReactorMultipliers() {
        RecipeNode node = new RecipeNode("node-chem", "Chem Node", 10.0, 120.0, GTVoltageTier.MV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_chemical_reactor"));

        CoilHelper.CoilStats stats = new CoilHelper.CoilStats(2700, 200, 80, 125, 80, 32);
        GTCoilAddon coil = new GTCoilAddon("gtceu:kanthal_coil", "Kanthal Coil", "2700K", null, stats);

        // 1. In vanilla GTCEu environment (StarT not loaded), LCR does NOT receive coil buffs
        com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper.clearCache();
        com.gtceu.calcboard.api.util.ModCompatHelper.setTestOverride("start_core", false);
        MachineAddon vanillaTailored = CoilHelper.tailorCoilAddon(coil, node);
        Assertions.assertEquals(1.0, vanillaTailored.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(1.0, vanillaTailored.getEutMultiplier(), 0.001);

        // 2. In Star Technology environment, LCR receives coil speed/energy multipliers
        try {
            com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper.clearCache();
            com.gtceu.calcboard.api.util.ModCompatHelper.setTestOverride("start_core", true);
            MachineAddon startTailored = CoilHelper.tailorCoilAddon(coil, node);
            Assertions.assertEquals(0.8, startTailored.getDurationMultiplier(), 0.001);
            Assertions.assertEquals(0.8, startTailored.getEutMultiplier(), 0.001);
        } finally {
            com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper.clearCache();
            com.gtceu.calcboard.api.util.ModCompatHelper.clearTestOverrides();
        }
    }

    @Test
    @DisplayName("Test Multi Smelter Parallel Multiplier and Node Total Parallel")
    public void testMultiSmelterParallelMultiplier() {
        RecipeNode node = new RecipeNode("node-smelt", "Smelt Node", 10.0, 120.0, GTVoltageTier.MV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:multi_smelter"));
        node.setMultiblock(true);

        // 1. Without coil: default parallel is 1
        Assertions.assertEquals(1, node.getTotalParallel());

        // 2. With RTM Alloy coil (smelterParallel = 128)
        CoilHelper.CoilStats rtmStats = new CoilHelper.CoilStats(4500, 200, 70, 175, 85, 128);
        GTCoilAddon rtmCoil = new GTCoilAddon("gtceu:rtm_alloy_coil_block", "RTM Alloy Coil Block", "4500K", null, rtmStats);
        MachineAddon tailoredRtm = CoilHelper.tailorCoilAddon(rtmCoil, node);
        node.getAddons().add(tailoredRtm);

        // Effective total parallel should be exactly 128 (not 8 * 128 = 1024)
        Assertions.assertEquals(128, node.getTotalParallel());

        // 3. Clear and test Naquadah coil (smelterParallel = 64)
        node.getAddons().clear();
        CoilHelper.CoilStats stats = new CoilHelper.CoilStats(3600, 150, 70, 125, 80, 64);
        GTCoilAddon coil = new GTCoilAddon("gtceu:naquadah_coil", "Naquadah Coil", "3600K", null, stats);

        MachineAddon tailored = CoilHelper.tailorCoilAddon(coil, node);
        Assertions.assertEquals(64, tailored.getParallelMultiplier());
        Assertions.assertEquals(1.0, tailored.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(1.0, tailored.getEutMultiplier(), 0.001);

        node.getAddons().add(tailored);
        Assertions.assertEquals(64, node.getTotalParallel());
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

    @Test
    @DisplayName("Test Liquefaction Tower Coil Speed Bonus (Cupro 25% slower, Kanthal base, +50% speed per tier above Kanthal)")
    public void testLiquefactionTowerCoilModifiers() {
        ResourceLocation ltId = ResourceLocation.tryParse("gtceu:liquefaction_tower");
        RecipeNode node = new RecipeNode("node-lt", "Liquefaction Tower", 10.0, 120.0, GTVoltageTier.MV);
        node.setMachineIcon(ltId);
        node.setMultiblock(true);

        // 1. Detection check
        Assertions.assertTrue(com.gtceu.calcboard.api.catalog.MultiblockDetector.isCoilMultiblock(ltId),
                "Liquefaction Tower must be detected as a coil multiblock");

        // 2. Cupronickel (1800K) -> 25% slower (speed = 0.75, duration = 1.0 / 0.75 = 1.333...)
        CoilHelper.CoilStats cuproStats = new CoilHelper.CoilStats(1800, 100, 100, 100, 100, 16);
        GTCoilAddon cupro = new GTCoilAddon("gtceu:cupronickel_coil", "Cupronickel Coil", "1800K", null, cuproStats);
        MachineAddon tailoredCupro = CoilHelper.tailorCoilAddon(cupro, node);
        Assertions.assertEquals(1.0 / 0.75, tailoredCupro.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(1.0, tailoredCupro.getEutMultiplier(), 0.001);

        // 3. Kanthal (2700K) -> Base speed (1.0x)
        CoilHelper.CoilStats kanthalStats = new CoilHelper.CoilStats(2700, 100, 90, 125, 95, 32);
        GTCoilAddon kanthal = new GTCoilAddon("gtceu:kanthal_coil", "Kanthal Coil", "2700K", null, kanthalStats);
        MachineAddon tailoredKanthal = CoilHelper.tailorCoilAddon(kanthal, node);
        Assertions.assertEquals(1.0, tailoredKanthal.getDurationMultiplier(), 0.001);

        // 4. Nichrome (3600K) -> +50% speed (1.5x speed -> duration = 1.0 / 1.5 = 0.666...)
        CoilHelper.CoilStats nichromeStats = new CoilHelper.CoilStats(3600, 150, 80, 150, 90, 64);
        GTCoilAddon nichrome = new GTCoilAddon("gtceu:nichrome_coil", "Nichrome Coil", "3600K", null, nichromeStats);
        MachineAddon tailoredNichrome = CoilHelper.tailorCoilAddon(nichrome, node);
        Assertions.assertEquals(1.0 / 1.5, tailoredNichrome.getDurationMultiplier(), 0.001);

        // 5. RTM Alloy (4500K) -> +100% speed (2.0x speed -> duration = 1.0 / 2.0 = 0.5)
        CoilHelper.CoilStats rtmStats = new CoilHelper.CoilStats(4500, 200, 70, 175, 85, 128);
        GTCoilAddon rtm = new GTCoilAddon("gtceu:rtm_alloy_coil", "RTM Alloy Coil", "4500K", null, rtmStats);
        MachineAddon tailoredRtm = CoilHelper.tailorCoilAddon(rtm, node);
        Assertions.assertEquals(0.5, tailoredRtm.getDurationMultiplier(), 0.001);

        // 6. HSS-G (5400K) -> +150% speed (2.5x speed -> duration = 1.0 / 2.5 = 0.4)
        CoilHelper.CoilStats hssgStats = new CoilHelper.CoilStats(5400, 250, 60, 200, 80, 256);
        GTCoilAddon hssg = new GTCoilAddon("gtceu:hssg_coil", "HSS-G Coil", "5400K", null, hssgStats);
        MachineAddon tailoredHssg = CoilHelper.tailorCoilAddon(hssg, node);
        Assertions.assertEquals(0.4, tailoredHssg.getDurationMultiplier(), 0.001);

        // 7. Category capability check: AddonCategory.COIL must be present
        var cats = com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.getApplicableAddonCategories(node);
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.COIL),
                "Liquefaction Tower applicable addon categories must contain COIL");
    }

    @Test
    @DisplayName("Test Nuclear Fuel Factory Coil Speed Bonus (shares Pyrolyse Oven overclock speed formula)")
    public void testNuclearFuelFactoryCoilSpeedModifiers() {
        ResourceLocation nffId = ResourceLocation.tryParse("gtceu:nuclear_fuel_factory");
        RecipeNode node = new RecipeNode("node-nff", "Nuclear Fuel Factory", 10.0, 1920.0, GTVoltageTier.EV);
        node.setMachineIcon(nffId);
        node.setMultiblock(true);

        // Cupronickel (1800K) -> 75% speed -> duration = 1.0 / 0.75 = 1.333x
        CoilHelper.CoilStats cuproStats = new CoilHelper.CoilStats(1800, 75, 100, 100, 100, 16);
        GTCoilAddon cupro = new GTCoilAddon("gtceu:cupronickel_coil", "Cupronickel Coil", "1800K", null, cuproStats);
        MachineAddon tailoredCupro = CoilHelper.tailorCoilAddon(cupro, node);
        Assertions.assertEquals(1.0 / 0.75, tailoredCupro.getDurationMultiplier(), 0.001);

        // RTM Alloy (4500K) -> 200% speed -> duration = 1.0 / 2.0 = 0.5x
        CoilHelper.CoilStats rtmStats = new CoilHelper.CoilStats(4500, 200, 70, 175, 85, 128);
        GTCoilAddon rtm = new GTCoilAddon("gtceu:rtm_alloy_coil", "RTM Alloy Coil", "4500K", null, rtmStats);
        MachineAddon tailoredRtm = CoilHelper.tailorCoilAddon(rtm, node);
        Assertions.assertEquals(0.5, tailoredRtm.getDurationMultiplier(), 0.001);

        // Tritanium (10800K) -> 550% speed -> duration = 1.0 / 5.5 = 0.1818x
        CoilHelper.CoilStats tritaniumStats = new CoilHelper.CoilStats(10800, 550, 40, 300, 60, 512);
        GTCoilAddon tritanium = new GTCoilAddon("gtceu:tritanium_coil", "Tritanium Coil", "10800K", null, tritaniumStats);
        MachineAddon tailoredTritanium = CoilHelper.tailorCoilAddon(tritanium, node);
        Assertions.assertEquals(1.0 / 5.5, tailoredTritanium.getDurationMultiplier(), 0.001);
    }

    @Test
    @DisplayName("Simulated KubeJS proxy modifier with pyrolyseOvenOverclock is properly classified")
    public void testKubeJsProxyModifierClassification() {
        // Simulates a Rhino/KubeJS JavaInterfaceProxy holding an anonymous function
        Object dummyJsFunction = new Object() {
            @Override
            public String toString() {
                return "(machine, recipe) => GTRecipeModifiers.pyrolyseOvenOverclock(machine, recipe)";
            }
        };

        Object simulatedKubeJsProxy = new Object() {
            public final Object function = dummyJsFunction;

            @Override
            public String toString() {
                return "JavaInterfaceProxy[" + function + "]";
            }
        };

        GTCEuCoilModifierHelper.CoilMachineKind kind = GTCEuCoilModifierHelper.classifyModifierObject(simulatedKubeJsProxy);
        Assertions.assertEquals(GTCEuCoilModifierHelper.CoilMachineKind.PYROLYSE_OVEN, kind);
    }
}

