package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class SlimCardInteractionTest {

    @BeforeAll
    public static void init() {
        ModAdapterRegistry.init();
    }

    @BeforeEach
    public void setUp() {
        BoardManager.getInstance().setSlimCardMode(true);
    }

    @AfterEach
    public void tearDown() {
        BoardManager.getInstance().setSlimCardMode(false);
    }

    @Test
    public void testSlimCardModeDisablesCardTierControls() {
        RecipeNode node = RecipeNode.create("Centrifuge", 20.0, 30.0, GTVoltageTier.MV);
        node.setPos(100.0, 100.0);
        node.setCardWidth(200);

        NodeWidget widget = new NodeWidget(node);

        int row2Y = 100 + 20 + 6 + 18; // 144
        double tierBtnX = 100 + 10;
        double tierBtnY = row2Y + 5;

        BoardManager.getInstance().setSlimCardMode(true);
        Assertions.assertFalse(widget.isTierButtonHovered(tierBtnX, tierBtnY),
                "Tier button should not be hovered when in slim card mode");
        Assertions.assertFalse(widget.isOcButtonHovered(tierBtnX + 50, tierBtnY),
                "OC button should not be hovered when in slim card mode");
        Assertions.assertFalse(widget.isMachineConfigButtonHovered(tierBtnX + 80, tierBtnY),
                "Machine config button should not be hovered when in slim card mode");

        BoardManager.getInstance().setSlimCardMode(false);
        Assertions.assertTrue(widget.isTierButtonHovered(tierBtnX, tierBtnY),
                "Tier button should be hovered when not in slim card mode");
    }

    @Test
    public void testSlimCardModeAlternativeInputCyclingOnScroll() {
        RecipeNode node = RecipeNode.create("Centrifuge", 20.0, 30.0, GTVoltageTier.MV);
        node.setPos(100.0, 100.0);
        node.setCardWidth(200);

        IngredientStack in = IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1.0);
        in.setAlternatives(List.of(
                ResourceLocation.tryParse("minecraft:copper_ingot"),
                ResourceLocation.tryParse("minecraft:tin_ingot")
        ));
        node.getInputs().add(in);

        NodeWidget widget = new NodeWidget(node);

        BoardManager.getInstance().setSlimCardMode(true);

        float inX = widget.getInputPortX(0);
        float inY = widget.getInputPortY(0);

        Assertions.assertEquals(0, widget.getHoveredInputPortIndex(inX, inY));

        boolean scrolled = widget.mouseScrolled(inX, inY, 1.0);
        Assertions.assertTrue(scrolled, "Mouse scroll on input port with alternatives should be handled");

        Assertions.assertEquals(GTVoltageTier.MV, node.getTargetTier(),
                "Voltage tier must not change when scrolling over alternative input port in slim mode");
        Assertions.assertEquals(ResourceLocation.tryParse("minecraft:tin_ingot"), in.getId(),
                "Alternative input should cycle to tin ingot");
    }
}
