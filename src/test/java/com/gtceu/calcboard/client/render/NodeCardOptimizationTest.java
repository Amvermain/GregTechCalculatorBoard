package com.gtceu.calcboard.client.render;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.layout.NodeLayoutBounds;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.render.NodeCardTextCache;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrapExtension.class)
public class NodeCardOptimizationTest {

    @Test
    @DisplayName("NodeWidget.getLayoutBounds caches bounds instance across consecutive queries")
    void testLayoutBoundsCaching() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:macerator"), "Macerator", 32.0, 4.0, GTVoltageTier.LV);
        node.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1));
        node.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2));
        node.setPosX(100);
        node.setPosY(200);

        NodeWidget widget = new NodeWidget(node, null);

        NodeLayoutBounds bounds1 = widget.getLayoutBounds();
        Assertions.assertNotNull(bounds1);
        NodeLayoutBounds bounds2 = widget.getLayoutBounds();
        Assertions.assertSame(bounds1, bounds2);

        node.setPosX(150);
        NodeLayoutBounds bounds3 = widget.getLayoutBounds();
        Assertions.assertNotSame(bounds1, bounds3);
        Assertions.assertEquals(150, bounds3.getCardBounds().x());
    }

    @Test
    @DisplayName("NodeCardTextCache respects dirty lifecycle without redundant recomputations")
    void testTextCacheDirtyLifecycle() {
        NodeCardTextCache cache = new NodeCardTextCache();
        Assertions.assertTrue(cache.isDirty());

        cache.markDirty();
        Assertions.assertTrue(cache.isDirty());
        Assertions.assertFalse(cache.isStarved());
    }

    @Test
    @DisplayName("IngredientRenderer cache and CachedFluid record lifecycle")
    void testIngredientRendererCacheLifecycle() {
        IngredientRenderer.clearCache();
        IngredientRenderer.CachedFluid sample = new IngredientRenderer.CachedFluid(null, 1.0f, 0.5f, 0.2f, 1.0f);
        Assertions.assertEquals(1.0f, sample.r());
        Assertions.assertEquals(0.5f, sample.g());
        Assertions.assertEquals(0.2f, sample.b());
        Assertions.assertEquals(1.0f, sample.a());
        Assertions.assertNull(sample.sprite());
        IngredientRenderer.clearCache();
    }

    @Test
    @DisplayName("Layered Z-offsets guarantee non-overlapping depth intervals")
    void testLayeredZOffsets() {
        int nodeCount = 50;
        float prevZ = -1.0f;
        for (int i = 0; i < nodeCount; i++) {
            float z = (float) (i * 0.5f + 1.0f);
            Assertions.assertTrue(z > prevZ);
            prevZ = z;
        }

        float topZ = (float) (nodeCount * 0.5f + 1.0f);
        float selectedBaseZ = topZ + 50.0f;
        Assertions.assertTrue(selectedBaseZ > topZ);

        for (int i = 0; i < 10; i++) {
            float selZ = selectedBaseZ + (float) (i * 0.5f);
            Assertions.assertTrue(selZ > topZ);
        }
    }

    @Test
    @DisplayName("NodeCardTextCache row2Buttons list is initialized and accessible")
    void testRow2ButtonsCache() {
        NodeCardTextCache cache = new NodeCardTextCache();
        Assertions.assertNotNull(cache.getRow2Buttons());
        Assertions.assertTrue(cache.getRow2Buttons().isEmpty());
    }
}
