package com.gtceu.calcboard.compat.greate;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.create.CreateModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

public class GreateModAdapterTest {

    private static GreateModAdapter adapter;

    @BeforeAll
    public static void setUp() {
        ModAdapterRegistry.init();
        adapter = new GreateModAdapter();
    }

    @Test
    public void testAdapterRegistrationAndPriority() {
        IModAdapter registeredAdapter = ModAdapterRegistry.getAdapterForModId("greate");
        Assertions.assertNotNull(registeredAdapter);
        Assertions.assertInstanceOf(GreateModAdapter.class, registeredAdapter);

        Assertions.assertEquals(95, adapter.getPriority());
        CreateModAdapter createAdapter = new CreateModAdapter();
        Assertions.assertTrue(adapter.getPriority() > createAdapter.getPriority());
    }

    @Test
    public void testHandlesCategoryAndRouting() {
        ResourceLocation greateCategory = ResourceLocation.tryParse("greate:milling");
        ResourceLocation createCategory = ResourceLocation.tryParse("create:milling");

        Assertions.assertTrue(adapter.handlesCategory(greateCategory));
        Assertions.assertFalse(adapter.handlesCategory(createCategory));

        IModAdapter resolvedGreate = ModAdapterRegistry.getAdapterForCategory(greateCategory);
        Assertions.assertInstanceOf(GreateModAdapter.class, resolvedGreate);

        IModAdapter resolvedCreate = ModAdapterRegistry.getAdapterForCategory(createCategory);
        Assertions.assertInstanceOf(CreateModAdapter.class, resolvedCreate);
    }

    @Test
    public void testHandlesNode() {
        RecipeNode greateNode = RecipeNode.create("greate-mixer", 100.0, 32.0, GTVoltageTier.LV);
        greateNode.setMachineIcon(ResourceLocation.tryParse("greate:steel_mechanical_mixer"));
        Assertions.assertTrue(adapter.handlesNode(greateNode));

        CreateModAdapter createAdapter = new CreateModAdapter();
        Assertions.assertFalse(createAdapter.handlesNode(greateNode));
    }

    @Test
    public void testShaftCapacityTable() {
        Assertions.assertEquals(8.0, GreateProperties.getShaftCapacityForTier(0));
        Assertions.assertEquals(32.0, GreateProperties.getShaftCapacityForTier(1));
        Assertions.assertEquals(128.0, GreateProperties.getShaftCapacityForTier(2));
        Assertions.assertEquals(512.0, GreateProperties.getShaftCapacityForTier(3));
        Assertions.assertEquals(2048.0, GreateProperties.getShaftCapacityForTier(4));
        Assertions.assertEquals(8192.0, GreateProperties.getShaftCapacityForTier(5));
        Assertions.assertEquals(32768.0, GreateProperties.getShaftCapacityForTier(6));
        Assertions.assertEquals(131072.0, GreateProperties.getShaftCapacityForTier(7));
        Assertions.assertEquals(524288.0, GreateProperties.getShaftCapacityForTier(8));
        Assertions.assertEquals(2097152.0, GreateProperties.getShaftCapacityForTier(9));

        Assertions.assertEquals("ULS", GreateProperties.getTierName(0));
        Assertions.assertEquals("LS", GreateProperties.getTierName(1));
        Assertions.assertEquals("HS", GreateProperties.getTierName(3));
        Assertions.assertEquals("UHS", GreateProperties.getTierName(9));
    }

    @Test
    public void testKineticOverclockCalculation() {
        RecipeNode node = RecipeNode.create("Steel Mixer", 100.0, 32.0, GTVoltageTier.LV);
        node.setRpm(32);

        OverclockMode.OverclockResult result32 = adapter.computeOverclock(node, GTVoltageTier.LV, false);
        Assertions.assertEquals(100.0, result32.durationTicks(), 0.01);
        Assertions.assertEquals(32.0, result32.eut(), 0.01);

        node.setRpm(64);
        OverclockMode.OverclockResult result64 = adapter.computeOverclock(node, GTVoltageTier.LV, false);
        Assertions.assertEquals(50.0, result64.durationTicks(), 0.01);
        Assertions.assertEquals(64.0, result64.eut(), 0.01);
    }

    @Test
    public void testFanProcessingFixedDuration() {
        RecipeNode fanNode = RecipeNode.create("Tiered Fan", 150.0, 16.0, GTVoltageTier.ULV);
        fanNode.setRecipeCategoryId(ResourceLocation.tryParse("greate:splashing"));
        fanNode.setRpm(128);

        OverclockMode.OverclockResult result = adapter.computeOverclock(fanNode, GTVoltageTier.ULV, false);
        Assertions.assertEquals(150.0, result.durationTicks(), 0.01);
        Assertions.assertEquals(64.0, result.eut(), 0.01);
    }

    @Test
    public void testNodeValidationTierMismatchAndCapacity() {
        RecipeNode node = RecipeNode.create("Andesite Press", 100.0, 16.0, GTVoltageTier.ULV);
        node.setMachineCount(1.0);
        node.getProperties().set(GreateProperties.MACHINE_TIER, 0);       // ULV (8 SU)
        node.getProperties().set(GreateProperties.REQUIRED_RECIPE_TIER, 1); // LV

        List<Component> warnings = new java.util.ArrayList<>();
        boolean valid = adapter.validateNode(node, warnings);
        Assertions.assertFalse(valid);
        Assertions.assertEquals(2, warnings.size());
    }

    @Test
    public void testAdaptRecipeDetails() {
        Object mockRecipe = new Object() {
            public int getRecipeTier() { return 2; } // MV
            public int getCircuitNumber() { return 4; }
            public int getProcessingDuration() { return 80; }
        };

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();

        boolean adapted = adapter.adaptRecipeDetails(null, mockRecipe, details);
        Assertions.assertTrue(adapted);
        Assertions.assertEquals(EnergyType.KINETIC_SU, details.energyType);
        Assertions.assertEquals(GTVoltageTier.MV, details.tier);
        Assertions.assertEquals(80, details.durationTicks);
        Assertions.assertEquals(48.0, details.eut, 0.01); // (2 + 1) * 0.5 * 32 = 48.0 SU
    }

    @Test
    public void testFormatEnergyStatsWithShaftLimit() {
        RecipeNode node = RecipeNode.create("Aluminium Press", 100.0, 48.0, GTVoltageTier.MV);
        node.setEnergyType(EnergyType.KINETIC_SU);
        node.getProperties().set(GreateProperties.IS_GREATE, true);
        node.getProperties().set(GreateProperties.MACHINE_TIER, 2); // MS (128 SU capacity)
        node.setRpm(64); // speedFactor = 2.0 -> 96 SU

        String stats = adapter.formatEnergyStats(node, PowerDisplayMode.EUT);
        Assertions.assertTrue(stats.contains("96"));
        Assertions.assertTrue(stats.contains("128 SU"));

        List<Component> tooltip = adapter.buildEnergyTooltip(node);
        Assertions.assertFalse(tooltip.isEmpty());
        Assertions.assertTrue(tooltip.stream().anyMatch(c -> c.getString().contains("128 SU")));
        Assertions.assertTrue(tooltip.stream().anyMatch(c -> c.getString().contains("stress_headroom") || c.getString().contains("Headroom")));
    }

    @Test
    public void testMachineCountScalingDoesNotCauseShaftOverstress() {
        RecipeNode node = RecipeNode.create("Aluminium Press (Multiple)", 100.0, 48.0, GTVoltageTier.MV);
        node.setEnergyType(EnergyType.KINETIC_SU);
        node.getProperties().set(GreateProperties.IS_GREATE, true);
        node.getProperties().set(GreateProperties.MACHINE_TIER, 2); // MS (128 SU capacity)
        node.getProperties().set(GreateProperties.REQUIRED_RECIPE_TIER, 2);
        node.setRpm(64); // single machine stress = 96 SU (<= 128 SU)
        node.setMachineCount(2.0); // total stress = 192 SU (> 128 SU, but <= 256 SU total capacity)

        List<Component> warnings = new java.util.ArrayList<>();
        boolean valid = adapter.validateNode(node, warnings);
        Assertions.assertTrue(valid);
        Assertions.assertTrue(warnings.isEmpty());

        String stats = adapter.formatEnergyStats(node, PowerDisplayMode.EUT);
        Assertions.assertTrue(stats.contains("192"));
        Assertions.assertTrue(stats.contains("256 SU"));

        List<Component> tooltip = adapter.buildEnergyTooltip(node);
        Assertions.assertTrue(tooltip.stream().anyMatch(c -> {
            String text = c.getString();
            return text.contains("256 SU");
        }));
        Assertions.assertTrue(tooltip.stream().anyMatch(c -> {
            String text = c.getString();
            return text.contains("stress_headroom") || text.contains("Headroom");
        }));
    }
}
