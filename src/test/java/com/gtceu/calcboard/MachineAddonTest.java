package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Machine Addons, Turbine Rotors, Heating Coils, Parallel Hatches, and Thermal Augments.
 */
public class MachineAddonTest {

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

        // 3. Downgrade Rotor Holder to HV (2,048 base) with Titanium -> capped at 2,662 EU/t (84 parallel)
        turbine.setTargetTier(GTVoltageTier.HV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(84, turbine.getParallel());
        Assertions.assertEquals(2662.0, turbine.getSingleMachineEUt(), 0.001);

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
        // Duration: 2.0s * (2.20 + 0.10) = 4.60s
        Assertions.assertEquals(4.60, scheeliteTurbine.getEffectiveDurationSeconds(), 0.001);
        // Throughput: (20 / 92 ticks) * 1152 parallel = 250.434... cycles/s
        Assertions.assertEquals(250.434, scheeliteTurbine.getCyclesPerSecond(), 0.01);
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
        Assertions.assertTrue(pyrolyseCats.contains(MachineAddon.Category.PARALLEL));
        Assertions.assertTrue(pyrolyseCats.contains(MachineAddon.Category.MAINTENANCE));
        Assertions.assertFalse(pyrolyseCats.contains(MachineAddon.Category.ROTOR));

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
    }

    @Test
    public void testDynamicCoilHelperStats() {
        Object mockCoil = new Object() {
            public int getCoilTemperature() { return 2700; }
            public int getTier() { return 2; }
            public int getEnergyDiscount() { return 90; }
            public int getPyrolyseSpeed() { return 150; }
            public int getSmelterParallel() { return 32; }
        };

        var kanthal = CoilHelper.extractStatsFromBlockObject(mockCoil);
        Assertions.assertNotNull(kanthal);
        Assertions.assertEquals(2700, kanthal.temperature());
        Assertions.assertEquals(150, kanthal.pyrolyseSpeedPercent());
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
        Assertions.assertEquals(32, cupro.smelterParallel());

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

        Assertions.assertEquals(GTVoltageTier.HV, steamTurbine.getTurbineBaseTier());
        Assertions.assertEquals(10, steamTurbine.getTurbineHolderEfficiencyBonus());
        Assertions.assertEquals(2048.0, steamTurbine.getNodeRotorHolderMaxEUt(GTVoltageTier.EV, 100));
        Assertions.assertEquals(1.10, steamTurbine.getEffectiveDurationSeconds(), 0.001);

        RecipeNode gasTurbine = RecipeNode.create("Large Gas Turbine", 40.0, 32.0, GTVoltageTier.IV);
        gasTurbine.setGenerator(true);
        gasTurbine.setRotorEfficiency(220);
        gasTurbine.setRotorPower(225);

        Assertions.assertEquals(GTVoltageTier.EV, gasTurbine.getTurbineBaseTier());
        Assertions.assertEquals(10, gasTurbine.getTurbineHolderEfficiencyBonus());
        Assertions.assertEquals(18432.0, gasTurbine.getNodeRotorHolderMaxEUt(GTVoltageTier.IV, 225));

        RecipeNode plasmaTurbine = RecipeNode.create("Large Plasma Turbine", 20.0, 1000.0, GTVoltageTier.LuV);
        plasmaTurbine.setGenerator(true);
        plasmaTurbine.setRotorEfficiency(150);
        plasmaTurbine.setRotorPower(100);

        Assertions.assertEquals(GTVoltageTier.IV, plasmaTurbine.getTurbineBaseTier());
        Assertions.assertEquals(10, plasmaTurbine.getTurbineHolderEfficiencyBonus());
        Assertions.assertEquals(32768.0, plasmaTurbine.getNodeRotorHolderMaxEUt(GTVoltageTier.LuV, 100));

        RecipeNode nyinsaneTurbine = RecipeNode.create("Nyinsane Plasma Turbine", 20.0, 1000.0, GTVoltageTier.IV);
        nyinsaneTurbine.setGenerator(true);
        Assertions.assertEquals(196608.0, nyinsaneTurbine.getNodeRotorHolderMaxEUt(GTVoltageTier.IV, 100));
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

        MachineAddon arcAddon = DynamicAddonCrawler.parseThermalAugmentTag(arcTag, "LV ARC Kit", ResourceLocation.tryParse("kubejs:lv_arc_kit"));
        Assertions.assertNotNull(arcAddon);
        Assertions.assertEquals(1.5, arcAddon.getEutMultiplier(), 0.001);
        Assertions.assertEquals(0.95, arcAddon.getDurationMultiplier(), 0.001);
        Assertions.assertEquals(MachineAddon.Category.THERMAL_AUGMENT, arcAddon.getCategory());

        CompoundTag mciTag = new CompoundTag();
        CompoundTag mciAug = new CompoundTag();
        mciAug.putFloat("DynamoEnergy", 1.15f);
        mciAug.putString("Type", "Dynamo");
        mciTag.put("AugmentData", mciAug);

        MachineAddon mciAddon = DynamicAddonCrawler.parseThermalAugmentTag(mciTag, "LV MCI Kit", ResourceLocation.tryParse("kubejs:lv_mci_kit"));
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

        MachineAddon lvKit = DynamicAddonCrawler.parseThermalAugmentTag(lvTag, "§7LV§r Upgrade Kit", ResourceLocation.tryParse("kubejs:lv_upgrade_kit"));
        Assertions.assertNotNull(lvKit);
        Assertions.assertEquals(6, lvKit.getParallelMultiplier());

        CompoundTag evTag = new CompoundTag();
        CompoundTag evAug = new CompoundTag();
        evAug.putFloat("BaseMod", 48.0f);
        evAug.putString("Type", "Upgrade");
        evTag.put("AugmentData", evAug);

        MachineAddon evKit = DynamicAddonCrawler.parseThermalAugmentTag(evTag, "§5EV§r Upgrade Kit", ResourceLocation.tryParse("kubejs:ev_upgrade_kit"));
        Assertions.assertNotNull(evKit);
        Assertions.assertEquals(48, evKit.getParallelMultiplier());

        // 2. Test KubeJS ARC Kits (0.5x, 1.0x, 2.0x, 3.0x power & 0.95x, 0.90x, 0.85x, 0.80x duration)
        CompoundTag evArcTag = new CompoundTag();
        CompoundTag evArcAug = new CompoundTag();
        evArcAug.putString("Type", "Dynamo");
        evArcAug.putFloat("DynamoEnergy", 0.80f);
        evArcAug.putFloat("DynamoPower", 3.0f);
        evArcTag.put("AugmentData", evArcAug);

        MachineAddon evArc = DynamicAddonCrawler.parseThermalAugmentTag(evArcTag, "§5EV§r Auxiliary Reaction Chamber Kit", ResourceLocation.tryParse("kubejs:ev_arc_kit"));
        Assertions.assertNotNull(evArc);
        Assertions.assertEquals(4.0, evArc.getEutMultiplier(), 0.001);
        Assertions.assertEquals(0.80, evArc.getDurationMultiplier(), 0.001);

        // 3. Test KubeJS MCI Kits (1.15x, 1.30x, 1.45x, 1.60x duration)
        CompoundTag evMciTag = new CompoundTag();
        CompoundTag evMciAug = new CompoundTag();
        evMciAug.putString("Type", "Dynamo");
        evMciAug.putFloat("DynamoEnergy", 1.60f);
        evMciTag.put("AugmentData", evMciAug);

        MachineAddon evMci = DynamicAddonCrawler.parseThermalAugmentTag(evMciTag, "§5EV§r Multi-cycle Injectors Kit", ResourceLocation.tryParse("kubejs:ev_mci_kit"));
        Assertions.assertNotNull(evMci);
        Assertions.assertEquals(1.0, evMci.getEutMultiplier(), 0.001);
        Assertions.assertEquals(1.60, evMci.getDurationMultiplier(), 0.001);

        // 4. Test Recursive GTCEu Content / SizedIngredient Mock Unpacking
        // 5. Test Thermal Machine 1 Upgrade Kit Rule & 3-Slot Augment Stacking
        RecipeNode dynamo = RecipeNode.create("Lapidary Fuel (Diamond)", 20.0, 100.0, GTVoltageTier.LV);
        dynamo.setGenerator(true);

        // Install LV Upgrade Kit (6x)
        dynamo.addAddon(lvKit.copy());
        Assertions.assertEquals(6, dynamo.getTotalParallel());
        Assertions.assertEquals(1, dynamo.getAddons().size());

        // Install EV Upgrade Kit (48x) -> replaces LV Upgrade Kit!
        dynamo.addAddon(evKit.copy());
        Assertions.assertEquals(48, dynamo.getTotalParallel());
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
}
