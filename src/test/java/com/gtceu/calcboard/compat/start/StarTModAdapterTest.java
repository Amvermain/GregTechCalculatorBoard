package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.NodeRateCalculator;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying StarTModAdapter SPI compliance, SPT/NPT fluid booster synchronization,
 * and exact consumption rates according to Star Technology Core physics.
 */
class StarTModAdapterTest {

    @BeforeAll
    static void setup() {
        ModAdapterRegistry.init();
    }

    private RecipeNode createSPTNode() {
        RecipeNode node = new RecipeNode("test_spt", "Supreme Plasma Turbine", 20.0, 98304.0, GTVoltageTier.UHV);
        node.setGenerator(true);
        node.setMachineIcon(ResourceLocation.tryParse("start_core:supreme_plasma_turbine"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("start_core:plasma_generator"));
        node.setMultiblock(true);
        node.setBaseEUt(-98304.0);
        node.setTargetTier(GTVoltageTier.UHV);
        node.setRotorEfficiency(100);
        node.setRotorPower(100);
        node.setParallel(6);
        // Base plasma input
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:argon_plasma"), "Argon Plasma", 20.0, 1.0));
        return node;
    }

    private RecipeNode createNPTNode() {
        RecipeNode node = new RecipeNode("test_npt", "Nyinsane Plasma Turbine", 20.0, 196608.0, GTVoltageTier.UEV);
        node.setGenerator(true);
        node.setMachineIcon(ResourceLocation.tryParse("start_core:nyinsane_plasma_turbine"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("start_core:plasma_generator"));
        node.setMultiblock(true);
        node.setBaseEUt(-196608.0);
        node.setTargetTier(GTVoltageTier.UEV);
        node.setRotorEfficiency(100);
        node.setRotorPower(100);
        node.setParallel(12);
        // Base plasma input
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:argon_plasma"), "Argon Plasma", 20.0, 1.0));
        return node;
    }

    @Test
    @DisplayName("StarTModAdapter: Adapter Resolution & Booster SPI Support")
    void testStarTAdapterResolutionAndBoosterSPI() {
        RecipeNode spt = createSPTNode();
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(spt);
        assertNotNull(adapter);
        assertTrue(adapter instanceof StarTModAdapter, "SPT should be handled by StarTModAdapter");
        assertTrue(adapter.supportsBoosterControl(spt), "SPT should support booster control");

        // Verify colors and display components
        Component display = adapter.getBoosterDisplayComponent(spt);
        assertNotNull(display);
        assertTrue(display.getString().contains("0.9x") || display.toString().contains("0.9x") || display.getString().contains("turbine_boost_none"),
                "Default unboosted SPT display should indicate 0.9x or boost translation key");

        int bg = adapter.getBoosterBackgroundColor(spt, false);
        int border = adapter.getBoosterBorderColor(spt, false);
        int text = adapter.getBoosterTextColor(spt, false);
        assertNotEquals(0, bg);
        assertNotEquals(0, border);
        assertNotEquals(0, text);

        List<Component> tooltip = new ArrayList<>();
        adapter.buildBoosterTooltip(spt, tooltip);
        assertFalse(tooltip.isEmpty(), "Booster tooltip should not be empty");
    }

    @Test
    @DisplayName("StarTModAdapter: SPT Booster Cycling and Exact Fluid Rates")
    void testSPTBoosterFluidSynchronization() {
        RecipeNode spt = createSPTNode();
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(spt);
        assertNotNull(adapter);

        // 1. Initial State: Unboosted (0.9x, no booster fluids)
        adapter.syncBoosterInputs(spt);
        assertEquals(1, spt.getInputs().size(), "Only base plasma input should exist initially");

        // 2. Cycle to Passive Boost (WS2 only: 1.25x)
        adapter.cycleBooster(spt, 1);
        assertEquals(1.25, GTTurbineHelper.getTurbineBoostMultiplier(spt), 1e-6);
        assertEquals(2, spt.getInputs().size(), "WS2 fluid input should be added");
        IngredientStack ws2Stack = spt.getInputs().get(1);
        assertEquals("gtceu:tungsten_disulfide", ws2Stack.getId().toString());

        // Verify continuous flow rate: 1000 mB/hr = 0.277778 mB/s per machine
        Map<IngredientStack, Double> rates = NodeRateCalculator.calculateInputRates(spt);
        double ws2Rate = rates.get(ws2Stack);
        assertEquals(1000.0 / 3600.0, ws2Rate, 1e-4, "WS2 consumption rate should match 1000 mB/hr");

        // 3. Cycle to Full Boost (WS2 + SS-He3: 2.0x)
        adapter.cycleBooster(spt, 1);
        assertEquals(2.0, GTTurbineHelper.getTurbineBoostMultiplier(spt), 1e-6);
        assertEquals(3, spt.getInputs().size(), "Both WS2 and SS-He3 fluid inputs should exist");
        IngredientStack he3Stack = spt.getInputs().get(2);
        assertEquals("gtceu:superstate_helium_3", he3Stack.getId().toString());

        Map<IngredientStack, Double> fullRates = NodeRateCalculator.calculateInputRates(spt);
        double fullWs2Rate = fullRates.get(ws2Stack);
        double he3Rate = fullRates.get(he3Stack);
        assertEquals(1000.0 / 3600.0, fullWs2Rate, 1e-4, "WS2 consumption rate should match 1000 mB/hr");
        assertEquals(2500.0 / 3600.0, he3Rate, 1e-4, "SS-He3 consumption rate should match 2500 mB/hr");

        // 4. Cycle back to Unboosted (0.9x)
        adapter.cycleBooster(spt, 1);
        assertEquals(0.9, GTTurbineHelper.getTurbineBoostMultiplier(spt), 1e-6);
        assertEquals(1, spt.getInputs().size(), "Booster fluids should be removed when unboosted");
    }

    @Test
    @DisplayName("StarTModAdapter: NPT Booster Cycling and Exact Fluid Rates")
    void testNPTBoosterFluidSynchronization() {
        RecipeNode npt = createNPTNode();
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(npt);
        assertNotNull(adapter);

        // 1. Initial State: Unboosted (0.8x)
        adapter.syncBoosterInputs(npt);
        assertEquals(1, npt.getInputs().size());
        assertEquals(0.8, GTTurbineHelper.getTurbineBoostMultiplier(npt), 1e-6);

        // 2. Cycle to Passive Boost (WS2: 1.50x, 2,500 mB/hr)
        adapter.cycleBooster(npt, 1);
        assertEquals(1.50, GTTurbineHelper.getTurbineBoostMultiplier(npt), 1e-6);
        assertEquals(2, npt.getInputs().size());
        IngredientStack ws2Stack = npt.getInputs().get(1);
        assertEquals("gtceu:tungsten_disulfide", ws2Stack.getId().toString());

        Map<IngredientStack, Double> rates = NodeRateCalculator.calculateInputRates(npt);
        double ws2Rate = rates.get(ws2Stack);
        assertEquals(2500.0 / 3600.0, ws2Rate, 1e-4, "NPT WS2 consumption rate should match 2500 mB/hr");

        // 3. Cycle to Full Boost (WS2 + BEC-Og: 3.0x, 800 mB/hr)
        adapter.cycleBooster(npt, 1);
        assertEquals(3.0, GTTurbineHelper.getTurbineBoostMultiplier(npt), 1e-6);
        assertEquals(3, npt.getInputs().size());
        IngredientStack ogStack = npt.getInputs().get(2);
        assertEquals("gtceu:bec_og", ogStack.getId().toString());

        Map<IngredientStack, Double> fullRates = NodeRateCalculator.calculateInputRates(npt);
        double ogRate = fullRates.get(ogStack);
        assertEquals(800.0 / 3600.0, ogRate, 1e-4, "BEC-Og consumption rate should match 800 mB/hr");
    }

    @Test
    @DisplayName("GTCEu Standard Turbines Do Not Support Booster Control")
    void testStandardGTCEuTurbinesNoBooster() {
        RecipeNode lpt = new RecipeNode("test_lpt", "Large Plasma Turbine", 20.0, 16384.0, GTVoltageTier.LuV);
        lpt.setGenerator(true);
        lpt.setMachineIcon(ResourceLocation.tryParse("gtceu:large_plasma_turbine"));
        lpt.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_generator"));
        lpt.setMultiblock(true);

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(lpt);
        assertNotNull(adapter);
        assertFalse(adapter.supportsBoosterControl(lpt), "Standard LPT should not support booster control");
        assertNull(adapter.getBoosterDisplayComponent(lpt));
    }
}
