package com.gtceu.calcboard.api.bom;

import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class MultiblockBOMTest {

    @Test
    public void testMultiblockStructureDefAndPartCategories() {
        ResourceLocation controllerId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");

        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(controllerId, "Electric Blast Furnace", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:heatproof_machine_casing"), "Heat Proof Casing", 10, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:cupronickel_coil_block"), "Cupronickel Coil", 16, PartCategory.COIL),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), "LV Energy Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_input_bus"), "LV Input Bus", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_output_bus"), "LV Output Bus", 1, PartCategory.HATCH_BUS)
        );

        MultiblockStructureDef def = new MultiblockStructureDef(
            controllerId,
            "Electric Blast Furnace",
            parts,
            16,
            1,
            1,
            1,
            1,
            1,
            1
        );

        MultiblockStructureCatalog.registerManualStructure(def);
        MultiblockStructureDef retrieved = MultiblockStructureCatalog.getStructure(controllerId);
        Assertions.assertNotNull(retrieved);
        Assertions.assertEquals(16, retrieved.coilSlotCount());
        Assertions.assertEquals(6, retrieved.parts().size());
    }

    @Test
    public void testMultiblockBOMCalculationWithDynamicCoilAndTier() {
        ResourceLocation controllerId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");

        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(controllerId, "Electric Blast Furnace", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:heatproof_machine_casing"), "Heat Proof Casing", 10, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:cupronickel_coil_block"), "Cupronickel Coil", 16, PartCategory.COIL),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), "LV Energy Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_input_bus"), "LV Input Bus", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_output_bus"), "LV Output Bus", 1, PartCategory.HATCH_BUS)
        );

        MultiblockStructureDef def = new MultiblockStructureDef(
            controllerId,
            "Electric Blast Furnace",
            parts,
            16,
            1,
            1,
            1,
            1,
            1,
            1
        );
        MultiblockStructureCatalog.registerManualStructure(def);

        RecipeNode node = new RecipeNode("ebf_node", "Electric Blast Furnace", 100, 100, GTVoltageTier.MV);
        node.setMachineIcon(controllerId);
        node.setMultiblock(true);
        node.setMachineCount(2.4); // Physical count should ceil to 3
        node.setTargetTier(GTVoltageTier.EV);

        // Equip Kanthal Coil Addon
        MachineAddon kanthalAddon = new MachineAddon(
            "gtceu:kanthal_coil_block",
            "Kanthal Heating Coil",
            AddonCategory.COIL,
            "Coil 2700K",
            ResourceLocation.tryParse("gtceu:kanthal_coil_block")
        );
        node.getAddons().add(kanthalAddon);

        // Inputs & Outputs
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:raw_iron"), "Raw Iron", 1.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));

        // 1. Calculate BOM (1x Normal Tier Hatch)
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        Assertions.assertEquals(3, summary.totalMultiblockCount());
        Assertions.assertFalse(summary.aggregatedItems().isEmpty());

        // Check Casings
        MultiblockBOMSummary.BOMItemEntry casingEntry = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().getPath().contains("heatproof_machine_casing"))
            .findFirst().orElse(null);
        Assertions.assertNotNull(casingEntry);
        Assertions.assertEquals(30, casingEntry.totalAmount()); // 10 * 3 = 30

        // Check Coil (Should be Kanthal, NOT Cupronickel)
        MultiblockBOMSummary.BOMItemEntry coilEntry = summary.aggregatedItems().stream()
            .filter(e -> e.category() == PartCategory.COIL)
            .findFirst().orElse(null);
        Assertions.assertNotNull(coilEntry);
        Assertions.assertEquals("Kanthal Heating Coil", coilEntry.displayName());
        Assertions.assertEquals(48, coilEntry.totalAmount()); // 16 * 3 = 48

        // Check Energy Hatch (Should be EV)
        MultiblockBOMSummary.BOMItemEntry ehEntry = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().getPath().contains("energy_input_hatch"))
            .findFirst().orElse(null);
        Assertions.assertNotNull(ehEntry);
        Assertions.assertTrue(ehEntry.itemId().getPath().contains("ev_energy_input_hatch"));
        Assertions.assertEquals(3, ehEntry.totalAmount()); // 1 * 3 = 3
    }

    @Test
    public void testDualLowerTierEnergyHatchToggle() {
        ResourceLocation controllerId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");

        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(controllerId, "Electric Blast Furnace", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), "LV Energy Hatch", 1, PartCategory.HATCH_BUS)
        );
        MultiblockStructureCatalog.registerManualStructure(new MultiblockStructureDef(controllerId, "Electric Blast Furnace", parts, 0, 1, 1, 1, 1, 1, 1));

        RecipeNode node = new RecipeNode("ebf_node2", "Electric Blast Furnace", 100, 100, GTVoltageTier.MV);
        node.setMachineIcon(controllerId);
        node.setMultiblock(true);
        node.setMachineCount(2.0);
        node.setTargetTier(GTVoltageTier.EV); // Target EV

        // Calculate BOM with dualLowerTierEnergyHatches = true
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), true);

        MultiblockBOMSummary.BOMItemEntry ehEntry = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().getPath().contains("energy_input_hatch"))
            .findFirst().orElse(null);
        Assertions.assertNotNull(ehEntry);
        // Lower tier than EV is HV
        Assertions.assertTrue(ehEntry.itemId().getPath().contains("hv_energy_input_hatch"));
        // 2 hatches per machine * 2 machines = 4
        Assertions.assertEquals(4, ehEntry.totalAmount());
    }

    @Test
    public void testDynamicIOSizing() {
        ResourceLocation controllerId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");

        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(controllerId, "Large Chemical Reactor", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_input_hatch"), "LV Input Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_output_hatch"), "LV Output Hatch", 1, PartCategory.HATCH_BUS)
        );
        MultiblockStructureCatalog.registerManualStructure(new MultiblockStructureDef(controllerId, "Large Chemical Reactor", parts, 0, 1, 1, 1, 1, 1, 1));

        RecipeNode node = new RecipeNode("lcr_node", "Large Chemical Reactor", 100, 100, GTVoltageTier.HV);
        node.setMachineIcon(controllerId);
        node.setMultiblock(true);
        node.setMachineCount(1.0);
        node.setTargetTier(GTVoltageTier.HV);

        // Recipe requiring 3 fluid inputs and 2 fluid outputs
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrogen"), "Nitrogen", 1000.0));
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 3000.0));
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 500.0));

        node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitric_acid"), "Nitric Acid", 1000.0));
        node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        MultiblockBOMSummary.BOMItemEntry inHatch = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().getPath().contains("input_hatch"))
            .findFirst().orElse(null);
        Assertions.assertNotNull(inHatch);
        Assertions.assertEquals(3, inHatch.totalAmount()); // 3 fluid inputs -> 3 input hatches

        MultiblockBOMSummary.BOMItemEntry outHatch = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().getPath().contains("output_hatch"))
            .findFirst().orElse(null);
        Assertions.assertNotNull(outHatch);
        Assertions.assertEquals(2, outHatch.totalAmount()); // 2 fluid outputs -> 2 output hatches
    }

    @Test
    public void testBOMSummaryFormattingAndSorting() {
        MultiblockBOMSummary.BOMItemEntry entry1 = new MultiblockBOMSummary.BOMItemEntry(
            ResourceLocation.tryParse("minecraft:stone"),
            "Stone",
            120,
            1,
            56,
            PartCategory.CASING,
            List.of("EBF (x2)")
        );

        Assertions.assertEquals("1 stacks + 56 (120)", entry1.formatStackCount());

        MultiblockBOMSummary.BOMItemEntry entry2 = new MultiblockBOMSummary.BOMItemEntry(
            ResourceLocation.tryParse("minecraft:stone"),
            "Stone",
            64,
            1,
            0,
            PartCategory.CASING,
            List.of("EBF (x1)")
        );
        Assertions.assertEquals("1 stacks (64)", entry2.formatStackCount());

        MultiblockBOMSummary.BOMItemEntry entry3 = new MultiblockBOMSummary.BOMItemEntry(
            ResourceLocation.tryParse("minecraft:stone"),
            "Stone",
            10,
            0,
            10,
            PartCategory.CASING,
            List.of("EBF (x1)")
        );
        Assertions.assertEquals("10 items", entry3.formatStackCount());
    }

    @Test
    public void testSingleblockMachineBOMCalculation() {
        RecipeNode singleNode1 = new RecipeNode("assembler_node", "MV Assembler", 100, 100, GTVoltageTier.MV);
        singleNode1.setMachineIcon(ResourceLocation.tryParse("gtceu:mv_assembler"));
        singleNode1.setMultiblock(false);
        singleNode1.setMachineCount(2.0);

        RecipeNode singleNode2 = new RecipeNode("macerator_node", "LV Macerator", 100, 100, GTVoltageTier.LV);
        singleNode2.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_macerator"));
        singleNode2.setMultiblock(false);
        singleNode2.setMachineCount(1.0);

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(singleNode1, singleNode2), false);

        Assertions.assertEquals(3, summary.totalMultiblockCount());
        Assertions.assertEquals(2, summary.totalUniqueItemTypes());

        MultiblockBOMSummary.BOMItemEntry assemblerEntry = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:mv_assembler")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(assemblerEntry);
        Assertions.assertEquals(2, assemblerEntry.totalAmount());
        Assertions.assertEquals(PartCategory.CONTROLLER, assemblerEntry.category());

        MultiblockBOMSummary.BOMItemEntry maceratorEntry = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:lv_macerator")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(maceratorEntry);
        Assertions.assertEquals(1, maceratorEntry.totalAmount());
    }

    @Test
    public void testBoilerAndUnregisteredItemBOMFiltering() {
        // 1. Steam Boiler (Liquid HP Steel)
        RecipeNode boiler = new RecipeNode("boiler_node", "Steam Boiler (용암)", 1000, 0, GTVoltageTier.ULV);
        boiler.setEnergyType(EnergyType.HEAT_OR_SELF);
        boiler.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_liquid_boiler"));
        boiler.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:lava"), "Lava", 100.0));
        boiler.setMachineIcon(GTBoilerTier.HP_STEEL.getDefaultIcon(true)); // gtceu:hp_steam_liquid_boiler
        boiler.setMachineCount(10.0);

        // 2. Mock Lava Node with unresolvable dummy ID (should be filtered or not resolve to Air)
        RecipeNode dummyNode = new RecipeNode("dummy_lava", "Lava (조약돌)", 200, 32, GTVoltageTier.LV);
        dummyNode.setMachineIcon(ResourceLocation.tryParse("minecraft:air"));
        dummyNode.setMachineCount(1.0);

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(boiler, dummyNode), false);

        // Verify that minecraft:air is NOT in aggregatedItems
        boolean hasAir = summary.aggregatedItems().stream().anyMatch(e -> e.itemId().getPath().equals("air") || e.displayName().equals("공기") || e.displayName().equalsIgnoreCase("air"));
        Assertions.assertFalse(hasAir, "BOM should never contain Air items");

        // Verify that HP Steel Liquid Boiler is present
        MultiblockBOMSummary.BOMItemEntry boilerEntry = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:hp_steam_liquid_boiler")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(boilerEntry);
        Assertions.assertEquals(10, boilerEntry.totalAmount());
    }

    @Test
    public void testCasingReductionWhenExtraHatchesInstalled() {
        ResourceLocation controllerId = ResourceLocation.tryParse("gtceu:large_distillery");

        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(controllerId, "Large Distillery", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:watertight_casing"), "Watertight Casing", 42, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:steel_pipe_casing"), "Steel Pipe Casing", 1, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"), "EV Energy Input Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_input_hatch"), "EV Input Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_output_hatch"), "EV Output Hatch", 1, PartCategory.HATCH_BUS)
        );
        MultiblockStructureCatalog.registerManualStructure(new MultiblockStructureDef(controllerId, "Large Distillery", parts, 0, 1, 1, 1, 1, 1, 1));

        RecipeNode node = new RecipeNode("lfd_node", "Large Distillery", 100, 100, GTVoltageTier.EV);
        node.setMachineIcon(controllerId);
        node.setMultiblock(true);
        node.setMachineCount(1.0);
        node.setTargetTier(GTVoltageTier.UIV);

        // Install 2 Energy Hatches (16A + 1A)
        com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon h16A = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
            "gtceu:uiv_16a_energy_input_hatch", "UIV 16A Energy Hatch", "", ResourceLocation.tryParse("gtceu:uiv_16a_energy_input_hatch"), GTVoltageTier.UIV, 16, false, false, false
        );
        com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon h1A = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
            "gtceu:uiv_energy_input_hatch", "UIV Energy Hatch", "", ResourceLocation.tryParse("gtceu:uiv_energy_input_hatch"), GTVoltageTier.UIV, 1, false, false, false
        );
        node.getAddons().add(h16A);
        node.getAddons().add(h1A);

        // Install 1 Parallel Hatch
        com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon parHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon(
            "gtceu:elite_parallel_control_hatch", "Elite Parallel Control Hatch", "", ResourceLocation.tryParse("gtceu:elite_parallel_control_hatch"), 64, false
        );
        node.getAddons().add(parHatch);

        // Calculate BOM: base hatches were 4 (1 energy, 1 maint, 1 in, 1 out).
        // resolved hatches are: 2 energy + 1 maint + 1 in + 1 out + 1 parallel = 6 (+2 extra hatches).
        // Watertight Casing must be reduced from 42 to 40 (42 - 2 = 40).
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        MultiblockBOMSummary.BOMItemEntry casing = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:watertight_casing")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(casing);
        Assertions.assertEquals(40, casing.totalAmount());
    }

    @Test
    public void testHybridSmartHatchOverrideWithMultiFluidHatch() {
        ResourceLocation controllerId = ResourceLocation.tryParse("gtceu:large_distillery");

        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(controllerId, "Large Distillery", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:watertight_casing"), "Watertight Casing", 42, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:steel_pipe_casing"), "Steel Pipe Casing", 1, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"), "EV Energy Input Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_input_hatch"), "EV Input Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_output_hatch"), "EV Output Hatch", 1, PartCategory.HATCH_BUS)
        );
        MultiblockStructureCatalog.registerManualStructure(new MultiblockStructureDef(controllerId, "Large Distillery", parts, 0, 1, 1, 1, 1, 1, 1));

        RecipeNode node = new RecipeNode("lfd_16x_node", "Large Distillery", 100, 100, GTVoltageTier.EV);
        node.setMachineIcon(controllerId);
        node.setMultiblock(true);
        node.setMachineCount(1.0);
        node.setTargetTier(GTVoltageTier.EV);

        // 11 Fluid outputs
        for (int i = 0; i < 11; i++) {
            node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_" + i), "Fluid " + i, 1000.0));
        }

        // Install 16x Output Hatch
        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon h16x = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:ev_output_hatch_16x", "EV 16x Output Hatch", "", ResourceLocation.tryParse("gtceu:ev_output_hatch_16x"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT, GTVoltageTier.EV, 16, 2048000L, false
        );
        node.getAddons().add(h16x);

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        // Check that 16x output hatch is present
        MultiblockBOMSummary.BOMItemEntry hatch16xEntry = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_output_hatch_16x")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(hatch16xEntry);
        Assertions.assertEquals(1, hatch16xEntry.totalAmount());

        // Check that standard 1x output hatch was completely replaced (not present in BOM)
        MultiblockBOMSummary.BOMItemEntry standardHatch = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_output_hatch")))
            .findFirst().orElse(null);
        Assertions.assertNull(standardHatch);

        // Casing count: 42 casings (16x hatch replaces the 1 base output hatch)
        MultiblockBOMSummary.BOMItemEntry casing = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:watertight_casing")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(casing);
        Assertions.assertEquals(42, casing.totalAmount());
    }

    @Test
    public void testHybridSmartHatchOverridePartialCoverage() {
        ResourceLocation controllerId = ResourceLocation.tryParse("gtceu:large_distillery");

        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(controllerId, "Large Distillery", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:watertight_casing"), "Watertight Casing", 42, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:steel_pipe_casing"), "Steel Pipe Casing", 1, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"), "EV Energy Input Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_input_hatch"), "EV Input Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_output_hatch"), "EV Output Hatch", 1, PartCategory.HATCH_BUS)
        );
        MultiblockStructureCatalog.registerManualStructure(new MultiblockStructureDef(controllerId, "Large Distillery", parts, 0, 1, 1, 1, 1, 1, 1));

        RecipeNode node = new RecipeNode("lfd_4x_node", "Large Distillery", 100, 100, GTVoltageTier.EV);
        node.setMachineIcon(controllerId);
        node.setMultiblock(true);
        node.setMachineCount(1.0);
        node.setTargetTier(GTVoltageTier.EV);

        // 11 Fluid outputs
        for (int i = 0; i < 11; i++) {
            node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_" + i), "Fluid " + i, 1000.0));
        }

        // Install 4x Output Hatch (covers 4, remaining 7 needed)
        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon h4x = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:ev_output_hatch_4x", "EV 4x Output Hatch", "", ResourceLocation.tryParse("gtceu:ev_output_hatch_4x"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT, GTVoltageTier.EV, 4, 512000L, false
        );
        node.getAddons().add(h4x);

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        // Check 4x hatch: 1
        MultiblockBOMSummary.BOMItemEntry h4xEntry = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_output_hatch_4x")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(h4xEntry);
        Assertions.assertEquals(1, h4xEntry.totalAmount());

        // Check standard 1x output hatch: 7 (11 - 4 = 7)
        MultiblockBOMSummary.BOMItemEntry standardHatch = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_output_hatch")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(standardHatch);
        Assertions.assertEquals(7, standardHatch.totalAmount());

        // Casing count: 42 - 7 = 35 (1 base output hatch + 7 extra = 8 output hatches total)
        MultiblockBOMSummary.BOMItemEntry casing = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:watertight_casing")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(casing);
        Assertions.assertEquals(35, casing.totalAmount());
    }

    @Test
    public void testHatchSafetyValidationWarnings() {
        var adapter = new com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter();

        RecipeNode node = new RecipeNode("val_node", "Centrifuge", 100, 100, GTVoltageTier.LV);
        node.setMultiblock(true);

        // Recipe has 5 item outputs
        for (int i = 0; i < 5; i++) {
            node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:item_" + i), "Item " + i, 1.0));
        }

        // Equip LV Output Bus (only 4 slots)
        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon lvBus = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:lv_output_bus", "LV Output Bus", "", ResourceLocation.tryParse("gtceu:lv_output_bus"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.ITEM_OUTPUT, GTVoltageTier.LV, 4, 0L, false
        );
        node.getAddons().add(lvBus);

        java.util.List<net.minecraft.network.chat.Component> warnings = new java.util.ArrayList<>();
        boolean valid = adapter.validateNode(node, warnings);

        Assertions.assertFalse(valid);
        Assertions.assertFalse(warnings.isEmpty());
        Assertions.assertTrue(warnings.stream().anyMatch(w -> w.getString().contains("item_slot_deficit") || w.getString().contains("item output") || w.getString().contains("출력 슬롯")));

        // Test Tank Capacity Overflow
        RecipeNode fluidNode = new RecipeNode("fluid_val_node", "Large Chemical Reactor", 100, 100, GTVoltageTier.LV);
        fluidNode.setMultiblock(true);
        fluidNode.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 160000.0)); // 160,000 mB

        // Equip LV Output Hatch (16,000 mB capacity)
        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon lvFluidHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:lv_output_hatch", "LV Output Hatch", "", ResourceLocation.tryParse("gtceu:lv_output_hatch"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT, GTVoltageTier.LV, 1, 16000L, false
        );
        fluidNode.getAddons().add(lvFluidHatch);

        java.util.List<net.minecraft.network.chat.Component> fluidWarnings = new java.util.ArrayList<>();
        boolean fluidValid = adapter.validateNode(fluidNode, fluidWarnings);

        Assertions.assertFalse(fluidValid);
        Assertions.assertFalse(fluidWarnings.isEmpty());
        Assertions.assertTrue(fluidWarnings.stream().anyMatch(w -> w.getString().contains("tank_capacity_overflow") || w.getString().contains("Tank overflow") || w.getString().contains("용량 초과")));
    }

    @Test
    public void testShapeVariantMatchingForDifferentPortRequirements() {
        ResourceLocation dtId = ResourceLocation.tryParse("gtceu:test_distillation_tower");

        // Variant 1: 2-layer tower (1 output hatch, 20 casings)
        MultiblockStructureDef var2Layer = new MultiblockStructureDef(
            dtId, "Test Distillation Tower",
            List.of(
                new MultiblockStructurePart(dtId, "Test Distillation Tower", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:clean_machine_casing"), "Clean Casing", 20, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"), "EV Energy Input Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_input_hatch"), "EV Input Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_output_hatch"), "EV Output Hatch", 1, PartCategory.HATCH_BUS)
            ),
            0, 1, 1, 1, 1, 1, 1
        );

        // Variant 2: 5-layer tower (4 output hatches, 38 casings)
        MultiblockStructureDef var5Layer = new MultiblockStructureDef(
            dtId, "Test Distillation Tower",
            List.of(
                new MultiblockStructurePart(dtId, "Test Distillation Tower", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:clean_machine_casing"), "Clean Casing", 38, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"), "EV Energy Input Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_input_hatch"), "EV Input Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_output_hatch"), "EV Output Hatch", 4, PartCategory.HATCH_BUS)
            ),
            0, 1, 1, 1, 1, 4, 1
        );

        // Variant 3: 12-layer tower (11 output hatches, 82 casings)
        MultiblockStructureDef var12Layer = new MultiblockStructureDef(
            dtId, "Test Distillation Tower",
            List.of(
                new MultiblockStructurePart(dtId, "Test Distillation Tower", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:clean_machine_casing"), "Clean Casing", 82, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"), "EV Energy Input Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_input_hatch"), "EV Input Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_output_hatch"), "EV Output Hatch", 11, PartCategory.HATCH_BUS)
            ),
            0, 1, 1, 1, 1, 11, 1
        );

        MultiblockStructureCatalog.registerManualStructure(var2Layer);
        MultiblockStructureCatalog.registerManualStructure(var5Layer);
        MultiblockStructureCatalog.registerManualStructure(var12Layer);

        // Test 1: Node with 4 fluid outputs -> Should pick 5-layer variant (38 casings, 4 output hatches)
        RecipeNode node4 = new RecipeNode("dt_4_node", "DT 4-out", 100, 100, GTVoltageTier.EV);
        node4.setMachineIcon(dtId);
        node4.setMultiblock(true);
        node4.setMachineCount(1.0);
        node4.setTargetTier(GTVoltageTier.EV);
        for (int i = 0; i < 4; i++) {
            node4.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:f_" + i), "Fluid " + i, 1000.0));
        }

        MultiblockBOMSummary summary4 = MultiblockBOMCalculator.calculateBOM(List.of(node4), false);
        MultiblockBOMSummary.BOMItemEntry casing4 = summary4.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:clean_machine_casing")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(casing4);
        Assertions.assertEquals(38, casing4.totalAmount());

        MultiblockBOMSummary.BOMItemEntry hatch4 = summary4.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_output_hatch")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(hatch4);
        Assertions.assertEquals(4, hatch4.totalAmount());

        // Test 2: Node with 11 fluid outputs -> Should pick 12-layer variant (82 casings, 11 output hatches)
        RecipeNode node11 = new RecipeNode("dt_11_node", "DT 11-out", 100, 100, GTVoltageTier.EV);
        node11.setMachineIcon(dtId);
        node11.setMultiblock(true);
        node11.setMachineCount(1.0);
        node11.setTargetTier(GTVoltageTier.EV);
        for (int i = 0; i < 11; i++) {
            node11.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:f_" + i), "Fluid " + i, 1000.0));
        }

        MultiblockBOMSummary summary11 = MultiblockBOMCalculator.calculateBOM(List.of(node11), false);
        MultiblockBOMSummary.BOMItemEntry casing11 = summary11.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:clean_machine_casing")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(casing11);
        Assertions.assertEquals(82, casing11.totalAmount());

        // Test 3: Large Chemical Reactor with 4 fluid outputs + 4x Output Hatch equipped -> 1 4x hatch
        ResourceLocation lcrId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        RecipeNode node4x = new RecipeNode("lcr_4x_node", "Large Chemical Reactor", 100, 100, GTVoltageTier.EV);
        node4x.setMachineIcon(lcrId);
        node4x.setMultiblock(true);
        node4x.setMachineCount(1.0);
        node4x.setTargetTier(GTVoltageTier.EV);
        for (int i = 0; i < 4; i++) {
            node4x.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:f_" + i), "Fluid " + i, 1000.0));
        }
        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon h4x = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:ev_output_hatch_4x", "EV 4x Output Hatch", "", ResourceLocation.tryParse("gtceu:ev_output_hatch_4x"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT, GTVoltageTier.EV, 4, 64000L, false
        );
        node4x.getAddons().add(h4x);

        MultiblockBOMSummary summary4x = MultiblockBOMCalculator.calculateBOM(List.of(node4x), false);
        MultiblockBOMSummary.BOMItemEntry h4xEntry = summary4x.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_output_hatch_4x")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(h4xEntry);
        Assertions.assertEquals(1, h4xEntry.totalAmount());
    }

    @Test
    void testDistillationTowerRejectsMultiFluidHatches() {
        RecipeNode dtNode = new RecipeNode("dt_node", "Distillation Tower", 100, 100, GTVoltageTier.EV);
        dtNode.setMachineIcon(ResourceLocation.tryParse("gtceu:distillation_tower"));
        dtNode.setMultiblock(true);

        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon singleHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:ev_output_hatch", "EV Output Hatch", "", ResourceLocation.tryParse("gtceu:ev_output_hatch"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT, GTVoltageTier.EV, 1, 16000L, false
        );

        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon nonupleHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:ev_output_hatch_9x", "EV Nonuple Output Hatch", "", ResourceLocation.tryParse("gtceu:ev_output_hatch_9x"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT, GTVoltageTier.EV, 9, 144000L, false
        );

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(dtNode);
        Assertions.assertTrue(adapter.isAddonCompatible(dtNode, singleHatch));
        Assertions.assertTrue(adapter.canInstallAddon(dtNode, singleHatch));

        Assertions.assertFalse(adapter.isAddonCompatible(dtNode, nonupleHatch));
        Assertions.assertFalse(adapter.canInstallAddon(dtNode, nonupleHatch));

        dtNode.getAddons().add(nonupleHatch);
        List<net.minecraft.network.chat.Component> warnings = new java.util.ArrayList<>();
        boolean valid = adapter.validateNode(dtNode, warnings);
        Assertions.assertFalse(valid);
        Assertions.assertTrue(warnings.stream().anyMatch(w -> w.getString().contains("dt_multi_fluid_unsupported") || w.getString().contains("증류탑") || w.getString().contains("Distillation Tower")));
    }

    @Test
    void testDistillationTowerMaxOutputHatchesStrictlyFollowsRecipe() {
        RecipeNode dt5 = new RecipeNode("dt_5_node", "Distillation Tower (Wood Tar)", 100, 100, GTVoltageTier.HV);
        dt5.setMachineIcon(ResourceLocation.tryParse("gtceu:distillation_tower"));
        dt5.setMultiblock(true);
        // 5 fluid outputs (like Wood Tar)
        for (int i = 0; i < 5; i++) {
            dt5.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_" + i), "Fluid " + i, 100.0));
        }

        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon lvHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:lv_output_hatch", "LV Output Hatch", "", ResourceLocation.tryParse("gtceu:lv_output_hatch"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT, GTVoltageTier.LV, 1, 16000L, false
        );

        int maxSlots = com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog.getMaxHatchSlotsAllowed(dt5, lvHatch);
        Assertions.assertEquals(5, maxSlots);

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(dt5);
        // Install 5 hatches
        for (int i = 0; i < 5; i++) {
            Assertions.assertTrue(adapter.canInstallAddon(dt5, lvHatch));
            dt5.getAddons().add(lvHatch);
        }

        // 6th hatch installation must be rejected!
        Assertions.assertFalse(adapter.canInstallAddon(dt5, lvHatch));

        // If forced, validateNode must produce warning
        dt5.getAddons().add(lvHatch);
        List<net.minecraft.network.chat.Component> warnings = new java.util.ArrayList<>();
        boolean valid = adapter.validateNode(dt5, warnings);
        Assertions.assertFalse(valid);
        Assertions.assertTrue(warnings.stream().anyMatch(w -> w.getString().contains("dt_excess_output_hatches") || w.getString().contains("유체 분획") || w.getString().contains("fractions")));
    }

    @Test
    void testDeductiveBlueprintAllowedHatchFiltering() {
        ResourceLocation mbId = ResourceLocation.tryParse("gtceu:mock_structure_no_bus");
        MultiblockStructureDef mockDef = new MultiblockStructureDef(
            mbId, "Mock Fluid Tower",
            List.of(
                new MultiblockStructurePart(mbId, "Mock Fluid Tower", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:clean_machine_casing"), "Clean Casing", 82, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"), "EV Energy Input Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_input_hatch"), "EV Input Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_output_hatch"), "EV Output Hatch", 11, PartCategory.HATCH_BUS)
            ),
            0, 1, 0, 0, 1, 11, 1,
            java.util.Set.of("INPUT_ENERGY", "IMPORT_FLUIDS", "EXPORT_FLUIDS", "MAINTENANCE"),
            java.util.Set.of(ResourceLocation.tryParse("gtceu:clean_machine_casing"))
        );
        MultiblockStructureCatalog.registerManualStructure(mockDef);

        RecipeNode node = new RecipeNode("mock_blueprint_test", "Mock Fluid Tower", 100, 100, GTVoltageTier.EV);
        node.setMachineIcon(mbId);
        node.setMultiblock(true);

        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon outputBus = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:lv_output_bus", "LV Output Bus", "", ResourceLocation.tryParse("gtceu:lv_output_bus"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.ITEM_OUTPUT, GTVoltageTier.LV, 4, 0L, false
        );

        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon inputHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:lv_input_hatch", "LV Input Hatch", "", ResourceLocation.tryParse("gtceu:lv_input_hatch"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_INPUT, GTVoltageTier.LV, 1, 16000L, false
        );

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);

        // Blueprint has 0 output buses -> output bus is NOT compatible!
        Assertions.assertFalse(adapter.isAddonCompatible(node, outputBus));
        Assertions.assertFalse(adapter.canInstallAddon(node, outputBus));

        // Blueprint has 1 input hatch -> input hatch is compatible!
        Assertions.assertTrue(adapter.isAddonCompatible(node, inputHatch));
        Assertions.assertTrue(adapter.canInstallAddon(node, inputHatch));
    }

    @Test
    void testMultipleHatchInstallationAndBOM() {
        ResourceLocation dtId = ResourceLocation.tryParse("gtceu:test_distillation_tower_multi");
        MultiblockStructureDef var12Layer = new MultiblockStructureDef(
            dtId, "Test Distillation Tower",
            List.of(
                new MultiblockStructurePart(dtId, "Test Distillation Tower", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:clean_machine_casing"), "Clean Casing", 82, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"), "EV Energy Input Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_input_hatch"), "EV Input Hatch", 1, PartCategory.HATCH_BUS),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ev_output_hatch"), "EV Output Hatch", 11, PartCategory.HATCH_BUS)
            ),
            0, 1, 1, 1, 1, 11, 1
        );
        MultiblockStructureCatalog.registerManualStructure(var12Layer);

        RecipeNode node = new RecipeNode("dt_multi_hatch", "Distillation Tower", 100, 100, GTVoltageTier.EV);
        node.setMachineIcon(dtId);
        node.setMultiblock(true);
        node.setMachineCount(1.0);
        node.setTargetTier(GTVoltageTier.EV);
        for (int i = 0; i < 11; i++) {
            node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:f_" + i), "Fluid " + i, 1000.0));
        }

        com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon lvHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon(
            "gtceu:lv_output_hatch", "LV Output Hatch", "", ResourceLocation.tryParse("gtceu:lv_output_hatch"),
            com.gtceu.calcboard.compat.gtceu.addon.GTHatchAddon.HatchType.FLUID_OUTPUT, GTVoltageTier.LV, 1, 16000L, false
        );

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        // Install 11 LV Output Hatches
        for (int i = 0; i < 11; i++) {
            adapter.handleInstallAddon(node, lvHatch, false);
        }

        Assertions.assertEquals(11, node.getAddons().size());
        Assertions.assertEquals(11, adapter.getAddonInstalledCount(node, lvHatch));

        // Check BOM contains 11 LV Output Hatches and 82 Clean Machine Casings
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);
        MultiblockBOMSummary.BOMItemEntry lvEntry = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:lv_output_hatch")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(lvEntry);
        Assertions.assertEquals(11, lvEntry.totalAmount());

        MultiblockBOMSummary.BOMItemEntry casingEntry = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:clean_machine_casing")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(casingEntry);
        Assertions.assertEquals(82, casingEntry.totalAmount());
    }

    @Test
    public void testMaintenanceHatchAndMufflerBOMResolution() {
        ResourceLocation boilerId = ResourceLocation.tryParse("gtceu:bronze_large_boiler");
        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(boilerId, "Bronze Large Boiler", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:bronze_firebox_casing"), "Bronze Firebox Casing", 20, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:auto_maintenance_hatch"), "Auto Maintenance Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:iv_muffler_hatch"), "IV Muffler Hatch", 1, PartCategory.HATCH_BUS)
        );
        MultiblockStructureDef def = new MultiblockStructureDef(boilerId, "Bronze Large Boiler", parts, 0, 0, 0, 0, 1, 1, 1);
        MultiblockStructureCatalog.registerManualStructure(def);

        RecipeNode node = new RecipeNode("boiler_node", "Large Bronze Boiler", 0, 0, GTVoltageTier.ULV);
        node.setMachineIcon(boilerId);
        node.setMultiblock(true);
        node.setMachineCount(1.0);

        // 1. Default (no maintenance addon equipped) -> should resolve to gtceu:maintenance_hatch and gtceu:lv_muffler_hatch
        MultiblockBOMSummary defaultSummary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);
        Assertions.assertTrue(defaultSummary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:maintenance_hatch"))));
        Assertions.assertTrue(defaultSummary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:lv_muffler_hatch"))));
        Assertions.assertFalse(defaultSummary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:iv_muffler_hatch"))));

        // 2. Equip Auto Maintenance Hatch -> should resolve to gtceu:auto_maintenance_hatch in BOM
        MachineAddon autoMaint = new MachineAddon(
            "gtceu:auto_maintenance_hatch", "Auto Maintenance Hatch", AddonCategory.MAINTENANCE, "", ResourceLocation.tryParse("gtceu:auto_maintenance_hatch")
        );
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        adapter.handleInstallAddon(node, autoMaint, false);

        MultiblockBOMSummary autoSummary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);
        Assertions.assertTrue(autoSummary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:auto_maintenance_hatch"))));
        Assertions.assertFalse(autoSummary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:maintenance_hatch"))));
        Assertions.assertTrue(autoSummary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:lv_muffler_hatch"))));

        // 3. Equip EV Muffler Hatch -> should have BOTH Auto Maintenance Hatch AND EV Muffler Hatch
        MachineAddon evMuffler = new MachineAddon(
            "gtceu:ev_muffler_hatch", "EV Muffler Hatch", AddonCategory.MAINTENANCE, "", ResourceLocation.tryParse("gtceu:ev_muffler_hatch")
        );
        adapter.handleInstallAddon(node, evMuffler, false);
        Assertions.assertEquals(2, node.getAddons().size());

        MultiblockBOMSummary dualSummary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);
        Assertions.assertTrue(dualSummary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:auto_maintenance_hatch"))));
        Assertions.assertTrue(dualSummary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_muffler_hatch"))));
        Assertions.assertFalse(dualSummary.aggregatedItems().stream().anyMatch(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:lv_muffler_hatch"))));
    }

    @Test
    public void testMultiblockWithoutExplicitHatchesInShapeGetsAutomaticHatchesAndCasingReduction() {
        ResourceLocation greenhouseId = ResourceLocation.tryParse("gtceu:tree_synthesizer");

        // Multiblock structure with NO hatches in preview shape (only controller, casing, glass, dirt)
        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(greenhouseId, "Tree Synthesizer", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:solid_machine_casing"), "Solid Machine Casing", 48, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("minecraft:glass"), "Glass", 12, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("minecraft:dirt"), "Dirt", 9, PartCategory.CASING)
        );
        MultiblockStructureDef def = new MultiblockStructureDef(greenhouseId, "Tree Synthesizer", parts, 0, 0, 0, 0, 0, 0, 0);
        MultiblockStructureCatalog.registerManualStructure(def);

        RecipeNode node = new RecipeNode("tree_greenhouse_node", "Tree Greenhouse (Water)", 100, 100, GTVoltageTier.EV);
        node.setMachineIcon(greenhouseId);
        node.setMultiblock(true);
        node.setMachineCount(1.0);
        node.setTargetTier(GTVoltageTier.EV);

        // Inputs: 1 Fluid (Water) + 1 Item (Fertilizer)
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:fertilizer"), "Fertilizer", 1.0));

        // Outputs: 1 Fluid (Oxygen) + 2 Items (Oak Log, Oak Sapling)
        node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 100.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 16.0));
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_sapling"), "Oak Sapling", 2.0));

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        // 1. Check Energy Hatch
        MultiblockBOMSummary.BOMItemEntry energyHatch = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(energyHatch, "Energy Input Hatch should be automatically added");
        Assertions.assertEquals(1, energyHatch.totalAmount());

        // 2. Check Input Bus (1 item input -> 1 EV input bus)
        MultiblockBOMSummary.BOMItemEntry inputBus = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_input_bus")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(inputBus, "Input Bus should be automatically added");
        Assertions.assertEquals(1, inputBus.totalAmount());

        // 3. Check Output Bus (2 item outputs fit in 1 EV output bus of 16 slots)
        MultiblockBOMSummary.BOMItemEntry outputBus = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_output_bus")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(outputBus, "Output Bus should be automatically added");
        Assertions.assertEquals(1, outputBus.totalAmount());

        // 4. Check Input Hatch (1 fluid input -> 1 EV input hatch)
        MultiblockBOMSummary.BOMItemEntry inputHatch = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_input_hatch")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(inputHatch, "Input Hatch should be automatically added");
        Assertions.assertEquals(1, inputHatch.totalAmount());

        // 5. Check Output Hatch (1 fluid output -> 1 EV output hatch)
        MultiblockBOMSummary.BOMItemEntry outputHatch = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:ev_output_hatch")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(outputHatch, "Output Hatch should be automatically added");
        Assertions.assertEquals(1, outputHatch.totalAmount());

        // 6. Check Maintenance Hatch (1 maintenance hatch)
        MultiblockBOMSummary.BOMItemEntry maintHatch = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:maintenance_hatch")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(maintHatch, "Maintenance Hatch should be automatically added");
        Assertions.assertEquals(1, maintHatch.totalAmount());

        // 7. Check Casing Reduction:
        // Total extra hatches = 1 (energy) + 1 (in bus) + 1 (out bus) + 1 (in hatch) + 1 (out hatch) + 1 (maint) = 6 hatches.
        // Solid Machine Casing was 48, reduced by 6 -> 42 casings!
        MultiblockBOMSummary.BOMItemEntry casing = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:solid_machine_casing")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(casing);
        Assertions.assertEquals(42, casing.totalAmount(), "Solid Machine Casing should be reduced by 6 (48 - 6 = 42)");

        // 8. Non-replaceable casings (Glass, Dirt) remain unchanged
        MultiblockBOMSummary.BOMItemEntry glass = summary.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("minecraft:glass")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(glass);
        Assertions.assertEquals(12, glass.totalAmount());

        // 9. Check Addon Categories: Non-coil multiblock (Tree Synthesizer) MUST NOT have COIL category
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        List<AddonCategory> applicableCats = adapter.getApplicableAddonCategories(node);
        Assertions.assertTrue(applicableCats.contains(AddonCategory.ENERGY_HATCH));
        Assertions.assertTrue(applicableCats.contains(AddonCategory.HATCH_BUS));
        Assertions.assertTrue(applicableCats.contains(AddonCategory.MAINTENANCE));
        Assertions.assertFalse(applicableCats.contains(AddonCategory.COIL), "Tree Synthesizer MUST NOT have COIL category");
    }

    @Test
    public void testOptionalParallelHatchAndUnusedIOExcludedAndCasingRestored() {
        ResourceLocation dtId = ResourceLocation.tryParse("gtceu:distillation_tower_sample");

        // Structure def containing preview sample: Casing 63, Pipe 4, Energy 1, Maint 1, Muffler 1, InputHatch 1, OutputHatch 4, OutputBus 1, EliteParallel 1 (Total: 78 blocks)
        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(dtId, "Distillation Tower", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:watertight_casing"), "Watertight Casing", 63, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:steel_pipe_casing"), "Steel Pipe Casing", 4, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:mv_energy_input_hatch"), "MV Energy Input Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:maintenance_hatch"), "Maintenance Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:mv_muffler_hatch"), "MV Muffler Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:mv_input_hatch"), "MV Input Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:mv_output_hatch"), "MV Output Hatch", 4, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:mv_output_bus"), "MV Output Bus", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:elite_parallel_control_hatch"), "Elite Parallel Control Hatch", 1, PartCategory.HATCH_BUS)
        );
        MultiblockStructureDef def = new MultiblockStructureDef(dtId, "Distillation Tower", parts, 0, 1, 0, 1, 1, 4, 1);
        MultiblockStructureCatalog.registerManualStructure(def);

        // Case 1: Fluid-only recipe without parallel addon
        RecipeNode nodeNoParallel = new RecipeNode("dt_fluid_only", "Distillation Tower (Naphtha)", 100, 100, GTVoltageTier.MV);
        nodeNoParallel.setMachineIcon(dtId);
        nodeNoParallel.setMultiblock(true);
        nodeNoParallel.setMachineCount(1.0);
        nodeNoParallel.setTargetTier(GTVoltageTier.MV);

        // 1 Fluid Input, 4 Fluid Outputs, NO Items
        nodeNoParallel.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:naphtha"), "Naphtha", 1000.0));
        for (int i = 0; i < 4; i++) {
            nodeNoParallel.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fraction_" + i), "Fraction " + i, 250.0));
        }

        MultiblockBOMSummary summary1 = MultiblockBOMCalculator.calculateBOM(List.of(nodeNoParallel), false);

        // Parallel Hatch MUST NOT be present
        boolean hasParallel = summary1.aggregatedItems().stream().anyMatch(e -> e.itemId().getPath().contains("parallel"));
        Assertions.assertFalse(hasParallel, "Parallel hatch must NOT be in BOM when not equipped");

        // Watertight Casing must be restored from 63 to 64 (from removed parallel hatch)
        MultiblockBOMSummary.BOMItemEntry casing1 = summary1.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:watertight_casing")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(casing1);
        Assertions.assertEquals(64, casing1.totalAmount(), "Watertight casing must be restored from 63 to 64");

        // Total blocks must equal exactly 78
        int totalBlocks1 = summary1.aggregatedItems().stream().mapToInt(MultiblockBOMSummary.BOMItemEntry::totalAmount).sum();
        Assertions.assertEquals(78, totalBlocks1, "Total structure block count must be perfectly preserved");

        // Case 2: Fluid-only recipe WITH Advanced Parallel Hatch equipped
        RecipeNode nodeWithParallel = new RecipeNode("dt_with_parallel", "Distillation Tower (Naphtha + Parallel)", 100, 100, GTVoltageTier.MV);
        nodeWithParallel.setMachineIcon(dtId);
        nodeWithParallel.setMultiblock(true);
        nodeWithParallel.setMachineCount(1.0);
        nodeWithParallel.setTargetTier(GTVoltageTier.MV);
        nodeWithParallel.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:naphtha"), "Naphtha", 1000.0));
        for (int i = 0; i < 4; i++) {
            nodeWithParallel.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fraction_" + i), "Fraction " + i, 250.0));
        }

        com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon advParallel = new com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon(
            "gtceu:advanced_parallel_control_hatch", "Advanced Parallel Control Hatch", "", ResourceLocation.tryParse("gtceu:advanced_parallel_control_hatch"), 16, false
        );
        nodeWithParallel.getAddons().add(advParallel);

        MultiblockBOMSummary summary2 = MultiblockBOMCalculator.calculateBOM(List.of(nodeWithParallel), false);

        // Advanced Parallel Hatch MUST be present (amount 1)
        MultiblockBOMSummary.BOMItemEntry parEntry = summary2.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:advanced_parallel_control_hatch")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(parEntry);
        Assertions.assertEquals(1, parEntry.totalAmount());

        // Watertight Casing: 63
        MultiblockBOMSummary.BOMItemEntry casing2 = summary2.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:watertight_casing")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(casing2);
        Assertions.assertEquals(63, casing2.totalAmount());

        // Total blocks must still equal 78
        int totalBlocks2 = summary2.aggregatedItems().stream().mapToInt(MultiblockBOMSummary.BOMItemEntry::totalAmount).sum();
        Assertions.assertEquals(78, totalBlocks2, "Total structure block count must be perfectly preserved");
    }

    @Test
    public void testOutputBusSlotCapacityScaling() {
        ResourceLocation controllerId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(controllerId, "Electric Blast Furnace", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:heatproof_machine_casing"), "Heat Proof Casing", 10, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:cupronickel_coil_block"), "Cupronickel Coil", 16, PartCategory.COIL),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), "LV Energy Hatch", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_input_bus"), "LV Input Bus", 1, PartCategory.HATCH_BUS),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_output_bus"), "LV Output Bus", 1, PartCategory.HATCH_BUS)
        );
        MultiblockStructureCatalog.registerManualStructure(new MultiblockStructureDef(controllerId, "Electric Blast Furnace", parts, 16, 1, 1, 1, 1, 1, 1));

        RecipeNode node = new RecipeNode("ebf_5_outputs", "Electric Blast Furnace", 100, 100, GTVoltageTier.LV);
        node.setMachineIcon(controllerId);
        node.setMultiblock(true);
        node.setMachineCount(1.0);
        node.setTargetTier(GTVoltageTier.LV);

        // 5 distinct item outputs
        for (int i = 1; i <= 5; i++) {
            node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:item_" + i), "Item " + i, 1.0));
        }

        // At LV tier (4 slots per LV output bus): ceil(5 / 4) = 2 LV Output Buses required (not 5!)
        MultiblockBOMSummary summaryLV = MultiblockBOMCalculator.calculateBOM(List.of(node), false);
        MultiblockBOMSummary.BOMItemEntry lvBusEntry = summaryLV.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:lv_output_bus")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(lvBusEntry);
        Assertions.assertEquals(2, lvBusEntry.totalAmount(), "5 output items require 2 LV Output Buses (4 slots each = 8 slots)");

        // At MV tier (9 slots per MV output bus): ceil(5 / 9) = 1 MV Output Bus required
        node.setTargetTier(GTVoltageTier.MV);
        MultiblockBOMSummary summaryMV = MultiblockBOMCalculator.calculateBOM(List.of(node), false);
        MultiblockBOMSummary.BOMItemEntry mvBusEntry = summaryMV.aggregatedItems().stream()
            .filter(e -> e.itemId().equals(ResourceLocation.tryParse("gtceu:mv_output_bus")))
            .findFirst().orElse(null);
        Assertions.assertNotNull(mvBusEntry);
        Assertions.assertEquals(1, mvBusEntry.totalAmount(), "5 output items require 1 MV Output Bus (9 slots)");
    }
}



