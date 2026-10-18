package com.gtceu.calcboard.compat.gtceu;

import com.google.gson.JsonObject;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFFuel;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFModuleSlot;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFModuleType;
import com.gtceu.calcboard.api.model.RecipeNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MCFRocketModuleFuelRegressionTest {

    @Test
    @DisplayName("BUG-FIX: Dense Hydrazine is removed and legacy IDs safely fallback")
    void testDenseHydrazineRemovedAndFallback() {
        assertNull(MCFFuel.fromFluidId(net.minecraft.resources.ResourceLocation.tryParse("gtceu:dense_hydrazine_fuel_mixture")));
        assertEquals(MCFFuel.CETANE_DIESEL, MCFFuel.fromIdOrDefault("DENSE_HYDRAZINE", MCFModuleType.UCM));
        assertEquals(MCFFuel.ROCKET_FUEL, MCFFuel.fromIdOrDefault("DENSE_HYDRAZINE", MCFModuleType.SRM));

        JsonObject legacyJson = new JsonObject();
        legacyJson.addProperty("slot", 0);
        legacyJson.addProperty("enabled", true);
        legacyJson.addProperty("type", "SRM");
        legacyJson.addProperty("fuel", "DENSE_HYDRAZINE");
        legacyJson.addProperty("oxBoost", true);

        MCFModuleSlot deserializedSlot = MCFModuleSlot.fromJson(legacyJson);
        assertEquals(MCFModuleType.SRM, deserializedSlot.getModuleType());
        assertEquals(MCFFuel.ROCKET_FUEL, deserializedSlot.getFuel());
    }

    @Test
    @DisplayName("BUG-FIX: Combustion modules (UCM, SCM) only allow combustion generator fuels")
    void testCombustionModuleFuels() {
        List<MCFFuel> ucmFuels = MCFModuleType.UCM.getAvailableFuels();
        assertTrue(ucmFuels.contains(MCFFuel.CETANE_DIESEL));
        assertTrue(ucmFuels.contains(MCFFuel.HIGH_OCTANE_GASOLINE));
        assertTrue(ucmFuels.contains(MCFFuel.GASOLINE));
        assertTrue(ucmFuels.contains(MCFFuel.DIESEL));
        assertTrue(ucmFuels.contains(MCFFuel.BIO_DIESEL));

        assertFalse(ucmFuels.contains(MCFFuel.ROCKET_FUEL));
        assertFalse(ucmFuels.contains(MCFFuel.RP_1));
        assertFalse(ucmFuels.contains(MCFFuel.MONOMETHYLHYDRAZINE));
        assertFalse(ucmFuels.contains(MCFFuel.SORBITOL_HYPERGOLIC_FUEL));

        assertEquals(MCFFuel.CETANE_DIESEL, MCFModuleType.UCM.getDefaultFuel());
        assertEquals(MCFFuel.CETANE_DIESEL, MCFModuleType.SCM.getDefaultFuel());
    }

    @Test
    @DisplayName("BUG-FIX: Rocket modules (SRM, NRM) only allow Star Technology modular rocket fuels")
    void testRocketModuleFuels() {
        List<MCFFuel> srmFuels = MCFModuleType.SRM.getAvailableFuels();
        assertTrue(srmFuels.contains(MCFFuel.ROCKET_FUEL));
        assertTrue(srmFuels.contains(MCFFuel.RP_1));
        assertTrue(srmFuels.contains(MCFFuel.MONOMETHYLHYDRAZINE));
        assertTrue(srmFuels.contains(MCFFuel.SORBITOL_HYPERGOLIC_FUEL));

        assertFalse(srmFuels.contains(MCFFuel.CETANE_DIESEL));
        assertFalse(srmFuels.contains(MCFFuel.HIGH_OCTANE_GASOLINE));
        assertFalse(srmFuels.contains(MCFFuel.GASOLINE));
        assertFalse(srmFuels.contains(MCFFuel.DIESEL));
        assertFalse(srmFuels.contains(MCFFuel.BIO_DIESEL));

        assertEquals(MCFFuel.ROCKET_FUEL, MCFModuleType.SRM.getDefaultFuel());
        assertEquals(MCFFuel.ROCKET_FUEL, MCFModuleType.NRM.getDefaultFuel());
    }

    @Test
    @DisplayName("BUG-FIX: Switching slot module type auto-coerces incompatible fuel to type default")
    void testSlotModuleTypeCoercion() {
        MCFModuleSlot slot = new MCFModuleSlot(0, true, MCFModuleType.UCM, MCFFuel.CETANE_DIESEL, false);
        assertEquals(MCFFuel.CETANE_DIESEL, slot.getFuel());

        slot.setModuleType(MCFModuleType.SRM);
        assertEquals(MCFFuel.ROCKET_FUEL, slot.getFuel());

        slot.setFuel(MCFFuel.SORBITOL_HYPERGOLIC_FUEL);
        assertEquals(MCFFuel.SORBITOL_HYPERGOLIC_FUEL, slot.getFuel());

        slot.setModuleType(MCFModuleType.SCM);
        assertEquals(MCFFuel.CETANE_DIESEL, slot.getFuel());
    }

    @Test
    @DisplayName("BUG-FIX: Rocket fuel energy density and consumption matches Star Technology specification")
    void testRocketFuelEnergyDensity() {
        assertEquals(5120.0, MCFFuel.ROCKET_FUEL.getEnergyPerMb(), 0.001);
        assertEquals(7680.0, MCFFuel.RP_1.getEnergyPerMb(), 0.001);
        assertEquals(12800.0, MCFFuel.MONOMETHYLHYDRAZINE.getEnergyPerMb(), 0.001);
        assertEquals(20480.0, MCFFuel.SORBITOL_HYPERGOLIC_FUEL.getEnergyPerMb(), 0.001);
    }

    @Test
    @DisplayName("BUG-FIX: SRM node does not duplicate Tungsten Disulfide port across Core and Aux")
    void testSRMNoDuplicateTungstenDisulfidePort() {
        com.gtceu.calcboard.testutil.SimulatedGTEnvironment.setupFullEnvironment();
        try {
            RecipeNode node = new RecipeNode("srm-test", "Supreme Rocket Module", 640.0, -32.0, com.gtceu.calcboard.api.type.GTVoltageTier.UV);
            node.setMachineIcon(GTCombustionHelper.START_T3_ROCKET);
            node.setRecipeCategoryId(net.minecraft.resources.ResourceLocation.tryParse("start_core:modular_rocket_module"));
            node.setGenerator(true);
            node.setMultiblock(true);

            com.gtceu.calcboard.api.model.IngredientStack fuel = com.gtceu.calcboard.api.model.IngredientStack.fluid(net.minecraft.resources.ResourceLocation.tryParse("gtceu:sorbitol_hypergolic_fuel"), "Sorbitol Hypergolic Fuel", 1.0);
            com.gtceu.calcboard.api.model.IngredientStack corruptedLube = com.gtceu.calcboard.api.model.IngredientStack.fluid(GTCombustionHelper.TUNGSTEN_DISULFIDE, "Tungsten Disulfide", 200.0);
            node.setBaseSpec(com.gtceu.calcboard.api.model.RecipeSpec.of(
                    "srm-test",
                    net.minecraft.resources.ResourceLocation.tryParse("start_core:modular_rocket_module"),
                    640.0,
                    -32.0,
                    List.of(fuel, corruptedLube),
                    List.of()
            ));

            List<com.gtceu.calcboard.api.model.ProjectedPort> projected = node.getProjectedInputs();

            long ws2Count = projected.stream()
                    .filter(p -> GTCombustionHelper.TUNGSTEN_DISULFIDE.equals(p.stack().getId()))
                    .count();
            assertEquals(1, ws2Count, "Tungsten Disulfide port must exist exactly ONCE");

            boolean hasCoreWs2 = projected.stream()
                    .anyMatch(p -> GTCombustionHelper.TUNGSTEN_DISULFIDE.equals(p.stack().getId()) && p.isCore());
            assertFalse(hasCoreWs2, "Tungsten Disulfide must NOT be projected as a CORE_RECIPE input");

            boolean hasAuxWs2 = projected.stream()
                    .anyMatch(p -> GTCombustionHelper.TUNGSTEN_DISULFIDE.equals(p.stack().getId()) && p.isAuxiliary());
            assertTrue(hasAuxWs2, "Tungsten Disulfide must only be projected as an AUXILIARY input");
        } finally {
            com.gtceu.calcboard.testutil.SimulatedGTEnvironment.tearDownEnvironment();
        }
    }
}
