package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.model.ModuleInputPin;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.inspector.BoundaryPinInspector;
import com.gtceu.calcboard.client.gui.inspector.JunctionNodeInspector;
import com.gtceu.calcboard.client.gui.inspector.MachineNodeInspector;
import com.gtceu.calcboard.client.gui.inspector.PageSettingsInspector;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class NodeInspectorPanelSubComponentTest {

    @BeforeAll
    public static void init() {
        ModAdapterRegistry.init();
    }

    @Test
    public void testSubInspectorsInstantiationAndNullSafety() {
        NodeInspectorPanel panel = new NodeInspectorPanel(null);

        Assertions.assertNotNull(panel.getMachineInspector());
        Assertions.assertNotNull(panel.getJunctionInspector());
        Assertions.assertNotNull(panel.getBoundaryPinInspector());
        Assertions.assertNotNull(panel.getPageSettingsInspector());

        Assertions.assertNull(panel.getActiveInspector());
        Assertions.assertEquals(0, panel.getPanelWidth());
        Assertions.assertEquals(160, panel.getPanelHeight());
        Assertions.assertFalse(panel.isVisible());
        Assertions.assertFalse(panel.isPageSettingsMode());
    }

    @Test
    public void testSubInspectorContentHeights() {
        NodeInspectorPanel panel = new NodeInspectorPanel(null);

        Assertions.assertEquals(240, panel.getJunctionInspector().getContentHeight());
        Assertions.assertEquals(240, panel.getBoundaryPinInspector().getContentHeight());
        Assertions.assertEquals(210, panel.getPageSettingsInspector().getContentHeight());
        Assertions.assertEquals(0, panel.getMachineInspector().getContentHeight());

        RecipeNode node = RecipeNode.create("Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        int machineContentH = panel.getMachineInspector().calculateContentHeight(node);
        Assertions.assertTrue(machineContentH > 100);
    }

    @Test
    public void testTierDelegationsToMachineInspector() {
        NodeInspectorPanel panel = new NodeInspectorPanel(null);
        RecipeNode node = RecipeNode.create("Combustion Generator", 20.0, 32.0, GTVoltageTier.LV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_combustion_generator"));

        Assertions.assertEquals(
                panel.getMachineInspector().getInspectorTiers(node),
                panel.getInspectorTiers(node)
        );
        Assertions.assertEquals(
                panel.getMachineInspector().getTierControlsHeight(node),
                panel.getTierControlsHeight(node)
        );
    }

    @Test
    public void testPageSettingsModeLifecycle() {
        NodeInspectorPanel panel = new NodeInspectorPanel(null);
        panel.openPageSettings();

        Assertions.assertTrue(panel.isPageSettingsMode());
        Assertions.assertTrue(panel.isVisible());
        Assertions.assertEquals(NodeInspectorPanel.PANEL_WIDTH, panel.getPanelWidth());
        Assertions.assertTrue(panel.getActiveInspector() instanceof PageSettingsInspector);

        panel.close();
        Assertions.assertFalse(panel.isPageSettingsMode());
        Assertions.assertFalse(panel.isVisible());
        Assertions.assertEquals(0, panel.getPanelWidth());
    }

    @Test
    public void testActiveInspectorResolutionWithMockNodes() {
        NodeInspectorPanel panel = new NodeInspectorPanel(null);

        RecipeNode machineNode = RecipeNode.create("Wiremill", 50.0, 16.0, GTVoltageTier.LV);
        NodeWidget machineWidget = new NodeWidget(machineNode, null);
        panel.setTargetWidget(machineWidget);
        Assertions.assertTrue(panel.getActiveInspector() instanceof MachineNodeInspector);

        RecipeNode junctionNode = RecipeNode.createReroute(10.0, 20.0);
        NodeWidget junctionWidget = new NodeWidget(junctionNode, null);
        panel.setTargetWidget(junctionWidget);
        Assertions.assertTrue(panel.getActiveInspector() instanceof JunctionNodeInspector);

        ModuleInputPin pinNode = new ModuleInputPin("pin_1", "Input Pin", null);
        NodeWidget pinWidget = new NodeWidget(pinNode, null);
        panel.setTargetWidget(pinWidget);
        Assertions.assertTrue(panel.getActiveInspector() instanceof BoundaryPinInspector);

        panel.openPageSettings();
        Assertions.assertTrue(panel.getActiveInspector() instanceof PageSettingsInspector);
    }

    @Test
    public void testSubInspectorsBindingAndUnbindingLifecycle() {
        NodeInspectorPanel panel = new NodeInspectorPanel(null);

        RecipeNode machineNode = RecipeNode.create("Wiremill", 50.0, 16.0, GTVoltageTier.LV);
        NodeWidget machineWidget = new NodeWidget(machineNode, null);
        panel.setTargetWidget(machineWidget);
        Assertions.assertSame(machineWidget, panel.getMachineInspector().getTargetWidget());
        Assertions.assertTrue(panel.isVisible());
        Assertions.assertEquals(NodeInspectorPanel.PANEL_WIDTH, panel.getPanelWidth());

        RecipeNode junctionNode = RecipeNode.createReroute(10.0, 20.0);
        NodeWidget junctionWidget = new NodeWidget(junctionNode, null);
        panel.setTargetWidget(junctionWidget);
        Assertions.assertSame(junctionWidget, panel.getJunctionInspector().getTargetWidget());

        panel.close();
        Assertions.assertNull(panel.getMachineInspector().getTargetWidget());
        Assertions.assertNull(panel.getJunctionInspector().getTargetWidget());
        Assertions.assertNull(panel.getBoundaryPinInspector().getTargetWidget());
        Assertions.assertFalse(panel.isVisible());
        Assertions.assertEquals(0, panel.getPanelWidth());

        panel.setTargetWidget(machineWidget);
        Assertions.assertSame(machineWidget, panel.getMachineInspector().getTargetWidget());
        panel.setTargetWidget(null);
        Assertions.assertNull(panel.getMachineInspector().getTargetWidget());
    }

    @Test
    public void testSubInspectorClickConsumptionPreventsBleedThrough() {
        NodeInspectorPanel panel = new NodeInspectorPanel(null);

        RecipeNode machineNode = RecipeNode.create("Wiremill", 50.0, 16.0, GTVoltageTier.LV);
        NodeWidget machineWidget = new NodeWidget(machineNode, null);
        panel.getMachineInspector().bind(machineWidget);

        boolean machineConsumed = panel.getMachineInspector().mouseClicked(100, 50, 110, 60, 0);
        Assertions.assertTrue(machineConsumed);

        RecipeNode junctionNode = RecipeNode.createReroute(10.0, 20.0);
        NodeWidget junctionWidget = new NodeWidget(junctionNode, null);
        panel.getJunctionInspector().bind(junctionWidget);
        boolean junctionConsumed = panel.getJunctionInspector().mouseClicked(100, 50, 110, 60, 0);
        Assertions.assertTrue(junctionConsumed);

        ModuleInputPin pinNode = new ModuleInputPin("pin_1", "Input Pin", null);
        NodeWidget pinWidget = new NodeWidget(pinNode, null);
        panel.getBoundaryPinInspector().bind(pinWidget);
        boolean pinConsumed = panel.getBoundaryPinInspector().mouseClicked(100, 50, 110, 60, 0);
        Assertions.assertTrue(pinConsumed);

        panel.getPageSettingsInspector().bind(null);
        boolean pageConsumed = panel.getPageSettingsInspector().mouseClicked(100, 50, 110, 60, 0);
        Assertions.assertTrue(pageConsumed);
    }

    @Test
    public void testJunctionAndBoundaryPinHeightSafety() {
        NodeInspectorPanel panel = new NodeInspectorPanel(null);

        RecipeNode junctionNode = RecipeNode.createReroute(10.0, 20.0);
        NodeWidget junctionWidget = new NodeWidget(junctionNode, null);
        panel.setTargetWidget(junctionWidget);
        Assertions.assertEquals(240, panel.getPanelHeight());

        ModuleInputPin pinNode = new ModuleInputPin("pin_1", "Input Pin", null);
        NodeWidget pinWidget = new NodeWidget(pinNode, null);
        panel.setTargetWidget(pinWidget);
        Assertions.assertEquals(240, panel.getPanelHeight());
    }
}
