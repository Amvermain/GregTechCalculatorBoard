package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class NodeInspectorPanelTierTest {

    private NodeInspectorPanel inspector;

    @BeforeAll
    public static void init() {
        com.gtceu.calcboard.api.spi.ModAdapterRegistry.init();
    }

    @BeforeEach
    public void setUp() {
        inspector = new NodeInspectorPanel(null);
    }

    @Test
    public void testCombustionGeneratorInspectorTiers() {
        RecipeNode node = RecipeNode.create("Combustion Generator", 20.0, 32.0, GTVoltageTier.LV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_combustion_generator"));

        Assertions.assertTrue(GTCombustionHelper.isCombustionFamily(node));

        List<GTVoltageTier> tiers = inspector.getInspectorTiers(node);
        int expectedSize = GTCombustionHelper.hasStarTCombustionModules() ? 7 : 5;
        Assertions.assertEquals(expectedSize, tiers.size());
        Assertions.assertEquals(GTVoltageTier.LV, tiers.get(0));
        Assertions.assertEquals(GTCombustionHelper.getMaxCombustionTier(), tiers.get(tiers.size() - 1));

        int height = inspector.getTierControlsHeight(node);
        Assertions.assertEquals(36, height);
    }

    @Test
    public void testMultiblockStandardMachineTiersReachMax() {
        RecipeNode node = RecipeNode.create("Large Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_chemical_reactor"));
        node.setTargetTier(GTVoltageTier.EV);

        List<GTVoltageTier> tiers = inspector.getInspectorTiers(node);
        Assertions.assertEquals(14, tiers.size());
        Assertions.assertEquals(GTVoltageTier.LV, tiers.get(0));
        Assertions.assertEquals(GTVoltageTier.MAX, tiers.get(13));
        Assertions.assertTrue(tiers.contains(GTVoltageTier.IV));
        Assertions.assertTrue(tiers.contains(GTVoltageTier.LuV));
        Assertions.assertTrue(tiers.contains(GTVoltageTier.ZPM));
        Assertions.assertTrue(tiers.contains(GTVoltageTier.UV));
        Assertions.assertTrue(tiers.contains(GTVoltageTier.UHV));
        Assertions.assertTrue(tiers.contains(GTVoltageTier.UEV));

        int height = inspector.getTierControlsHeight(node);
        Assertions.assertEquals(76, height);
    }

    @Test
    public void testSingleblockTurbineTiersCappedAtHV() {
        RecipeNode node = new RecipeNode("sb_turbine", "Basic Steam Turbine", 20.0, 32.0, GTVoltageTier.LV);
        node.setGenerator(true);
        node.setMultiblock(false);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:basic_steam_turbine"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_turbine"));
        node.setTargetTier(GTVoltageTier.LV);

        List<GTVoltageTier> tiers = inspector.getInspectorTiers(node);
        Assertions.assertEquals(List.of(GTVoltageTier.LV, GTVoltageTier.MV, GTVoltageTier.HV), tiers);

        int height = inspector.getTierControlsHeight(node);
        Assertions.assertEquals(16, height);
    }

    @Test
    public void testMultiblockTurbineTiersStartAtBaseTier() {
        RecipeNode node = new RecipeNode("mb_turbine", "Large Gas Turbine", 20.0, 2048.0, GTVoltageTier.EV);
        node.setGenerator(true);
        node.setMultiblock(true);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:large_gas_turbine"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        node.setTargetTier(GTVoltageTier.EV);

        List<GTVoltageTier> tiers = inspector.getInspectorTiers(node);
        Assertions.assertFalse(tiers.isEmpty());
        Assertions.assertEquals(GTVoltageTier.EV, tiers.get(0));
        Assertions.assertEquals(GTVoltageTier.MAX, tiers.get(tiers.size() - 1));
    }

    @Test
    public void testMultiRowGridChipLayoutNoOverflow() {
        int totalW = NodeInspectorPanel.PANEL_WIDTH - 16;
        int cols = 4;
        int gap = 4;
        int chipW = (totalW - gap * (cols - 1)) / cols;

        Assertions.assertEquals(179, totalW);
        Assertions.assertEquals(41, chipW);

        int maxRightEdge = 3 * (chipW + gap) + chipW;
        Assertions.assertEquals(176, maxRightEdge);
        Assertions.assertTrue(maxRightEdge <= totalW);
    }
}
