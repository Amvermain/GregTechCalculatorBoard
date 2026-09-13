package com.gtceu.calcboard.client.gui.action;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.history.command.BatchChangeTierCommand;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonLifecycleHandler;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class PageTargetVoltageAndBatchTierTest {

    @Test
    @DisplayName("BoardPage defaultVoltageTier and autoEquipEnergyHatches serialize, deserialize, and copy correctly")
    void testBoardPageSerializationAndCopy() {
        BoardPage page = new BoardPage("test_page", "Test Page", new FlowGraph());
        page.setFolderPath("folder/sub");
        Assertions.assertNull(page.getDefaultVoltageTier());
        Assertions.assertTrue(page.isAutoEquipEnergyHatches());

        page.setDefaultVoltageTier(GTVoltageTier.EV);
        page.setAutoEquipEnergyHatches(false);

        BoardPage copy = page.copy();
        Assertions.assertEquals(GTVoltageTier.EV, copy.getDefaultVoltageTier());
        Assertions.assertFalse(copy.isAutoEquipEnergyHatches());

        CompoundTag tag = page.serializeNBT();
        BoardPage restored = BoardPage.deserializeNBT(tag);
        Assertions.assertEquals(GTVoltageTier.EV, restored.getDefaultVoltageTier());
        Assertions.assertFalse(restored.isAutoEquipEnergyHatches());

        page.setDefaultVoltageTier(null);
        CompoundTag nullTierTag = page.serializeNBT();
        BoardPage restoredNull = BoardPage.deserializeNBT(nullTierTag);
        Assertions.assertNull(restoredNull.getDefaultVoltageTier());
    }

    @Test
    @DisplayName("NodeProvisioningPipeline upgrades singleblock nodes and protects against downgrading")
    void testNodeProvisioningSingleblock() {
        BoardPage page = new BoardPage("page_ev", "EV Page", new FlowGraph());
        page.setDefaultVoltageTier(GTVoltageTier.EV);

        RecipeNode singleblock = RecipeNode.create("Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        singleblock.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_chemical_reactor"));

        NodeProvisioningPipeline.provision(singleblock, page);
        Assertions.assertEquals(GTVoltageTier.EV, singleblock.getTargetTier());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:ev_chemical_reactor"), singleblock.getMachineIcon());

        RecipeNode highTierNode = RecipeNode.create("Platinum Refinery", 200.0, 7000.0, GTVoltageTier.IV);
        highTierNode.setMachineIcon(ResourceLocation.tryParse("gtceu:iv_chemical_reactor"));

        NodeProvisioningPipeline.provision(highTierNode, page);
        Assertions.assertEquals(GTVoltageTier.IV, highTierNode.getTargetTier());

        RecipeNode heatNode = RecipeNode.create("Steam Boiler", 100.0, 0.0, null);
        heatNode.setEnergyType(EnergyType.HEAT_OR_SELF);
        GTVoltageTier originalTier = heatNode.getTargetTier();

        NodeProvisioningPipeline.provision(heatNode, page);
        Assertions.assertEquals(originalTier, heatNode.getTargetTier());
        Assertions.assertNotEquals(GTVoltageTier.EV, heatNode.getTargetTier());
        Assertions.assertEquals(EnergyType.HEAT_OR_SELF, heatNode.getEnergyType());

        BoardPage autoPage = new BoardPage("page_auto", "Auto Page", new FlowGraph());
        RecipeNode autoNode = RecipeNode.create("Centrifuge", 100.0, 30.0, GTVoltageTier.LV);
        NodeProvisioningPipeline.provision(autoNode, autoPage);
        Assertions.assertEquals(GTVoltageTier.LV, autoNode.getTargetTier());
    }

    @Test
    @DisplayName("NodeProvisioningPipeline installs default energy hatches for multiblock machines")
    void testNodeProvisioningMultiblock() {
        BoardPage page = new BoardPage("page_ev", "EV Page", new FlowGraph());
        page.setDefaultVoltageTier(GTVoltageTier.EV);
        page.setAutoEquipEnergyHatches(true);

        RecipeNode multiblock = RecipeNode.create("Large Brewing Vat", 300.0, 240.0, GTVoltageTier.HV);
        multiblock.setMultiblock(true);
        multiblock.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));

        NodeProvisioningPipeline.provision(multiblock, page);
        Assertions.assertTrue(GTAddonCompatibilityHandler.hasEnergyHatch(multiblock));
        Assertions.assertEquals(GTVoltageTier.EV, multiblock.getTargetTier());

        BoardPage noHatchPage = new BoardPage("page_no_hatch", "No Hatch Page", new FlowGraph());
        noHatchPage.setDefaultVoltageTier(GTVoltageTier.EV);
        noHatchPage.setAutoEquipEnergyHatches(false);

        RecipeNode multiblock2 = RecipeNode.create("Large Brewing Vat", 300.0, 240.0, GTVoltageTier.HV);
        multiblock2.setMultiblock(true);
        multiblock2.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));

        NodeProvisioningPipeline.provision(multiblock2, noHatchPage);
        Assertions.assertFalse(GTAddonCompatibilityHandler.hasEnergyHatch(multiblock2));

        RecipeNode highTierMultiblock = RecipeNode.create("Large Brewing Vat", 300.0, 8000.0, GTVoltageTier.IV);
        highTierMultiblock.setMultiblock(true);
        highTierMultiblock.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));

        NodeProvisioningPipeline.provision(highTierMultiblock, page);
        Assertions.assertTrue(GTAddonCompatibilityHandler.hasEnergyHatch(highTierMultiblock));
        Assertions.assertEquals(GTVoltageTier.IV, highTierMultiblock.getTargetTier());
    }

    @Test
    @DisplayName("BatchChangeTierCommand applies target voltage atomically and supports Undo/Redo")
    void testBatchChangeTierCommandAndUndoRedo() {
        FlowGraph graph = new FlowGraph();

        RecipeNode singleLv = RecipeNode.create("Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        singleLv.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_chemical_reactor"));
        graph.addNode(singleLv);

        RecipeNode multiLv = RecipeNode.create("Large Brewing Vat", 300.0, 30.0, GTVoltageTier.LV);
        multiLv.setMultiblock(true);
        multiLv.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));
        GTEnergyHatchAddon lvHatch = new GTEnergyHatchAddon(
                "gtceu:lv_energy_input_hatch", "LV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:lv_energy_input_hatch"), GTVoltageTier.LV, 2, false, false, false
        );
        GTAddonLifecycleHandler.onAddonInstalled(multiLv, lvHatch);
        graph.addNode(multiLv);

        RecipeNode multi4A = RecipeNode.create("Large Brewing Vat", 300.0, 30.0, GTVoltageTier.LV);
        multi4A.setMultiblock(true);
        multi4A.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));
        GTEnergyHatchAddon hatch4A = new GTEnergyHatchAddon(
                "gtceu:lv_energy_input_hatch_4a", "4A Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:lv_energy_input_hatch_4a"), GTVoltageTier.LV, 4, false, false, false
        );
        GTAddonLifecycleHandler.onAddonInstalled(multi4A, hatch4A);
        graph.addNode(multi4A);

        RecipeNode multiLaser = RecipeNode.create("Large Brewing Vat", 300.0, 30.0, GTVoltageTier.LV);
        multiLaser.setMultiblock(true);
        multiLaser.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));
        GTEnergyHatchAddon laserHatch = new GTEnergyHatchAddon(
                "gtceu:laser_target_hatch", "Laser Hatch", "",
                ResourceLocation.tryParse("gtceu:laser_target_hatch"), GTVoltageTier.EV, 2, true, false, false
        );
        GTAddonLifecycleHandler.onAddonInstalled(multiLaser, laserHatch);
        graph.addNode(multiLaser);

        RecipeNode heatNode = RecipeNode.create("Steam Boiler", 100.0, 0.0, null);
        heatNode.setEnergyType(EnergyType.HEAT_OR_SELF);
        graph.addNode(heatNode);

        int applicableCount = BoardActionHandler.countBatchApplicableNodes(graph, GTVoltageTier.EV);
        Assertions.assertEquals(2, applicableCount);

        List<BatchChangeTierCommand.NodeTierSnapshot> prevSnapshots = new ArrayList<>();
        List<BatchChangeTierCommand.NodeTierSnapshot> newSnapshots = new ArrayList<>();

        prevSnapshots.add(BatchChangeTierCommand.NodeTierSnapshot.of(singleLv));
        singleLv.setTargetTier(GTVoltageTier.EV);
        singleLv.setMachineIcon(ResourceLocation.tryParse("gtceu:ev_chemical_reactor"));
        newSnapshots.add(BatchChangeTierCommand.NodeTierSnapshot.of(singleLv));

        prevSnapshots.add(BatchChangeTierCommand.NodeTierSnapshot.of(multiLv));
        multiLv.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH);
        GTEnergyHatchAddon evHatch = new GTEnergyHatchAddon(
                "gtceu:ev_energy_input_hatch", "EV Energy Hatch", "",
                ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"), GTVoltageTier.EV, 2, false, false, false
        );
        GTAddonLifecycleHandler.onAddonInstalled(multiLv, evHatch);
        newSnapshots.add(BatchChangeTierCommand.NodeTierSnapshot.of(multiLv));

        BatchChangeTierCommand cmd = new BatchChangeTierCommand(prevSnapshots, newSnapshots, GTVoltageTier.EV);

        Assertions.assertEquals(GTVoltageTier.EV, singleLv.getTargetTier());
        Assertions.assertEquals(GTVoltageTier.EV, multiLv.getTargetTier());
        Assertions.assertEquals(4, ((GTEnergyHatchAddon) multi4A.getAddons().get(0)).getAmperage());
        Assertions.assertTrue(((GTEnergyHatchAddon) multiLaser.getAddons().get(0)).isLaser());

        cmd.undo(graph);
        Assertions.assertEquals(GTVoltageTier.LV, singleLv.getTargetTier());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:lv_chemical_reactor"), singleLv.getMachineIcon());
        Assertions.assertEquals(GTVoltageTier.LV, multiLv.getTargetTier());

        cmd.redo(graph);
        Assertions.assertEquals(GTVoltageTier.EV, singleLv.getTargetTier());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:ev_chemical_reactor"), singleLv.getMachineIcon());
        Assertions.assertEquals(GTVoltageTier.EV, multiLv.getTargetTier());
    }

    @Test
    @DisplayName("Batch applicability accurately filters special hatches, non-electric nodes, and already-matching tiers")
    void testBatchApplicabilityEdgeCases() {
        FlowGraph graph = new FlowGraph();

        // 1. Multiblock without energy hatch -> applicable
        RecipeNode mbWithoutHatch = RecipeNode.create("Large Brewing Vat", 300.0, 30.0, GTVoltageTier.LV);
        mbWithoutHatch.setMultiblock(true);
        mbWithoutHatch.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));
        graph.addNode(mbWithoutHatch);

        // 2. Multiblock with 16A hatch -> special, excluded
        RecipeNode mb16A = RecipeNode.create("Large Brewing Vat", 300.0, 30.0, GTVoltageTier.LV);
        mb16A.setMultiblock(true);
        mb16A.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));
        GTEnergyHatchAddon hatch16A = new GTEnergyHatchAddon(
                "gtceu:lv_energy_input_hatch_16a", "16A Hatch", "",
                ResourceLocation.tryParse("gtceu:lv_energy_input_hatch_16a"), GTVoltageTier.LV, 16, false, false, false
        );
        GTAddonLifecycleHandler.onAddonInstalled(mb16A, hatch16A);
        graph.addNode(mb16A);

        // 3. Multiblock with substation hatch -> special, excluded
        RecipeNode mbSub = RecipeNode.create("Large Brewing Vat", 300.0, 30.0, GTVoltageTier.LV);
        mbSub.setMultiblock(true);
        mbSub.setMachineIcon(ResourceLocation.tryParse("gtceu:large_brewing_vat"));
        GTEnergyHatchAddon subHatch = new GTEnergyHatchAddon(
                "gtceu:substation_hatch", "Substation Hatch", "",
                ResourceLocation.tryParse("gtceu:substation_hatch"), GTVoltageTier.EV, 64, false, true, false
        );
        GTAddonLifecycleHandler.onAddonInstalled(mbSub, subHatch);
        graph.addNode(mbSub);

        // 4. Singleblock already at target tier -> excluded
        RecipeNode sbAlreadyEv = RecipeNode.create("Chemical Reactor", 100.0, 30.0, GTVoltageTier.EV);
        sbAlreadyEv.setMachineIcon(ResourceLocation.tryParse("gtceu:ev_chemical_reactor"));
        graph.addNode(sbAlreadyEv);

        // 5. Singleblock at LV -> applicable
        RecipeNode sbLv = RecipeNode.create("Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        sbLv.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_chemical_reactor"));
        graph.addNode(sbLv);

        // 6. Kinetic machine -> excluded
        RecipeNode rotNode = RecipeNode.create("Mechanical Press", 20.0, 0.0, null);
        rotNode.setEnergyType(EnergyType.KINETIC_SU);
        graph.addNode(rotNode);

        // Only mbWithoutHatch and sbLv are applicable to EV
        int count = BoardActionHandler.countBatchApplicableNodes(graph, GTVoltageTier.EV);
        Assertions.assertEquals(2, count);
    }

    @Test
    @DisplayName("Workstation minimum tier sanitization prevents phantom applicability for machines that cannot downgrade")
    void testBatchApplicabilityWithSanitizedWorkstationTier() {
        FlowGraph graph = new FlowGraph();

        RecipeNode cleanroom = RecipeNode.create("Cleanroom", 100.0, 30.0, null);
        cleanroom.setAvailableWorkstations(List.of(
                ResourceLocation.tryParse("gtceu:hv_cleanroom"),
                ResourceLocation.tryParse("gtceu:ev_cleanroom")
        ));
        cleanroom.setTargetTier(GTVoltageTier.HV);
        cleanroom.setMachineIcon(ResourceLocation.tryParse("gtceu:hv_cleanroom"));
        graph.addNode(cleanroom);

        // Page target is LV, but cleanroom cannot be downgraded below HV
        int count = BoardActionHandler.countBatchApplicableNodes(graph, GTVoltageTier.LV);
        Assertions.assertEquals(0, count);

        // Provisioning on an LV page must respect the HV minimum workstation tier
        BoardPage lvPage = new BoardPage("lv_page", "LV Page", new FlowGraph());
        lvPage.setDefaultVoltageTier(GTVoltageTier.LV);

        RecipeNode newCleanroom = RecipeNode.create("Cleanroom", 100.0, 30.0, null);
        newCleanroom.setAvailableWorkstations(List.of(
                ResourceLocation.tryParse("gtceu:hv_cleanroom"),
                ResourceLocation.tryParse("gtceu:ev_cleanroom")
        ));
        NodeProvisioningPipeline.provision(newCleanroom, lvPage);
        Assertions.assertEquals(GTVoltageTier.HV, newCleanroom.getTargetTier());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:hv_cleanroom"), newCleanroom.getMachineIcon());
    }
}
