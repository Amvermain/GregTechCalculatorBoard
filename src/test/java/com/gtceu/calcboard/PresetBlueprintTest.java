package com.gtceu.calcboard;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PresetBlueprintTest {

    @Test
    public void generateAndVerifyPresetBlueprints() {
        // 1. Preset 1: Petrochemical Distillation & 16x Parallel Loop (GTCEu)
        FlowGraph petroGraph = new FlowGraph();
        
        RecipeNode oilPump = RecipeNode.create("Oil Drilling Rig", 20.0, 128.0, GTVoltageTier.MV);
        oilPump.setPos(50, 100);
        oilPump.setCardWidth(180);
        oilPump.setCardHeight(130);
        oilPump.setMachineIcon(ResourceLocation.tryParse("gtceu:oil_drilling_rig"));
        oilPump.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oil"), "Oil", 1000.0, 1.0));
        petroGraph.addNode(oilPump);

        RecipeNode distTower = RecipeNode.create("Distillation Tower", 100.0, 1920.0, GTVoltageTier.EV);
        distTower.setPos(320, 80);
        distTower.setCardWidth(200);
        distTower.setCardHeight(160);
        distTower.setMultiblock(true);
        distTower.setMachineIcon(ResourceLocation.tryParse("gtceu:distillation_tower"));
        distTower.setTargetTier(GTVoltageTier.LuV);
        distTower.setParallel(16);
        distTower.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oil"), "Oil", 1000.0, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 300.0, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:light_fuel"), "Light Fuel", 400.0, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:naphtha"), "Naphtha", 200.0, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:refinery_gas"), "Refinery Gas", 100.0, 1.0));
        
        // Add 16x parallel hatch and LuV energy hatch
        distTower.getAddons().add(new GTParallelHatchAddon("gtceu:ev_parallel_hatch", "Parallel Control Hatch (16x)", "16x Parallels", ResourceLocation.tryParse("gtceu:ev_parallel_hatch"), 16, false));
        distTower.getAddons().add(new GTEnergyHatchAddon("gtceu:luv_energy_hatch", "Energy Hatch (LuV 1A)", "LuV 1A Power", ResourceLocation.tryParse("gtceu:luv_energy_hatch"), GTVoltageTier.LuV, 1, false, false, false));
        petroGraph.addNode(distTower);

        RecipeNode cracker = RecipeNode.create("Cracker", 60.0, 512.0, GTVoltageTier.HV);
        cracker.setPos(600, 80);
        cracker.setCardWidth(190);
        cracker.setCardHeight(140);
        cracker.setMultiblock(true);
        cracker.setMachineIcon(ResourceLocation.tryParse("gtceu:cracker"));
        cracker.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 300.0, 1.0));
        cracker.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        cracker.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_heavy_fuel"), "Cracked Heavy Fuel", 300.0, 1.0));
        petroGraph.addNode(cracker);

        petroGraph.addConnection(oilPump.getId(), 0, distTower.getId(), 0);
        petroGraph.addConnection(distTower.getId(), 0, cracker.getId(), 0);

        CanvasGroupFrame petroFrame = CanvasGroupFrame.createFromNodes("Petrochemical Refining Unit", List.of(oilPump, distTower, cracker), CanvasGroupFrame.COLOR_BLUE);
        petroFrame.setNote("16x Parallel Distillation Tower with LuV Energy Hatch powering EV recipes");
        petroGraph.addFrame(petroFrame);

        String petroCode = BlueprintCodec.exportToString(petroGraph, 0, 0, 1.0);
        System.out.println("=== PRESET 1: PETROCHEMICAL ===");
        System.out.println(petroCode);
        Assertions.assertNotNull(BlueprintCodec.importFromString(petroCode, null));

        // 2. Preset 2: Create Kinetics & Create New Age Power
        FlowGraph createGraph = new FlowGraph();

        RecipeNode waterWheel = RecipeNode.create("Large Water Wheel", 20.0, 512.0, GTVoltageTier.LV);
        waterWheel.setPos(60, 100);
        waterWheel.setCardWidth(180);
        waterWheel.setCardHeight(120);
        waterWheel.setEnergyType(EnergyType.KINETIC_SU);
        waterWheel.setGenerator(true);
        waterWheel.setMachineIcon(ResourceLocation.tryParse("create:large_water_wheel"));
        waterWheel.addOutput(IngredientStack.stressUnit(512.0));
        createGraph.addNode(waterWheel);

        RecipeNode genCoil = RecipeNode.create("Generator Coil", 20.0, 512.0, GTVoltageTier.ULV);
        genCoil.setPos(300, 100);
        genCoil.setCardWidth(180);
        genCoil.setCardHeight(130);
        genCoil.setEnergyType(EnergyType.ELECTRIC_FE);
        genCoil.setGenerator(true);
        genCoil.setMachineIcon(ResourceLocation.tryParse("create_new_age:generator_coil"));
        genCoil.addInput(IngredientStack.stressUnit(512.0));
        genCoil.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("forge:fe"), "FE", 256.0, 1.0));
        createGraph.addNode(genCoil);

        RecipeNode motor = RecipeNode.create("Advanced Motor", 20.0, 1024.0, GTVoltageTier.LV);
        motor.setPos(540, 100);
        motor.setCardWidth(180);
        motor.setCardHeight(130);
        motor.setEnergyType(EnergyType.ELECTRIC_FE);
        motor.setMachineIcon(ResourceLocation.tryParse("create_new_age:advanced_motor"));
        motor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("forge:fe"), "FE", 256.0, 1.0));
        motor.addOutput(IngredientStack.stressUnit(2048.0));
        createGraph.addNode(motor);

        createGraph.addConnection(waterWheel.getId(), 0, genCoil.getId(), 0);
        createGraph.addConnection(genCoil.getId(), 0, motor.getId(), 0);

        CanvasGroupFrame createFrame = CanvasGroupFrame.createFromNodes("Kinetic & Electricity System", List.of(waterWheel, genCoil, motor), CanvasGroupFrame.COLOR_AMBER);
        createFrame.setNote("Water Wheel 512 SU -> Generator Coil -> FE -> Advanced Motor");
        createGraph.addFrame(createFrame);

        String createCode = BlueprintCodec.exportToString(createGraph, 0, 0, 1.0);
        System.out.println("=== PRESET 2: CREATE & NEW AGE ===");
        System.out.println(createCode);
        Assertions.assertNotNull(BlueprintCodec.importFromString(createCode, null));

        // 3. Preset 3: Multiblock Construction BOM & Compound Module (GTCEu)
        FlowGraph bomGraph = new FlowGraph();
        RecipeNode ebf = RecipeNode.create("Electric Blast Furnace", 100.0, 120.0, GTVoltageTier.MV);
        ebf.setPos(50, 100);
        ebf.setCardWidth(200);
        ebf.setCardHeight(150);
        ebf.setMultiblock(true);
        ebf.setMachineIcon(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        ebf.getAddons().add(new GTEnergyHatchAddon("gtceu:mv_energy_hatch", "Energy Hatch (MV 1A)", "MV Power", ResourceLocation.tryParse("gtceu:mv_energy_hatch"), GTVoltageTier.MV, 1, false, false, false));
        ebf.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
        ebf.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:steel_ingot"), "Steel Ingot", 1.0));
        bomGraph.addNode(ebf);

        RecipeNode lcr = RecipeNode.create("Large Chemical Reactor", 120.0, 480.0, GTVoltageTier.HV);
        lcr.setPos(320, 100);
        lcr.setCardWidth(200);
        lcr.setCardHeight(150);
        lcr.setMultiblock(true);
        lcr.setMachineIcon(ResourceLocation.tryParse("gtceu:large_chemical_reactor"));
        lcr.getAddons().add(new GTEnergyHatchAddon("gtceu:hv_energy_hatch", "Energy Hatch (HV 1A)", "HV Power", ResourceLocation.tryParse("gtceu:hv_energy_hatch"), GTVoltageTier.HV, 1, false, false, false));
        lcr.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:steel_ingot"), "Steel Ingot", 1.0));
        lcr.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:stainless_steel_ingot"), "Stainless Steel Ingot", 1.0));
        bomGraph.addNode(lcr);

        bomGraph.addConnection(ebf.getId(), 0, lcr.getId(), 0);

        CanvasGroupFrame bomFrame = CanvasGroupFrame.createFromNodes("High-Tier Metallurgy Compound", List.of(ebf, lcr), CanvasGroupFrame.COLOR_PURPLE);
        bomFrame.setNote("EBF + LCR Steel & Stainless Steel Production line for Multiblock BOM inspection");
        bomGraph.addFrame(bomFrame);

        String bomCode = BlueprintCodec.exportToString(bomGraph, 0, 0, 1.0);
        System.out.println("=== PRESET 3: MULTIBLOCK BOM & COMPOUND ===");
        System.out.println(bomCode);
        Assertions.assertNotNull(BlueprintCodec.importFromString(bomCode, null));

        // 4. Preset 4: Thermal & Systeams Boiler & Steam Dynamo
        FlowGraph thermalGraph = new FlowGraph();
        RecipeNode boiler = RecipeNode.create("Compression Boiler", 20.0, 0.0, GTVoltageTier.LV);
        boiler.setPos(60, 100);
        boiler.setCardWidth(200);
        boiler.setCardHeight(150);
        boiler.setEnergyType(EnergyType.HEAT_OR_SELF);
        boiler.setMachineIcon(ResourceLocation.tryParse("thermal:boiler_compression"));
        boiler.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0, 1.0));
        boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 800.0, 1.0));
        thermalGraph.addNode(boiler);

        RecipeNode dynamo = RecipeNode.create("Steam Dynamo", 20.0, 40.0, GTVoltageTier.LV);
        dynamo.setPos(320, 100);
        dynamo.setCardWidth(200);
        dynamo.setCardHeight(150);
        dynamo.setEnergyType(EnergyType.ELECTRIC_FE);
        dynamo.setGenerator(true);
        dynamo.setMachineIcon(ResourceLocation.tryParse("thermal:dynamo_steam"));
        dynamo.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 800.0, 1.0));
        dynamo.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("forge:fe"), "FE", 800.0, 1.0));
        thermalGraph.addNode(dynamo);

        thermalGraph.addConnection(boiler.getId(), 0, dynamo.getId(), 0);

        CanvasGroupFrame thermalFrame = CanvasGroupFrame.createFromNodes("Thermal Steam Power Loop", List.of(boiler, dynamo), CanvasGroupFrame.COLOR_ROSE);
        thermalFrame.setNote("Compression Boiler Water -> Steam -> Steam Dynamo FE Generator");
        thermalGraph.addFrame(thermalFrame);

        String thermalCode = BlueprintCodec.exportToString(thermalGraph, 0, 0, 1.0);
        System.out.println("=== PRESET 4: THERMAL & SYSTEAMS ===");
        System.out.println(thermalCode);
        Assertions.assertNotNull(BlueprintCodec.importFromString(thermalCode, null));
    }
}


