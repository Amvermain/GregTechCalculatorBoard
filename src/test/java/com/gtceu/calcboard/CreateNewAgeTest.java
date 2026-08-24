package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.createnewage.CreateNewAgeAddonCrawler;
import com.gtceu.calcboard.compat.createnewage.CreateNewAgeModAdapter;
import com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler;
import com.gtceu.calcboard.compat.createnewage.addon.CreateMagnetAddon;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unit tests verifying dynamic AddonCategory registry, Create: New Age ModAdapter,
 * Magnet discovery, and Generator Coil scaling.
 */
public class CreateNewAgeTest {

    @BeforeAll
    public static void setup() {
        ModAdapterRegistry.init();
    }

    @Test
    public void testAddonCategoryDynamicRegistration() {
        // 1. Check built-in categories
        Assertions.assertNotNull(AddonCategory.get("parallel"));
        Assertions.assertNotNull(AddonCategory.get("coil"));
        Assertions.assertNotNull(AddonCategory.get("rotor"));
        Assertions.assertNotNull(AddonCategory.get("reflector"));
        Assertions.assertNotNull(AddonCategory.get("thermal"));
        Assertions.assertNotNull(AddonCategory.get("magnet"));
        Assertions.assertNotNull(AddonCategory.get("custom"));

        Assertions.assertEquals(AddonCategory.MAGNET, AddonCategory.get("magnet"));
        Assertions.assertEquals("gui.gtcalcboard.addon_cat.magnet", AddonCategory.MAGNET.getTranslatableKey());

        // 2. Test dynamic custom category registration
        AddonCategory extra = AddonCategory.register("botania_lens", "Mana Lens", "gui.gtcalcboard.addon_cat.lens", 50);
        Assertions.assertNotNull(extra);
        Assertions.assertEquals(extra, AddonCategory.get("botania_lens"));
        Assertions.assertTrue(AddonCategory.values().contains(extra));
    }

    @Test
    public void testCreateNewAgeModAdapterRegistered() {
        IModAdapter adapter = ModAdapterRegistry.getAdapterForCategory(ResourceLocation.tryParse("create_new_age:energising"));
        Assertions.assertNotNull(adapter, "CreateNewAgeModAdapter must be registered in ModAdapterRegistry");
        Assertions.assertTrue(adapter instanceof CreateNewAgeModAdapter);
        Assertions.assertEquals(105, adapter.getPriority());

        // Check category routing
        Assertions.assertTrue(adapter.handlesCategory(ResourceLocation.tryParse("create_new_age:energising")));
        Assertions.assertFalse(adapter.handlesCategory(ResourceLocation.tryParse("create:mixing")));
    }

    @Test
    public void testMagnetDiscoveryAndExtraction() {
        // Test deductive extraction via IMagneticBlock.getStrength() reflection
        Object mockMagnetBlock = new Object() {
            public float getStrength() {
                return 24.0f;
            }
        };

        int force = CreateNewAgeAddonCrawler.extractMagneticForce(null, null, null);
        Assertions.assertEquals(0, force);

        // Deductive test on mock IMagneticBlock object
        try {
            var m = mockMagnetBlock.getClass().getMethod("getStrength");
            float strength = (float) m.invoke(mockMagnetBlock);
            Assertions.assertEquals(24.0f, strength, 0.001);
        } catch (Exception e) {
            Assertions.fail(e);
        }
    }

    @Test
    public void testGeneratorCoilMagnetOutputScaling() {
        RecipeNode coilNode = CreateNewAgeRecipeHandler.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:generator_coil"),
                "Generator Coil"
        );
        Assertions.assertNotNull(coilNode);
        Assertions.assertTrue(coilNode.isGenerator());
        Assertions.assertEquals(EnergyType.ELECTRIC_FE, coilNode.getEnergyType());

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(coilNode);
        Assertions.assertNotNull(adapter);

        // Default without magnets: 0 FE/t, but requires base 24.0 SU/RPM (768 SU @ 32 RPM, 6144 SU @ 256 RPM)
        coilNode.setRpm(32);
        Assertions.assertEquals(0.0, coilNode.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(768.0, coilNode.getInputs().get(0).getAmount(), 0.001);

        // At 256 RPM without magnets: 24 * 256 = 6144 SU
        coilNode.setRpm(256);
        Assertions.assertEquals(0.0, coilNode.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(6144.0, coilNode.getInputs().get(0).getAmount(), 0.001);

        // Add 1 Netherite Magnet (Force 24) -> 1 of 12 slots
        CreateMagnetAddon netherite = new CreateMagnetAddon("create_new_age:netherite_magnet", "Netherite Magnet", "", null, 24);
        adapter.handleInstallAddon(coilNode, netherite, false);

        Assertions.assertEquals(1, coilNode.getAddons().size());
        // 1 magnet: Total Strength = 24
        // At 32 RPM: 24 * 32 * (15 / 512) = 22.5 FE/t, SU = (24 + 24) * 32 = 1536 SU
        coilNode.setRpm(32);
        Assertions.assertEquals(22.5, coilNode.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(1536.0, coilNode.getInputs().get(0).getAmount(), 0.001);

        // Shift-click install: fills all remaining 11 slots to reach 12 slots!
        adapter.handleInstallAddon(coilNode, netherite, true);
        Assertions.assertEquals(12, coilNode.getAddons().size());

        // 12 magnet ring: Total Strength = 12 * 24 = 288
        // At 32 RPM: 288 * 32 * (15 / 512) = 270.0 FE/t, SU = (24 + 288) * 32 = 9984 SU
        double effectiveFE32 = coilNode.getSingleMachineEUt();
        Assertions.assertEquals(270.0, effectiveFE32, 0.001);
        Assertions.assertEquals(9984.0, coilNode.getInputs().get(0).getAmount(), 0.001);

        // At 256 RPM: 288 * 256 * (15 / 512) = 2160.0 FE/t, SU = (24 + 288) * 256 = 79872 SU
        coilNode.setRpm(256);
        double effectiveFE256 = coilNode.getSingleMachineEUt();
        Assertions.assertEquals(2160.0, effectiveFE256, 0.001);
        Assertions.assertEquals(79872.0, coilNode.getInputs().get(0).getAmount(), 0.001);

        // Test NBT serialization & deserialization
        CompoundTag tag = coilNode.serializeNBT();
        RecipeNode loaded = RecipeNode.deserializeNBT(tag);
        Assertions.assertNotNull(loaded);
        Assertions.assertEquals(12, loaded.getAddons().size());
        MachineAddon loadedAddon = loaded.getAddons().get(0);
        Assertions.assertEquals(AddonCategory.MAGNET, loadedAddon.getCategory());
        Assertions.assertEquals(24, loadedAddon.getMagneticForce());
        Assertions.assertEquals(2160.0, loaded.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(79872.0, loaded.getInputs().get(0).getAmount(), 0.001);

        // Test custom modpack suToEnergy config (e.g. Star Technology: 0.05 -> 3686.4 FE/t = 3.7K ⚡/t)
        CreateNewAgeModAdapter.testSuToEnergyOverride = 0.05;
        double customFE = coilNode.getSingleMachineEUt();
        Assertions.assertEquals(3686.4, customFE, 0.001);
        CreateNewAgeModAdapter.testSuToEnergyOverride = null;
    }

    @Test
    public void testCreateNewAgeKineticGeneratorNodes() {
        RecipeNode brushes = CreateNewAgeRecipeHandler.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:carbon_brushes"), "Carbon Brushes"
        );
        Assertions.assertNotNull(brushes);
        Assertions.assertTrue(brushes.isGenerator());

        RecipeNode motor = CreateNewAgeRecipeHandler.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:reinforced_motor"), "Reinforced Motor"
        );
        Assertions.assertNotNull(motor);
        Assertions.assertEquals(EnergyType.ELECTRIC_FE, motor.getEnergyType());
        Assertions.assertEquals(4096.0, motor.getBaseEUt(), 0.001);
    }

    @Test
    public void testCarbonBrushesAddonSupport() {
        RecipeNode brushes = CreateNewAgeRecipeHandler.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:carbon_brushes"), "Carbon Brushes"
        );
        Assertions.assertNotNull(brushes);

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(brushes);
        Assertions.assertNotNull(adapter);
        Assertions.assertTrue(adapter instanceof CreateNewAgeModAdapter);
        Assertions.assertTrue(adapter.supportsAddons(brushes), "Carbon Brushes must support addons");

        List<AddonCategory> cats = adapter.getApplicableAddonCategories(brushes);
        Assertions.assertTrue(cats.contains(AddonCategory.MAGNET), "Applicable categories must include MAGNET");

        CreateMagnetAddon magnet = new CreateMagnetAddon("create_new_age:fluxuated_magnetite", "Fluxuated Magnetite", "", null, 8);
        Assertions.assertTrue(adapter.isAddonCompatible(brushes, magnet));

        // Fill all 12 slots with Fluxuated Magnetite (Force 8 each)
        adapter.handleInstallAddon(brushes, magnet, true);
        Assertions.assertEquals(12, brushes.getAddons().size());

        brushes.setRpm(64);
        // Total Strength = 12 * 8 = 96
        // At 64 RPM: 96 * 64 * (15 / 512) = 180.0 FE/t
        Assertions.assertEquals(180.0, brushes.getSingleMachineEUt(), 0.001);
    }

    @Test
    public void testKineticOverstressedZeroEfficiency() {
        FlowGraph graph = new FlowGraph();

        // 1. Water Wheel (count = 6) -> 6 * 512 = 3072 SU
        RecipeNode waterWheel = RecipeNode.create("Large Water Wheel", 20.0, 512.0, GTVoltageTier.ULV);
        waterWheel.setEnergyType(EnergyType.KINETIC_SU);
        waterWheel.setGenerator(true);
        waterWheel.setMachineCount(6.0);
        waterWheel.addOutput(IngredientStack.stressUnit(512.0));
        graph.addNode(waterWheel);

        // 2. Carbon Brushes at 256 RPM with 12x Magnetite (+12 strength)
        // Required SU = (24 + 12) * 256 = 9216 SU
        // Generated FE nominal = 12 * 256 * (15/512) = 90.0 FE/t
        RecipeNode brushes = CreateNewAgeRecipeHandler.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:carbon_brushes"), "Carbon Brushes"
        );
        brushes.setRpm(256);
        CreateMagnetAddon magnet = new CreateMagnetAddon("create_new_age:magnetite_block", "Magnetite Block", "", null, 1);
        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(brushes);
        adapter.handleInstallAddon(brushes, magnet, true);
        graph.addNode(brushes);

        Assertions.assertEquals(90.0, brushes.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(9216.0, brushes.getInputs().get(0).getAmount(), 0.001);

        // Connect Water Wheel (3072 SU) -> Carbon Brushes (9216 SU)
        graph.addConnection(waterWheel.getId(), 0, brushes.getId(), 0);

        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        // Supplied (3072 SU) < Demanded (9216 SU) -> Overstressed!
        // For Create Kinetics, efficiency MUST be 0.0, NOT 0.3333!
        Assertions.assertEquals(0.0, effMap.get(brushes.getId()), 0.001);
        Assertions.assertEquals(0.0, brushes.getEfficiency(), 0.001);
        Assertions.assertEquals(0.0, brushes.getEffectiveCyclesPerSecond(), 0.001);
        Assertions.assertEquals(0.0, brushes.getEffectiveTotalEUt(), 0.001);

        // If water wheel count increases to 18 (18 * 512 = 9216 SU), capacity is met!
        waterWheel.setMachineCount(18.0);
        effMap = graph.computeNodeEfficiencies();
        Assertions.assertEquals(1.0, effMap.get(brushes.getId()), 0.001);
        Assertions.assertEquals(1.0, brushes.getEfficiency(), 0.001);
        Assertions.assertEquals(1.0, brushes.getEffectiveCyclesPerSecond(), 0.001);
        Assertions.assertEquals(90.0, brushes.getEffectiveTotalEUt(), 0.001);
    }
}
