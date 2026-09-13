package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import com.gtceu.calcboard.testutil.SimulatedGTEnvironment;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class NodeMachineRecipeSwitchLifecycleTest {

    private static final ResourceLocation EBF_ID = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
    private static final ResourceLocation EBF_CAT = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
    private static final ResourceLocation LV_CHEM_ID = ResourceLocation.tryParse("gtceu:lv_chemical_reactor");
    private static final ResourceLocation EV_CHEM_ID = ResourceLocation.tryParse("gtceu:ev_chemical_reactor");
    private static final ResourceLocation LCR_ID = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
    private static final ResourceLocation CHEM_CAT = ResourceLocation.tryParse("gtceu:chemical_reactor");

    @BeforeEach
    public void setUp() {
        SimulatedGTEnvironment.setupFullEnvironment();
    }

    @AfterEach
    public void tearDown() {
        SimulatedGTEnvironment.tearDownEnvironment();
    }

    @Test
    public void testIncompatibleRecipeSwitchPurgesCoilAndUpdatesMachine() {
        FlowGraph graph = new FlowGraph();
        RecipeNode ebfNode = RecipeNode.create(EBF_ID, "Steel Smelting", 400.0, 120.0, GTVoltageTier.MV);
        ebfNode.setRecipeCategoryId(EBF_CAT);
        ebfNode.setMultiblock(true);
        ebfNode.setAvailableWorkstations(List.of(EBF_ID));

        MachineAddon coil = new MachineAddon("gtceu:cupronickel_coil", "Cupronickel Coil", AddonCategory.COIL, "", null);
        ebfNode.getAddons().add(coil);
        graph.addNode(ebfNode);

        Assertions.assertTrue(ebfNode.isMultiblock());
        Assertions.assertEquals(1, ebfNode.getAddons().size());

        RecipeNode chemTemplate = RecipeNode.create(LV_CHEM_ID, "Rubber Polymerization", 200.0, 30.0, GTVoltageTier.LV);
        chemTemplate.setRecipeCategoryId(CHEM_CAT);
        chemTemplate.setMultiblock(false);
        chemTemplate.setAvailableWorkstations(List.of(LV_CHEM_ID, LCR_ID));

        BoardCommand.SwitchRecipeCommand cmd = graph.switchNodeRecipe(ebfNode, chemTemplate);
        Assertions.assertNotNull(cmd);

        Assertions.assertEquals(LV_CHEM_ID, ebfNode.getMachineIcon());
        Assertions.assertFalse(ebfNode.isMultiblock());
        Assertions.assertTrue(ebfNode.getAddons().isEmpty());
    }

    @Test
    public void testCompatibleRecipeSwitchPreservesMachineAndParallel() {
        FlowGraph graph = new FlowGraph();
        RecipeNode lcrNode = RecipeNode.create(LCR_ID, "Sulfuric Acid", 300.0, 60.0, GTVoltageTier.LV);
        lcrNode.setRecipeCategoryId(CHEM_CAT);
        lcrNode.setMultiblock(true);
        lcrNode.setParallel(8);
        lcrNode.setCustomParallel(8);
        lcrNode.setAvailableWorkstations(List.of(LV_CHEM_ID, LCR_ID));
        graph.addNode(lcrNode);

        RecipeNode altChemTemplate = RecipeNode.create(LV_CHEM_ID, "Nitric Acid", 250.0, 45.0, GTVoltageTier.LV);
        altChemTemplate.setRecipeCategoryId(CHEM_CAT);
        altChemTemplate.setAvailableWorkstations(List.of(LV_CHEM_ID, LCR_ID));

        BoardCommand.SwitchRecipeCommand cmd = graph.switchNodeRecipe(lcrNode, altChemTemplate);
        Assertions.assertNotNull(cmd);

        Assertions.assertEquals(LCR_ID, lcrNode.getMachineIcon());
        Assertions.assertTrue(lcrNode.isMultiblock());
        Assertions.assertEquals(8, lcrNode.getParallel());
    }

    @Test
    public void testLowTierRecipeSwitchClampsTargetTierAndMachineIcon() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node = RecipeNode.create(LV_CHEM_ID, "Simple Reaction", 100.0, 30.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(CHEM_CAT);
        node.setMultiblock(false);
        node.setTargetTier(GTVoltageTier.LV);
        node.setAvailableWorkstations(List.of(LV_CHEM_ID, EV_CHEM_ID));
        graph.addNode(node);

        RecipeNode evTemplate = RecipeNode.create(EV_CHEM_ID, "Advanced Reaction", 150.0, 1920.0, GTVoltageTier.EV);
        evTemplate.setRecipeCategoryId(CHEM_CAT);
        evTemplate.setMultiblock(false);
        evTemplate.setAvailableWorkstations(List.of(LV_CHEM_ID, EV_CHEM_ID));

        BoardCommand.SwitchRecipeCommand cmd = graph.switchNodeRecipe(node, evTemplate);
        Assertions.assertNotNull(cmd);

        Assertions.assertEquals(GTVoltageTier.EV, node.getTargetTier());
        Assertions.assertEquals(EV_CHEM_ID, node.getMachineIcon());
    }

    @Test
    public void testSwitchRecipeUndoRedoLosslessRestoration() {
        FlowGraph graph = new FlowGraph();
        RecipeNode ebfNode = RecipeNode.create(EBF_ID, "Steel Smelting", 400.0, 120.0, GTVoltageTier.MV);
        ebfNode.setRecipeCategoryId(EBF_CAT);
        ebfNode.setMultiblock(true);
        ebfNode.setTargetTier(GTVoltageTier.HV);
        ebfNode.setAvailableWorkstations(List.of(EBF_ID));

        MachineAddon coil = new MachineAddon("gtceu:cupronickel_coil", "Cupronickel Coil", AddonCategory.COIL, "", null);
        ebfNode.getAddons().add(coil);
        graph.addNode(ebfNode);

        RecipeNode chemTemplate = RecipeNode.create(LV_CHEM_ID, "Rubber Polymerization", 200.0, 30.0, GTVoltageTier.LV);
        chemTemplate.setRecipeCategoryId(CHEM_CAT);
        chemTemplate.setMultiblock(false);
        chemTemplate.setAvailableWorkstations(List.of(LV_CHEM_ID));

        BoardCommand.SwitchRecipeCommand cmd = graph.switchNodeRecipe(ebfNode, chemTemplate);
        Assertions.assertNotNull(cmd);
        Assertions.assertEquals(LV_CHEM_ID, ebfNode.getMachineIcon());
        Assertions.assertTrue(ebfNode.getAddons().isEmpty());

        cmd.undo(graph);
        Assertions.assertEquals(EBF_ID, ebfNode.getMachineIcon());
        Assertions.assertTrue(ebfNode.isMultiblock());
        Assertions.assertEquals(GTVoltageTier.HV, ebfNode.getTargetTier());
        Assertions.assertEquals(1, ebfNode.getAddons().size());
        Assertions.assertEquals("gtceu:cupronickel_coil", ebfNode.getAddons().get(0).getId());

        cmd.redo(graph);
        Assertions.assertEquals(LV_CHEM_ID, ebfNode.getMachineIcon());
        Assertions.assertFalse(ebfNode.isMultiblock());
        Assertions.assertTrue(ebfNode.getAddons().isEmpty());
    }

    @Test
    public void testSwitchMachineWorkstationReconciliation() {
        RecipeNode node = RecipeNode.create(LCR_ID, "Rubber Polymerization", 200.0, 30.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(CHEM_CAT);
        node.setMultiblock(true);
        node.setParallel(4);
        node.getAddons().add(new MachineAddon("gtceu:cupronickel_coil", "Cupronickel Coil", AddonCategory.COIL, "", null));

        NodeHardwareReconciler.reconcileForMachine(node, LV_CHEM_ID);

        Assertions.assertEquals(LV_CHEM_ID, node.getMachineIcon());
        Assertions.assertFalse(node.isMultiblock());
        Assertions.assertEquals(1, node.getParallel());
        Assertions.assertTrue(node.getAddons().isEmpty());
    }
}
