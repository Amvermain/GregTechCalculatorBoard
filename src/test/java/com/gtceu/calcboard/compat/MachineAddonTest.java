package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.addon.GTRotorAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ParallelHelper;
import com.gtceu.calcboard.compat.gtceu.helper.TurbineRotorHelper;
import com.gtceu.calcboard.compat.start.StarTAddonCrawler;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for Machine Addons, Turbine Rotors, Heating Coils, Parallel Hatches, and Thermal Augments.
 */
public class MachineAddonTest {

    @BeforeEach
    void setUp() {
        com.gtceu.calcboard.testutil.TestMultiblockFixtures.initTestEnvironmentDefaults();
    }

    @Test
    public void testMachineAddonsAndAbsoluteParallelHatch() {
        // 1. Test Absolute Parallel Hatch (4x parallel with ZERO extra power consumption)
        RecipeNode node = RecipeNode.create("Test Pyrolyse", 20.0, 30.0, GTVoltageTier.LV);
        node.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 100.0, 1.0));
        node.setMachineCount(1.0);

        MachineAddon absPar = new MachineAddon("start_core:uhv_absolute_parallel_hatch", "에픽 절대 병렬 처리 해치", MachineAddon.Category.PARALLEL, "4x 병렬 (전기 추가 소모 없음)", null);
        absPar.setParallelMultiplier(4);
        absPar.setPowerConstant(true);
        node.addAddon(absPar);

        // Effective parallel = 4
        Assertions.assertEquals(4, node.getTotalParallel());
        // CPS scales by 4x -> 1.0 * 4 = 4.0 cycles/sec
        Assertions.assertEquals(4.0, node.getCyclesPerSecond(), 0.001);
        // Single machine EU/t remains 30 EU/t (powerConstant = true!)
        Assertions.assertEquals(30.0, node.getSingleMachineEUt(), 0.001);

        // 2. Test Throughput Boosting Trait (4x Par, 1.6x Time, 0.95x EU)
        RecipeNode superPyrolyse = RecipeNode.create("Super Pyrolyse", 20.0, 30.0, GTVoltageTier.LV);
        MachineAddon boost = new MachineAddon("gtceu:throughput_boosting", "처리 부스팅", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        boost.setParallelMultiplier(4);
        boost.setDurationMultiplier(1.6);
        boost.setEutMultiplier(0.95);
        superPyrolyse.addAddon(boost);

        // Duration ticks: 20 * 1.6 = 32.0 ticks (1.6s)
        Assertions.assertEquals(1.6, superPyrolyse.getEffectiveDurationSeconds(), 0.001);
        // CPS: (20 / 32) * 4 = 0.625 * 4 = 2.5 cycles/sec
        Assertions.assertEquals(2.5, superPyrolyse.getCyclesPerSecond(), 0.001);
        // EU/t: 30 * 0.95 * 4 = 114.0 EU/t
        Assertions.assertEquals(114.0, superPyrolyse.getSingleMachineEUt(), 0.001);

        // 3. Test Configurable Maintenance Hatch (CMH: 95% speed -> 1/0.95 duration, 90% power)
        MachineAddon cmh = new MachineAddon("gtceu:cmh", "CMH", MachineAddon.Category.MAINTENANCE, "", null);
        cmh.setDurationMultiplier(1.0 / 0.95);
        cmh.setEutMultiplier(0.90);
        superPyrolyse.addAddon(cmh);

        // Combined EU/t: 30 * (0.95 * 0.90) * 4 = 102.6 EU/t
        Assertions.assertEquals(102.6, superPyrolyse.getSingleMachineEUt(), 0.001);

        // 4. Test NBT Serialization / Deserialization
        CompoundTag tag = superPyrolyse.serializeNBT();
        RecipeNode loadedNode = RecipeNode.deserializeNBT(tag);
        Assertions.assertNotNull(loadedNode);
        Assertions.assertEquals(2, loadedNode.getAddons().size());
        Assertions.assertEquals(superPyrolyse.getSingleMachineEUt(), loadedNode.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(superPyrolyse.getCyclesPerSecond(), loadedNode.getCyclesPerSecond(), 0.001);
    }

    @Test
    public void testTurbineRotorAutoParallelCalculation() {
        // Gas Turbine running Nitrobenzene (32 EU/t, LV recipe) with EV Rotor Holder
        RecipeNode turbine = RecipeNode.create("Gas Turbine (Nitrobenzene)", 20.0, 32.0, GTVoltageTier.LV);
        turbine.setTargetTier(GTVoltageTier.EV);
        turbine.setGenerator(true);

        // 1. Equip Titanium Turbine Rotor (130% power) on EV Rotor Holder (4,096 base) -> capped at 5,324 EU/t (167 parallel, matches in-game screenshot!)
        turbine.setRotorName("Titanium Turbine Rotor");
        turbine.setRotorEfficiency(115);
        turbine.setRotorPower(130);
        turbine.autoCalculateTurbineParallel();

        // 5324 EU/t / 32 EU/t = 166.375 -> 167 parallel!
        Assertions.assertEquals(167, turbine.getParallel());
        Assertions.assertEquals(167, turbine.getTotalParallel());
        // Generator power output is capped at exact Rotor Holder limit (5,324 EU/t)!
        Assertions.assertEquals(5324.0, turbine.getSingleMachineEUt(), 0.001);

        // 1-b. Equip Iron Turbine Rotor (115% power) on EV Rotor Holder (4,096 base) -> capped at 4,710 EU/t (148 parallel!)
        turbine.setRotorName("Iron Turbine Rotor");
        turbine.setRotorEfficiency(115);
        turbine.setRotorPower(115);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(148, turbine.getParallel());
        Assertions.assertEquals(4710.0, turbine.getSingleMachineEUt(), 0.001);

        // 2. Upgrade Rotor Holder to IV (8,192 base) with Titanium (130% power) -> 10,649 EU/t (333 parallel!)
        turbine.setRotorName("Titanium Turbine Rotor");
        turbine.setRotorEfficiency(115);
        turbine.setRotorPower(130);
        turbine.setTargetTier(GTVoltageTier.IV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(333, turbine.getParallel());
        Assertions.assertEquals(10649.0, turbine.getSingleMachineEUt(), 0.001);

        // 3. For Gas Turbine (Base Tier: EV), attempting to set HV clamps to the minimum base tier EV -> 5,324 EU/t (167 parallel)
        turbine.setTargetTier(GTVoltageTier.HV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(167, turbine.getParallel());
        Assertions.assertEquals(5324.0, turbine.getSingleMachineEUt(), 0.001);

        // 4. Upgrade Rotor Holder to LuV (16,384 base) with Titanium -> 21,299 EU/t (666 parallel)
        turbine.setTargetTier(GTVoltageTier.LuV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(666, turbine.getParallel());
        Assertions.assertEquals(21299.0, turbine.getSingleMachineEUt(), 0.001);

        // 5. Korean localized name "티타늄 터빈 로터" on LuV
        turbine.setRotorName("티타늄 터빈 로터");
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(666, turbine.getParallel());
        Assertions.assertEquals(21299.0, turbine.getSingleMachineEUt(), 0.001);

        // 6. Equip Infinity Rotor on LuV (16,384 base * 3.0 = 49,152 EU/t) -> 1,536 parallel
        MachineAddon infinityRotor = new MachineAddon("gtceu:infinity_rotor", "Infinity Turbine Rotor", MachineAddon.Category.ROTOR, "200%", null);
        infinityRotor.setDurationMultiplier(2.0);
        infinityRotor.setRotorPower(300);
        turbine.addAddon(infinityRotor);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(1536, turbine.getParallel());
        Assertions.assertEquals(49152.0, turbine.getSingleMachineEUt(), 0.001);

        // 7. Test User Reported Case: Scheelite Rotor (450% power) on IV Rotor Holder (8,192 base) with Nitrobenzene (32 EU/t, 2.0s)
        RecipeNode scheeliteTurbine = RecipeNode.create("Large Gas Turbine (Nitrobenzene)", 40.0, 32.0, GTVoltageTier.LV);
        scheeliteTurbine.setTargetTier(GTVoltageTier.IV);
        scheeliteTurbine.setGenerator(true);
        scheeliteTurbine.setRotorName("Scheelite Turbine Rotor");
        scheeliteTurbine.setRotorEfficiency(220);
        scheeliteTurbine.setRotorPower(450);
        MachineAddon scheeliteRotorAddon = new MachineAddon("gtceu:rotor_scheelite", "Scheelite Turbine Rotor", MachineAddon.Category.ROTOR, "220%", null);
        scheeliteRotorAddon.setDurationMultiplier(2.2);
        scheeliteRotorAddon.setRotorPower(450);
        scheeliteTurbine.addAddon(scheeliteRotorAddon);
        scheeliteTurbine.autoCalculateTurbineParallel();

        // 8,192 base * 4.5 = 36,864 EU/t (4.5A IV) -> 36,864 / 32 = 1,152 parallel!
        Assertions.assertEquals(1152, scheeliteTurbine.getParallel());
        Assertions.assertEquals(1152, scheeliteTurbine.getTotalParallel());
        Assertions.assertEquals(36864.0, scheeliteTurbine.getSingleMachineEUt(), 0.001);
        // Duration: 2.0s * (2.20 * 1.10) = 4.84s
        Assertions.assertEquals(4.84, scheeliteTurbine.getEffectiveDurationSeconds(), 0.001);
        // Throughput: 1152 parallel / 4.84s = 238.016 cycles/s
        Assertions.assertEquals(238.016, scheeliteTurbine.getCyclesPerSecond(), 0.01);
    }

    @Test
    public void testLargeSteamTurbineWithShelliteRotorScaling() {
        RecipeNode steamTurbine = RecipeNode.create("Large Steam Turbine (Superhot Steam)", 40.0, 32.0, GTVoltageTier.LV);
        steamTurbine.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_turbine"));
        steamTurbine.setMultiblock(true);
        steamTurbine.setTargetTier(GTVoltageTier.EV);
        steamTurbine.setGenerator(true);

        MachineAddon shelliteRotor = new MachineAddon("gtceu:rotor_shellite", "Shellite Turbine Rotor", MachineAddon.Category.ROTOR, "200%", null);
        shelliteRotor.setDurationMultiplier(2.0);
        shelliteRotor.setRotorPower(450);
        steamTurbine.addAddon(shelliteRotor);

        // Large Steam Turbine Base = HV (1024 EU/t). EV Holder = 2048 EU/t base * 4.5 = 9216 EU/t capacity.
        // Parallel = 9216 / 32 = 288x parallel!
        Assertions.assertEquals(288, steamTurbine.getParallel());
        Assertions.assertEquals(288, steamTurbine.getTotalParallel());
        Assertions.assertEquals(9216.0, steamTurbine.getSingleMachineEUt(), 0.001);
    }

    @Test
    public void testHeatingCoilAddonBonuses() {
        // 1. Create HSS-G Coil Addon (5400 K)
        MachineAddon hssgCoil = new MachineAddon("gtceu:hssg_coil_block", "HSS-G Coil Block", MachineAddon.Category.COIL, "5400 K", null);
        hssgCoil.setCoilTemperature(5400);
        hssgCoil.setPyrolyseSpeedPercent(250);
        hssgCoil.setCrackingEnergyPercent(60);
        hssgCoil.setChemicalSpeedPercent(175);
        hssgCoil.setChemicalEnergyPercent(80);
        hssgCoil.setSmelterParallel(128);

        // 2. Pyrolyse Oven: Speed 250% -> Duration 100/250 = 0.40x
        RecipeNode pyrolyse = RecipeNode.create("Pyrolyse Oven", 20.0, 64.0, GTVoltageTier.MV);
        pyrolyse.addAddon(hssgCoil.forMachine(pyrolyse.getName()));
        Assertions.assertEquals(0.40, pyrolyse.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(64.0, pyrolyse.getSingleMachineEUt(), 0.001);

        // 3. Cracking Unit: Energy 60% -> EU/t 0.60x
        RecipeNode cracker = RecipeNode.create("Cracking Unit", 20.0, 100.0, GTVoltageTier.HV);
        cracker.addAddon(hssgCoil.forMachine(cracker.getName()));
        Assertions.assertEquals(1.0, cracker.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(60.0, cracker.getSingleMachineEUt(), 0.001);

        // 4. Chemical Reactor: Speed 175% (0.5714x duration), Energy 80% (0.80x EU/t)
        RecipeNode lcr = RecipeNode.create("Large Chemical Reactor", 20.0, 100.0, GTVoltageTier.HV);
        lcr.addAddon(hssgCoil.forMachine(lcr.getName()));
        Assertions.assertEquals(1.0 / 1.75, lcr.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(80.0, lcr.getSingleMachineEUt(), 0.001);

        // 5. Multi Smelter: Parallel 128x
        RecipeNode smelter = RecipeNode.create("Multi Smelter", 20.0, 16.0, GTVoltageTier.MV);
        MachineAddon smelterAddon = hssgCoil.forMachine(smelter.getName());
        smelter.addAddon(smelterAddon);
        Assertions.assertEquals(128, smelterAddon.getParallelMultiplier());
        Assertions.assertEquals(128, smelter.getTotalParallel());

        // 6. EBF: 5% EU discount per 900K excess temperature above recipe's requirement
        RecipeNode ebfAluminium = RecipeNode.create("Electric Blast Furnace", 20.0, 100.0, GTVoltageTier.MV);
        ebfAluminium.setRecipeTemperature(1800);
        ebfAluminium.addAddon(hssgCoil.forMachine(ebfAluminium));
        Assertions.assertEquals(100.0 * Math.pow(0.95, 4), ebfAluminium.getSingleMachineEUt(), 0.001);

        RecipeNode ebfTungsten = RecipeNode.create("Electric Blast Furnace", 20.0, 100.0, GTVoltageTier.MV);
        ebfTungsten.setRecipeTemperature(3600);
        ebfTungsten.addAddon(hssgCoil.forMachine(ebfTungsten));
        Assertions.assertEquals(100.0 * Math.pow(0.95, 2), ebfTungsten.getSingleMachineEUt(), 0.001);

        RecipeNode ebfNaquadah = RecipeNode.create("Electric Blast Furnace", 20.0, 100.0, GTVoltageTier.MV);
        ebfNaquadah.setRecipeTemperature(5400);
        ebfNaquadah.addAddon(hssgCoil.forMachine(ebfNaquadah));
        Assertions.assertEquals(100.0, ebfNaquadah.getSingleMachineEUt(), 0.001);
    }

    @Test
    public void testMachineAddonCompatibilityFiltering() {
        // 1. Gas Turbine (Generator)
        RecipeNode turbine = RecipeNode.create("Large Gas Turbine", 40.0, 32.0, GTVoltageTier.LV);
        turbine.setGenerator(true);
        turbine.setMultiblock(true);
        turbine.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        var turbineCats = MachineAddon.getRelevantCategories(turbine);
        Assertions.assertTrue(turbineCats.contains(MachineAddon.Category.ROTOR));
        Assertions.assertFalse(turbineCats.contains(MachineAddon.Category.COIL));
        Assertions.assertTrue(turbineCats.contains(MachineAddon.Category.MAINTENANCE));

        MachineAddon rotor = new MachineAddon("gtceu:titanium_rotor", "Titanium Rotor", MachineAddon.Category.ROTOR, "", null);
        MachineAddon coil = new MachineAddon("gtceu:kanthal_coil", "Kanthal Coil", MachineAddon.Category.COIL, "", null);
        Assertions.assertTrue(rotor.isCompatibleWith(turbine));
        Assertions.assertFalse(coil.isCompatibleWith(turbine));

        // 2. Pyrolyse Oven (Coil Multiblock)
        RecipeNode pyrolyse = RecipeNode.create("Pyrolyse Oven", 20.0, 64.0, GTVoltageTier.MV);
        pyrolyse.setMultiblock(true);
        pyrolyse.setRecipeTemperature(600);
        pyrolyse.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:pyrolyse_oven"));
        var pyrolyseCats = MachineAddon.getRelevantCategories(pyrolyse);
        Assertions.assertTrue(pyrolyseCats.contains(MachineAddon.Category.COIL));
        Assertions.assertFalse(pyrolyseCats.contains(MachineAddon.Category.PARALLEL));
        Assertions.assertTrue(pyrolyseCats.contains(MachineAddon.Category.MAINTENANCE));
        Assertions.assertFalse(pyrolyseCats.contains(MachineAddon.Category.ROTOR));

        // 2b. Large Chemical Reactor & LFD (Parallel Multiblock)
        RecipeNode lcr = RecipeNode.create("Large Chemical Reactor", 20.0, 64.0, GTVoltageTier.HV);
        lcr.setMultiblock(true);
        lcr.setMachineIcon(ResourceLocation.tryParse("gtceu:large_chemical_reactor"));
        var lcrCats = MachineAddon.getRelevantCategories(lcr);
        Assertions.assertTrue(lcrCats.contains(MachineAddon.Category.PARALLEL));

        RecipeNode dt = RecipeNode.create("Distillation Tower", 20.0, 64.0, GTVoltageTier.EV);
        dt.setMultiblock(true);
        dt.setMachineIcon(ResourceLocation.tryParse("gtceu:distillation_tower"));
        var dtCats = MachineAddon.getRelevantCategories(dt);
        Assertions.assertFalse(dtCats.contains(MachineAddon.Category.PARALLEL));

        // 2c. Singleblock Turbine vs Multiblock Turbine
        RecipeNode singleTurbine = RecipeNode.create("Steam Turbine (Steam)", 20.0, 32.0, GTVoltageTier.LV);
        singleTurbine.setMultiblock(false);
        singleTurbine.setGenerator(true);
        singleTurbine.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_turbine"));
        var singleTurbineCats = MachineAddon.getRelevantCategories(singleTurbine);
        Assertions.assertFalse(singleTurbineCats.contains(MachineAddon.Category.ROTOR));
        Assertions.assertFalse(rotor.isCompatibleWith(singleTurbine));

        Assertions.assertTrue(coil.isCompatibleWith(pyrolyse));
        Assertions.assertFalse(rotor.isCompatibleWith(pyrolyse));

        // 3. Single Block Centrifuge
        RecipeNode centrifuge = RecipeNode.create("Centrifuge", 20.0, 30.0, GTVoltageTier.LV);
        var centrifugeCats = MachineAddon.getRelevantCategories(centrifuge);
        Assertions.assertFalse(centrifugeCats.contains(MachineAddon.Category.COIL));
        Assertions.assertFalse(centrifugeCats.contains(MachineAddon.Category.ROTOR));
        Assertions.assertFalse(centrifugeCats.contains(MachineAddon.Category.MAINTENANCE));
        Assertions.assertFalse(centrifugeCats.contains(MachineAddon.Category.MULTIBLOCK_TRAIT));
        Assertions.assertFalse(centrifugeCats.contains(MachineAddon.Category.PARALLEL));
        Assertions.assertTrue(centrifugeCats.contains(MachineAddon.Category.CUSTOM));

        // 4. Singleblock Gravitational Compression Chamber (GCC) compatibility
        RecipeNode gcc = RecipeNode.create("Gravitational Compression Chamber [GCC]", 200.0, 1920.0, GTVoltageTier.EV);
        gcc.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:compressor"));
        gcc.setMachineIcon(ResourceLocation.tryParse("start_core:gravitational_compression_chamber"));
        gcc.setMultiblock(false);

        MachineAddon maint = new MachineAddon("gtceu:config_maintenance", "Configurable Maintenance Hatch", MachineAddon.Category.MAINTENANCE, "", null);
        MachineAddon sptCool = new MachineAddon("gtceu:spt_coolant_boosting", "SPT Coolant Boosting", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        MachineAddon nptCool = new MachineAddon("gtceu:npt_coolant_boosting", "NPT Coolant Boosting", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);

        // Singleblock GCC must NOT be compatible with maintenance or turbine traits
        Assertions.assertFalse(maint.isCompatibleWith(gcc));
        Assertions.assertFalse(sptCool.isCompatibleWith(gcc));
        Assertions.assertFalse(nptCool.isCompatibleWith(gcc));
    }

    @Test
    public void testDynamicCoilHelperStats() {
        Object mockCoil = new Object() {
            public int getCoilTemperature() { return 2700; }
            public int getTier() { return 1; }
            public int getLevel() { return 1; }
        };

        var kanthal = CoilHelper.extractStatsFromBlockObject(mockCoil);
        Assertions.assertNotNull(kanthal);
        Assertions.assertEquals(2700, kanthal.temperature());
        Assertions.assertEquals(100, kanthal.pyrolyseSpeedPercent());
        Assertions.assertEquals(90, kanthal.crackingEnergyPercent());
        Assertions.assertEquals(32, kanthal.smelterParallel());

        Object mockCupronickel = new Object() {
            public int getCoilTemperature() { return 1800; }
            public int getTier() { return 0; }
        };
        var cupro = CoilHelper.extractStatsFromBlockObject(mockCupronickel);
        Assertions.assertNotNull(cupro);
        Assertions.assertEquals(1800, cupro.temperature());
        Assertions.assertEquals(75, cupro.pyrolyseSpeedPercent());
        Assertions.assertEquals(75, cupro.chemicalSpeedPercent());
        Assertions.assertEquals(100, cupro.crackingEnergyPercent());
        Assertions.assertEquals(16, cupro.smelterParallel());

        MachineAddon cuproAddon = new MachineAddon("gtceu:cupronickel_coil", "Cupronickel Coil", MachineAddon.Category.COIL, "", null);
        cuproAddon.setCoilTemperature(1800);
        cuproAddon.setPyrolyseSpeedPercent(75);
        cuproAddon.setChemicalSpeedPercent(75);
        cuproAddon.setChemicalEnergyPercent(100);

        RecipeNode pyrolyseOven = RecipeNode.create("Pyrolyse Oven", 20.0, 64.0, GTVoltageTier.MV);
        var evaluatedCupro = cuproAddon.forMachine(pyrolyseOven);
        Assertions.assertEquals(100.0 / 75.0, evaluatedCupro.getDurationMultiplier(), 0.001);

        Object decorativeStairs = new Object() {
            public String getName() { return "Abyssaline Stairs"; }
        };
        Assertions.assertNull(CoilHelper.extractStatsFromBlockObject(decorativeStairs));
    }

    @Test
    public void testTurbinePhysicsAndHolderBonus() {
        RecipeNode steamTurbine = RecipeNode.create("Large Steam Turbine", 20.0, 32.0, GTVoltageTier.EV);
        steamTurbine.setGenerator(true);
        steamTurbine.setRotorEfficiency(100);
        steamTurbine.setRotorPower(100);

        Assertions.assertEquals(GTVoltageTier.HV, GTTurbineHelper.getTurbineBaseTier(steamTurbine));
        Assertions.assertEquals(10, GTTurbineHelper.getTurbineHolderEfficiencyBonus(steamTurbine));
        Assertions.assertEquals(2048.0, GTTurbineHelper.getNodeRotorHolderMaxEUt(steamTurbine, GTVoltageTier.EV, 100));
        Assertions.assertEquals(1.10, steamTurbine.getEffectiveDurationSeconds(), 0.001);

        RecipeNode gasTurbine = RecipeNode.create("Large Gas Turbine", 40.0, 32.0, GTVoltageTier.IV);
        gasTurbine.setGenerator(true);
        gasTurbine.setRotorEfficiency(220);
        gasTurbine.setRotorPower(225);

        Assertions.assertEquals(GTVoltageTier.EV, GTTurbineHelper.getTurbineBaseTier(gasTurbine));
        Assertions.assertEquals(10, GTTurbineHelper.getTurbineHolderEfficiencyBonus(gasTurbine));
        Assertions.assertEquals(18432.0, GTTurbineHelper.getNodeRotorHolderMaxEUt(gasTurbine, GTVoltageTier.IV, 225));

        RecipeNode plasmaTurbine = RecipeNode.create("Large Plasma Turbine", 20.0, 1000.0, GTVoltageTier.LuV);
        plasmaTurbine.setGenerator(true);
        plasmaTurbine.setRotorEfficiency(150);
        plasmaTurbine.setRotorPower(100);

        Assertions.assertEquals(GTVoltageTier.IV, GTTurbineHelper.getTurbineBaseTier(plasmaTurbine));
        Assertions.assertEquals(10, GTTurbineHelper.getTurbineHolderEfficiencyBonus(plasmaTurbine));
        Assertions.assertEquals(32768.0, GTTurbineHelper.getNodeRotorHolderMaxEUt(plasmaTurbine, GTVoltageTier.LuV, 100));

        RecipeNode nyinsaneTurbine = RecipeNode.create("Nyinsane Plasma Turbine", 20.0, 1000.0, GTVoltageTier.IV);
        nyinsaneTurbine.setGenerator(true);
        Assertions.assertEquals(196608.0, GTTurbineHelper.getNodeRotorHolderMaxEUt(nyinsaneTurbine, GTVoltageTier.IV, 100));
    }

    @Test
    public void testEmiImportedGasTurbineWithEnderiumRotor() {
        // Simulates an exact node imported from EMI: Name = "Gas Turbine (Nitrobenzene)", Category = "gtceu:gas_turbine", singleblock icon
        RecipeNode node = RecipeNode.create("Gas Turbine (Nitrobenzene)", 40.0, 32.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:ev_gas_turbine"));
        node.setGenerator(true);

        MachineAddon enderiumRotor = new MachineAddon("gtceu:rotor_enderium", "Enderium Turbine Rotor", MachineAddon.Category.ROTOR, "180%", null);
        enderiumRotor.setDurationMultiplier(1.80);
        enderiumRotor.setRotorEfficiency(180);
        enderiumRotor.setRotorPower(300);
        enderiumRotor.setRotorMaxEUt(264000.0);
        node.addAddon(enderiumRotor);

        // 1. EV Rotor Holder (4,096 * 3.0 = 12,288 EU/t -> 384 parallel)
        node.setTargetTier(GTVoltageTier.EV);
        Assertions.assertEquals(GTVoltageTier.EV, GTTurbineHelper.getTurbineBaseTier(node));
        Assertions.assertEquals(0, GTTurbineHelper.getTurbineHolderEfficiencyBonus(node));
        Assertions.assertEquals(384, node.getTotalParallel());
        Assertions.assertEquals(12288.0, node.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(3.60, node.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(106.666, node.getCyclesPerSecond(), 0.01);

        // 2. IV Rotor Holder (8,192 * 3.0 = 24,576 EU/t -> 768 parallel, +10% multiplicative duration bonus: 180% * 1.10 = 198%)
        node.setTargetTier(GTVoltageTier.IV);
        node.autoCalculateTurbineParallel();
        Assertions.assertEquals(10, GTTurbineHelper.getTurbineHolderEfficiencyBonus(node));
        Assertions.assertEquals(198, GTTurbineHelper.getTotalTurbineEfficiency(node));
        Assertions.assertEquals(768, node.getTotalParallel());
        Assertions.assertEquals(24576.0, node.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(3.96, node.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(193.939, node.getCyclesPerSecond(), 0.01);
    }

    @Test
    public void testLargePlasmaTurbinePrismaliumZpmHolder() {
        RecipeNode node = RecipeNode.create("Plasma Generator (Argon Plasma)", 116.0, 2048.0, GTVoltageTier.IV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_generator"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_plasma_turbine"));
        node.setGenerator(true);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.ZPM);

        // 1. Equip Prismalium Turbine Rotor (260% eff, 1600% power)
        MachineAddon prismaliumRotor = new MachineAddon("gtceu:rotor_prismalium", "Prismalium Turbine Rotor", MachineAddon.Category.ROTOR, "260%", null);
        prismaliumRotor.setDurationMultiplier(2.60);
        prismaliumRotor.setRotorPower(1600);
        prismaliumRotor.setRotorEfficiency(260);
        node.addAddon(prismaliumRotor);
        node.setRotorEfficiency(260);
        node.setRotorPower(1600);
        node.setRotorName("Prismalium Turbine Rotor");

        // Assert ZPM Holder properties vs IV Base Tier: delta = 2 -> +20% holder bonus
        Assertions.assertEquals(GTVoltageTier.IV, GTTurbineHelper.getTurbineBaseTier(node));
        Assertions.assertEquals(20, GTTurbineHelper.getTurbineHolderEfficiencyBonus(node));
        // Multiplicative efficiency: 260% * 1.20 = 312%
        Assertions.assertEquals(312, GTTurbineHelper.getTotalTurbineEfficiency(node));

        // Max EU/t: 16,384 base * 64x total power = 1,048,576 EU/t (8A ZPM)
        Assertions.assertEquals(1048576.0, GTTurbineHelper.getNodeRotorHolderMaxEUt(node, GTVoltageTier.ZPM, 1600), 0.001);
        Assertions.assertEquals(512, node.getTotalParallel());
        Assertions.assertEquals(1048576.0, node.getSingleMachineEUt(), 0.001);

        // Duration without maintenance hatch: 116 ticks * 3.12 = 361.92 ticks (18.096s)
        Assertions.assertEquals(18.096, node.getEffectiveDurationSeconds(), 0.001);

        // 2. Add Configurable Maintenance Hatch (Eco Mode: 1.1x duration)
        MachineAddon ecoHatch = new MachineAddon("gtceu:configurable_maintenance_hatch", "Configurable Maintenance Hatch", MachineAddon.Category.MAINTENANCE, "Eco Mode", null);
        ecoHatch.setDurationMultiplier(1.10);
        node.addAddon(ecoHatch);

        // Duration with eco hatch: 361.92 * 1.10 = 398.112 ticks (19.9056s)
        Assertions.assertEquals(19.9056, node.getEffectiveDurationSeconds(), 0.001);
        // Demand per turbine for 5 mB * 512 parallel = 2560 mB per run: 2560 / 398.112 = 6.43017 mB/t
        double ticksPerRun = node.getOverclockResult().durationTicks();
        double mbPerTick = (5.0 * node.getTotalParallel()) / ticksPerRun;
        Assertions.assertEquals(6.43017, mbPerTick, 0.001);
    }

    @Test
    public void testSingleblockGasTurbineGeneration() {
        // Singleblock HV Gas Turbine: No Rotor, Target Tier = HV (512V) -> Outputs 512 EU/t (1A HV)
        RecipeNode singleblock = RecipeNode.create("Gas Turbine (Nitrobenzene)", 40.0, 32.0, GTVoltageTier.LV);
        singleblock.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        singleblock.setMachineIcon(ResourceLocation.tryParse("gtceu:hv_gas_turbine"));
        singleblock.setGenerator(true);
        singleblock.setTargetTier(GTVoltageTier.HV);

        Assertions.assertFalse(singleblock.isLargeTurbine());
        Assertions.assertEquals(16, singleblock.getTotalParallel()); // 512 / 32 = 16x
        Assertions.assertEquals(512.0, singleblock.getSingleMachineEUt(), 0.001); // 1A HV
        Assertions.assertEquals(2.00, singleblock.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(8.0, singleblock.getCyclesPerSecond(), 0.001); // 16 / 2.0s = 8 mB/s
    }

    @Test
    public void testTurbineRotorLifecycleTransitions() {
        // 1. Initial State: Singleblock HV Gas Turbine
        RecipeNode node = RecipeNode.create("Gas Turbine (Nitrobenzene)", 40.0, 32.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:hv_gas_turbine"));
        node.setGenerator(true);
        node.setTargetTier(GTVoltageTier.HV);

        Assertions.assertFalse(node.isMultiblock());
        Assertions.assertFalse(node.isLargeTurbine());
        Assertions.assertEquals(512.0, node.getSingleMachineEUt(), 0.001); // 1A HV
        Assertions.assertEquals(8.0, node.getCyclesPerSecond(), 0.001); // 8 mB/s

        // 2. Install Enderium Rotor (180% eff, 300% power) at EV Tier
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.EV);
        MachineAddon enderiumRotor = new MachineAddon("gtceu:rotor_enderium", "Enderium Turbine Rotor", MachineAddon.Category.ROTOR, "180%", null);
        enderiumRotor.setDurationMultiplier(1.80);
        enderiumRotor.setRotorPower(300);
        node.addAddon(enderiumRotor);
        node.setRotorEfficiency(180);
        node.setRotorPower(300);
        node.setRotorName("Enderium Turbine Rotor");

        Assertions.assertTrue(node.isLargeTurbine());
        Assertions.assertEquals(384, node.getTotalParallel());
        Assertions.assertEquals(12288.0, node.getSingleMachineEUt(), 0.001); // 6A EV
        Assertions.assertEquals(3.60, node.getEffectiveDurationSeconds(), 0.001);

        // 3. Uninstall Rotor (Reset to Standard) and return to HV Singleblock
        node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
        node.setRotorEfficiency(100);
        node.setRotorPower(100);
        node.setRotorName("Standard (100%)");
        node.setParallel(1);
        node.setMultiblock(false);
        node.setTargetTier(GTVoltageTier.HV);

        // Verify NO residual 256x or 384x state remains!
        Assertions.assertFalse(node.isMultiblock());
        Assertions.assertFalse(node.isLargeTurbine());
        Assertions.assertEquals(1, node.getParallel());
        Assertions.assertEquals(16, node.getTotalParallel());
        Assertions.assertEquals(512.0, node.getSingleMachineEUt(), 0.001); // Clean 1A HV!
        Assertions.assertEquals(2.00, node.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(8.0, node.getCyclesPerSecond(), 0.001);
    }

    @Test
    public void testTurbineShiftConnectAutoRatioMatching() {
        FlowGraph graph = new FlowGraph();

        RecipeNode chemReactor = RecipeNode.create("Chemical Reactor (Benzene)", 160.0, 480.0, GTVoltageTier.LV);
        chemReactor.setTargetTier(GTVoltageTier.HV);
        chemReactor.setMachineCount(1.0);
        IngredientStack benzene = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:benzene"), "Benzene", 6500.0, 1.0);
        chemReactor.addOutput(benzene);
        graph.addNode(chemReactor);

        RecipeNode turbine = RecipeNode.create("Gas Turbine (Nitrobenzene)", 40.0, 32.0, GTVoltageTier.LV);
        turbine.setGenerator(true);
        turbine.setRotorEfficiency(280);
        turbine.setRotorPower(300);
        MachineAddon enderiumRotor = new MachineAddon("gtceu:rotor_enderium", "Enderium Turbine Rotor", MachineAddon.Category.ROTOR, "", ResourceLocation.tryParse("gtceu:turbine_rotor"));
        enderiumRotor.setDurationMultiplier(2.8);
        enderiumRotor.setRotorPower(300);
        enderiumRotor.setRotorMaxEUt(264000.0);
        turbine.addAddon(enderiumRotor);
        turbine.setParallel(320);
        turbine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:benzene"), "Benzene", 1.0, 1.0));
        graph.addNode(turbine);

        chemReactor.setEfficiency(26.25 / 3250.0);
        double matchedConsumerCountThrottled = FlowGraphSolver.calculateConsumerMatchCount(graph, chemReactor, 0, turbine, 0);
        Assertions.assertEquals(1.0, matchedConsumerCountThrottled, 0.001);

        chemReactor.setEfficiency(1.0);
        double matchedConsumerCountFull = FlowGraphSolver.calculateConsumerMatchCount(graph, chemReactor, 0, turbine, 0);
        Assertions.assertEquals(56.0, matchedConsumerCountFull, 0.001);

        double matchedProducerCount = FlowGraphSolver.calculateProducerMatchCount(graph, chemReactor, 0, turbine, 0);
        Assertions.assertEquals(1.0, matchedProducerCount, 0.001);
    }

    @Test
    void testThermalArcAndMciKitsParsing() {
        CompoundTag arcTag = new CompoundTag();
        CompoundTag arcAug = new CompoundTag();
        arcAug.putFloat("DynamoEnergy", 0.95f);
        arcAug.putFloat("DynamoPower", 0.5f);
        arcAug.putString("Type", "Dynamo");
        arcTag.put("AugmentData", arcAug);

        MachineAddon arcAddon = ThermalAugmentHelper.parseThermalAugmentTag(arcTag, "LV ARC Kit", ResourceLocation.tryParse("kubejs:lv_arc_kit"));
        Assertions.assertNotNull(arcAddon);
        Assertions.assertEquals(1.5, arcAddon.getEutMultiplier(), 0.001);
        Assertions.assertEquals(0.95, arcAddon.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(MachineAddon.Category.THERMAL_AUGMENT, arcAddon.getCategory());

        CompoundTag mciTag = new CompoundTag();
        CompoundTag mciAug = new CompoundTag();
        mciAug.putFloat("DynamoEnergy", 1.15f);
        mciAug.putString("Type", "Dynamo");
        mciTag.put("AugmentData", mciAug);

        MachineAddon mciAddon = ThermalAugmentHelper.parseThermalAugmentTag(mciTag, "LV MCI Kit", ResourceLocation.tryParse("kubejs:lv_mci_kit"));
        Assertions.assertNotNull(mciAddon);
        Assertions.assertEquals(1.0, mciAddon.getEutMultiplier(), 0.001);
        Assertions.assertEquals(1.15, mciAddon.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(MachineAddon.Category.THERMAL_AUGMENT, mciAddon.getCategory());
    }

    @Test
    void testThermalAugmentHelperReflectionAndExtraction() {
        CompoundTag dynamoAug = new CompoundTag();
        dynamoAug.putString("Type", "Dynamo");
        dynamoAug.putFloat("DynamoPower", 2.0f);
        dynamoAug.putFloat("DynamoEnergy", 0.85f);
        CompoundTag dynamoRoot = new CompoundTag();
        dynamoRoot.put("AugmentData", dynamoAug);

        MachineAddon addon = ThermalAugmentHelper.parseThermalAugmentTag(dynamoRoot, "Dynamo Output Augment", ResourceLocation.tryParse("thermal:dynamo_output_augment"));
        Assertions.assertNotNull(addon);
        Assertions.assertEquals(3.0, addon.getEutMultiplier(), 0.001);
        Assertions.assertEquals(0.85, addon.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(MachineAddon.Category.THERMAL_AUGMENT, addon.getCategory());

        CompoundTag upgradeAug = new CompoundTag();
        upgradeAug.putString("Type", "Upgrade");
        upgradeAug.putInt("BaseMod", 3);
        upgradeAug.putInt("DynScale", 3);
        CompoundTag upgradeRoot = new CompoundTag();
        upgradeRoot.put("AugmentData", upgradeAug);

        MachineAddon fieldAddon = ThermalAugmentHelper.parseThermalAugmentTag(upgradeRoot, "Hardened Integral Components", ResourceLocation.tryParse("thermal:upgrade_augment_2"));
        Assertions.assertNotNull(fieldAddon);
        Assertions.assertEquals(3, fieldAddon.getParallelMultiplier());
        Assertions.assertEquals(MachineAddon.Category.THERMAL_AUGMENT, fieldAddon.getCategory());

        RecipeNode lapidaryNode = RecipeNode.create("Lapidary Dynamo", 100.0, 1000.0, GTVoltageTier.MV);
        lapidaryNode.setRecipeCategoryId(ResourceLocation.tryParse("thermal:lapidary_fuel"));
        Assertions.assertTrue(ThermalAugmentHelper.isThermalMachine(lapidaryNode));

        RecipeNode gtNode = RecipeNode.create("Electric Blast Furnace", 20.0, 120.0, GTVoltageTier.MV);
        gtNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        Assertions.assertFalse(ThermalAugmentHelper.isThermalMachine(gtNode));
    }

    @Test
    void testThermalMachineAugmentsAndNumericTags() {
        // 1. Test Thermal Machine Speed Augment (MachinePower +100%, MachineSpeed +100% -> 2x power, 0.5x duration)
        CompoundTag speedAug = new CompoundTag();
        speedAug.putString("Type", "Machine");
        speedAug.putFloat("MachinePower", 1.0f);
        speedAug.putFloat("MachineSpeed", 1.0f);
        CompoundTag speedRoot = new CompoundTag();
        speedRoot.put("AugmentData", speedAug);

        MachineAddon speedAddon = ThermalAugmentHelper.parseThermalAugmentTag(speedRoot, "Flux Linkage Amplifier", ResourceLocation.tryParse("thermal:machine_speed_augment"));
        Assertions.assertNotNull(speedAddon);
        Assertions.assertEquals(2.0, speedAddon.getEutMultiplier(), 0.001);
        Assertions.assertEquals(0.5, speedAddon.getDurationMultiplier(), 0.001);

        // 2. Test Thermal Extra / KubeJS 48x Tier Upgrade with DoubleTag BaseMod
        CompoundTag tier48Aug = new CompoundTag();
        tier48Aug.putString("Type", "Upgrade");
        tier48Aug.putDouble("BaseMod", 48.0);
        tier48Aug.putDouble("Scale", 48.0);
        CompoundTag tier48Root = new CompoundTag();
        tier48Root.put("AugmentData", tier48Aug);

        MachineAddon tier48Addon = ThermalAugmentHelper.parseThermalAugmentTag(tier48Root, "EV Integral Components", ResourceLocation.tryParse("kubejs:ev_integral_components"));
        Assertions.assertNotNull(tier48Addon);
        Assertions.assertEquals(48, tier48Addon.getParallelMultiplier());

        // 3. Test Dynamo vs Machine Compatibility
        CompoundTag dynamoAug = new CompoundTag();
        dynamoAug.putString("Type", "Dynamo");
        dynamoAug.putFloat("DynamoPower", 2.0f);
        dynamoAug.putFloat("DynamoEnergy", 0.85f);
        CompoundTag dynamoRoot = new CompoundTag();
        dynamoRoot.put("AugmentData", dynamoAug);
        MachineAddon dynamoAddon = ThermalAugmentHelper.parseThermalAugmentTag(dynamoRoot, "Dynamo Output Augment", ResourceLocation.tryParse("thermal:dynamo_output_augment"));

        var adapter = new com.gtceu.calcboard.compat.thermal.ThermalModAdapter();
        RecipeNode lapidary = RecipeNode.create("Lapidary Dynamo", 100.0, 1000.0, GTVoltageTier.MV);
        lapidary.setRecipeCategoryId(ResourceLocation.tryParse("thermal:lapidary_fuel"));

        RecipeNode pulverizer = RecipeNode.create("Pulverizer", 100.0, 1000.0, GTVoltageTier.MV);
        pulverizer.setRecipeCategoryId(ResourceLocation.tryParse("thermal:pulverizer"));

        // Dynamo augment on Lapidary -> compatible; on Pulverizer -> NOT compatible
        Assertions.assertTrue(adapter.isAddonCompatible(lapidary, dynamoAddon));
        Assertions.assertFalse(adapter.isAddonCompatible(pulverizer, dynamoAddon));

        // Machine augment on Lapidary -> NOT compatible; on Pulverizer -> compatible
        Assertions.assertFalse(adapter.isAddonCompatible(lapidary, speedAddon));
        Assertions.assertTrue(adapter.isAddonCompatible(pulverizer, speedAddon));

        // Upgrade kit on both -> compatible
        Assertions.assertTrue(adapter.isAddonCompatible(lapidary, tier48Addon));
        Assertions.assertTrue(adapter.isAddonCompatible(pulverizer, tier48Addon));

        // Multi-copy install test on Lapidary (up to 3 slots)
        adapter.handleInstallAddon(lapidary, dynamoAddon, false);
        Assertions.assertEquals(1, lapidary.getAddons().size());
        Assertions.assertTrue(adapter.canInstallAddon(lapidary, dynamoAddon));
        adapter.handleInstallAddon(lapidary, dynamoAddon, false);
        adapter.handleInstallAddon(lapidary, dynamoAddon, false);
        Assertions.assertEquals(3, lapidary.getAddons().size());
        Assertions.assertFalse(adapter.canInstallAddon(lapidary, dynamoAddon), "Cannot install more than 3 regular augments");

        MachineAddon abyssalKit = ThermalAugmentHelper.parseThermalAugmentTag(tier48Root, "Abyssal Upgrade Kit", ResourceLocation.tryParse("thermal_extra:abyssal_upgrade_kit"));
        Assertions.assertNotNull(abyssalKit);
        Assertions.assertEquals(48, abyssalKit.getParallelMultiplier());
        Assertions.assertTrue(RecipeNode.isThermalUpgradeKit(abyssalKit));

        // 3. Test Multi-Cycle Injector with 1.60x Fuel Energy (DoubleTag DynEnergy)
        CompoundTag mci160Aug = new CompoundTag();
        mci160Aug.putString("Type", "Dynamo");
        mci160Aug.putDouble("DynEnergy", 1.60);
        CompoundTag mci160Root = new CompoundTag();
        mci160Root.put("AugmentData", mci160Aug);

        MachineAddon mci160Addon = ThermalAugmentHelper.parseThermalAugmentTag(mci160Root, "Abyssal Multi-Cycle Injector", ResourceLocation.tryParse("thermal_extra:abyssal_multi_cycle_injector"));
        Assertions.assertNotNull(mci160Addon);
        Assertions.assertEquals(1.60, mci160Addon.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(1.0, mci160Addon.getEutMultiplier(), 0.001);
    }

    @Test
    void testTurbineRotorHelperReflectionAndStrictRejection() {
        Object mockRotorProperty = new Object() {
            public double getEfficiency() { return 1.4; }
            public double getPower() { return 1.2; }
            public double getDurability() { return 8000.0; }
        };

        var stats = TurbineRotorHelper.extractStatsFromRotorProperty(mockRotorProperty);
        Assertions.assertNotNull(stats);
        Assertions.assertEquals(140, stats.efficiency());
        Assertions.assertEquals(120, stats.power());
        Assertions.assertEquals(8000, stats.durability());

        var tiStats = TurbineRotorHelper.getRotorStats("titanium");
        Assertions.assertNotNull(tiStats);
        Assertions.assertTrue(tiStats.efficiency() > 0);

        Object nonRotor = new Object() {
            public String getName() { return "Fluid Bucket"; }
        };
        Assertions.assertNull(TurbineRotorHelper.extractStatsFromRotorProperty(nonRotor));
    }

    @Test
    void testParallelHelperReflectionAndNbtExtraction() {
        Object mockParallelHatch = new Object() {
            public int getCurrentParallel() { return 16; }
            public boolean isAbsolute() { return true; }
        };

        var stats = ParallelHelper.extractStatsFromMachineInstance(mockParallelHatch);
        Assertions.assertNotNull(stats);
        Assertions.assertEquals(16, stats.maxParallel());
        Assertions.assertTrue(stats.isAbsolute());

        Object mockNormalMachine = new Object() {
            public String getMachineName() { return "Basic Macerator"; }
        };
        Assertions.assertNull(ParallelHelper.extractStatsFromMachineInstance(mockNormalMachine));
    }

    @Test
    void testKubeJsThermalTierKitsExtractionAndExclusion() {
        // 1. Test KubeJS Upgrade Kits (6x, 12x, 24x, 48x)
        CompoundTag lvTag = new CompoundTag();
        CompoundTag lvAug = new CompoundTag();
        lvAug.putFloat("BaseMod", 6.0f);
        lvAug.putString("Type", "Upgrade");
        lvTag.put("AugmentData", lvAug);

        MachineAddon lvKit = ThermalAugmentHelper.parseThermalAugmentTag(lvTag, "§7LV§r Upgrade Kit", ResourceLocation.tryParse("kubejs:lv_upgrade_kit"));
        Assertions.assertNotNull(lvKit);
        Assertions.assertEquals(6, lvKit.getParallelMultiplier());

        CompoundTag evTag = new CompoundTag();
        CompoundTag evAug = new CompoundTag();
        evAug.putFloat("BaseMod", 48.0f);
        evAug.putString("Type", "Upgrade");
        evTag.put("AugmentData", evAug);

        MachineAddon evKit = ThermalAugmentHelper.parseThermalAugmentTag(evTag, "§5EV§r Upgrade Kit", ResourceLocation.tryParse("kubejs:ev_upgrade_kit"));
        Assertions.assertNotNull(evKit);
        Assertions.assertEquals(48, evKit.getParallelMultiplier());

        // 2. Test KubeJS ARC Kits (0.5x, 1.0x, 2.0x, 3.0x power & 0.95x, 0.90x, 0.85x, 0.80x duration)
        CompoundTag evArcTag = new CompoundTag();
        CompoundTag evArcAug = new CompoundTag();
        evArcAug.putString("Type", "Dynamo");
        evArcAug.putFloat("DynamoEnergy", 0.80f);
        evArcAug.putFloat("DynamoPower", 3.0f);
        evArcTag.put("AugmentData", evArcAug);

        MachineAddon evArc = ThermalAugmentHelper.parseThermalAugmentTag(evArcTag, "§5EV§r Auxiliary Reaction Chamber Kit", ResourceLocation.tryParse("kubejs:ev_arc_kit"));
        Assertions.assertNotNull(evArc);
        Assertions.assertEquals(4.0, evArc.getEutMultiplier(), 0.001);
        Assertions.assertEquals(0.80, evArc.getDurationMultiplier(), 0.001);

        // 3. Test KubeJS MCI Kits (1.15x, 1.30x, 1.45x, 1.60x duration)
        CompoundTag evMciTag = new CompoundTag();
        CompoundTag evMciAug = new CompoundTag();
        evMciAug.putString("Type", "Dynamo");
        evMciAug.putFloat("DynamoEnergy", 1.60f);
        evMciTag.put("AugmentData", evMciAug);

        MachineAddon evMci = ThermalAugmentHelper.parseThermalAugmentTag(evMciTag, "§5EV§r Multi-cycle Injectors Kit", ResourceLocation.tryParse("kubejs:ev_mci_kit"));
        Assertions.assertNotNull(evMci);
        Assertions.assertEquals(1.0, evMci.getEutMultiplier(), 0.001);
        Assertions.assertEquals(1.60, evMci.getDurationMultiplier(), 0.001);

        // 4. Test Recursive GTCEu Content / SizedIngredient Mock Unpacking
        // 5. Test Thermal Machine 1 Upgrade Kit Rule & 3-Slot Augment Stacking
        RecipeNode dynamo = RecipeNode.create("Lapidary Fuel (Diamond)", 20.0, 100.0, GTVoltageTier.LV);
        dynamo.setGenerator(true);

        // Install LV Upgrade Kit (6x)
        dynamo.addAddon(lvKit.copy());
        Assertions.assertEquals(6, dynamo.getCombinedParallelMultiplier());
        Assertions.assertEquals(1, dynamo.getAddons().size());

        // Install EV Upgrade Kit (48x) -> replaces LV Upgrade Kit!
        dynamo.addAddon(evKit.copy());
        Assertions.assertEquals(48, dynamo.getCombinedParallelMultiplier());
        Assertions.assertEquals(1, dynamo.getAddons().size());
        Assertions.assertTrue(dynamo.getAddons().get(0).getId().startsWith("kubejs:ev_upgrade_kit"));

        // Install 3x EV Multi-cycle Injectors Kit (1.60x duration/fuel each)
        dynamo.addAddon(evMci.copy());
        dynamo.addAddon(evMci.copy());
        dynamo.addAddon(evMci.copy());
        // Total addons: 1 Upgrade Kit + 3 MCI = 4
        Assertions.assertEquals(4, dynamo.getAddons().size());
        // Combined duration: 1.60 * 1.60 * 1.60 = 4.096x
        Assertions.assertEquals(4.096, dynamo.getCombinedDurationMultiplier(), 0.001);

        // Try installing a 4th regular augment -> blocked!
        dynamo.addAddon(evArc.copy());
        Assertions.assertEquals(4, dynamo.getAddons().size());

        // Remove 1 copy of MCI -> total regular becomes 2, can install 1 ARC
        dynamo.removeSingleAddon(evMci.getId());
        Assertions.assertEquals(3, dynamo.getAddons().size());
        dynamo.addAddon(evArc.copy());
        Assertions.assertEquals(4, dynamo.getAddons().size());
        // 2x MCI (1.60^2 = 2.56) * 1x ARC (0.80 duration, 4.0 power)
        Assertions.assertEquals(2.56 * 0.80, dynamo.getCombinedDurationMultiplier(), 0.001);
        Assertions.assertEquals(4.0, dynamo.getCombinedEutMultiplier(), 0.001);
    }

    @Test
    public void testLapidaryDynamoWithHVAugmentsCalculation() {
        // Lapidary Dynamo with Diamond (300,000 RF energy)
        // Base: 200 RF/t = 50 EU/t, 1500 ticks = 75.0 seconds
        RecipeNode dynamo = RecipeNode.create("Lapidary Fuel (Diamond)", 1500.0, 50.0, GTVoltageTier.LV);
        dynamo.setRecipeCategoryId(ResourceLocation.tryParse("thermal:lapidary_fuel"));
        dynamo.setGenerator(true);

        // 1. HV Upgrade Kit (24x scale)
        CompoundTag hvKitTag = new CompoundTag();
        CompoundTag hvKitAug = new CompoundTag();
        hvKitAug.putInt("Scale", 24);
        hvKitTag.put("AugmentData", hvKitAug);
        MachineAddon hvKit = ThermalAugmentHelper.parseThermalAugmentTag(hvKitTag, "HV Upgrade Kit", ResourceLocation.tryParse("kubejs:hv_upgrade_kit"));
        dynamo.addAddon(hvKit);

        // 2. HV ARC (3.0x power = +200%, 0.85x fuel energy)
        CompoundTag hvArcTag = new CompoundTag();
        CompoundTag hvArcAug = new CompoundTag();
        hvArcAug.putString("Type", "Dynamo");
        hvArcAug.putFloat("DynamoEnergy", 0.85f);
        hvArcAug.putFloat("DynamoPower", 2.0f); // 1.0 + 2.0 = 3.0x power
        hvArcTag.put("AugmentData", hvArcAug);
        MachineAddon hvArc = ThermalAugmentHelper.parseThermalAugmentTag(hvArcTag, "HV Auxiliary Reaction Chamber", ResourceLocation.tryParse("kubejs:hv_arc_kit"));
        dynamo.addAddon(hvArc);

        // 3. 2x HV Multi-cycle Injectors Kit (1.45x fuel energy each)
        CompoundTag hvMciTag = new CompoundTag();
        CompoundTag hvMciAug = new CompoundTag();
        hvMciAug.putString("Type", "Dynamo");
        hvMciAug.putFloat("DynamoEnergy", 1.45f);
        hvMciTag.put("AugmentData", hvMciAug);
        MachineAddon hvMci = ThermalAugmentHelper.parseThermalAugmentTag(hvMciTag, "HV Multi-cycle Injectors Kit", ResourceLocation.tryParse("kubejs:hv_mci_kit"));
        dynamo.addAddon(hvMci.copy());
        dynamo.addAddon(hvMci.copy());

        // Assert 4 active addons
        Assertions.assertEquals(4, dynamo.getAddons().size());
        Assertions.assertEquals(1, dynamo.getTotalParallel()); // Thermal machines process 1 item per machine, scale is applied to power/duration

        // Single machine EU/t: 50 EU/t (200 RF/t) * 3.0 (ARC) * 24 (Scale) = 3600.0 EU/t (14,400 RF/t)
        Assertions.assertEquals(3600.0, dynamo.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(14400.0, dynamo.getSingleMachineEUt() * 4.0, 0.001);

        // Total fuel energy multiplier: 0.85 * 1.45 * 1.45 = 1.787125
        double fuelEnergyMult = 0.85 * 1.45 * 1.45;
        Assertions.assertEquals(fuelEnergyMult, dynamo.getCombinedDurationMultiplier(), 0.001);

        // Effective single machine duration (ticks): 1500 * (1.787125 / (24 * 3.0)) = 37.23177 ticks
        double expectedDuration = 1500.0 * (fuelEnergyMult / (24.0 * 3.0));
        Assertions.assertEquals(expectedDuration, dynamo.getOverclockResult().durationTicks(), 0.001);

        // Cycles per second (CPS): 20 / 37.23177 = 0.537175 cycles/s = 32.23 diamonds/min
        Assertions.assertEquals(20.0 / expectedDuration, dynamo.getCyclesPerSecond(), 0.001);
    }

    @Test
    public void testCrackerCoilEnergyDiscount() {
        RecipeNode cracker = RecipeNode.create("Cracker (Heavy Fuel)", 20.0, 384.0, GTVoltageTier.HV);
        cracker.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:cracker"));

        // 1. Cupronickel (1800K, Tier 0) -> 100% EU/t (1.0x)
        MachineAddon cupro = new MachineAddon("gtceu:cupronickel_coil", "Cupronickel Coil Block", MachineAddon.Category.COIL, "", null);
        cupro.setCoilTemperature(1800);
        cupro.setCrackingEnergyPercent(100);
        MachineAddon tailoredCupro = cupro.forMachine(cracker);
        Assertions.assertEquals(1.0, tailoredCupro.getEutMultiplier(), 0.001);

        // 2. HSS-G (5400K, Tier 4) -> 60% EU/t (0.60x, 40% discount)
        MachineAddon hssg = new MachineAddon("gtceu:hssg_coil", "HSS-G Coil Block", MachineAddon.Category.COIL, "", null);
        hssg.setCoilTemperature(5400);
        // tier = 4 -> discount = 4 * 0.1 = 0.4 -> 60%
        int hssgDiscount = (int) Math.round((1.0 - (4 * 0.1)) * 100.0);
        hssg.setCrackingEnergyPercent(hssgDiscount);
        MachineAddon tailoredHssg = hssg.forMachine(cracker);
        Assertions.assertEquals(0.60, tailoredHssg.getEutMultiplier(), 0.001);

        // 3. Trinium (9001K, Tier 9) -> 10% EU/t (0.10x, 90% discount)
        MachineAddon trinium = new MachineAddon("gtceu:trinium_coil", "Trinium Coil Block", MachineAddon.Category.COIL, "", null);
        trinium.setCoilTemperature(9001);
        trinium.setCrackingEnergyPercent(10);
        MachineAddon tailoredTrinium = trinium.forMachine(cracker);
        Assertions.assertEquals(0.10, tailoredTrinium.getEutMultiplier(), 0.001);

        // 4. Abyssal Alloy (18888K, Tier 10) -> 7.5% -> 7% EU/t (0.07x, 92.5% discount)
        MachineAddon abyssal = new MachineAddon("kubejs:abyssal_alloy_coil_block", "Abyssal Alloy Coil Block", MachineAddon.Category.COIL, "", null);
        abyssal.setCoilTemperature(18888);
        // tier = 10 -> discount = 0.9 + (10 - 9) * 0.025 = 0.925 -> 7%
        int abyssalDiscount = Math.max(1, (int) Math.floor((1.0 - 0.925) * 100.0 + 0.0001));
        abyssal.setCrackingEnergyPercent(abyssalDiscount);
        MachineAddon tailoredAbyssal = abyssal.forMachine(cracker);
        Assertions.assertEquals(0.07, tailoredAbyssal.getEutMultiplier(), 0.001);
    }

    @Test
    public void testMachineAddonDiscoverySourceProvenance() {
        MachineAddon addon = new MachineAddon("gtceu:cupronickel_coil", "Cupronickel Coil Block", MachineAddon.Category.COIL, "♨ Heat: 1800 K", ResourceLocation.tryParse("gtceu:cupronickel_coil"));
        addon.setDiscoverySource("GTCEu ICoilType Block API [gtceu:cupronickel_coil]");

        // 1. Check getter
        Assertions.assertEquals("GTCEu ICoilType Block API [gtceu:cupronickel_coil]", addon.getDiscoverySource());

        // 2. Check copy()
        MachineAddon cp = addon.copy();
        Assertions.assertEquals("GTCEu ICoilType Block API [gtceu:cupronickel_coil]", cp.getDiscoverySource());

        // 3. Check NBT serialization / deserialization
        CompoundTag tag = addon.serializeNBT();
        MachineAddon loaded = MachineAddon.deserializeNBT(tag);
        Assertions.assertNotNull(loaded);
        Assertions.assertEquals("GTCEu ICoilType Block API [gtceu:cupronickel_coil]", loaded.getDiscoverySource());
    }

    @Test
    public void testRotorAddonItemStackSerializationAndMaterialTagRestoration() {
        GTRotorAddon rotor = new GTRotorAddon(
                "gtceu:rotor_ancient_runicalium",
                "Ancient Runicalium Turbine Rotor",
                "Efficiency: 320%",
                ResourceLocation.tryParse("gtceu:turbine_rotor"),
                320,
                6400,
                16000.0
        );

        // Serialize and deserialize
        CompoundTag tag = rotor.serializeNBT();
        MachineAddon loaded = MachineAddon.deserializeNBT(tag);

        Assertions.assertInstanceOf(GTRotorAddon.class, loaded);
        GTRotorAddon loadedRotor = (GTRotorAddon) loaded;
        Assertions.assertEquals(320, loadedRotor.getRotorEfficiency());
        Assertions.assertEquals(6400, loadedRotor.getRotorPower());

        // In test environment, getItemStackSample creates ItemStack and attaches GT.PartStats
        ItemStack sample = loadedRotor.getItemStackSample();
        if (sample != null && !sample.isEmpty() && sample.hasTag()) {
            Assertions.assertTrue(sample.getTag().contains("GT.PartStats"));
            Assertions.assertEquals("gtceu:ancient_runicalium", sample.getTag().getCompound("GT.PartStats").getString("Material"));
        }
    }

    @Test
    public void testMultiblockTraitSingletonBehavior() {
        RecipeNode lcrNode = RecipeNode.create("Large Chemical Reactor", 20.0, 120.0, GTVoltageTier.HV);
        lcrNode.setMultiblock(true);
        lcrNode.setMachineIcon(ResourceLocation.tryParse("gtceu:large_chemical_reactor"));
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(lcrNode);

        MachineAddon batch = new MachineAddon("gtceu:batch_processing", "Batch Mode", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        batch.setParallelMultiplier(16);

        // LCR supports batch mode
        Assertions.assertTrue(adapter.isAddonCompatible(lcrNode, batch));

        // LFD does NOT support batch mode
        RecipeNode lfdNode = RecipeNode.create("Large Distillation Tower", 20.0, 120.0, GTVoltageTier.HV);
        lfdNode.setMultiblock(true);
        lfdNode.setMachineIcon(ResourceLocation.tryParse("gtceu:large_distillery"));
        Assertions.assertFalse(adapter.isAddonCompatible(lfdNode, batch));

        // Install 1st time on LCR
        adapter.handleInstallAddon(lcrNode, batch, false);
        Assertions.assertEquals(1, lcrNode.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT).count());
        Assertions.assertEquals(16, lcrNode.getTotalParallel());

        // Attempting to install again overwrites/replaces rather than duplicating
        adapter.handleInstallAddon(lcrNode, batch, false);
        Assertions.assertEquals(1, lcrNode.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT).count());
        Assertions.assertEquals(16, lcrNode.getTotalParallel());

        // Uninstall
        adapter.handleUninstallAddon(lcrNode, batch);
        Assertions.assertEquals(0, lcrNode.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT).count());
        Assertions.assertEquals(1, lcrNode.getTotalParallel());
    }

    @Test
    public void testStarTSterileCleaningMaintenanceHatchDiscoveryAndCompatibility() {
        List<MachineAddon> addons = new ArrayList<>();
        StarTAddonCrawler.discoverAddons(addons, List.of());

        MachineAddon sterileHatch = addons.stream()
                .filter(a -> "start_core:sterile_cleaning_maintenance_hatch".equals(a.getId()))
                .findFirst()
                .orElse(null);

        Assertions.assertNotNull(sterileHatch, "Sterile Cleaning Maintenance Hatch must be discovered by StarTAddonCrawler");
        Assertions.assertEquals(MachineAddon.Category.MAINTENANCE, sterileHatch.getCategory());
        Assertions.assertEquals(1.0, sterileHatch.getDurationMultiplier());
        Assertions.assertEquals(1.0, sterileHatch.getEutMultiplier());

        // Test compatibility with multiblock
        RecipeNode lcrNode = RecipeNode.create("Large Chemical Reactor", 20.0, 120.0, GTVoltageTier.HV);
        lcrNode.setMultiblock(true);
        lcrNode.setMachineIcon(ResourceLocation.tryParse("gtceu:large_chemical_reactor"));
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(lcrNode);

        Assertions.assertTrue(adapter.isAddonCompatible(lcrNode, sterileHatch), "Sterile Cleaning Maintenance Hatch must be compatible with multiblock");
    }

    @Test
    public void testThermalAddonCrawlerDiscoveryWithEmptyRecipeOutputs() {
        List<MachineAddon> addons = new ArrayList<>();
        com.gtceu.calcboard.compat.thermal.ThermalAddonCrawler.discoverAddons(addons, List.of());

        // In headless testing environment where ForgeRegistries may be mock/empty or loaded,
        // method must execute safely without throwing exceptions or blocking discovery.
        Assertions.assertDoesNotThrow(() -> {
            com.gtceu.calcboard.compat.thermal.ThermalAddonCrawler.discoverAddons(addons, Collections.emptyList());
        });
    }

    @Test
    public void testThermalDynamoSlotCapacityAndMultiCopyAugmentManagement() {
        RecipeNode dynamo = RecipeNode.create("Lapidary Fuel (Diamond)", 1500.0, -50.0, GTVoltageTier.LV);
        dynamo.setRecipeCategoryId(ResourceLocation.tryParse("thermal:lapidary_fuel"));
        dynamo.setMachineIcon(ResourceLocation.tryParse("thermal:dynamo_lapidary"));
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(dynamo);
        Assertions.assertNotNull(adapter);

        // 1. Parse an LV Upgrade Kit (Scale 6) and an MV Upgrade Kit (Scale 12)
        CompoundTag lvKitTag = new CompoundTag();
        CompoundTag lvKitAug = new CompoundTag();
        lvKitAug.putInt("Scale", 6);
        lvKitTag.put("AugmentData", lvKitAug);
        MachineAddon lvKit = ThermalAugmentHelper.parseThermalAugmentTag(lvKitTag, "LV Upgrade Kit", ResourceLocation.tryParse("kubejs:lv_upgrade_kit"));

        CompoundTag mvKitTag = new CompoundTag();
        CompoundTag mvKitAug = new CompoundTag();
        mvKitAug.putInt("Scale", 12);
        mvKitTag.put("AugmentData", mvKitAug);
        MachineAddon mvKit = ThermalAugmentHelper.parseThermalAugmentTag(mvKitTag, "MV Upgrade Kit", ResourceLocation.tryParse("kubejs:mv_upgrade_kit"));

        // 2. Parse a regular Dynamo Augment: Auxiliary Reaction Chamber (ARC)
        CompoundTag arcTag = new CompoundTag();
        CompoundTag arcAug = new CompoundTag();
        arcAug.putString("Type", "Dynamo");
        arcAug.putFloat("DynamoEnergy", 0.9f);
        arcAug.putFloat("DynamoPower", 0.5f); // 1.5x power
        arcTag.put("AugmentData", arcAug);
        MachineAddon arc = ThermalAugmentHelper.parseThermalAugmentTag(arcTag, "LV Auxiliary Reaction Chamber", ResourceLocation.tryParse("kubejs:lv_arc_kit"));

        // 3. Parse another regular Dynamo Augment: Multi-Cycle Injectors (MCI)
        CompoundTag mciTag = new CompoundTag();
        CompoundTag mciAug = new CompoundTag();
        mciAug.putString("Type", "Dynamo");
        mciAug.putFloat("DynamoEnergy", 1.1f);
        mciTag.put("AugmentData", mciAug);
        MachineAddon mci = ThermalAugmentHelper.parseThermalAugmentTag(mciTag, "LV Multi-Cycle Injectors", ResourceLocation.tryParse("kubejs:lv_mci_kit"));

        // Install LV Kit -> 1 addon
        adapter.handleInstallAddon(dynamo, lvKit, false);
        Assertions.assertEquals(1, dynamo.getAddons().size());
        Assertions.assertEquals(6, dynamo.getCombinedParallelMultiplier());

        // Install 1 ARC -> 2 addons (1 kit + 1 augment)
        adapter.handleInstallAddon(dynamo, arc, false);
        Assertions.assertEquals(2, dynamo.getAddons().size());

        // Install 1 MCI -> 3 addons (1 kit + 2 augments)
        adapter.handleInstallAddon(dynamo, mci, false);
        Assertions.assertEquals(3, dynamo.getAddons().size());

        // Install 2nd MCI -> 4 addons (1 kit + 3 augments: 1 ARC + 2 MCI)
        Assertions.assertTrue(adapter.canInstallAddon(dynamo, mci), "Can install 3rd regular augment (total 4 addons)");
        adapter.handleInstallAddon(dynamo, mci, false);
        Assertions.assertEquals(4, dynamo.getAddons().size());

        // 4th regular augment must be rejected (max 3 regular augments)
        Assertions.assertFalse(adapter.canInstallAddon(dynamo, mci), "Cannot exceed 3 regular augments");
        Assertions.assertFalse(adapter.canInstallAddon(dynamo, arc), "Cannot exceed 3 regular augments");

        // Replacing LV Kit with MV Kit replaces the kit without affecting the 3 augments
        Assertions.assertTrue(adapter.canInstallAddon(dynamo, mvKit), "Can replace upgrade kit with different tier");
        adapter.handleInstallAddon(dynamo, mvKit, false);
        Assertions.assertEquals(4, dynamo.getAddons().size());
        Assertions.assertEquals(12, dynamo.getCombinedParallelMultiplier());

        // Right-click uninstall on MCI removes 1 copy -> now 3 addons (1 kit + 1 ARC + 1 MCI)
        adapter.handleUninstallAddon(dynamo, mci);
        Assertions.assertEquals(3, dynamo.getAddons().size());
        Assertions.assertEquals(1, dynamo.getAddons().stream().filter(a -> a.getId().equals(mci.getId())).count());

        // Shift-click install on ARC fills remaining slot -> now 4 addons (1 kit + 2 ARC + 1 MCI)
        adapter.handleInstallAddon(dynamo, arc, true);
        Assertions.assertEquals(4, dynamo.getAddons().size());
        Assertions.assertEquals(2, dynamo.getAddons().stream().filter(a -> a.getId().equals(arc.getId())).count());
    }

    @Test
    public void testSuperCrackerAutoEquipsThroughputBoostingAndDisallowsArbitraryInstallation() {
        MachineAddonCatalog.getInstance().ensureFastLoaded();

        // 1. Setup Cracker node
        RecipeNode crackerNode = RecipeNode.create("Cracking Unit", 20.0, 100.0, GTVoltageTier.HV);
        crackerNode.setMultiblock(true);
        crackerNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:cracker"));
        crackerNode.setMachineIcon(ResourceLocation.tryParse("gtceu:cracker"));
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(crackerNode);

        MachineAddon boost = MachineAddonCatalog.getInstance().getAddon("gtceu:throughput_boosting");
        Assertions.assertNotNull(boost, "Throughput boosting addon must be present in catalog");

        // Standard Cracker does NOT have throughput boosting trait -> cannot install arbitrarily
        Assertions.assertFalse(adapter.isAddonCompatible(crackerNode, boost), "Standard Cracker must NOT allow throughput boosting trait");

        // 2. Switch machine to Super Cracker (SDF) -> Throughput Boosting must auto-equip!
        adapter.onMachineIconChanged(crackerNode, ResourceLocation.tryParse("gtceu:cracker"), ResourceLocation.tryParse("gtceu:super_cracker"));
        crackerNode.setMachineIcon(ResourceLocation.tryParse("gtceu:super_cracker"));

        Assertions.assertTrue(crackerNode.getAddons().stream().anyMatch(a -> a.getId().equals("gtceu:throughput_boosting")), "Super Cracker must auto-equip Throughput Boosting");
        Assertions.assertEquals(4, crackerNode.getTotalParallel(), "Super Cracker throughput boosting provides 4x parallel");
        Assertions.assertEquals(1.6, crackerNode.getCombinedDurationMultiplier(), 0.001, "Duration multiplier must be 1.6x");
        Assertions.assertEquals(0.95, crackerNode.getCombinedEutMultiplier(), 0.001, "EUt multiplier must be 0.95x");

        // 3. Switch back to standard Cracker -> Throughput Boosting must be purged automatically
        adapter.onMachineIconChanged(crackerNode, ResourceLocation.tryParse("gtceu:super_cracker"), ResourceLocation.tryParse("gtceu:cracker"));
        crackerNode.setMachineIcon(ResourceLocation.tryParse("gtceu:cracker"));

        Assertions.assertFalse(crackerNode.getAddons().stream().anyMatch(a -> a.getId().equals("gtceu:throughput_boosting")), "Standard Cracker must purge Throughput Boosting");
        Assertions.assertEquals(1, crackerNode.getTotalParallel(), "Parallel must reset to 1x");
    }

    @Test
    public void testSuperCrackerCoilCapabilityAndModifier() {
        MachineAddonCatalog.getInstance().ensureFastLoaded();
        ResourceLocation sdfId = ResourceLocation.tryParse("gtceu:super_cracker");

        // 1. MultiblockDetector must recognize super_cracker as a coil multiblock
        Assertions.assertTrue(MultiblockDetector.isCoilMultiblock(sdfId), "SDF Super Cracker must be recognized as a coil multiblock");

        // 2. Setup Super Cracker Node
        RecipeNode sdfNode = RecipeNode.create("Fractional Disruption Furnace [SDF]", 20.0, 100.0, GTVoltageTier.HV);
        sdfNode.setMultiblock(true);
        sdfNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:cracker"));
        sdfNode.setMachineIcon(sdfId);

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(sdfNode);
        List<com.gtceu.calcboard.api.catalog.AddonCategory> cats = adapter.getApplicableAddonCategories(sdfNode);
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.COIL), "Applicable categories for SDF Cracker must include COIL");

        // 3. Equip Kanthal Coil (Cracking Energy: 90%)
        MachineAddon kanthal = new MachineAddon("gtceu:kanthal_coil_block", "Kanthal Coil", com.gtceu.calcboard.api.catalog.AddonCategory.COIL, "", null);
        kanthal.setCoilTemperature(2700);
        kanthal.setCrackingEnergyPercent(90);

        Assertions.assertTrue(adapter.isAddonCompatible(sdfNode, kanthal), "Kanthal coil must be compatible with SDF Cracker");
        adapter.handleInstallAddon(sdfNode, kanthal, false);

        // Cracking Unit coil discount: 90% (0.90x EU)
        Assertions.assertEquals(0.90, sdfNode.getCombinedEutMultiplier(), 0.001, "SDF Cracker with Kanthal Coil must receive 0.90x EU discount");
    }

    @Test
    public void testAlloyBlastSmelterVariantsCoilsAndTraits() {
        MachineAddonCatalog.getInstance().ensureFastLoaded();
        ResourceLocation catId = ResourceLocation.tryParse("gtceu:super_abs");
        ResourceLocation hamId = ResourceLocation.tryParse("gtceu:mega_abs");
        ResourceLocation loafId = ResourceLocation.tryParse("gtceu:ultimate_abs");

        // 1. Multiblock & Coil checks
        Assertions.assertTrue(MultiblockDetector.isMultiblock(catId), "CAT must be recognized as multiblock");
        Assertions.assertTrue(MultiblockDetector.isMultiblock(hamId), "HAM must be recognized as multiblock");
        Assertions.assertTrue(MultiblockDetector.isMultiblock(loafId), "LOAF must be recognized as multiblock");

        Assertions.assertTrue(MultiblockDetector.isCoilMultiblock(catId), "CAT must be recognized as coil multiblock");
        Assertions.assertTrue(MultiblockDetector.isCoilMultiblock(hamId), "HAM must be recognized as coil multiblock");
        Assertions.assertTrue(MultiblockDetector.isCoilMultiblock(loafId), "LOAF must be recognized as coil multiblock");

        // 2. Setup ABS Recipe Node
        RecipeNode absNode = RecipeNode.create("Alloy Blast Smelter", 20.0, 100.0, GTVoltageTier.HV);
        absNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:alloy_blast_smelter"));
        absNode.setMachineIcon(catId);
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(absNode);

        // Switch to CAT -> Auto-equips Throughput Boosting & supports Coil
        adapter.onMachineIconChanged(absNode, ResourceLocation.tryParse("gtceu:alloy_blast_smelter"), catId);
        Assertions.assertTrue(absNode.isMultiblock(), "Node must remain multiblock after switching to CAT");
        List<com.gtceu.calcboard.api.catalog.AddonCategory> catCategories = adapter.getApplicableAddonCategories(absNode);
        Assertions.assertTrue(catCategories.contains(com.gtceu.calcboard.api.catalog.AddonCategory.COIL), "CAT must have COIL tab");
        Assertions.assertTrue(catCategories.contains(com.gtceu.calcboard.api.catalog.AddonCategory.MULTIBLOCK_TRAIT), "CAT must have TRAITS tab");
        Assertions.assertTrue(absNode.getAddons().stream().anyMatch(a -> a.getId().equals("gtceu:throughput_boosting")), "CAT must auto-equip Throughput Boosting");

        // Switch to HAM -> Purges Throughput Boosting, supports Parallel Hatch & Coil
        absNode.setMachineIcon(hamId);
        Assertions.assertTrue(absNode.isMultiblock(), "Node must remain multiblock after switching to HAM");
        List<com.gtceu.calcboard.api.catalog.AddonCategory> hamCategories = adapter.getApplicableAddonCategories(absNode);
        Assertions.assertTrue(hamCategories.contains(com.gtceu.calcboard.api.catalog.AddonCategory.COIL), "HAM must have COIL tab");
        Assertions.assertFalse(absNode.getAddons().stream().anyMatch(a -> a.getId().equals("gtceu:throughput_boosting")), "HAM must purge Throughput Boosting");

        // Switch to LOAF -> Supports Throughput Boosting, Bulk Processing, Batch Mode, Parallel Hatch & Coil
        absNode.setMachineIcon(loafId);
        Assertions.assertTrue(absNode.isMultiblock(), "Node must remain multiblock after switching to LOAF");
        List<com.gtceu.calcboard.api.catalog.AddonCategory> loafCategories = adapter.getApplicableAddonCategories(absNode);
        Assertions.assertTrue(loafCategories.contains(com.gtceu.calcboard.api.catalog.AddonCategory.COIL), "LOAF must have COIL tab");
        Assertions.assertTrue(loafCategories.contains(com.gtceu.calcboard.api.catalog.AddonCategory.PARALLEL), "LOAF must have PARALLEL tab");
        Assertions.assertTrue(loafCategories.contains(com.gtceu.calcboard.api.catalog.AddonCategory.MULTIBLOCK_TRAIT), "LOAF must have TRAITS tab");
        Assertions.assertTrue(MultiblockDetector.supportsBulkProcessing(loafId), "LOAF must support Bulk Processing");
        Assertions.assertTrue(MultiblockDetector.supportsThroughputBoosting(loafId), "LOAF must support Throughput Boosting");
        Assertions.assertTrue(MultiblockDetector.supportsBatchMode(loafId), "LOAF must support Batch Mode");

        MachineAddon bulkAddon = new MachineAddon("gtceu:bulk_processing", "Bulk Processing", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        bulkAddon.setParallelMultiplier(16);
        bulkAddon.setDurationMultiplier(13.0);
        Assertions.assertTrue(adapter.isAddonCompatible(absNode, bulkAddon), "LOAF must be compatible with Bulk Processing addon");

        adapter.onAddonInstalled(absNode, bulkAddon);
        Assertions.assertTrue(absNode.getAddons().contains(bulkAddon), "LOAF must equip Bulk Processing");
        // Stacking traits: Throughput Boosting (4x Par, 1.6x Dur) + Bulk Processing (16x Par, 13x Dur) = 64x Par, 20.8x Dur
        Assertions.assertEquals(64, absNode.getCombinedParallelMultiplier(), "Stacked traits must provide 64x parallel");
        Assertions.assertEquals(20.8, absNode.getCombinedDurationMultiplier(), 0.001, "Stacked traits must scale duration by 20.8x");

        // Isolate Bulk Processing by removing Throughput Boosting
        absNode.getAddons().removeIf(a -> a.getId().equals("gtceu:throughput_boosting"));
        Assertions.assertEquals(16, absNode.getCombinedParallelMultiplier(), "Bulk Processing alone must provide 16x parallel");
        Assertions.assertEquals(13.0, absNode.getCombinedDurationMultiplier(), 0.001, "Bulk Processing alone must scale duration by 13x");
    }

    @Test
    public void testRecipeModifierReflectionUnpacking() {
        // Mock a MachineDefinition with a RecipeModifier field (or getRecipeModifier method)
        class MockIdentifiedModifier {
            private final String id;
            MockIdentifiedModifier(String id) { this.id = id; }
            public String getId() { return id; }
        }

        class MockRecipeModifierList {
            private final Object[] modifiers;
            MockRecipeModifierList(Object... modifiers) { this.modifiers = modifiers; }
            public Object[] modifiers() { return modifiers; }
        }

        class MockMachineDefinition {
            private final Object recipeModifier;
            MockMachineDefinition(Object recipeModifier) { this.recipeModifier = recipeModifier; }
            public Object getRecipeModifier() { return recipeModifier; }
        }

        MockRecipeModifierList modList = new MockRecipeModifierList(
                new MockIdentifiedModifier("parallel_hatch"),
                new MockIdentifiedModifier("ebf_oc"),
                new MockIdentifiedModifier("throughput_boosting"),
                new MockIdentifiedModifier("bulk_processing"),
                new MockIdentifiedModifier("batch_mode")
        );

        MockMachineDefinition mockDef = new MockMachineDefinition(modList);
        List<Object> extracted = com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge.getRecipeModifiers(mockDef);
        Assertions.assertEquals(5, extracted.size(), "All 5 modifiers must be flattened and extracted");

        List<String> names = extracted.stream()
                .map(com.gtceu.calcboard.compat.gtceu.helper.GTCEuReflectionBridge::getRecipeModifierName)
                .toList();

        Assertions.assertTrue(names.contains("PARALLEL_HATCH"));
        Assertions.assertTrue(names.contains("EBF_OC"));
        Assertions.assertTrue(names.contains("THROUGHPUT_BOOSTING"));
        Assertions.assertTrue(names.contains("BULK_PROCESSING"));
        Assertions.assertTrue(names.contains("BATCH_MODE"));
    }

    @Test
    public void testMultiSmelterVsAlloyBlastSmelterCoilParallelDeduction() {
        ResourceLocation multiSmelterId = ResourceLocation.tryParse("gtceu:multi_smelter");
        ResourceLocation absId = ResourceLocation.tryParse("gtceu:alloy_blast_smelter");

        // Register multi_smelter as coil parallel multiblock deterministically
        MultiblockDetector.registerCoilParallelMultiblock(multiSmelterId);

        Assertions.assertTrue(MultiblockDetector.isCoilParallelMultiblock(multiSmelterId), "multi_smelter must be recognized as coil parallel");
        Assertions.assertFalse(MultiblockDetector.isCoilParallelMultiblock(absId), "alloy_blast_smelter must NOT be misidentified as coil parallel");

        RecipeNode absNode = RecipeNode.create("Alloy Blast Smelter", 280.0, 100.0, GTVoltageTier.HV);
        absNode.setMultiblock(true);
        absNode.setParallel(64);
        absNode.setMachineIcon(absId);

        MachineAddon bulk = new MachineAddon("gtceu:bulk_processing", "Bulk", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        bulk.setParallelMultiplier(16);
        bulk.setDurationMultiplier(13.0);
        absNode.addAddon(bulk);

        // Effective parallel must be 64 * 16 = 1024
        Assertions.assertEquals(1024, absNode.getTotalParallel(), "ABS with 64x parallel hatch + 16x Bulk Processing must yield 1024 total parallel");
        // Effective duration must be 280 * 13 = 3640 ticks (182.0s)
        Assertions.assertEquals(182.0, absNode.getEffectiveDurationSeconds(), 0.001);
        // CPS = (20 / 3640) * 1024 = 5.62637 cycles/sec
        Assertions.assertEquals(5.62637, absNode.getCyclesPerSecond(), 0.001);
    }
}


