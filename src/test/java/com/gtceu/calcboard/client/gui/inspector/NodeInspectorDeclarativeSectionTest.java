package com.gtceu.calcboard.client.gui.inspector;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.client.gui.inspector.section.BoilerThrottleSection;
import com.gtceu.calcboard.client.gui.inspector.section.HardwareConfigSection;
import com.gtceu.calcboard.client.gui.inspector.section.IInspectorSection;
import com.gtceu.calcboard.client.gui.inspector.section.InspectorHeaderSection;
import com.gtceu.calcboard.client.gui.inspector.section.MachineCountSection;
import com.gtceu.calcboard.client.gui.inspector.section.MultiblockEnergyHatchSection;
import com.gtceu.calcboard.client.gui.inspector.section.OverclockModeSection;
import com.gtceu.calcboard.client.gui.inspector.section.SingleblockTierSection;
import com.gtceu.calcboard.client.gui.inspector.section.StatsSummarySection;
import com.gtceu.calcboard.client.gui.inspector.section.SteamModeSection;
import com.gtceu.calcboard.client.gui.inspector.section.SubPageModuleSection;
import com.gtceu.calcboard.client.gui.widget.NodeInspectorPanel;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.handler.GTEnergyHatchCalculator;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

public class NodeInspectorDeclarativeSectionTest {

    @BeforeAll
    public static void init() {
        ModAdapterRegistry.init();
    }

    @Test
    public void testSingleblockElectricSectionComposition() {
        CompositeNodeInspector inspector = new CompositeNodeInspector(null);
        RecipeNode node = RecipeNode.create("Wiremill", 50.0, 16.0, GTVoltageTier.LV);
        node.setMultiblock(false);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setOverclockMode(OverclockMode.STANDARD);

        List<IInspectorSection> applicable = inspector.getApplicableSections(node);

        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof InspectorHeaderSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof MachineCountSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof SingleblockTierSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof OverclockModeSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof HardwareConfigSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof StatsSummarySection));

        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof MultiblockEnergyHatchSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SteamModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof BoilerThrottleSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SubPageModuleSection));
    }

    @Test
    public void testMultiblockElectricSectionComposition() {
        CompositeNodeInspector inspector = new CompositeNodeInspector(null);
        RecipeNode node = RecipeNode.create("Large Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_chemical_reactor"));
        node.setTargetTier(GTVoltageTier.EV);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setOverclockMode(OverclockMode.STANDARD);

        List<IInspectorSection> applicable = inspector.getApplicableSections(node);

        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof InspectorHeaderSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof MachineCountSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof MultiblockEnergyHatchSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof OverclockModeSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof HardwareConfigSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof StatsSummarySection));

        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SingleblockTierSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SteamModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof BoilerThrottleSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SubPageModuleSection));
    }

    @Test
    public void testSteamNodeSectionComposition() {
        CompositeNodeInspector inspector = new CompositeNodeInspector(null);
        RecipeNode node = RecipeNode.create("Steam Macerator", 100.0, 4.0, GTVoltageTier.LV);
        node.setMultiblock(false);
        node.setSteamMode(SteamMode.HIGH_PRESSURE);

        List<IInspectorSection> applicable = inspector.getApplicableSections(node);

        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof InspectorHeaderSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof MachineCountSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof SteamModeSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof HardwareConfigSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof StatsSummarySection));

        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SingleblockTierSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof MultiblockEnergyHatchSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof OverclockModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof BoilerThrottleSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SubPageModuleSection));
    }

    @Test
    public void testBoilerSectionComposition() {
        CompositeNodeInspector inspector = new CompositeNodeInspector(null);
        RecipeNode node = RecipeNode.create("Solid Boiler", 100.0, 0.0, GTVoltageTier.LV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lp_steam_solid_boiler"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));

        List<IInspectorSection> applicable = inspector.getApplicableSections(node);

        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof InspectorHeaderSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof MachineCountSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof BoilerThrottleSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof HardwareConfigSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof StatsSummarySection));

        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SingleblockTierSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof MultiblockEnergyHatchSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof OverclockModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SteamModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SubPageModuleSection));
    }

    @Test
    public void testModuleNodeSectionComposition() {
        CompositeNodeInspector inspector = new CompositeNodeInspector(null);
        RecipeNode node = new RecipeNode("mod_1", "Sub Process", 100.0, 120.0, GTVoltageTier.MV);
        node.setRole(new com.gtceu.calcboard.api.model.role.SubPageModuleNodeRole("sub_circuit_board"));

        List<IInspectorSection> applicable = inspector.getApplicableSections(node);

        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof InspectorHeaderSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof MachineCountSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof SubPageModuleSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof StatsSummarySection));

        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SingleblockTierSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof MultiblockEnergyHatchSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof OverclockModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SteamModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof BoilerThrottleSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof HardwareConfigSection));
    }

    @Test
    public void testGeneratorSectionComposition() {
        CompositeNodeInspector inspector = new CompositeNodeInspector(null);
        RecipeNode node = RecipeNode.create("Combustion Generator", 20.0, 32.0, GTVoltageTier.LV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_combustion_generator"));
        node.setGenerator(true);
        node.setMultiblock(false);
        node.setEnergyType(EnergyType.ELECTRIC_EU);

        List<IInspectorSection> applicable = inspector.getApplicableSections(node);

        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof InspectorHeaderSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof MachineCountSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof SingleblockTierSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof HardwareConfigSection));
        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof StatsSummarySection));

        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof OverclockModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof MultiblockEnergyHatchSection));
    }

    @Test
    public void testMultiblockEnergyHatchOneClickInstallAndSwap() {
        RecipeNode node = RecipeNode.create("Large Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_chemical_reactor"));
        node.setTargetTier(GTVoltageTier.LV);
        node.setEnergyType(EnergyType.ELECTRIC_EU);

        Assertions.assertTrue(GTEnergyHatchCalculator.requiresEnergyHatch(node));
        Assertions.assertFalse(GTAddonCompatibilityHandler.hasEnergyHatch(node));

        MultiblockEnergyHatchSection hatchSection = new MultiblockEnergyHatchSection();
        NodeWidget widget = new NodeWidget(node, null);
        hatchSection.bind(widget, node, null);
        Assertions.assertTrue(hatchSection.isApplicable(node));

        List<GTVoltageTier> tiers = SingleblockTierSection.getInspectorTiers(node);
        int evIndex = tiers.indexOf(GTVoltageTier.EV);
        Assertions.assertTrue(evIndex >= 0);

        int cols = 4;
        int gap = 4;
        int totalW = NodeInspectorPanel.PANEL_WIDTH - 16;
        int chipW = (totalW - gap * (cols - 1)) / cols;
        int chipH = 16;
        int x = 10;
        int y = 20;
        int gridY = y + 12 + 24;
        int evX = x + (evIndex % cols) * (chipW + gap) + 4;
        int evY = gridY + (evIndex / cols) * (chipH + 4) + 4;

        boolean clicked = hatchSection.mouseClicked(x, y, totalW, evX, evY, 0);
        Assertions.assertTrue(clicked);

        Assertions.assertTrue(GTAddonCompatibilityHandler.hasEnergyHatch(node));
        Assertions.assertEquals(GTVoltageTier.EV, node.getTargetTier());
        GTEnergyHatchAddon primaryHatch = MultiblockEnergyHatchSection.getPrimaryEnergyHatch(node);
        Assertions.assertNotNull(primaryHatch);
        Assertions.assertEquals(GTVoltageTier.EV, primaryHatch.getTier());

        boolean clickedAgain = hatchSection.mouseClicked(x, y, totalW, evX, evY, 0);
        Assertions.assertTrue(clickedAgain);
        Assertions.assertEquals(1, node.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH).count());

        int ivIndex = tiers.indexOf(GTVoltageTier.IV);
        Assertions.assertTrue(ivIndex >= 0);
        int ivX = x + (ivIndex % cols) * (chipW + gap) + 4;
        int ivY = gridY + (ivIndex / cols) * (chipH + 4) + 4;

        boolean clickedIv = hatchSection.mouseClicked(x, y, totalW, ivX, ivY, 0);
        Assertions.assertTrue(clickedIv);

        Assertions.assertEquals(GTVoltageTier.IV, node.getTargetTier());
        GTEnergyHatchAddon swappedHatch = MultiblockEnergyHatchSection.getPrimaryEnergyHatch(node);
        Assertions.assertNotNull(swappedHatch);
        Assertions.assertEquals(GTVoltageTier.IV, swappedHatch.getTier());
        Assertions.assertEquals(1, node.getAddons().stream().filter(a -> a.getCategory() == MachineAddon.Category.ENERGY_HATCH).count());
    }

    @Test
    public void testSectionHeightsAndClickConsumption() {
        CompositeNodeInspector inspector = new CompositeNodeInspector(null);
        RecipeNode node = RecipeNode.create("Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        NodeWidget widget = new NodeWidget(node, null);
        inspector.bind(widget);

        int height = inspector.getContentHeight();
        Assertions.assertTrue(height > 150);

        boolean consumed = inspector.mouseClicked(50, 50, 60, 60, 0);
        Assertions.assertTrue(consumed);

        boolean rightClickConsumed = inspector.mouseClicked(50, 50, 60, 60, 1);
        Assertions.assertFalse(rightClickConsumed);
    }

    @Test
    public void testMultipleHatchesHighestTierPrimarySelection() {
        RecipeNode node = RecipeNode.create("Large Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setEnergyType(EnergyType.ELECTRIC_EU);

        GTEnergyHatchAddon lvHatch = new GTEnergyHatchAddon("lv_hatch", "LV Hatch", "", null, GTVoltageTier.LV, 2, false, false, false);
        GTEnergyHatchAddon evHatch = new GTEnergyHatchAddon("ev_hatch", "EV Hatch", "", null, GTVoltageTier.EV, 2, false, false, false);

        node.getAddons().add(lvHatch);
        node.getAddons().add(evHatch);

        GTEnergyHatchAddon primary = MultiblockEnergyHatchSection.getPrimaryEnergyHatch(node);
        Assertions.assertNotNull(primary);
        Assertions.assertEquals(GTVoltageTier.EV, primary.getTier());
    }

    @Test
    public void testSteamMultiblockTaxonomyExclusion() {
        CompositeNodeInspector inspector = new CompositeNodeInspector(null);
        RecipeNode node = RecipeNode.create("Steam Ore Processing", 100.0, 16.0, GTVoltageTier.ULV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:steam_ore_factory"));
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setMultiblock(true);

        List<IInspectorSection> applicable = inspector.getApplicableSections(node);

        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof SteamModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SingleblockTierSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof MultiblockEnergyHatchSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof OverclockModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof BoilerThrottleSection));
    }

    @Test
    public void testElectricBoilerTaxonomyExclusion() {
        CompositeNodeInspector inspector = new CompositeNodeInspector(null);
        RecipeNode node = RecipeNode.create("Electric Boiler", 100.0, 120.0, GTVoltageTier.LV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lp_steam_solid_boiler"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_boiler"));
        node.setEnergyType(EnergyType.ELECTRIC_EU);

        List<IInspectorSection> applicable = inspector.getApplicableSections(node);

        Assertions.assertTrue(applicable.stream().anyMatch(s -> s instanceof BoilerThrottleSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SingleblockTierSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof MultiblockEnergyHatchSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof OverclockModeSection));
        Assertions.assertFalse(applicable.stream().anyMatch(s -> s instanceof SteamModeSection));
    }

    @Test
    public void testMachineCountSectionClickInteractions() {
        RecipeNode node = RecipeNode.create("Electric Blast Furnace", 100.0, 100.0, GTVoltageTier.EV);
        node.setMachineCount(2.0);
        NodeWidget widget = new NodeWidget(node, null);
        MachineCountSection section = new MachineCountSection();
        section.bind(widget, node, null);

        int x = 50;
        int y = 100;
        int w = 179;
        int ctrlY = y + 12;
        int boxX = x + 18;
        int plusX = boxX + 56 + 2;
        int halfX = plusX + 18;
        int doubleX = halfX + 24;
        int anchorX = doubleX + 24;

        // 1. Click Plus (+) button -> Should increase machine count from 2.0 to 3.0
        boolean plusClicked = section.mouseClicked(x, y, w, plusX + 8, ctrlY + 8, 0);
        Assertions.assertTrue(plusClicked);
        Assertions.assertEquals(3.0, node.getMachineCount(), 0.001);

        // 2. Click Minus (-) button -> Should decrease machine count from 3.0 to 2.0
        boolean minusClicked = section.mouseClicked(x, y, w, x + 8, ctrlY + 8, 0);
        Assertions.assertTrue(minusClicked);
        Assertions.assertEquals(2.0, node.getMachineCount(), 0.001);

        // 3. Click Half (/2) button -> Should scale machine count from 2.0 to 1.0
        boolean halfClicked = section.mouseClicked(x, y, w, halfX + 8, ctrlY + 8, 0);
        Assertions.assertTrue(halfClicked);
        Assertions.assertEquals(1.0, node.getMachineCount(), 0.001);

        // 4. Click Double (x2) button -> Should scale machine count from 1.0 to 2.0
        boolean doubleClicked = section.mouseClicked(x, y, w, doubleX + 8, ctrlY + 8, 0);
        Assertions.assertTrue(doubleClicked);
        Assertions.assertEquals(2.0, node.getMachineCount(), 0.001);

        // 5. Click Anchor (⌖) button -> Should be handled
        boolean anchorClicked = section.mouseClicked(x, y, w, anchorX + 8, ctrlY + 8, 0);
        Assertions.assertTrue(anchorClicked);

        // 6. Click Count Box -> Starts inline editing
        boolean boxClicked = section.mouseClicked(x, y, w, boxX + 8, ctrlY + 8, 0);
        Assertions.assertTrue(boxClicked);
        Assertions.assertTrue(widget.getCountEditor().isEditing());

        // 7. Click outside controls -> Should return false
        boolean outsideClicked = section.mouseClicked(x, y, w, x - 10, y - 10, 0);
        Assertions.assertFalse(outsideClicked);
    }
}


