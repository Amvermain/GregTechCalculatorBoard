package com.gtceu.calcboard.client.gui.compat.greate;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.compat.IModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.greate.GreateProperties;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class GreateModGuiHandlerTest {

    private static GreateModGuiHandler handler;

    @BeforeAll
    public static void setUp() {
        ModAdapterRegistry.init();
        handler = new GreateModGuiHandler();
    }

    @Test
    public void testRegistryResolutionForGreateNode() {
        RecipeNode node = RecipeNode.create("Greate Press", 100.0, 32.0, GTVoltageTier.ULV);
        node.setMachineIcon(ResourceLocation.tryParse("greate:andesite_mechanical_press"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("greate:packing"));
        node.setEnergyType(EnergyType.KINETIC_SU);
        node.getProperties().set(GreateProperties.MACHINE_TIER, 0);

        IModGuiHandler resolvedHandler = ModGuiHandlerRegistry.getHandlerForNode(node);
        Assertions.assertNotNull(resolvedHandler);
        Assertions.assertInstanceOf(GreateModGuiHandler.class, resolvedHandler);
        Assertions.assertEquals("greate", resolvedHandler.getModId());
    }

    @Test
    public void testHoverHitboxes() {
        RecipeNode node = RecipeNode.create("Greate Press", 100.0, 32.0, GTVoltageTier.ULV);
        node.setPosX(100);
        node.setPosY(100);
        node.setCardWidth(140);
        node.setRpm(32);
        node.getProperties().set(GreateProperties.MACHINE_TIER, 0);

        int row2Y = 100 + 20 + 6 + 18; // 144

        // Tier button at x + 6 (106) to x + 6 + 32 (138)
        Assertions.assertTrue(handler.isTierOrSpeedControlHovered(node, 110, row2Y + 5));
        Assertions.assertFalse(handler.isTierOrSpeedControlHovered(node, 100, row2Y + 5));
        Assertions.assertFalse(handler.isTierOrSpeedControlHovered(node, 145, row2Y + 5));

        // Secondary control (RPM) at x + 41 (141)
        Assertions.assertTrue(handler.isSecondaryControlHovered(node, 150, row2Y + 5));
    }

    @Test
    public void testTierCycling() {
        RecipeNode node = RecipeNode.create("Greate Press", 100.0, 32.0, GTVoltageTier.ULV);
        node.getProperties().set(GreateProperties.MACHINE_TIER, 0);
        node.getProperties().set(GreateProperties.REQUIRED_RECIPE_TIER, 0);

        Assertions.assertEquals(0, node.getProperties().get(GreateProperties.MACHINE_TIER));
        Assertions.assertEquals("ULS", GreateProperties.getTierName(0));

        GreateProperties.cycleTier(node, 1);
        Assertions.assertEquals(1, node.getProperties().get(GreateProperties.MACHINE_TIER));
        Assertions.assertEquals("LS", GreateProperties.getTierName(1));
        Assertions.assertEquals(GTVoltageTier.LV, node.getTargetTier());
        Assertions.assertEquals(32.0, node.getProperties().get(GreateProperties.SHAFT_CAPACITY));

        GreateProperties.cycleTier(node, -1);
        Assertions.assertEquals(0, node.getProperties().get(GreateProperties.MACHINE_TIER));
        Assertions.assertEquals("ULS", GreateProperties.getTierName(0));
    }

    @Test
    public void testNodeValidationAndDeficitWarning() {
        RecipeNode node = RecipeNode.create("Greate Compacting", 200.0, 128.0, GTVoltageTier.MV);
        node.setEnergyType(EnergyType.KINETIC_SU);
        node.getProperties().set(GreateProperties.IS_GREATE, true);
        node.getProperties().set(GreateProperties.REQUIRED_RECIPE_TIER, 2); // MS
        node.getProperties().set(GreateProperties.MACHINE_TIER, 0); // ULS (Deficit)

        com.gtceu.calcboard.compat.IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        Assertions.assertInstanceOf(com.gtceu.calcboard.compat.greate.GreateModAdapter.class, adapter);

        java.util.List<net.minecraft.network.chat.Component> warnings = new java.util.ArrayList<>();
        boolean operationalWithWarnings = adapter.validateNode(node, warnings);
        Assertions.assertFalse(operationalWithWarnings);
        Assertions.assertFalse(warnings.isEmpty());

        boolean operationalHeadless = adapter.validateNode(node, null);
        Assertions.assertFalse(operationalHeadless);

        node.getProperties().set(GreateProperties.MACHINE_TIER, 2); // Raised to MS
        warnings.clear();
        boolean satisfied = adapter.validateNode(node, warnings);
        Assertions.assertTrue(satisfied);
        Assertions.assertTrue(warnings.isEmpty());
    }

    @Test
    public void testGreateBadges() {
        RecipeNode node = RecipeNode.create("Greate Compacting", 200.0, 128.0, GTVoltageTier.MV);
        node.setEnergyType(EnergyType.KINETIC_SU);
        node.getProperties().set(GreateProperties.IS_GREATE, true);
        node.getProperties().set(GreateProperties.REQUIRED_RECIPE_TIER, 2); // MS
        node.getProperties().set(GreateProperties.MACHINE_TIER, 0); // ULS
        node.getProperties().set(GreateProperties.CIRCUIT_NUMBER, 2);
        node.getProperties().set(GreateProperties.HEAT_CONDITION, "HEATED");

        java.util.List<com.gtceu.calcboard.api.property.NodeBadge> badges =
                com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node);

        Assertions.assertTrue(badges.stream().anyMatch(b -> b.text().contains("MS Req")));
        Assertions.assertTrue(badges.stream().anyMatch(b -> b.text().contains("Heated")));
        Assertions.assertTrue(badges.stream().anyMatch(b -> b.text().contains("[#2]")));

        node.getProperties().set(GreateProperties.MACHINE_TIER, 2);
        java.util.List<com.gtceu.calcboard.api.property.NodeBadge> satisfiedBadges =
                com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node);

        Assertions.assertFalse(satisfiedBadges.stream().anyMatch(b -> b.text().contains("MS Req")));
        Assertions.assertTrue(satisfiedBadges.stream().anyMatch(b -> b.text().contains("Heated")));
        Assertions.assertTrue(satisfiedBadges.stream().anyMatch(b -> b.text().contains("[#2]")));
    }

    @Test
    public void testGreateModAdapterHandlesNodeWithIsGreateProperty() {
        RecipeNode node = RecipeNode.create("Custom Packing", 100.0, 32.0, GTVoltageTier.ULV);
        node.setMachineIcon(ResourceLocation.tryParse("create:mechanical_press"));
        node.setRecipeCategoryId(ResourceLocation.tryParse("tfg:compacting"));
        node.getProperties().set(GreateProperties.IS_GREATE, true);

        com.gtceu.calcboard.compat.IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        Assertions.assertInstanceOf(com.gtceu.calcboard.compat.greate.GreateModAdapter.class, adapter);

        IModGuiHandler guiHandler = ModGuiHandlerRegistry.getHandlerForNode(node);
        Assertions.assertInstanceOf(GreateModGuiHandler.class, guiHandler);
    }

    @Test
    public void testIgnoredWorkstationFiltering() {
        Assertions.assertTrue(EmiRecipeConverter.isIgnoredWorkstation(ResourceLocation.tryParse("gtceu:dimension_marker")));
        Assertions.assertTrue(EmiRecipeConverter.isIgnoredWorkstation(ResourceLocation.tryParse("start_core:temperature_marker_item")));
        Assertions.assertTrue(EmiRecipeConverter.isIgnoredWorkstation(null));
        Assertions.assertFalse(EmiRecipeConverter.isIgnoredWorkstation(ResourceLocation.tryParse("greate:steel_mechanical_press")));
    }

    @Test
    public void testGreateMachineHelperSafelyHandlesNonTiered() {
        RecipeNode node = RecipeNode.create("Test Press", 100.0, 32.0, GTVoltageTier.ULV);
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("create:basin"));
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("greate:andesite_mechanical_press"));

        Assertions.assertDoesNotThrow(() -> {
            GreateProperties.cycleTier(node, 1);
            com.gtceu.calcboard.compat.greate.GreateMachineHelper.syncMachineIconToTier(node, 1);
        });
        Assertions.assertEquals(1, node.getProperties().get(GreateProperties.MACHINE_TIER));
    }
}
