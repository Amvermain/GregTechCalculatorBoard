package com.gtceu.calcboard.client.gui.compat.ae2;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class Ae2ItemTooltipRegressionTest {

    @BeforeEach
    public void setup() {
        BoardManager.getInstance().resetToDefault();
        PatternGraphRegistry.getInstance().clear();
    }

    @Test
    public void testNonPatternItemDoesNotDisplayAe2LinkedPageTooltip() {
        BoardPage page = BoardManager.getInstance().addPage("Page 1");
        RecipeNode node = RecipeNode.create("Crystalline Extractor", 20.0, 30.0, GTVoltageTier.MV);
        node.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:amethyst_shard"), "Amethyst Shard", 1.0, 1.0));
        page.getGraph().addNode(node);

        ItemStack amethystStack = new ItemStack(Items.AMETHYST_SHARD);
        List<Component> tooltipLines = new ArrayList<>();
        ItemTooltipEvent event = new ItemTooltipEvent(amethystStack, null, tooltipLines, TooltipFlag.Default.NORMAL);

        ClientAe2CraftConfirmHook.onItemTooltip(event);

        boolean hasAe2LinkedTooltip = tooltipLines.stream()
                .map(Component::getString)
                .anyMatch(line -> line.contains("⚡") || line.contains("연동됨") || line.contains("Linked"));

        Assertions.assertFalse(hasAe2LinkedTooltip, "Regular item should never display AE2 linked page tooltip even if board produces it");
    }
}
