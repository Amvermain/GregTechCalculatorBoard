package com.gtceu.calcboard.client.gui.compat.createdieselgenerators;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.compat.IModGuiHandler;
import com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.createdieselgenerators.CDGRecipeHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CreateDieselGeneratorsModGuiHandlerTest {

    private static CreateDieselGeneratorsModGuiHandler handler;

    @BeforeAll
    public static void setUp() {
        ModAdapterRegistry.init();
        handler = new CreateDieselGeneratorsModGuiHandler();
    }

    @Test
    public void testRegistryResolutionForCDGNode() {
        RecipeNode node = RecipeNode.create("Diesel Engine", 20.0, 6144.0, GTVoltageTier.LV);
        node.setMachineIcon(CDGRecipeHandler.ITEM_DIESEL_ENGINE);
        node.setRecipeCategoryId(CDGRecipeHandler.CAT_DIESEL_COMBUSTION);
        node.setEnergyType(EnergyType.KINETIC_SU);
        node.setGenerator(true);

        IModGuiHandler resolved = ModGuiHandlerRegistry.getHandlerForNode(node);
        Assertions.assertNotNull(resolved);
        Assertions.assertInstanceOf(CreateDieselGeneratorsModGuiHandler.class, resolved);
        Assertions.assertEquals("createdieselgenerators", resolved.getModId());
    }

    @Test
    public void testGeneratorControlsHoveringDisabled() {
        RecipeNode engine = CDGRecipeHandler.createKineticGeneratorNode(CDGRecipeHandler.ITEM_DIESEL_ENGINE, "Diesel Engine");
        Assertions.assertNotNull(engine);
        Assertions.assertTrue(engine.isGenerator());

        Assertions.assertFalse(handler.isTierOrSpeedControlHovered(engine, 100, 100));
        Assertions.assertFalse(handler.isSecondaryControlHovered(engine, 100, 100));
    }

    @Test
    public void testPassiveNodeControlsHoveringDisabled() {
        RecipeNode passive = RecipeNode.create("Distillation", 100.0, 0.0, GTVoltageTier.ULV);
        passive.setEnergyType(EnergyType.NONE);
        Assertions.assertFalse(handler.isTierOrSpeedControlHovered(passive, 100, 100));
    }
}
