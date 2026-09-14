package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.NodeHardwareReconciler;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
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
public class MachineConfigDialogRebindTest {

    private static final ResourceLocation EBF_ID = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
    private static final ResourceLocation EBF_CAT = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
    private static final ResourceLocation LV_CHEM_ID = ResourceLocation.tryParse("gtceu:lv_chemical_reactor");

    private static final ResourceLocation LCR_ID = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
    private static final ResourceLocation CHEM_CAT = ResourceLocation.tryParse("gtceu:chemical_reactor");

    @BeforeEach
    public void setUp() {
        SimulatedGTEnvironment.setupFullEnvironment();
        GTCoilAddon cupro = new GTCoilAddon("gtceu:cupronickel_coil", "Cupronickel Coil", "", null);
        cupro.setCoilTemperature(1800);
        com.gtceu.calcboard.api.catalog.MachineAddonCatalog.getInstance().registerCustomAddon(cupro);
    }

    @AfterEach
    public void tearDown() {
        SimulatedGTEnvironment.tearDownEnvironment();
    }

    @Test
    public void testMachineConfigDialogRebindOnWorkstationSwitch() {
        BoardScreen screen = new BoardScreen();
        MachineConfigDialog dialog = new MachineConfigDialog(screen);

        RecipeNode node = RecipeNode.create(LCR_ID, "Rubber Polymerization", 200.0, 30.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(CHEM_CAT);
        node.setMultiblock(true);
        node.setParallel(4);

        dialog.open(node, AddonCategory.MULTIBLOCK_TRAIT);
        Assertions.assertEquals(AddonCategory.MULTIBLOCK_TRAIT, dialog.getSelectedCategory());

        NodeHardwareReconciler.reconcileForMachine(node, LV_CHEM_ID);
        dialog.rebind(node);

        Assertions.assertNotEquals(AddonCategory.MULTIBLOCK_TRAIT, dialog.getSelectedCategory());
        Assertions.assertEquals(1, node.getParallel());
    }

    @Test
    public void testMachineConfigDialogRebindOnRecipeSwitch() {
        BoardScreen screen = new BoardScreen();
        MachineConfigDialog dialog = new MachineConfigDialog(screen);
        FlowGraph graph = screen.getGraph();

        RecipeNode ebfNode = RecipeNode.create(EBF_ID, "Steel Smelting", 400.0, 120.0, GTVoltageTier.MV);
        ebfNode.setRecipeCategoryId(EBF_CAT);
        ebfNode.setMultiblock(true);
        ebfNode.getAddons().add(new GTCoilAddon("gtceu:cupronickel_coil", "Cupronickel Coil", "", null));
        graph.addNode(ebfNode);

        dialog.open(ebfNode, AddonCategory.COIL);
        Assertions.assertEquals(AddonCategory.COIL, dialog.getSelectedCategory());

        RecipeNode chemTemplate = RecipeNode.create(LV_CHEM_ID, "Rubber Polymerization", 200.0, 30.0, GTVoltageTier.LV);
        chemTemplate.setRecipeCategoryId(CHEM_CAT);
        chemTemplate.setMultiblock(false);
        chemTemplate.setAvailableWorkstations(List.of(LV_CHEM_ID, LCR_ID));

        graph.switchNodeRecipe(ebfNode, chemTemplate);
        dialog.rebind(ebfNode);

        Assertions.assertNotEquals(AddonCategory.COIL, dialog.getSelectedCategory());
        Assertions.assertEquals(1, ebfNode.getParallel());
        Assertions.assertTrue(ebfNode.getAddons().isEmpty());
    }
}
