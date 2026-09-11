package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.bom.MultiblockBOMCalculator;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

public class StructuralCoilProtectionTest {

    @Test
    @DisplayName("Structural fixed coil casing does not trigger functional coil multiblock detection")
    public void testStructuralFixedCoilNotTreatedAsCoilMultiblock() {
        ResourceLocation fixedControllerId = ResourceLocation.tryParse("gtceu:fixed_casing_machine");
        MultiblockStructureDef def = createFixedCoilStructureDef(fixedControllerId);
        MultiblockStructureCatalog.registerManualStructure(def);

        Assertions.assertFalse(MultiblockDetector.isCoilMultiblock(fixedControllerId));
        Assertions.assertEquals(0, def.coilSlotCount());

        RecipeNode node = new RecipeNode("test_fixed", "Fixed Casing Machine", 100, 100, GTVoltageTier.MV);
        node.setMachineIcon(fixedControllerId);
        node.setMultiblock(true);
        List<AddonCategory> activeCategories = CategoryCapability.DEFAULT.getActiveCategoriesForNode(node);
        Assertions.assertFalse(activeCategories.contains(AddonCategory.COIL));
    }

    @Test
    @DisplayName("Structural fixed coil block remains untouched in BOM even if addon coil is attached")
    public void testStructuralFixedCoilPreservedInBOM() {
        ResourceLocation fixedControllerId = ResourceLocation.tryParse("gtceu:structural_kiln");
        MultiblockStructureDef def = createFixedCoilStructureDef(fixedControllerId);
        MultiblockStructureCatalog.registerManualStructure(def);

        RecipeNode node = createNodeWithCoilAddon(fixedControllerId);
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        MultiblockBOMSummary.BOMItemEntry cuproEntry = findBOMEntryById(summary, ResourceLocation.tryParse("gtceu:cupronickel_coil_block"));
        Assertions.assertNotNull(cuproEntry);
        Assertions.assertEquals(16, cuproEntry.totalAmount());
        Assertions.assertEquals("Cupronickel Coil Block", cuproEntry.displayName());

        MultiblockBOMSummary.BOMItemEntry kanthalEntry = findBOMEntryById(summary, ResourceLocation.tryParse("gtceu:kanthal_coil_block"));
        Assertions.assertNull(kanthalEntry);
    }

    @Test
    @DisplayName("Functional coil multiblock correctly substitutes coil in BOM")
    public void testFunctionalCoilReplacedInBOM() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        MultiblockStructureDef def = createFunctionalCoilStructureDef(ebfId);
        MultiblockStructureCatalog.registerManualStructure(def);

        RecipeNode node = createNodeWithCoilAddon(ebfId);
        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(node), false);

        MultiblockBOMSummary.BOMItemEntry kanthalEntry = findBOMEntryById(summary, ResourceLocation.tryParse("gtceu:kanthal_coil_block"));
        Assertions.assertNotNull(kanthalEntry);
        Assertions.assertEquals(16, kanthalEntry.totalAmount());
        Assertions.assertEquals("Kanthal Heating Coil", kanthalEntry.displayName());
    }

    @Test
    @DisplayName("Liquefaction Tower correctly detects coil capability and exposes coil tab")
    public void testLiquefactionTowerCoilCapability() {
        ResourceLocation towerId = ResourceLocation.tryParse("gtceu:liquefaction_tower");
        MultiblockStructureDef def = createFunctionalCoilStructureDef(towerId);
        MultiblockStructureCatalog.registerManualStructure(def);

        Assertions.assertTrue(MultiblockDetector.isCoilMultiblock(towerId));

        RecipeNode node = new RecipeNode("test_tower", "Liquefaction Tower", 100, 100, GTVoltageTier.MV);
        node.setMachineIcon(towerId);
        node.setMultiblock(true);
        List<AddonCategory> activeCategories = CategoryCapability.DEFAULT.getActiveCategoriesForNode(node);
        Assertions.assertTrue(activeCategories.contains(AddonCategory.COIL));
    }

    @Test
    @DisplayName("Nuclear Fuel Factory correctly detects coil capability and exposes coil tab")
    public void testNuclearFuelFactoryCoilCapability() {
        ResourceLocation nffId = ResourceLocation.tryParse("gtceu:nuclear_fuel_factory");
        MultiblockStructureDef def = createFunctionalCoilStructureDef(nffId);
        MultiblockStructureCatalog.registerManualStructure(def);

        Assertions.assertTrue(MultiblockDetector.isCoilMultiblock(nffId));

        RecipeNode node = new RecipeNode("test_nff", "Nuclear Fuel Factory", 100, 100, GTVoltageTier.EV);
        node.setMachineIcon(nffId);
        node.setMultiblock(true);
        List<AddonCategory> activeCategories = CategoryCapability.DEFAULT.getActiveCategoriesForNode(node);
        Assertions.assertTrue(activeCategories.contains(AddonCategory.COIL));
    }

    private MultiblockStructureDef createFixedCoilStructureDef(ResourceLocation controllerId) {
        List<MultiblockStructurePart> parts = List.of(
                new MultiblockStructurePart(controllerId, "Fixed Casing Machine", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:solid_machine_casing"), "Solid Casing", 10, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:cupronickel_coil_block"), "Cupronickel Coil Block", 16, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), "LV Energy Hatch", 1, PartCategory.HATCH_BUS)
        );
        return new MultiblockStructureDef(
                controllerId,
                "Fixed Casing Machine",
                parts,
                0,
                1,
                0,
                0,
                0,
                0,
                0
        );
    }

    private MultiblockStructureDef createFunctionalCoilStructureDef(ResourceLocation controllerId) {
        List<MultiblockStructurePart> parts = List.of(
                new MultiblockStructurePart(controllerId, "Electric Blast Furnace", 1, PartCategory.CONTROLLER),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:heatproof_machine_casing"), "Heat Proof Casing", 10, PartCategory.CASING),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:cupronickel_coil_block"), "Cupronickel Coil Block", 16, PartCategory.COIL),
                new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), "LV Energy Hatch", 1, PartCategory.HATCH_BUS)
        );
        return new MultiblockStructureDef(
                controllerId,
                "Electric Blast Furnace",
                parts,
                16,
                1,
                0,
                0,
                0,
                0,
                0
        );
    }

    private RecipeNode createNodeWithCoilAddon(ResourceLocation controllerId) {
        RecipeNode node = new RecipeNode("test_node", controllerId.getPath(), 100, 100, GTVoltageTier.MV);
        node.setMachineIcon(controllerId);
        node.setMultiblock(true);
        node.setMachineCount(1.0);

        MachineAddon kanthalAddon = new MachineAddon(
                "gtceu:kanthal_coil_block",
                "Kanthal Heating Coil",
                AddonCategory.COIL,
                "Coil 2700K",
                ResourceLocation.tryParse("gtceu:kanthal_coil_block")
        );
        node.getAddons().add(kanthalAddon);
        return node;
    }

    private MultiblockBOMSummary.BOMItemEntry findBOMEntryById(MultiblockBOMSummary summary, ResourceLocation targetId) {
        return summary.aggregatedItems().stream()
                .filter(entry -> targetId.equals(entry.itemId()))
                .findFirst()
                .orElse(null);
    }
}
