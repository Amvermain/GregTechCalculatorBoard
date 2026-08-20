package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class CategoryCapabilityMatrixTest {

    private CategoryCapabilityMatrix matrix;

    @BeforeEach
    void setUp() {
        matrix = CategoryCapabilityMatrix.getInstance();
    }

    @Test
    void testLargeChemicalReactorCapability() {
        ResourceLocation lcrCat = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        CategoryCapability cap = matrix.getCapability(lcrCat);

        Assertions.assertNotNull(cap);
        Assertions.assertTrue(cap.hasMultiblockOption());
        Assertions.assertTrue(cap.canUseCoils());
        Assertions.assertFalse(cap.isTurbine());
        Assertions.assertFalse(cap.isThermal());

        RecipeNode lcrNode = RecipeNode.create("Large Chemical Reactor (Benzene)", 20.0, 120.0, GTVoltageTier.HV);
        lcrNode.setRecipeCategoryId(lcrCat);
        lcrNode.setMultiblock(true);

        List<MachineAddon.Category> activeCats = cap.getActiveCategoriesForNode(lcrNode);
        Assertions.assertTrue(activeCats.contains(MachineAddon.Category.COIL));
        Assertions.assertTrue(activeCats.contains(MachineAddon.Category.PARALLEL));
        Assertions.assertTrue(activeCats.contains(MachineAddon.Category.MAINTENANCE));
        Assertions.assertTrue(activeCats.contains(MachineAddon.Category.MULTIBLOCK_TRAIT));
    }

    @Test
    void testChemicalReactorCapability() {
        ResourceLocation crCat = ResourceLocation.tryParse("gtceu:chemical_reactor");
        CategoryCapability cap = matrix.getCapability(crCat);

        Assertions.assertNotNull(cap);
        Assertions.assertTrue(cap.hasSingleblockOption());
        Assertions.assertTrue(cap.hasMultiblockOption());
        Assertions.assertTrue(cap.canUseCoils());

        RecipeNode singleNode = RecipeNode.create("Chemical Reactor (Rubber)", 20.0, 30.0, GTVoltageTier.LV);
        singleNode.setRecipeCategoryId(crCat);
        singleNode.setMultiblock(false);

        List<MachineAddon.Category> activeCats = cap.getActiveCategoriesForNode(singleNode);
        Assertions.assertTrue(activeCats.contains(MachineAddon.Category.COIL));
        Assertions.assertTrue(activeCats.contains(MachineAddon.Category.PARALLEL));
    }

    @Test
    void testSteamTurbineCapability() {
        ResourceLocation turbineCat = ResourceLocation.tryParse("gtceu:steam_turbine");
        CategoryCapability cap = matrix.getCapability(turbineCat);

        Assertions.assertNotNull(cap);
        Assertions.assertTrue(cap.isTurbine());
        Assertions.assertFalse(cap.canUseCoils());
        Assertions.assertEquals(GTVoltageTier.HV, cap.turbineBaseTier());
        Assertions.assertEquals(1024.0, cap.turbineBaseProduction());

        RecipeNode turbineNode = RecipeNode.create("Large Steam Turbine", 20.0, 1024.0, GTVoltageTier.HV);
        turbineNode.setRecipeCategoryId(turbineCat);
        turbineNode.setGenerator(true);

        List<MachineAddon.Category> activeCats = cap.getActiveCategoriesForNode(turbineNode);
        Assertions.assertTrue(activeCats.contains(MachineAddon.Category.ROTOR));
        Assertions.assertFalse(activeCats.contains(MachineAddon.Category.COIL));
    }

    @Test
    void testThermalDynamoCapability() {
        ResourceLocation lapidaryCat = ResourceLocation.tryParse("thermal:lapidary_fuel");
        CategoryCapability cap = matrix.getCapability(lapidaryCat);

        Assertions.assertNotNull(cap);
        Assertions.assertTrue(cap.isThermal());
        Assertions.assertFalse(cap.isTurbine());
        Assertions.assertFalse(cap.canUseCoils());

        RecipeNode dynamoNode = RecipeNode.create("Lapidary Dynamo", 20.0, 100.0, GTVoltageTier.LV);
        dynamoNode.setRecipeCategoryId(lapidaryCat);
        dynamoNode.setGenerator(true);

        List<MachineAddon.Category> activeCats = cap.getActiveCategoriesForNode(dynamoNode);
        Assertions.assertTrue(activeCats.contains(MachineAddon.Category.THERMAL_AUGMENT));
        Assertions.assertFalse(activeCats.contains(MachineAddon.Category.COIL));
    }

    @Test
    void testRockFiltratorCoilRejection() {
        ResourceLocation rockCat = ResourceLocation.tryParse("gtceu:rock_filtrator");
        CategoryCapability cap = matrix.getCapability(rockCat);

        Assertions.assertNotNull(cap);
        Assertions.assertFalse(cap.canUseCoils());
    }
}
