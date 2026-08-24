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

        List<com.gtceu.calcboard.api.AddonCategory> activeCats = cap.getActiveCategoriesForNode(lcrNode);
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.COIL));
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.PARALLEL));
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.MAINTENANCE));
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.MULTIBLOCK_TRAIT));
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

        List<com.gtceu.calcboard.api.AddonCategory> activeCats = cap.getActiveCategoriesForNode(singleNode);
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.COIL));
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.PARALLEL));
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
        turbineNode.setMultiblock(true);

        List<com.gtceu.calcboard.api.AddonCategory> activeCats = cap.getActiveCategoriesForNode(turbineNode);
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.ROTOR));
        Assertions.assertFalse(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.COIL));
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

        List<com.gtceu.calcboard.api.AddonCategory> activeCats = cap.getActiveCategoriesForNode(dynamoNode);
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.THERMAL_AUGMENT));
        Assertions.assertFalse(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.COIL));
    }

    @Test
    void testRockFiltratorCoilRejection() {
        ResourceLocation rockCat = ResourceLocation.tryParse("gtceu:rock_filtrator");
        CategoryCapability cap = matrix.getCapability(rockCat);

        Assertions.assertNotNull(cap);
        Assertions.assertFalse(cap.canUseCoils());
    }

    @Test
    void testGasTurbineCapabilityAndRotorCategory() {
        ResourceLocation gasTurbineCat = ResourceLocation.tryParse("gtceu:gas_turbine");
        CategoryCapability cap = matrix.getCapability(gasTurbineCat);

        Assertions.assertNotNull(cap);
        Assertions.assertTrue(cap.isTurbine());
        Assertions.assertEquals(GTVoltageTier.EV, cap.turbineBaseTier());
        Assertions.assertEquals(4096.0, cap.turbineBaseProduction());

        RecipeNode turbineNode = RecipeNode.create("Gas Turbine (Nitrobenzene)", 20.0, 32.0, GTVoltageTier.LV);
        turbineNode.setRecipeCategoryId(gasTurbineCat);
        turbineNode.setGenerator(true);
        turbineNode.setMultiblock(true);

        List<com.gtceu.calcboard.api.AddonCategory> activeCats = cap.getActiveCategoriesForNode(turbineNode);
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.ROTOR));
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.MAINTENANCE));
        Assertions.assertFalse(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.PARALLEL));
        Assertions.assertFalse(activeCats.contains(com.gtceu.calcboard.api.AddonCategory.MULTIBLOCK_TRAIT));

        List<com.gtceu.calcboard.api.AddonCategory> relCats = MachineAddon.getRelevantCategories(turbineNode);
        Assertions.assertTrue(relCats.contains(com.gtceu.calcboard.api.AddonCategory.ROTOR));
        Assertions.assertTrue(relCats.contains(com.gtceu.calcboard.api.AddonCategory.MAINTENANCE));
        Assertions.assertFalse(relCats.contains(com.gtceu.calcboard.api.AddonCategory.PARALLEL));
    }

    @Test
    void testCustomMaterialTurbineRotorMatching() {
        MachineAddon enderiumRotor = new MachineAddon(
                "gtceu:rotor_enderium",
                "Enderium Turbine Rotor",
                MachineAddon.Category.ROTOR,
                "Turbine Fuel Efficiency 140%",
                ResourceLocation.tryParse("gtceu:turbine_rotor")
        );
        enderiumRotor.setDurationMultiplier(1.40);
        enderiumRotor.setRotorPower(130);

        RecipeNode node = RecipeNode.create("Gas Turbine (Nitrobenzene)", 20.0, 32.0, GTVoltageTier.LV);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:gas_turbine"));
        node.setGenerator(true);

        Assertions.assertTrue(MachineAddon.isTurbineMachine(node));
        node.addAddon(enderiumRotor);

        Assertions.assertEquals(1.40, node.getCombinedDurationMultiplier(), 0.001);
        Assertions.assertEquals("Enderium Turbine Rotor", enderiumRotor.getName());
        Assertions.assertEquals("gtceu:rotor_enderium", enderiumRotor.getId());
    }

    @Test
    void testTwoTrackCatalogLifecycle() {
        MachineAddonCatalog catalog = MachineAddonCatalog.getInstance();
        catalog.reset();

        Assertions.assertFalse(catalog.isReady());
        catalog.ensureFastLoaded();
        Assertions.assertTrue(catalog.isReady());
        Assertions.assertFalse(catalog.getAllAddons().isEmpty());

        // Fast load should provide basic traits
        Assertions.assertTrue(catalog.getAllAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT));
    }
}
